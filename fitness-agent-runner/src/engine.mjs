import { randomUUID } from 'node:crypto';
import { Backend } from './backend.mjs';
import { ControlledBrowser } from './browser.mjs';
import { ArtifactStore } from './artifacts.mjs';
import { AgentError, ownerKey, terminal, validateAction, redact, requiresBusinessWrite } from './policy.mjs';
import { UserData } from './user-data.mjs';

const labels={navigate:'打开项目页面',observe:'读取当前页面',click:'点击页面元素',fill:'填写页面字段',select:'选择页面选项',scroll:'滚动页面',back:'返回上一页',export_schedule:'生成课表文件',open_artifact:'请求打开成果文件',ask_user:'等待补充资料',finish:'核对任务结果'};
function fieldsOf(body,contentType){
 const fields=[];if(!body)return fields;let value;try{if(contentType.includes('json'))value=JSON.parse(body);else if(contentType.includes('application/x-www-form-urlencoded'))value=Object.fromEntries(new URLSearchParams(body));else throw new Error();if(!value||typeof value!=='object'||Array.isArray(value))throw new Error();}catch{throw new AgentError('无法完整解析请求内容，请通过人工界面提交');}
 function walk(o,prefix='',depth=0){if(depth>10)throw new AgentError('请求结构过深，无法完整预览，请通过人工界面提交');for(const [key,v] of Object.entries(o||{})){if(fields.length>=200)throw new AgentError('请求字段过多，无法完整预览，请通过人工界面提交');const label=(prefix?prefix+'.':'')+key;if(label.length>200||String(v??'').length>4000)throw new AgentError('请求字段过长，无法完整预览，请通过人工界面提交');if(/password|token|secret|cookie|authorization|api.?key/i.test(label)){fields.push({label,value:'[敏感字段已隐藏]'});continue;}if(v&&typeof v==='object')walk(v,label,depth+1);else fields.push({label,value:redact(v,4000)});}}walk(value);return fields;
}
export class TaskEngine{
 constructor(config,deps={}){this.config=config;this.backend=deps.backend||new Backend(config.backendOrigin);this.artifacts=new ArtifactStore(config.outputDirectory,{openFile:deps.openFile});this.tasks=new Map();this.active=null;this.closed=false;}
 get(id,session){const t=this.tasks.get(id);if(!t)throw new AgentError('任务不存在',404);if(t.owner!==ownerKey(session))throw new AgentError('任务不属于当前账号、门店或登录会话',403);return t;}
 public(t){return structuredClone(t.view);}
 list(s){return [...this.tasks.values()].filter(t=>t.owner===ownerKey(s)).slice(-10).reverse().map(t=>this.public(t));}
 create(goal,session,token){
  if(this.closed)throw new AgentError('执行器正在关闭',503);if(typeof goal!=='string'||!goal.trim()||goal.length>2000)throw new AgentError('任务描述须为1至2000字');
  if(this.active&&!terminal.has(this.active.view.status))throw new AgentError('本机已有活动任务，请先停止或完成它',409);
  const now=new Date().toISOString(),t={view:{id:randomUUID(),goal:goal.trim(),status:'RUNNING',createdAt:now,updatedAt:now,steps:[],artifacts:[]},owner:ownerKey(session),session,token,controller:new AbortController(),waiters:new Set(),history:[],effects:0,writes:0,openedArtifacts:new Set(),writePending:false,confirmationIds:new Set(),readRetries:0};
  this.tasks.set(t.view.id,t);this.active=t;
  t.userData=new UserData();t.modelGoal=t.userData.protect(t.view.goal);t.exports=new Map();
  for(const [id,old] of this.tasks){if(this.tasks.size<=30)break;if(terminal.has(old.view.status))this.tasks.delete(id);}
  t.deadline=setTimeout(()=>this.fail(t,'任务已达到10分钟执行时限'),this.config.taskTimeoutMs);t.deadline.unref();
  queueMicrotask(()=>this.run(t));return this.public(t);
 }
 touch(t){t.view.updatedAt=new Date().toISOString();for(const r of t.waiters)r();t.waiters.clear();}
 async verify(t){if(terminal.has(t.view.status)||t.controller.signal.aborted)throw new AgentError('任务已结束',409);const current=await this.backend.session(t.token,t.controller.signal);if(ownerKey(current)!==t.owner)throw new AgentError('账号、门店或登录会话已变化，任务停止',403);return current;}
 async wait(t){
  while(!terminal.has(t.view.status)&&t.view.status!=='RUNNING'){
   if(t.waitStarted&&Date.now()-t.waitStarted>this.config.waitTimeoutMs)throw new AgentError('等待已超时，请重新创建任务');
   if(Date.now()-(t.waitScopeCheckedAt||0)>2000){t.waitScopeCheckedAt=Date.now();await this.verify(t);}
   await new Promise(resolve=>{const timer=setTimeout(()=>{t.waiters.delete(done);resolve();},250);const done=()=>{clearTimeout(timer);resolve();};t.waiters.add(done);});
  }
  if(terminal.has(t.view.status))throw new AgentError('任务已经结束',409);
 }
 async gateWrite(t,request){
  if(!t.actionWindow||t.writePending)throw new AgentError('当前步骤不允许额外业务写请求');
  // Reserve synchronously so two network handlers cannot replace one frozen request.
  t.writePending=true;
  try{await this.verify(t);await this.wait(t);}catch(e){t.writePending=false;throw e;}
  t.writeError=null;t.waitStarted=Date.now();
  const id=randomUUID();t.view.pendingConfirmation={id,method:request.method,path:request.path,summary:'即将提交这一条冻结的业务请求；确认后仍由后台核验权限与业务条件',fields:fieldsOf(request.body,request.contentType)};
  t.view.status='WAITING_CONFIRMATION';this.touch(t);
  await new Promise((resolve,reject)=>{t.releaseWrite=resolve;t.rejectWrite=reject;});
 }
 writeResult(t,ok,message){t.writePending=false;t.releaseWrite=null;t.rejectWrite=null;if(ok){t.writes++;t.effects++;}else t.writeError=message;this.touch(t);}
 async settledWrites(t){await this.wait(t);while(t.writePending){await new Promise(r=>setTimeout(r,25));await this.wait(t);}if(t.writeError)throw new AgentError(t.writeError);}
 control(t,action,body){
  if(terminal.has(t.view.status))throw new AgentError('任务已结束',409);
  switch(action){
   case 'pause':if(t.view.status!=='PAUSED'){t.resumeStatus=t.view.status;t.view.status='PAUSED';t.waitStarted=Date.now();}break;
   case 'resume':if(t.view.status!=='PAUSED')throw new AgentError('任务当前未暂停',409);t.view.status=t.resumeStatus||'RUNNING';break;
   case 'stop':this.cancel(t);return this.public(t);
   case 'confirm':{
    if(t.view.status!=='WAITING_CONFIRMATION'||!t.view.pendingConfirmation||body.confirmationId!==t.view.pendingConfirmation.id||t.confirmationIds.has(body.confirmationId))throw new AgentError('确认已失效或已使用',409);
    if(Date.now()-t.waitStarted>this.config.waitTimeoutMs)throw new AgentError('确认已过期',409);
    t.confirmationIds.add(body.confirmationId);delete t.view.pendingConfirmation;t.view.status='RUNNING';t.releaseWrite();break;
   }
   case 'input':if(t.view.status!=='WAITING_INPUT')throw new AgentError('任务当前不需要补充资料',409);if(typeof body.message!=='string'||!body.message.trim()||body.message.length>2000)throw new AgentError('补充资料须为1至2000字');t.input=body.message.trim();delete t.view.question;t.view.status='RUNNING';break;
   default:throw new AgentError('不支持的任务控制');
  }
  this.touch(t);return this.public(t);
 }
 cancel(t){if(t.writePending&&!t.view.pendingConfirmation)t.view.error='已确认的业务请求可能已发送；请人工核对实际结果，不会自动重试';t.view.status='CANCELLED';delete t.view.pendingConfirmation;delete t.view.question;t.controller.abort();t.rejectWrite?.(new AgentError('任务已经停止'));t.writePending=false;this.touch(t);t.browser?.close();clearTimeout(t.deadline);}
 fail(t,message){if(terminal.has(t.view.status))return;const uncertain=t.writePending&&!t.view.pendingConfirmation;t.view.status='FAILED';t.view.error=redact(message+(uncertain?'；已确认的业务请求可能已发送，请人工核对实际结果，不会自动重试':'')+(t.writes?'；已收到'+t.writes+'条成功业务回执，请核对已完成的业务，勿重复办理':''),500);delete t.view.pendingConfirmation;delete t.view.question;t.controller.abort();t.rejectWrite?.(new AgentError(message));t.writePending=false;this.touch(t);t.browser?.close();clearTimeout(t.deadline);}
 async run(t){
  try{
   await this.verify(t);await this.wait(t);
   t.browser=new ControlledBrowser(this.config,{verify:()=>this.verify(t),awaitRunnable:()=>this.wait(t),write:r=>this.gateWrite(t,r),writeResult:(ok,msg)=>this.writeResult(t,ok,msg)});
   await t.browser.start(t.session,t.token);
   let observed=await t.browser.observe();t.view.screenshot=observed.screenshot;this.touch(t);
   for(let i=1;i<=this.config.maxSteps;i++){
    await this.wait(t);await this.verify(t);
    const interval=this.config.decisionIntervalMs??8000;
    while(Date.now()<(this.nextDecisionAt||0)){await this.wait(t);await new Promise(r=>setTimeout(r,Math.min(250,this.nextDecisionAt-Date.now())));}
    await this.wait(t);await this.verify(t);this.nextDecisionAt=Date.now()+interval;
    let decision;
    try{decision=await this.backend.next(t.token,{goal:t.modelGoal,observation:observed.observation,history:t.history.slice(-12),artifacts:t.view.artifacts.map(({id,name})=>({id,name})),...(t.input?{input:t.userData.protect(t.input)}:{})},t.controller.signal);}
    catch(e){
     if(e.code==='INVALID_DECISION'&&(t.invalidPlans=(t.invalidPlans||0)+1)<=2){
      const detail='模型规划参数未通过后台校验，尚未执行该动作；重新观察后规划，填写值必须为字符串，不能猜测元素引用';
      t.history.push({action:'observe',summary:detail,ok:false});t.view.steps.push({index:i,action:'observe',label:'重新核对规划',status:'FAILED',detail});observed=await t.browser.observe();t.view.screenshot=observed.screenshot;this.touch(t);continue;
     }throw e;
    }
    await this.wait(t);await this.verify(t);delete t.input;
    const {action,arguments:args}=validateAction(decision,observed.observation,t.session,t.view.artifacts);
    if(action==='fill')args.value=t.userData.resolve(args.value);
    const step={index:i,action,label:labels[action],status:'RUNNING'};t.view.steps.push(step);this.touch(t);
    try{
     if(action==='finish'){
      if(!t.effects)throw new AgentError('模型请求结束，但尚无实际页面操作或成果，未判定为成功');
      if(/生成|导出|保存.*(?:课表|文件)|CSV/i.test(t.view.goal)&&!t.view.artifacts.length)throw new AgentError('任务要求成果文件，但尚未生成，未判定为成功');
      if(/文件|导出|CSV|生成.*课表/i.test(t.view.goal)&&/打开/.test(t.view.goal)&&!t.openedArtifacts.size)throw new AgentError('任务要求打开成果文件，但尚未请求系统打开，未判定为成功');
      const expectsWrite=requiresBusinessWrite(t.view.goal);
      if((expectsWrite||t.confirmationIds.size>0)&&!t.writes)throw new AgentError('任务要求业务保存或提交，但尚无真实成功业务回执，未判定为成功');
      step.status='SUCCEEDED';step.detail='已核对真实页面操作、业务回执或成果文件';t.view.status='SUCCEEDED';this.touch(t);return;
     }
     if(action==='ask_user'){t.view.question=redact(args.question,500);t.view.status='WAITING_INPUT';t.waitStarted=Date.now();step.detail='等待用户补充资料';step.status='SUCCEEDED';this.touch(t);await this.wait(t);}
     else if(action==='export_schedule'){
      const key=args.from+'/'+args.to,existing=t.exports.get(key);
      if(existing)step.detail=`该日期范围已经生成文件，成果ID=${existing.id}；需要打开时使用open_artifact，不会重复创建文件`;
      else{if(t.view.artifacts.length>=10)throw new AgentError('当前任务成果已达10个上限');const rows=await this.backend.schedule(t.token,args.from,args.to,t.controller.signal);const guard=async()=>{await this.wait(t);await this.verify(t);await this.wait(t);};await guard();const artifact=await this.artifacts.schedule(t.view.id,rows,args.from,args.to,guard);t.view.artifacts.push(artifact);t.exports.set(key,artifact);t.effects++;step.detail=`已生成${t.session.role==='MEMBER'?'可预约课程':'可见排课'}文件（${rows.length}条课程，${artifact.bytes}字节），成果ID=${artifact.id}`;}
     }
     else if(action==='open_artifact'){step.detail=t.openedArtifacts.has(args.artifactId)?'此前已请求系统打开此成果，不重复启动窗口':await this.artifacts.open(t.view.id,args.artifactId,async()=>{await this.wait(t);await this.verify(t);await this.wait(t);});t.openedArtifacts.add(args.artifactId);t.effects++;}
     else{
      t.actionWindow=true;const previous=observed.observation,beforeWrites=t.writes;const executed=await t.browser.act(action,args);await this.settledWrites(t);t.actionWindow=false;
      observed=await t.browser.observe();t.view.screenshot=observed.screenshot;
      const changed=previous.url!==observed.observation.url||previous.text!==observed.observation.text;
      if(changed||['fill','select','scroll'].includes(action))t.effects++;
      step.detail=action==='observe'?'已读取当前真实页面；空列表或0条记录也是有效查询结果，不代表加载失败':action==='fill'||action==='select'?redact(`已${action==='fill'?'填写':'选择'}“${executed.targetName}”：${args.value}`,500):action==='click'?`已点击“${executed.targetName}”；${changed?'页面内容或地址已实际更新':'尚无可见内容变化，可能已选中'}`:changed?`已打开${observed.observation.url}并读取页面；空列表或0条记录也是有效查询结果`:action==='scroll'?'页面已滚动':'动作已执行；尚无可见内容变化，无需重复相同操作';
      if(t.writes>beforeWrites)step.detail+='；本步已确认提交并收到成功业务回执';
     }
     step.status='SUCCEEDED';t.readRetries=0;t.history.push({action,summary:redact(step.detail,500),ok:true});this.touch(t);
    }catch(e){
     t.actionWindow=false;step.status='FAILED';step.detail=redact(e.message,500);t.history.push({action,summary:step.detail,ok:false});this.touch(t);
     if(!t.writePending&&!t.writes&&!t.writeError&&!terminal.has(t.view.status)&&['navigate','observe','back','scroll','click','fill','select'].includes(action)&&t.readRetries++<2){observed=await t.browser.observe();t.view.screenshot=observed.screenshot;continue;}
     throw e;
    }
   }
   throw new AgentError(`任务达到${this.config.maxSteps}步上限，未确认完成`);
  }catch(e){this.fail(t,e instanceof AgentError?e.message:'受控浏览器执行失败；请核对页面或重新创建任务');}
  finally{clearTimeout(t.deadline);await t.browser?.close();t.token=null;t.userData.clear();t.releaseWrite=null;t.rejectWrite=null;}
 }
 async close(){this.closed=true;for(const t of this.tasks.values()){if(!terminal.has(t.view.status))this.cancel(t);await t.browser?.close();}}
}
