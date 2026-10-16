import { nextTick, watch } from 'vue'
import router from '@/router'
import { useAuthStore } from '@/store/modules/auth'
import { canAccessRoute } from '@/utils/accessControl.mjs'
import { createPageDriver } from './pageDriver.mjs'
import { PageTaskEngine } from './pageTaskEngine.mjs'
import { pageRequestGate } from './requestGate.mjs'
import type { ComputerTask, ComputerRequestConfig, ComputerResult, ComputerAgentStatus, ComputerArtifact } from '@/api/modules/computerAgent'

let engine: PageTaskEngine | undefined
const identity = () => {const a=useAuthStore(); return JSON.stringify([a.sessionGeneration,a.token,a.userInfo?.id,a.userInfo?.role,a.userInfo?.organizationId])}
async function api<T>(path: string, body?: unknown, signal?: AbortSignal, local=false): Promise<T> {
  const key=identity(),token=useAuthStore().token
  if(!token)throw Error('请先登录')
  if(navigator.onLine===false)throw Error('当前离线，无法执行网页任务')
  const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),35000)
  try {
    const response=await fetch((local?'/local-agent/v1':'/api/v1/agent')+path,{method:body===undefined?'GET':'POST',headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),credentials:'same-origin',redirect:'error',cache:'no-store',signal:signal?AbortSignal.any([signal,controller.signal]):controller.signal})
    if(identity()!==key)throw Error('登录身份已变更')
    let result
    try {result=await response.json()}catch{throw Error(local?'本机文件能力未连接，请启动本地服务':'网页规划服务返回格式不正确')}
    if(!response.ok||result.code!==0)throw Object.assign(Error(result.message||'网页任务请求失败'),{code:result.code})
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
  const driver=createPageDriver({document,window,router,allowed})
  // Opaque references keep user-supplied phone/email/identity values out of model prompts.
  const values=new Map<string,string>()
  const protect=(text: string)=>text.replace(/(?<!\d)\d{17}[0-9Xx](?!\d)|(?<!\d)1[3-9]\d{9}(?!\d)|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g,value=>{
    for(const [key,saved] of values)if(saved===value)return key
    const key=`{{USER_DATA_${values.size+1}}}`;values.set(key,value);return key
  })
  engine=new PageTaskEngine({identity,gate:pageRequestGate,
    session:(signal?: AbortSignal)=>api('/computer/session',undefined,signal),
    next:(body: {goal:string;input?:string},signal?: AbortSignal)=>api('/computer/next',{...body,goal:protect(body.goal),...(body.input?{input:protect(body.input)}:{})},signal),
    driver:{...driver,act:async(action:string,args:Record<string,string>)=>{
      if(action==='fill')args={...args,value:args.value.replace(/\{\{USER_DATA_\d+\}\}/g,key=>{const value=values.get(key);if(!value)throw Error('用户资料引用已失效');return value})}
      return driver.act(action,args)
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
export const getComputerAgentStatus=(config?:ComputerRequestConfig)=>result<ComputerAgentStatus>(async()=>{await api('/computer/session');ensure();return {available:true,platform:'current-page',scope:'正在使用的 FitChain 标签页',outputDirectory:'本机文件能力连接后可保存课表'}},config)
export const listComputerTasks=(config?:ComputerRequestConfig)=>result(()=>ensure().list() as ComputerTask[],config)
export const createComputerTask=(goal:string,config?:ComputerRequestConfig)=>result(()=>ensure().create(goal,config) as Promise<ComputerTask>,config)
export const getComputerTask=(id:string,config?:ComputerRequestConfig)=>result(()=>ensure().get(id) as ComputerTask,config)
export const pauseComputerTask=(id:string,config?:ComputerRequestConfig)=>result(()=>ensure().pause(id) as ComputerTask,config)
export const resumeComputerTask=(id:string,config?:ComputerRequestConfig)=>result(()=>ensure().resume(id) as ComputerTask,config)
export const stopComputerTask=(id:string,config?:ComputerRequestConfig)=>result(()=>ensure().stop(id) as ComputerTask,config)
export const confirmComputerTask=(id:string,confirmationId:string,config?:ComputerRequestConfig)=>result(()=>ensure().confirm(id,confirmationId) as Promise<ComputerTask>,config)
export const inputComputerTask=(id:string,message:string,config?:ComputerRequestConfig)=>result(()=>ensure().input(id,message) as ComputerTask,config)
export const suspendPageTasks=()=>engine?.suspend()
export const takeOverCurrentPage=()=>{engine?.invalidate();window.location.reload()}
