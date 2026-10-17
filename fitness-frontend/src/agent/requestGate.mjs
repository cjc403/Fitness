const copy = value => JSON.parse(JSON.stringify(value))
const uid = () => globalThis.crypto?.randomUUID?.() || `p${Date.now()}_${Math.random().toString(36).slice(2)}`
const readTools = new Set(['profile','courses','bookings','members','training_plans','students','leads','lead_options','retention','cash_summary','analytics'])
const readOnly = (method,path,data) => ['GET','HEAD','OPTIONS'].includes(method) || (method === 'POST' && path === '/api/v1/agent/query' && data && Object.keys(data).every(k=>['tool','arguments'].includes(k)) && readTools.has(data.tool)) || (method==='POST' && path==='/api/v1/member/recommendations' && data && Object.keys(data).every(k=>['goal','preferredTime'].includes(k)) && typeof data.goal==='string' && data.goal.length<=100 && ['ANY','MORNING','AFTERNOON','EVENING'].includes(data.preferredTime))
function fieldsOf(value, prefix = '', depth = 0, output = []) {
  if (depth > 10 || output.length >= 200) throw Error('请求参数过多，已阻止提交')
  if (value !== null && typeof value === 'object') {
    for (const [key, item] of Object.entries(value)) {
      if (/password|secret|token|api.?key/i.test(key)) throw Error('网页任务不能提交账号凭据')
      fieldsOf(item, prefix ? `${prefix}.${key}` : key, depth + 1, output)
    }
  } else {
    const text = String(value ?? '')
    if (text.length > 4000) throw Error('请求字段过长，已阻止提交')
    output.push({label:prefix || '内容',value:text})
  }
  return output
}
export function createRequestGate() {
  let binding = null, pending = null, awaitingTakeover = false, requiresFreshDocument = false
  const requests = new Set()
  const gate = {
    bind(hooks) { if (binding) throw Error('已有网页任务');if(requiresFreshDocument)throw Error('上一任务已停止，请刷新当前页清除旧表单的异步提交后再开始');awaitingTakeover=false; binding = {...hooks, controller:new AbortController()}; return gate },
    async intercept(config) {
      const owner = binding
      if (!owner) {
        let data=config.data;try{if(typeof data==='string')data=JSON.parse(data)}catch{}
        const path=new URL((config.baseURL||'/api').replace(/\/$/,'')+'/'+String(config.url||'').replace(/^\//,''),globalThis.location?.origin||'http://localhost').pathname
        if(awaitingTakeover&&!readOnly((config.method||'GET').toUpperCase(),path,data))throw Error('网页任务已停止；请刷新当前页后再提交')
        return config
      }
      const url = new URL((config.baseURL || '/api').replace(/\/$/, '') + '/' + String(config.url || '').replace(/^\//,''), globalThis.location?.origin || 'http://localhost')
      if (url.origin !== (globalThis.location?.origin || 'http://localhost') || !url.pathname.startsWith('/api/v1/')) throw Error('网页任务仅允许本页业务接口范围')
      if (url.pathname.startsWith('/api/v1/auth/')) throw Error('网页任务不能操作账号身份接口')
      config._pageAgent = owner
      config.signal = config.signal ? AbortSignal.any([config.signal,owner.controller.signal]) : owner.controller.signal
      requests.add(config)
      try {
        const method = (config.method || 'GET').toUpperCase()
        let data = config.data
        if (typeof data === 'string') { try {data=JSON.parse(data)} catch {throw Error('网页任务只允许预览 JSON 业务请求')} }
        const readonly = readOnly(method,url.pathname,data)
        if (readonly) return config
        // Reserve synchronously: two asynchronously validated submits never share a confirmation.
        if (pending) throw Error('同时触发多个写请求，请先核对当前确认')
        if (data != null && (typeof data !== 'object' || data instanceof FormData || data instanceof Blob)) throw Error('网页任务暂不支持文件上传或非 JSON 提交')
        const body = data == null ? undefined : JSON.stringify(data), params = config.params == null ? undefined : copy(config.params)
        if ((body?.length || 0) > 65536) throw Error('业务请求过大')
        const preview = {id:uid(),method,path:url.pathname+url.search,summary:'当前页面准备提交此请求；请核对冻结参数',fields:[...fieldsOf(data ?? {}),...(params ? fieldsOf(params,'查询参数') : [])]}
        const promise = new Promise((resolve,reject) => {pending={owner,config,preview,body,params,resolve,reject,url:globalThis.location?.href}})
        // Install rejection handling before any await, including session verification.
        promise.catch(()=>{})
        try {
          await owner.verify();if(binding!==owner||owner.controller.signal.aborted)throw Error('任务已停止')
          if(owner.prepare){
            const permit=await owner.prepare({method,path:url.pathname+url.search,body:data??null,params})
            if(binding!==owner||owner.controller.signal.aborted||pending?.config!==config)throw Error('任务已停止或许可已失效')
            preview.serverPermitId=permit.id;preview.before=permit.before;preview.expected=permit.request
          }
          owner.onPending(copy(preview))
        }
        catch(error) {if(pending?.config===config){pending.reject(error);pending=null}throw error}
        await promise
        if (binding !== owner || owner.controller.signal.aborted) throw Error('任务已停止')
        config.data = body; config.params = params; config.skipAuthRefresh = true
        if(preview.serverPermitId){config.headers??={};config.headers['X-FitChain-Agent-Permit']=preview.serverPermitId;config._pageAgentPermitId=preview.serverPermitId}
        return config
      } catch(error) { requests.delete(config); throw error }
    },
    async confirm(id) {
      const p=pending
      if (!p || p.preview.id!==id || p.confirming || p.refreshing) throw Error('确认已失效，请核对当前请求')
      p.confirming=true
      const epoch=p.epoch||0
      try {
        await p.owner.verify()
        if(p.url!==globalThis.location?.href)throw Error('页面已变化，请停止任务并重新核对提交')
        if(pending!==p || binding!==p.owner || p.owner.controller.signal.aborted) throw Error('确认已失效')
        if(p.preview.serverPermitId&&p.owner.approve){
          await p.owner.approve(p.preview.serverPermitId)
          await p.owner.verify()
          if(epoch!==(p.epoch||0))throw Object.assign(Error('任务确认已暂停，请恢复后重新核对'),{code:'PAUSED_TASK'})
          if(pending!==p||binding!==p.owner||p.owner.controller.signal.aborted)throw Error('任务已停止或确认失效')
        }
        pending=null; p.resolve()
      } catch(error) {if(error.code==='PAUSED_TASK'){p.confirming=false;throw error}if(pending===p){pending=null;p.reject(error)}throw error}
    },
    suspend(){if(pending)pending.epoch=(pending.epoch||0)+1},
    cancelPending(message='本次提交已取消'){if(pending){pending.epoch=(pending.epoch||0)+1;pending.reject(Error(message));requests.delete(pending.config);pending=null}},
    async refreshPending(){
      const p=pending;if(!p?.owner.prepare)return false
      p.epoch=(p.epoch||0)+1;p.refreshing=true
      try{
        const permit=await p.owner.prepare({method:p.preview.method,path:p.preview.path,body:p.body?JSON.parse(p.body):null,params:p.params})
        if(pending!==p||binding!==p.owner||p.owner.controller.signal.aborted)throw Error('任务已停止')
        Object.assign(p.preview,{id:uid(),serverPermitId:permit.id,before:permit.before,expected:permit.request});p.confirming=false;p.owner.onPending(copy(p.preview));return true
      }finally{p.refreshing=false}
    },
    settle(config,ok,detail,transport) {
      if(!config?._pageAgent)return
      requests.delete(config)
      if(config.skipAuthRefresh)config._pageAgent.onReceipt(ok,detail,transport)
    },
    async idle(current) {
      const start=Date.now()
      while(requests.size){if(!current())throw Error('网页任务已停止');if(!pending && Date.now()-start>30000)throw Error('页面请求未完成，请人工核对后重试');await new Promise(r=>setTimeout(r,40))}
    },
    cancel(message='网页任务已停止') {if(binding){awaitingTakeover=true;requiresFreshDocument=true}if(pending){pending.reject(Error(message));pending=null}binding?.controller.abort();requests.clear()},
    unbind({completed=false}={}) {gate.cancel();requiresFreshDocument=!completed;binding=null},
    // Clearing the latch in this document would release old async validation callbacks.
    // Manual takeover therefore creates a fresh document; this old gate stays blocked.
    takeOver(reload) {if(!binding&&awaitingTakeover&&typeof reload==='function')reload()},
    get pending() {return pending ? copy(pending.preview) : undefined}
  }
  return gate
}
export const pageRequestGate = createRequestGate()
