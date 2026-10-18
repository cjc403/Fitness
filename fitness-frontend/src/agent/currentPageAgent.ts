import { nextTick, watch } from 'vue'
import router from '@/router'
import { useAuthStore } from '@/store/modules/auth'
import { canAccessRoute } from '@/utils/accessControl.mjs'
import { createPageDriver } from './pageDriver.mjs'
import { PageTaskEngine } from './pageTaskEngine.mjs'
import { pageRequestGate, frozenRequestTarget } from './requestGate.mjs'
import { buildPageContext, chooseSemanticAction } from './pageContext.mjs'
import { createDocumentLeaseId } from './taskLease.mjs'
import { loadAgentVersion } from './capabilities.mjs'
import { historyTask } from './taskHistory.mjs'
import type { ComputerTask, ComputerRequestConfig, ComputerResult, ComputerAgentStatus, ComputerArtifact } from '@/api/modules/computerAgent'

let engine: PageTaskEngine | undefined
let v2Available=false
const documentNonce=createDocumentLeaseId()
type RemoteTask={id:string;version:number;goal:string;status:string;plan?:unknown;checkpoint?:unknown;createdAt:string;updatedAt:string}
const failureText:Record<string,string>={RATE_LIMITED:'模型服务限流，正在等待可用调用额度',MODEL_TIMEOUT:'模型规划超时，将核对页面后重试',MODEL_TRUNCATED:'模型输出被截断，请缩小本次任务范围',PROVIDER_AUTH:'模型服务凭据无效，请检查后台配置',PROVIDER_BALANCE:'模型服务余额不足',NOT_CONFIGURED:'尚未配置模型服务',CIRCUIT_OPEN:'模型服务暂时熔断，等待恢复',MODEL_PROTOCOL:'模型规划格式不符合工具协议',PROVIDER_UNAVAILABLE:'模型服务暂时不可用',MODEL_BUSY:'模型服务正在处理其他任务'}
const identity = () => {const a=useAuthStore(); return JSON.stringify([a.sessionGeneration,a.token,a.userInfo?.id,a.userInfo?.role,a.userInfo?.organizationId])}
async function api<T>(path: string, body?: unknown, signal?: AbortSignal, local=false): Promise<T> {
  const key=identity(),token=useAuthStore().token
  if(!token)throw Error('请先登录')
  if(navigator.onLine===false)throw Error('当前离线，无法执行网页任务')
  const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),/\/tasks\/[^/]+\/next$/.test(path)?100000:35000)
  try {
    const response=await fetch((local?'/local-agent/v1':'/api/v1/agent')+path,{method:body===undefined?'GET':'POST',headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),credentials:'same-origin',redirect:'error',cache:'no-store',signal:signal?AbortSignal.any([signal,controller.signal]):controller.signal})
    if(identity()!==key)throw Error('登录身份已变更')
    let result
    try {result=await response.json()}catch{throw Object.assign(Error(local?'本机文件能力未连接，请启动本地服务':'网页规划服务返回格式不正确'),{httpStatus:response.status})}
    if(!response.ok||result.code!==0)throw Object.assign(Error(result.message||'网页任务请求失败'),{code:result.code,httpStatus:response.status})
    return result.data
  } finally {clearTimeout(timer)}
}
function ensure() {
  if(engine)return engine
  const auth=useAuthStore()
  const allowed=(path: string)=>{
    if(!/^\/[a-z][a-z-]*$/.test(path)||['/login','/register','/change-password','/access-denied'].includes(path))return false
    const resolved=router.resolve(path)
    return resolved.matched.length>1&&canAccessRoute(resolved.meta,auth.userInfo?.role,auth.userInfo?.permissions)
  }
  const driver=createPageDriver({document,window,router,allowed,contextAdapter:{build:buildPageContext,choose:chooseSemanticAction}})
  // Opaque references keep user-supplied phone/email/identity values out of model prompts.
  const values=new Map<string,string>()
  const protect=(text: string)=>text.replace(/(?<!\d)\d{17}[0-9Xx](?!\d)|(?<!\d)1[3-9]\d{9}(?!\d)|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g,value=>{
    for(const [key,saved] of values)if(saved===value)return key
    const key=`{{USER_DATA_${values.size+1}}}`;values.set(key,value);return key
  })
  const command=(task:any)=>({version:task.remoteVersion,document:documentNonce})
  const persistence={
    create:async(goal:string,config:any)=>config.recover?api<RemoteTask>(`/tasks/${config.recover.id}/recover`,{version:config.recover.version,document:documentNonce},config.signal):api<RemoteTask>('/tasks',{goal:protect(goal),document:documentNonce},config.signal),
    next:async(task:any,body:any,signal:AbortSignal)=>{
      const turn=await api<any>(`/tasks/${task.id}/next`,{...command(task),observation:body.observation,history:body.history,artifacts:body.artifacts,...(body.input?{input:protect(body.input)}:{})},signal)
      if(turn.failure)turn.failure.message=(failureText[turn.failure.code]||'模型规划未完成')+'（诊断编号 '+turn.failure.traceId+'）'
      return turn
    },
    prepare:async(task:any,request:any)=>api<any>(`/tasks/${task.id}/prepare`,{...command(task),method:request.method,path:frozenRequestTarget(request.path,request.params),body:request.body}),
    approve:(task:any,receipt:string)=>api(`/tasks/${task.id}/approve/${receipt}`,command(task)),
    receipts:(task:any)=>api<any[]>(`/tasks/${task.id}/receipts`),
    finish:async(task:any,context:any,signal:AbortSignal)=>{const report=await api<any>(`/tasks/${task.id}/verify`,{...command(task),context},signal);task.remoteVersion=report.task.version;return report},
    pause:(task:any)=>api<RemoteTask>(`/tasks/${task.id}/pause`,{document:documentNonce}),
    resume:async(task:any)=>{const latest=await api<RemoteTask>(`/tasks/${task.id}`);return latest.status==='PAUSED'?api<RemoteTask>(`/tasks/${task.id}/resume`,{version:latest.version,document:documentNonce}):latest},
    revise:async(task:any,amendment:string)=>{const latest=await api<RemoteTask>(`/tasks/${task.id}`);return api<RemoteTask>(`/tasks/${task.id}/revise`,{version:latest.version,document:documentNonce,amendment:protect(amendment)})},
    close:async(task:any,status:string)=>{
      if(status==='SUCCEEDED')return
      for(let attempt=0;attempt<2;attempt++){const latest=await api<RemoteTask>(`/tasks/${task.id}`);if(['SUCCEEDED','FAILED','PARTIAL','CANCELLED'].includes(latest.status))return
        try{return await api(`/tasks/${task.id}/close`,{version:latest.version,document:documentNonce,status:['FAILED','PARTIAL','CANCELLED'].includes(status)?status:'CANCELLED'})}catch(e){if(attempt)throw e}
      }
    }
  }
  engine=new PageTaskEngine({identity,gate:pageRequestGate,persistence:v2Available?persistence:undefined,v2Persistence:persistence,
    session:(signal?: AbortSignal)=>api('/computer/session',undefined,signal),
    next:(body: {goal:string;input?:string},signal?: AbortSignal)=>api('/computer/next',{...body,goal:protect(body.goal),...(body.input?{input:protect(body.input)}:{})},signal),
    driver:{...driver,act:async(action:string,args:Record<string,string>,_session:unknown,canAct?:()=>boolean)=>{
      if(action==='use_skill'&&args.kind==='fill_fields'){
        const batch=args as unknown as {kind:string;fields:{field:string;value:string}[]}
        batch.fields=batch.fields.map(f=>({...f,value:f.value.replace(/\{\{USER_DATA_\d+\}\}/g,key=>{const value=values.get(key);if(!value)throw Error('用户资料引用已失效');return value})}))
      }
      if(action==='fill'||action==='use_skill'&&args.kind==='fill_field')args={...args,value:args.value.replace(/\{\{USER_DATA_\d+\}\}/g,key=>{const value=values.get(key);if(!value)throw Error('用户资料引用已失效');return value})}
      return driver.act(action,args,canAct)
    }},
    settle:async()=>{await nextTick();await new Promise(resolve=>setTimeout(resolve,400));await nextTick()},
    exportSchedule:(task:ComputerTask & {controller:AbortController;operationController:AbortController},args:{from:string;to:string})=>api<ComputerArtifact>('/page-files/export',{taskId:task.id,...args},AbortSignal.any([task.controller.signal,task.operationController.signal]),true),
    openArtifact:(task:ComputerTask & {controller:AbortController;operationController:AbortController},artifactId:string)=>api<string>('/page-files/open',{taskId:task.id,artifactId},AbortSignal.any([task.controller.signal,task.operationController.signal]),true),
    cleanup:()=>values.clear()
  })
  watch(identity,()=>{engine?.invalidate();values.clear()},{flush:'sync'})
  window.addEventListener('offline',()=>engine?.suspend('OFFLINE'))
  document.addEventListener('visibilitychange',()=>{if(document.hidden)engine?.suspend('PAGE_HIDDEN')})
  return engine
}
async function result<T>(operation:()=>T|Promise<T>,config:ComputerRequestConfig={}):Promise<ComputerResult<T>> {
  const key=identity(),assert=()=>{if(key!==identity()||config.signal?.aborted||config.isRequestCurrent?.()===false)throw Error('网页任务请求已失效')}
  assert();const data=await operation();assert();return {code:0,message:'成功',data}
}
export const getComputerAgentStatus=(config?:ComputerRequestConfig)=>result<ComputerAgentStatus>(async()=>{await api('/computer/session');v2Available=await loadAgentVersion(()=>api('/tasks/capabilities'))===2;const current=ensure();current.options.persistence=v2Available?current.options.v2Persistence:undefined;return {available:true,platform:v2Available?'current-page-v2':'current-page',scope:'正在使用的 FitChain 标签页',outputDirectory:'本机文件能力连接后可保存课表'}},config)
const remoteView=(t:RemoteTask,readOnly=false)=>historyTask(t,readOnly) as ComputerTask
export const listComputerTasks=(config?:ComputerRequestConfig)=>result(async()=>{const local=ensure().list() as ComputerTask[];if(!v2Available)return local;const remote=await api<{task:RemoteTask;readOnly:boolean}[]>('/tasks/history',undefined,config?.signal);return [...local,...remote.filter(t=>!local.some(l=>l.id===t.task.id)).map(t=>remoteView(t.task,t.readOnly))]},config)
export const createComputerTask=(goal:string,config?:ComputerRequestConfig)=>result(()=>ensure().create(goal,config) as Promise<ComputerTask>,config)
export const getComputerTask=(id:string,config?:ComputerRequestConfig)=>result(async()=>{const local=ensure().list().find(t=>t.id===id);if(local)return local as ComputerTask;if(v2Available){const item=await api<{task:RemoteTask;readOnly:boolean}>(`/tasks/history/${id}`,undefined,config?.signal);return remoteView(item.task,item.readOnly)}throw Error('未找到当前账号任务')},config)
export const pauseComputerTask=(id:string,config?:ComputerRequestConfig)=>result(()=>ensure().pause(id) as ComputerTask,config)
export const resumeComputerTask=(id:string,config?:ComputerRequestConfig)=>result(()=>ensure().resume(id) as ComputerTask,config)
export const stopComputerTask=(id:string,config?:ComputerRequestConfig)=>result(async()=>{if(ensure().list().some(t=>t.id===id))return ensure().stop(id) as ComputerTask;let task=await api<RemoteTask>(`/tasks/${id}`);if(['SUCCEEDED','FAILED','PARTIAL','CANCELLED'].includes(task.status))return remoteView(task);task=await api<RemoteTask>(`/tasks/${id}/recover`,{version:task.version,document:documentNonce},config?.signal);return remoteView(await api<RemoteTask>(`/tasks/${id}/cancel`,{version:task.version,document:documentNonce},config?.signal))},config)
export const recoverComputerTask=(id:string,config?:ComputerRequestConfig)=>result(async()=>{const t=await api<RemoteTask>(`/tasks/${id}`);if(t.goal.includes('{{USER_DATA_'))throw Error('旧任务的本地资料引用已失效，请重新描述完整任务；历史提交结果已保留');return ensure().create(t.goal,{...config,recover:{id,version:t.version}}) as Promise<ComputerTask>},config)
export const confirmComputerTask=(id:string,confirmationId:string,config?:ComputerRequestConfig)=>result(()=>ensure().confirm(id,confirmationId) as Promise<ComputerTask>,config)
export const inputComputerTask=(id:string,message:string,config?:ComputerRequestConfig)=>result(()=>ensure().input(id,message) as ComputerTask,config)
export const suspendPageTasks=()=>engine?.suspend()
export const takeOverCurrentPage=()=>{engine?.invalidate();window.location.reload()}
