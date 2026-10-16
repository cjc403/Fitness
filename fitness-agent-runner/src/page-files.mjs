import { AgentError, dateRange, exactObject, ownerKey } from './policy.mjs'

// File capability only. This never creates or navigates a browser.
export class PageFiles {
  constructor(backend,artifacts){this.backend=backend;this.artifacts=artifacts;this.tasks=new Map();this.busy=false}
  async handle(action,body,session,token,current) {
    exactObject(body,action==='export'?['taskId','from','to']:['taskId','artifactId'])
    if(typeof body.taskId!=='string'||!/^[A-Za-z0-9_-]{1,80}$/.test(body.taskId))throw new AgentError('网页任务标识不正确')
    const owner=ownerKey(session),existing=this.tasks.get(body.taskId)
    if(existing&&existing.owner!==owner)throw new AgentError('成果不属于当前登录会话',403)
    if(this.busy)throw new AgentError('已有文件操作，请稍后核对结果',409)
    const verify=async()=>{if(!current())throw new AgentError('网页请求已停止',409);if(ownerKey(await this.backend.session(token))!==owner||!current())throw new AgentError('登录身份已变更',403)}
    this.busy=true
    try {
      await verify()
      if(action==='open') {
        if(!existing||!existing.artifacts.some(a=>a.id===body.artifactId))throw new AgentError('文件不属于当前网页任务',403)
        if(existing.opened.has(body.artifactId))return existing.opened.get(body.artifactId)
        const result=await this.artifacts.open(body.taskId,body.artifactId,verify)
        existing.opened.set(body.artifactId,result);return result
      }
      dateRange(body.from,body.to)
      if(!existing&&this.tasks.size>=30)throw new AgentError('当前执行器文件任务已达上限，请重启后继续',409)
      const entry=existing||{owner,artifacts:[],exports:new Map(),opened:new Map()}
      const key=body.from+'_'+body.to
      if(entry.exports.has(key))return entry.exports.get(key)
      if(entry.artifacts.length>=10)throw new AgentError('本任务文件已达上限')
      const rows=await this.backend.schedule(token,body.from,body.to)
      const artifact=await this.artifacts.schedule(body.taskId,rows,body.from,body.to,verify)
      entry.artifacts.push(artifact);entry.exports.set(key,artifact);this.tasks.set(body.taskId,entry)
      return artifact
    } finally {this.busy=false}
  }
}
