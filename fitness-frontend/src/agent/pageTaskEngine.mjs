const terminal = status => ['SUCCEEDED','PARTIAL','FAILED','CANCELLED'].includes(status)
import { classifyFailure } from './taskContract.mjs'
const copy = value => JSON.parse(JSON.stringify(value))
const sleep = ms => new Promise(resolve=>setTimeout(resolve,ms))
const stamp = () => new Date().toISOString()
const uid = () => globalThis.crypto?.randomUUID?.() || `t${Date.now()}_${Math.random().toString(36).slice(2)}`
const scope = session => JSON.stringify([session.userId,session.role,session.venueId,session.sessionKey])
const needsReceipt = goal => String(goal).split(/[，,。；;、]/).some(clause=>{
  if(/不要|不必|无需|不提交|不保存|不新建|不修改|不发送/.test(clause))return false
  if(/(?:打开|查看|浏览).*(?:预约|签到|消息)/.test(clause)&&!/新建|新增|提交|保存|发送|帮我预约/.test(clause))return false
  if(/文件|CSV|导出/i.test(clause)&&!/新建(?:潜客|会员|预约)|提交|保存记录|发送消息|发布计划/.test(clause))return false
  return /新建|新增|创建|修改|改为|改成|改到|设为|设成|调整为|调整到|更新|删除|提交|保存|发送|发布|退款|开卡|帮我预约|取消预约|办理签到/.test(clause)
})
export class PageTaskEngine {
  constructor(options) {this.options={interval:7000,timeout:900000,waitTimeout:600000,...options};this.tasks=[];this.lifecycle=0}
  owned(t) {return t && t.identity===this.options.identity()}
  current(t) {return this.owned(t) && !terminal(t.status) && !t.controller.signal.aborted}
  public(t) {const {identity,session,controller,operationController,version,input,history,pausedStatus,remoteTransition,factFingerprints,execution,verifyRecovery,...value}=t;return copy(value)}
  list() {return this.tasks.filter(t=>this.owned(t)).map(t=>this.public(t))}
  raw(id) {const t=this.tasks.find(t=>t.id===id);if(!this.owned(t))throw Error('网页任务不属于当前登录身份');return t}
  get(id) {return this.public(this.raw(id))}
  executionTime(t) {return (t.execution?.elapsed||0)+(t.execution?.running?Date.now()-t.execution.at:0)}
  touch(t) {const now=Date.now(),elapsed=this.executionTime(t);t.execution={elapsed,at:now,running:t.status==='RUNNING'};t.updatedAt=stamp()}
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
    const remote=this.options.persistence&&!/导出|CSV|文件/i.test(goal)?await this.options.persistence.create(goal.trim(),config):null
    if(identity!==this.options.identity()||lifecycle!==this.lifecycle||config.signal?.aborted||config.isRequestCurrent?.()===false){if(remote)this.options.persistence.close({...remote,remoteVersion:remote.version},'CANCELLED').catch(()=>{});throw Error('创建请求已取消')}
    const t={id:remote?.id||uid(),goal:goal.trim(),status:remote?.status==='PAUSED'?'PAUSED':'RUNNING',...(remote?.status==='PAUSED'?{pausedStatus:'RUNNING',pauseReason:'USER'}:{}),createdAt:stamp(),updatedAt:stamp(),steps:[],artifacts:[],identity,session,controller:new AbortController(),version:0,history:[],input:'',...(remote?{durable:true,remoteVersion:remote.version,plan:remote.plan,verification:undefined,facts:[],receipts:[]}:{} )}
    t.verifyRecovery=!!config.recover&&t.durable===true
    this.options.gate.bind({verify:async()=>{await this.verify(t);if(t.status==='PAUSED')throw Object.assign(Error('任务已暂停，请继续后重新确认'),{code:'PAUSED_TASK'})},...(remote?{prepare:r=>this.options.persistence.prepare(t,r),approve:id=>this.options.persistence.approve(t,id)}:{}),onPending:preview=>{if(this.current(t)){t.pendingConfirmation=preview;if(t.status==='PAUSED')t.pausedStatus='WAITING_CONFIRMATION';else t.status='WAITING_CONFIRMATION';this.touch(t)}},onReceipt:(ok,detail,transport)=>{
      t.steps.push({index:t.steps.length+1,action:'submit',label:'业务提交回执',status:ok?'SUCCEEDED':'FAILED',detail})
      // The planner must receive the confirmed transport result, not infer it from page text.
      // `observe` is an existing feedback action accepted by the planning protocol.
      t.history.push({action:'observe',summary:`真实请求回执：人工确认已完成；${ok?'业务提交成功':'业务提交失败'}。${detail}`.slice(0,500),ok})
      if(t.durable){if(transport?.receiptId)t.receipts.push(transport.receiptId);if(!ok)t.lastFailure={code:'WRITE_UNKNOWN',detail,permitId:transport?.permitId}}
      else if(!ok)t.error=detail;this.touch(t)
    }})
    this.tasks=[t,...this.tasks.filter(item=>this.owned(item))].slice(0,30)
    this.runningTask=t
    void this.run(t)
    return this.public(t)
  }
  pause(id,reason='USER') {const t=this.raw(id);if(!['RUNNING','WAITING_CONFIRMATION','WAITING_INPUT'].includes(t.status))throw Error('当前任务不可暂停');t.pausedStatus=t.status;t.pauseReason=reason;t.status='PAUSED';t.version++;t.operationController?.abort();this.options.gate.suspend?.();if(t.durable&&this.options.persistence.pause)t.remoteTransition=this.options.persistence.pause(t).then(remote=>{t.remoteVersion=Math.max(t.remoteVersion,remote.version)}).catch(()=>{t.syncError='暂停记录尚未同步；本页已阻止继续操作，联网后可核对恢复'});this.touch(t);this.options.driver.clear();return this.public(t)}
  resume(id) {const t=this.raw(id);if(t.status!=='PAUSED')throw Error('当前任务不可继续');if(t.durable&&this.options.persistence.resume)return this.resumeRemote(t);t.status=t.pausedStatus||'RUNNING';t.pausedStatus=undefined;t.pauseReason=undefined;this.touch(t);return this.public(t)}
  async resumeRemote(t){
    await t.remoteTransition;if(!this.current(t)||t.status!=='PAUSED')throw Error('任务状态已变化')
    const remote=await this.options.persistence.resume(t)
    if(!this.current(t)||t.status!=='PAUSED')throw Error('任务状态已变化')
    t.remoteVersion=Math.max(t.remoteVersion,remote.version);if(remote.status==='SUCCEEDED'){t.status='SUCCEEDED';this.touch(t);return this.public(t)}const status=t.pausedStatus||'RUNNING';t.status='RUNNING';t.syncError=undefined
    if(status==='WAITING_CONFIRMATION'&&this.options.gate.pending&&!t.lastFailure)await this.options.gate.refreshPending();else t.status=status==='WAITING_CONFIRMATION'?'RUNNING':status
    t.pausedStatus=undefined;t.pauseReason=undefined;this.touch(t);return this.public(t)
  }
  stop(id) {const t=this.tasks.find(t=>t.id===id);if(!t)return;if(!terminal(t.status)){t.status='CANCELLED';t.version++;t.controller.abort();t.pendingConfirmation=undefined;this.options.gate.cancel();this.options.driver.clear();this.touch(t)}return this.public(t)}
  async confirm(id,confirmationId) {
    const t=this.raw(id);if(t.status!=='WAITING_CONFIRMATION' || t.pendingConfirmation?.id!==confirmationId)throw Error('确认已失效')
    try {await this.options.gate.confirm(confirmationId)} catch(error) {if(error.code==='PAUSED_TASK')throw error;if(t.durable&&/过期|许可已消费|冻结内容|业务对象已经变化/.test(error.message)){t.pendingConfirmation=undefined;t.history.push({action:'observe',summary:'确认未放行：'+error.message+'。重新核对当前业务结果后再准备。',ok:false});t.status='RUNNING';this.touch(t);return this.public(t)}t.error=error.message;t.status='FAILED';t.controller.abort();this.options.gate.cancel();this.touch(t);throw error}
    if(!this.current(t))throw Error('任务已停止')
    t.pendingConfirmation=undefined;t.status='RUNNING';this.touch(t);return this.public(t)
  }
  input(id,message) {const t=this.raw(id);if(t.durable&&this.options.persistence.revise)return this.reviseRemote(t,message);if(t.status!=='WAITING_INPUT'||!message?.trim()||message.length>2000)throw Error('当前任务不接受此补充');t.input=(t.input+'\n'+message.trim()).slice(-2000);t.question=undefined;t.status='RUNNING';this.touch(t);return this.public(t)}
  async reviseRemote(t,message){
    if(!this.current(t)||!message?.trim()||message.length>2000)throw Error('当前任务不接受此修订')
    t.version++;t.status='PAUSED';t.operationController?.abort();this.options.gate.cancelPending?.('任务目标已修订，旧提交已取消')
    await t.remoteTransition
    const remote=await this.options.persistence.revise(t,message.trim())
    if(!this.current(t))throw Error('任务已停止')
    t.remoteVersion=remote.version;t.goal=remote.goal||t.goal+'\n'+message.trim();t.input='';t.plan=remote.plan;t.finishAttempts=0;t.question=undefined;t.pendingConfirmation=undefined;t.status='RUNNING';t.history.push({action:'observe',summary:'用户修订目标：'+message.trim().slice(0,250)+'。旧确认失效，先核对已提交结果再继续。',ok:true});this.touch(t);return this.public(t)
  }
  suspend(reason='ASSISTANT_CLOSED') {this.lifecycle++;for(const t of this.tasks)if(this.current(t)&&t.status!=='PAUSED')this.pause(t.id,reason)}
  invalidate() {this.lifecycle++;for(const t of this.tasks)if(!terminal(t.status))this.stop(t.id);this.tasks=[]}
  async reconcileUnknownWrite(t){
    const version=t.version,permit=t.lastFailure?.permitId
    for(let attempt=0;attempt<2;attempt++){
      let receipts=[]
      try{receipts=await this.options.persistence.receipts?.(t)||[]}catch{}
      if(!this.current(t)||t.version!==version)return false
      const receipt=permit&&receipts.find(r=>r.id===permit)
      if(receipt?.status==='SUCCEEDED'){
        if(!t.receipts.includes(permit))t.receipts.push(permit)
        t.steps.push({index:t.steps.length+1,action:'submit',label:'服务端回执回查',status:'SUCCEEDED',detail:'响应丢失，服务端已确认本次业务保存成功；没有重复提交。页面若显示旧提示，请刷新查看最新记录。'})
        t.history.push({action:'observe',summary:'只读回查已确认本次提交成功，不得再次办理。',ok:true});t.lastFailure=undefined;this.touch(t);return true
      }
      if(receipt&&['FAILED','EXPIRED'].includes(receipt.status)){
        const reason=receipt.result?.message?String(receipt.result.message).replace(/[\r\n\t]+/g,' ').slice(0,300):''
        t.history.push({action:'observe',summary:'服务端确认原提交没有成功或已撤销；'+(reason?'原业务原因：'+reason+'；':'')+'请依据实际校验信息修正，不改变原目标；变更预约时间或接待人须询问用户。',ok:false});t.lastFailure=undefined
        if((t.writeRecoveries=(t.writeRecoveries||0)+1)<=2)return true
        break
      }
      if(attempt===0)await sleep(this.options.receiptRecoveryDelay??1000)
    }
    if(this.current(t)&&t.version===version){t.question='暂时无法确认这次提交的最终结果，已暂停且不会重试业务。恢复后会先回查同一回执；请勿重复办理。';this.pause(t.id,'WRITE_UNKNOWN')}
    return false
  }
  async run(t) {
    let effects=0,lastDecision=0,retries=0,blockedDecisions=0
    const attempts=new Map()
    this.touch(t)
    const completedWork=()=>t.verification?.partial||t.steps.some(s=>s.action==='submit'&&s.status==='SUCCEEDED')
    const fail=message=>{if(!terminal(t.status)){t.error=message;t.status=completedWork()?'PARTIAL':'FAILED';t.controller.abort();this.options.gate.cancel();this.touch(t)}}
    const deadline=setInterval(()=>{if(this.executionTime(t)>=this.options.timeout)fail('任务达到执行时限，已停止；已提交的请求请人工核对')},Math.min(250,Math.max(10,this.options.timeout/4)))
    let checking=false
    const watch=setInterval(async()=>{
      if(!this.current(t)||checking)return
      if(t.status!=='RUNNING'&&Date.now()-Date.parse(t.updatedAt)>this.options.waitTimeout){fail('等待超时，已阻止挂起操作');return}
      checking=true
      try {if(globalThis.navigator?.onLine!==false)await this.verify(t)}catch(error){
        if(!this.current(t))return
        const revoked=[401,403].includes(Number(error.httpStatus))||[1003,1100,1101,1102,1103,1105,2602].includes(Number(error.code))||/请先登录|登录身份已变更|登录或门店身份已变更/.test(error.message)
        if(revoked)fail('登录身份已失效，已停止后续操作')
        else if(t.status!=='PAUSED'){this.pause(t.id,'OFFLINE');t.syncError='暂时无法连接身份校验服务，已暂停操作；连接恢复后可继续核对原任务'}
      }finally{checking=false}
    },2000)
    try {
      for(let count=0;count<(t.durable?60:30);) {
        await this.runnable(t);if(this.executionTime(t)>this.options.timeout)throw Error('任务执行超时，已停止')
        await this.verify(t);await this.options.settle();await this.options.gate.idle(()=>this.current(t))
        await this.options.driver.ready?.(()=>this.current(t)&&t.status==='RUNNING')
        if(t.durable&&t.lastFailure?.code==='WRITE_UNKNOWN'&&!await this.reconcileUnknownWrite(t))continue
        const version=t.version,observation=await this.options.driver.observe({goal:t.goal+(t.input?'\n'+t.input:''),v2:t.durable===true})
        const submits=t.steps.filter(s=>s.action==='submit'&&s.status==='SUCCEEDED').length,evidenceKey=`${submits}:${t.factVersion||0}:${t.pageVersion||0}`
        if(t.durable&&(t.verifyRecovery||submits>0||t.factVersion>0||t.pageVersion>0)&&evidenceKey!==t.verifiedEvidence){
          const report=await this.options.persistence.finish(t,observation.context||{},t.controller.signal)
          if(!this.current(t))throw Error('任务已停止或身份已变更')
          if(t.version!==version){await this.runnable(t);continue}
          t.verification=report;t.verifiedEvidence=evidenceKey;t.verifyRecovery=false
          if(report.complete){t.status='SUCCEEDED';this.touch(t);break}
          const missing=report.outcomes?.filter(o=>o.status!=='SATISFIED').map(o=>o.label||o.id).join('；')
          if(missing)t.history.push({action:'observe',summary:('服务端核验：仍需完成'+missing+'；成功回执不可重放。').slice(0,500),ok:false})
        }
        const beforeState=await this.options.driver.fingerprint?.()
        const delay=Math.max(0,this.options.interval-(Date.now()-lastDecision));if(delay)await sleep(delay)
        await this.runnable(t);if(t.version!==version)continue
        let decision
        try {
          lastDecision=Date.now();const body={goal:t.goal,observation,history:t.history.slice(-12),artifacts:t.artifacts.map(({id,name})=>({id,name})),...(t.input?{input:t.input}:{})}
          if(t.durable){const turn=await this.options.persistence.next(t,body,t.controller.signal);t.remoteVersion=Math.max(t.remoteVersion,turn.task.version);t.plan=turn.task.plan||t.plan;t.facts=[...(t.facts||[]),...(turn.facts||[])].slice(-12);if(turn.facts?.length){const proof=turn.task.checkpoint?.queryProofs?.at(-1),fingerprint=JSON.stringify({facts:turn.facts.map(({tool,data})=>({tool,data})),arguments:proof?.arguments});t.factFingerprints=t.factFingerprints||[];if(!t.factFingerprints.includes(fingerprint)){t.factFingerprints.push(fingerprint);t.factVersion=(t.factVersion||0)+1}}if(turn.failure)throw Object.assign(Error(turn.failure.message||turn.failure.code),{failureCode:turn.failure.code,traceId:turn.failure.traceId});decision=turn.decision}
          else decision=await this.options.next(body,t.controller.signal)
        }
        catch(error){const recovery=classifyFailure(error);if(t.durable&&recovery.retryable&&retries++<2){t.steps.push({index:t.steps.length+1,action:'observe',label:'恢复规划',status:'RUNNING',detail:recovery.label+(error.traceId?' · '+error.traceId:'')});if(recovery.strategy==='WAIT')await sleep(this.options.recoveryDelay??10000);continue}if(error.code===1001&&retries++<2)continue;throw error}
        if(!this.current(t))throw Error('任务已停止或身份已变更')
        if(t.version!==version){await this.runnable(t);continue}
        await this.verify(t);await this.runnable(t)
        if(t.version!==version)continue
        const {action,arguments:args}=decision
        if(action==='set_plan'){
          t.plan=args;t.steps.push({index:t.steps.length+1,action,label:'规划任务步骤',status:'SUCCEEDED',detail:args.steps.map(s=>s.label).join(' → ')});t.history.push({action,summary:'已记录计划；仍须真实执行和逐项目标核验',ok:true});count++;this.touch(t);continue
        }
        if(action==='finish') {
          if(t.durable){
            t.verification=await this.options.persistence.finish(t,observation.context||{},t.controller.signal)
            await this.runnable(t)
            if(!t.verification.complete){
              const missing=t.verification.outcomes?.filter(o=>o.status!=='SATISFIED').map(o=>o.label||o.id).join('；')||'尚缺可验证的目标结果'
              t.history.push({action:'observe',summary:`目标核验未通过：${missing}。保留已完成部分，继续处理未完成目标；不能重复提交已成功请求。`.slice(0,500),ok:false})
              if((t.finishAttempts=(t.finishAttempts||0)+1)>=3){t.question='已保留实际完成部分；尚未完成：'+missing;t.status='WAITING_INPUT';this.touch(t)}
              continue
            }
            t.status='SUCCEEDED';this.touch(t);break
          }
          if(t.error)throw Error(t.error)
          if(!effects)throw Error('尚未在当前页面执行操作，不能报告完成')
          if(needsReceipt(t.goal)&&!t.steps.some(s=>s.action==='submit'&&s.status==='SUCCEEDED'))throw Error('尚未取得实际业务提交成功回执，不能报告完成')
          if(/(?:生成|导出).*(?:文件|CSV)|(?:文件|CSV).*(?:生成|导出)/i.test(t.goal)&&!t.artifacts.length)throw Error('尚未生成所需成果文件')
          if(/文件|导出|CSV|生成.*课表/i.test(t.goal)&&/打开/.test(t.goal)&&!t.steps.some(s=>s.action==='open_artifact'&&s.status==='SUCCEEDED'))throw Error('尚未请求系统打开成果文件，不能报告完成')
          t.status='SUCCEEDED';this.touch(t);break
        }
        if(action==='ask_user'){t.question=String(args.question).slice(0,500);t.status='WAITING_INPUT';this.touch(t);continue}
        if(beforeState!==undefined&&['click','fill','select','observe','navigate','back','scroll','use_skill'].includes(action)){
          const target=this.options.driver.describe?.(action,args)||JSON.stringify({action,args})
          const receipts=t.steps.filter(s=>s.action==='submit'&&s.status==='SUCCEEDED').length
          const key=JSON.stringify([beforeState,target,receipts,t.factVersion||0]),seen=attempts.get(key)||0
          if(seen>=2){
            const detail=`重复操作已阻止：相同页面状态下已执行两次“${target}”。请核对具体对象、字段值和当前弹窗，改用正确对象或关闭错误弹窗后重新定位，不重复同一动作。`
            t.history.push({action,summary:detail.slice(0,500),ok:false});blockedDecisions++
            if(blockedDecisions===1)t.steps.push({index:t.steps.length+1,action:'observe',label:'检测循环并重新定位',status:'SUCCEEDED',detail})
            if(blockedDecisions>=3){t.question='检测到重复操作循环，已阻止继续重复点击。请补充具体对象名称或编号，或说明当前页面与目标的差异。';t.status='WAITING_INPUT';this.touch(t);await this.runnable(t);attempts.clear();blockedDecisions=0}
            this.touch(t);continue
          }
          attempts.set(key,seen+1)
        }
        const step={index:t.steps.length+1,action,label:({navigate:'切换当前网页',click:'点击页面控件',fill:'填写页面表单',select:'选择页面选项',scroll:'滚动当前页面',back:'返回上一页面',observe:'读取当前页面',use_skill:'按业务对象操作',export_schedule:'生成课表文件',open_artifact:'请求打开成果文件'})[action]||action,status:'RUNNING'}
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
            step.detail=await this.options.driver.act(action,args,t.session,()=>this.current(t)&&t.status==='RUNNING'&&t.version===version)
            if(action!=='observe')effects++
          }
          await this.options.settle();await this.options.gate.idle(()=>this.current(t))
          // A transport receipt precedes the Vue handler that closes the form and
          // starts its refresh reads. Wait for those handlers, reads and rendering.
          await this.options.settle();await this.options.gate.idle(()=>this.current(t));await this.options.settle();await this.runnable(t)
          if(t.error)throw Error(t.error)
          if(t.durable&&action!=='observe'&&beforeState!==undefined&&beforeState!==await this.options.driver.fingerprint?.())t.pageVersion=(t.pageVersion||0)+1
          const feedback=await this.options.driver.feedback?.()
          await this.runnable(t)
          if(feedback)step.detail=String(step.detail)+'；操作后核对：'+feedback
          step.status='SUCCEEDED';t.history.push({action,summary:String(step.detail).slice(0,500),ok:true});retries=0
        } catch(error) {
          if(error.code==='ACTION_INTERRUPTED'&&this.current(t)){
            step.status='PAUSED';step.detail='任务状态变化时已阻止继续操作，恢复后将重新观察并定位控件'
            t.history.push({action,summary:step.detail,ok:false});await this.runnable(t);continue
          }
          if(t.status==='PAUSED'&&t.operationController?.signal.aborted&&error.name==='AbortError'){
            step.status='PAUSED';step.detail='文件请求已中断，继续后将重新核对成果；已开始的系统操作不能撤回'
            t.history.push({action,summary:step.detail,ok:false});await this.runnable(t);continue
          }
          step.status='FAILED';step.detail=error.message
          t.history.push({action,summary:String(error.message).slice(0,500),ok:false})
          if((error.code!=='STALE_PAGE'&&!(t.durable&&classifyFailure(error).retryable)) || retries++>=2)throw error
        }
        this.touch(t)
      }
      if(this.current(t))throw Error('已达到30步上限，请查看实际结果后继续')
    } catch(error) {if(!terminal(t.status)){t.status=this.owned(t)?completedWork()?'PARTIAL':'FAILED':'CANCELLED';t.error=error.message;this.touch(t)}}
    finally {clearInterval(deadline);clearInterval(watch);t.controller.abort();t.pendingConfirmation=undefined;this.options.gate.unbind({completed:t.status==='SUCCEEDED'});this.options.driver.clear();this.options.cleanup?.();t.input='';if(t.durable)try{await this.options.persistence.close(t,t.status)}catch(error){t.syncError='任务停止记录尚未同步，请刷新核对；不会自动重放业务请求'}if(this.runningTask===t)this.runningTask=null}
  }
}
