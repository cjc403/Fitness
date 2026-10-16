const terminal = status => ['SUCCEEDED','FAILED','CANCELLED'].includes(status)
const copy = value => JSON.parse(JSON.stringify(value))
const sleep = ms => new Promise(resolve=>setTimeout(resolve,ms))
const stamp = () => new Date().toISOString()
const uid = () => globalThis.crypto?.randomUUID?.() || `t${Date.now()}_${Math.random().toString(36).slice(2)}`
const scope = session => JSON.stringify([session.userId,session.role,session.venueId,session.sessionKey])
const needsReceipt = goal => String(goal).split(/[，,。；;、]/).some(clause=>{
  if(/仅|只|不要|不必|无需|不提交|不保存|不新建|不修改|不发送/.test(clause))return false
  if(/(?:打开|查看|浏览).*(?:预约|签到|消息)/.test(clause)&&!/新建|新增|提交|保存|发送|帮我预约/.test(clause))return false
  if(/文件|CSV|导出/i.test(clause)&&!/新建(?:潜客|会员|预约)|提交|保存记录|发送消息|发布计划/.test(clause))return false
  return /新建|新增|创建|修改|删除|提交|保存|发送|发布|退款|开卡|帮我预约|取消预约|办理签到/.test(clause)
})
export class PageTaskEngine {
  constructor(options) {this.options={interval:7000,timeout:600000,waitTimeout:300000,...options};this.tasks=[];this.lifecycle=0}
  owned(t) {return t && t.identity===this.options.identity()}
  current(t) {return this.owned(t) && !terminal(t.status) && !t.controller.signal.aborted}
  public(t) {const {identity,session,controller,operationController,version,input,history,pausedStatus,...value}=t;return copy(value)}
  list() {return this.tasks.filter(t=>this.owned(t)).map(t=>this.public(t))}
  raw(id) {const t=this.tasks.find(t=>t.id===id);if(!this.owned(t))throw Error('网页任务不属于当前登录身份');return t}
  get(id) {return this.public(this.raw(id))}
  touch(t) {t.updatedAt=stamp()}
  async verify(t) {
    if(!this.current(t))throw Error('网页任务已停止或身份已变更')
    const session=await this.options.session(t.controller.signal)
    if(!this.current(t) || scope(session)!==scope(t.session))throw Error('登录或门店身份已变更，任务已停止')
  }
  async runnable(t) {
    const start=Date.now()
    while(this.current(t) && t.status!=='RUNNING') {
      if(Date.now()-start>this.options.waitTimeout)throw Error('等待超时，请重新开始任务')
      await sleep(40)
    }
    if(!this.current(t))throw Error('任务已停止或身份已变更')
  }
  async create(goal,config={}) {
    if(typeof goal!=='string' || !goal.trim() || goal.length>2000)throw Error('目标须为1至2000字')
    if(this.runningTask || this.tasks.some(t=>this.current(t)))throw Error('已有进行中的网页任务，或上一任务正在停止，请稍后核对')
    const identity=this.options.identity(),lifecycle=this.lifecycle,session=await this.options.session(config.signal)
    if(identity!==this.options.identity()||lifecycle!==this.lifecycle||config.signal?.aborted||config.isRequestCurrent?.()===false)throw Error('创建请求已取消或当前页面状态已变更')
    if(this.tasks.some(t=>this.current(t)))throw Error('已有进行中的网页任务')
    const t={id:uid(),goal:goal.trim(),status:'RUNNING',createdAt:stamp(),updatedAt:stamp(),steps:[],artifacts:[],identity,session,controller:new AbortController(),version:0,history:[],input:''}
    this.options.gate.bind({verify:async()=>{await this.verify(t);if(t.status==='PAUSED')throw Object.assign(Error('任务已暂停，请继续后重新确认'),{code:'PAUSED_TASK'})},onPending:preview=>{if(this.current(t)){t.pendingConfirmation=preview;t.status='WAITING_CONFIRMATION';this.touch(t)}},onReceipt:(ok,detail)=>{t.steps.push({index:t.steps.length+1,action:'submit',label:'业务提交回执',status:ok?'SUCCEEDED':'FAILED',detail});if(!ok)t.error=detail;this.touch(t)}})
    this.tasks=[t,...this.tasks.filter(item=>this.owned(item))].slice(0,30)
    this.runningTask=t
    void this.run(t)
    return this.public(t)
  }
  pause(id) {const t=this.raw(id);if(!['RUNNING','WAITING_CONFIRMATION','WAITING_INPUT'].includes(t.status))throw Error('当前任务不可暂停');t.pausedStatus=t.status;t.status='PAUSED';t.version++;t.operationController?.abort();this.touch(t);this.options.driver.clear();return this.public(t)}
  resume(id) {const t=this.raw(id);if(t.status!=='PAUSED')throw Error('当前任务不可继续');t.status=t.pausedStatus||'RUNNING';t.pausedStatus=undefined;this.touch(t);return this.public(t)}
  stop(id) {const t=this.tasks.find(t=>t.id===id);if(!t)return;if(!terminal(t.status)){t.status='CANCELLED';t.version++;t.controller.abort();t.pendingConfirmation=undefined;this.options.gate.cancel();this.options.driver.clear();this.touch(t)}return this.public(t)}
  async confirm(id,confirmationId) {
    const t=this.raw(id);if(t.status!=='WAITING_CONFIRMATION' || t.pendingConfirmation?.id!==confirmationId)throw Error('确认已失效')
    try {await this.options.gate.confirm(confirmationId)} catch(error) {if(error.code==='PAUSED_TASK')throw error;t.error=error.message;t.status='FAILED';t.controller.abort();this.options.gate.cancel();this.touch(t);throw error}
    if(!this.current(t))throw Error('任务已停止')
    t.pendingConfirmation=undefined;t.status='RUNNING';this.touch(t);return this.public(t)
  }
  input(id,message) {const t=this.raw(id);if(t.status!=='WAITING_INPUT'||!message?.trim()||message.length>2000)throw Error('当前任务不接受此补充');t.input=(t.input+'\n'+message.trim()).slice(-2000);t.question=undefined;t.status='RUNNING';this.touch(t);return this.public(t)}
  suspend() {this.lifecycle++;for(const t of this.tasks)if(this.current(t)&&t.status!=='PAUSED')this.pause(t.id)}
  invalidate() {this.lifecycle++;for(const t of this.tasks)if(!terminal(t.status))this.stop(t.id);this.tasks=[]}
  async run(t) {
    let effects=0,lastDecision=0,retries=0
    const started=Date.now()
    const fail=message=>{if(!terminal(t.status)){t.error=message;t.status='FAILED';t.controller.abort();this.options.gate.cancel();this.touch(t)}}
    const deadline=setTimeout(()=>fail('任务超过10分钟，已停止；已提交的请求请人工核对'),this.options.timeout)
    let checking=false
    const watch=setInterval(async()=>{
      if(!this.current(t)||checking)return
      if(t.status!=='RUNNING'&&Date.now()-Date.parse(t.updatedAt)>this.options.waitTimeout){fail('等待超时，已阻止挂起操作');return}
      checking=true
      try {if(globalThis.navigator?.onLine!==false)await this.verify(t)}catch{fail('登录身份已失效，已停止后续操作')}finally{checking=false}
    },2000)
    try {
      for(let count=0;count<30;) {
        await this.runnable(t);if(Date.now()-started>this.options.timeout)throw Error('任务执行超时，已停止')
        await this.verify(t);await this.options.settle();await this.options.gate.idle(()=>this.current(t))
        const version=t.version,observation=await this.options.driver.observe(t.session)
        const delay=Math.max(0,this.options.interval-(Date.now()-lastDecision));if(delay)await sleep(delay)
        await this.runnable(t);if(t.version!==version)continue
        let decision
        try {lastDecision=Date.now();decision=await this.options.next({goal:t.goal,observation,history:t.history.slice(-12),artifacts:t.artifacts.map(({id,name})=>({id,name})),...(t.input?{input:t.input}:{})},t.controller.signal)}
        catch(error){if(error.code===1001&&retries++<2)continue;throw error}
        if(!this.current(t))throw Error('任务已停止或身份已变更')
        if(t.version!==version){await this.runnable(t);continue}
        await this.verify(t);await this.runnable(t)
        if(t.version!==version)continue
        const {action,arguments:args}=decision
        if(action==='finish') {
          if(t.error)throw Error(t.error)
          if(!effects)throw Error('尚未在当前页面执行操作，不能报告完成')
          if(needsReceipt(t.goal)&&!t.steps.some(s=>s.action==='submit'&&s.status==='SUCCEEDED'))throw Error('尚未取得实际业务提交成功回执，不能报告完成')
          if(/(?:生成|导出).*(?:文件|CSV)|(?:文件|CSV).*(?:生成|导出)/i.test(t.goal)&&!t.artifacts.length)throw Error('尚未生成所需成果文件')
          if(/文件|导出|CSV|生成.*课表/i.test(t.goal)&&/打开/.test(t.goal)&&!t.steps.some(s=>s.action==='open_artifact'&&s.status==='SUCCEEDED'))throw Error('尚未请求系统打开成果文件，不能报告完成')
          t.status='SUCCEEDED';this.touch(t);break
        }
        if(action==='ask_user'){t.question=String(args.question).slice(0,500);t.status='WAITING_INPUT';this.touch(t);continue}
        const step={index:t.steps.length+1,action,label:({navigate:'切换当前网页',click:'点击页面控件',fill:'填写页面表单',select:'选择页面选项',scroll:'滚动当前页面',back:'返回上一页面',observe:'读取当前页面',export_schedule:'生成课表文件',open_artifact:'请求打开成果文件'})[action]||action,status:'RUNNING'}
        t.steps.push(step);this.touch(t);count++
        try {
          if(['export_schedule','open_artifact'].includes(action))t.operationController=new AbortController()
          if(action==='export_schedule') {
            if(t.artifacts.length>=10)throw Error('本任务成果文件数量已达上限')
            const artifact=await this.options.exportSchedule(t,args);if(!t.artifacts.some(a=>a.id===artifact.id))t.artifacts.push(artifact);step.detail=`已生成 ${artifact.name}`;effects++
          } else if(action==='open_artifact') {
            if(!t.artifacts.some(a=>a.id===args.artifactId))throw Error('只能打开本任务已生成文件')
            step.detail=await this.options.openArtifact(t,args.artifactId);effects++
          } else {
            step.detail=await this.options.driver.act(action,args,t.session)
            if(action!=='observe')effects++
          }
          await this.options.settle();await this.options.gate.idle(()=>this.current(t));await this.runnable(t)
          if(t.error)throw Error(t.error)
          step.status='SUCCEEDED';t.history.push({action,summary:String(step.detail).slice(0,500),ok:true});retries=0
        } catch(error) {
          if(t.status==='PAUSED'&&t.operationController?.signal.aborted&&error.name==='AbortError'){
            step.status='PAUSED';step.detail='文件请求已中断，继续后将重新核对成果；已开始的系统操作不能撤回'
            t.history.push({action,summary:step.detail,ok:false});await this.runnable(t);continue
          }
          step.status='FAILED';step.detail=error.message
          t.history.push({action,summary:String(error.message).slice(0,500),ok:false})
          if(error.code!=='STALE_PAGE' || retries++>=2)throw error
        }
        this.touch(t)
      }
      if(this.current(t))throw Error('已达到30步上限，请查看实际结果后继续')
    } catch(error) {if(!terminal(t.status)){t.status=this.owned(t)?'FAILED':'CANCELLED';t.error=error.message;this.touch(t)}}
    finally {clearTimeout(deadline);clearInterval(watch);t.controller.abort();t.pendingConfirmation=undefined;this.options.gate.unbind({completed:t.status==='SUCCEEDED'});this.options.driver.clear();this.options.cleanup?.();t.input='';if(this.runningTask===t)this.runningTask=null}
  }
}
