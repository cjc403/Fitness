import test from 'node:test'
import assert from 'node:assert/strict'
import { PageTaskEngine } from '../../src/agent/pageTaskEngine.mjs'
import { createRequestGate } from '../../src/agent/requestGate.mjs'
const tick=()=>new Promise(r=>setTimeout(r,6))
const session={userId:1,role:'SUPER_ADMIN',venueId:1,sessionKey:'s'}
function setup(decisions,verification={complete:true,outcomes:[]}){
  const events=[],gate=createRequestGate();let n=0
  const persistence={create:async goal=>{events.push('create');return {id:'remote-task',version:0,goal,status:'RUNNING',plan:{}}},next:async(t,body)=>{events.push('next');return {decision:decisions[Math.min(n++,decisions.length-1)],task:{version:n,plan:{steps:[{id:'s1',label:'打开目标',kind:'NAVIGATE'}]}},facts:[]}},finish:async()=>{events.push('verify');return verification},close:async()=>events.push('close')}
  const engine=new PageTaskEngine({identity:()=> 'same',session:async()=>session,next:()=>{throw Error('must use V2')},persistence,driver:{observe:()=>({url:'http://localhost/training-plans',text:'训练',nodes:[],context:{path:'/training-plans'}}),act:async()=> '真实动作',clear:()=>{},feedback:()=> '真实后态'},gate,interval:0,settle:async()=>{},timeout:2000,waitTimeout:2000})
  return {engine,events,persistence}
}
async function done(engine,id){for(let i=0;i<100;i++){const t=engine.get(id);if(['SUCCEEDED','FAILED','WAITING_INPUT','PARTIAL','PAUSED'].includes(t.status))return t;await tick()}throw Error('task did not settle')}
test('V2 uses durable task identity and verifies the whole goal before reporting success',async()=>{
  const f=setup([{action:'navigate',arguments:{path:'/training-plans'}},{action:'finish',arguments:{}}]);const t=await f.engine.create('打开训练计划');assert.equal(t.id,'remote-task');const r=await done(f.engine,t.id);assert.equal(r.status,'SUCCEEDED');assert.ok(f.events.includes('verify'));assert.equal(r.remoteVersion,2)
})
test('partial verification is preserved and never presented as full success',async()=>{
  const f=setup([{action:'navigate',arguments:{path:'/training-plans'}},{action:'finish',arguments:{}}],{complete:false,partial:true,outcomes:[{id:'g1',status:'SATISFIED'},{id:'g2',status:'UNSATISFIED',label:'查询本周排课'}]});const t=await f.engine.create('打开训练计划，再查询本周排课');const r=await done(f.engine,t.id);assert.notEqual(r.status,'SUCCEEDED');assert.ok(r.verification.partial);f.engine.stop(t.id)
})
test('set_plan records visible subgoals without inventing a page action',async()=>{
  const f=setup([{action:'set_plan',arguments:{steps:[{id:'a',label:'找到指定课程',kind:'NAVIGATE'}]}},{action:'navigate',arguments:{path:'/training-plans'}},{action:'finish',arguments:{}}]);const t=await f.engine.create('打开训练计划');const r=await done(f.engine,t.id);assert.equal(r.status,'SUCCEEDED');assert.ok(r.steps.some(s=>s.action==='set_plan'));assert.ok(r.plan.steps.length)
})
test('a later failure retains verified partial work instead of hiding the committed result',async()=>{
 const f=setup([{action:'navigate',arguments:{path:'/training-plans'}},{action:'finish',arguments:{}}],{complete:false,partial:true,outcomes:[{id:'g1',status:'SATISFIED'},{id:'g2',status:'UNSATISFIED',label:'剩余目标'}]})
 const next=f.persistence.next;let n=0;f.persistence.next=async(...args)=>{if(++n>2)throw Object.assign(Error('当前权限不允许剩余目标'),{code:1003});return next(...args)}
 const t=await f.engine.create('打开训练计划，再处理剩余目标'),result=await done(f.engine,t.id);assert.equal(result.status,'PARTIAL');assert.ok(result.verification.partial)
})
test('a real successful submit triggers whole-goal verification before another model call',async()=>{
 const f=setup([{action:'click',arguments:{ref:'save'}}]);let calls=0
 const next=f.persistence.next;f.persistence.next=async(...args)=>{if(++calls>1)throw Error('provider budget exhausted');return next(...args)}
 f.engine.options.driver.act=async()=>{const task=f.engine.tasks[0];task.steps.push({action:'submit',status:'SUCCEEDED',receipt:'real-server-receipt'});return '已保存'}
 const t=await f.engine.create('创建训练计划草稿'),result=await done(f.engine,t.id)
 assert.equal(result.status,'SUCCEEDED');assert.equal(calls,1);assert.ok(f.events.includes('verify'))
})
test('an already committed request remains partial if a later step fails before finish',async()=>{
 const f=setup([{action:'click',arguments:{ref:'save'}}],{complete:false,partial:true,outcomes:[{status:'SATISFIED',label:'已保存草稿'},{status:'UNSATISFIED',label:'剩余查询'}]});let calls=0
 const next=f.persistence.next;f.persistence.next=async(...args)=>{if(++calls>1)throw Object.assign(Error('remaining step denied'),{code:1003});return next(...args)}
 f.engine.options.driver.act=async()=>{f.engine.tasks[0].steps.push({action:'submit',status:'SUCCEEDED',receipt:'real-server-receipt'});return '已保存'}
 const t=await f.engine.create('创建训练计划草稿，再查询其他数据'),result=await done(f.engine,t.id);assert.equal(result.status,'PARTIAL');assert.ok(result.verification.partial)
})
test('a verified business query ends without unnecessary page clicks or a second model call',async()=>{
 const f=setup([{action:'observe',arguments:{}}]);let calls=0
 const next=f.persistence.next;f.persistence.next=async(...args)=>{if(++calls>1)throw Error('unnecessary provider call');const turn=await next(...args);return {...turn,facts:[{tool:'courses',data:[],truncated:false}]}}
 const t=await f.engine.create('查询本周课程'),result=await done(f.engine,t.id);assert.equal(result.status,'SUCCEEDED');assert.equal(calls,1)
})
test('actual page progress verifies a navigation goal before another model call can loop',async()=>{
 const f=setup([{action:'navigate',arguments:{path:'/training-plans'}}]);let state='old',calls=0
 const next=f.persistence.next;f.persistence.next=async(...args)=>{if(++calls>1)throw Error('Goal was already reached; no extra planning call');return next(...args)}
 f.engine.options.driver.fingerprint=()=>state;f.engine.options.driver.act=async()=>{state='training-page';return '已打开训练计划'}
 const t=await f.engine.create('打开训练计划'),result=await done(f.engine,t.id);assert.equal(result.status,'SUCCEEDED');assert.equal(calls,1)
})
test('an unwriteable custom control gets bounded observation and a corrected action',async()=>{
 const f=setup([{action:'fill',arguments:{ref:'readonly-select',value:'课程'}},{action:'click',arguments:{ref:'open-select'}},{action:'finish',arguments:{}}]);let attempts=0
 f.engine.options.driver.act=async()=>{if(++attempts===1)throw Object.assign(Error('自定义下拉不可直接填写，请先展开再选择'),{code:'CONTROL_NOT_WRITABLE'});return '已选择课程'}
 const t=await f.engine.create('选择课程'),result=await done(f.engine,t.id);assert.equal(result.status,'SUCCEEDED');assert.equal(attempts,2);assert.ok(result.steps.some(s=>s.status==='FAILED'))
})
test('different server query results count as progress even when the visible page is unchanged',async()=>{
 const f=setup([{action:'observe',arguments:{}}]);let calls=0,acts=0
 const next=f.persistence.next;f.persistence.next=async(...args)=>{const turn=await next(...args);return {...turn,facts:[{tool:'students',data:[{id:++calls}],truncated:false}]}}
 f.persistence.finish=async()=>({complete:calls>=3,partial:false,outcomes:[]})
 f.engine.options.driver.fingerprint=()=> 'unchanged page';f.engine.options.driver.describe=()=> 'observe';f.engine.options.driver.act=async()=>{acts++;return '真实业务结果'}
 const t=await f.engine.create('查询三项业务'),result=await done(f.engine,t.id);assert.equal(result.status,'SUCCEEDED');assert.equal(acts,3);assert.ok(!result.steps.some(s=>s.label==='检测循环并重新定位'))
})
test('repeating the same server query cannot masquerade as endless progress',async()=>{
 const f=setup([{action:'observe',arguments:{}}],{complete:false,partial:false,outcomes:[]});let calls=0
 const next=f.persistence.next;f.persistence.next=async(...args)=>{calls++;const turn=await next(...args);return {...turn,facts:[{tool:'students',data:[{id:1}],asOf:String(calls),truncated:false}]}}
 f.engine.options.driver.fingerprint=()=> 'unchanged page';f.engine.options.driver.describe=()=> 'observe'
 const t=await f.engine.create('查询不能满足的业务'),result=await done(f.engine,t.id);assert.equal(result.status,'WAITING_INPUT');assert.ok(calls<10);f.engine.stop(t.id)
})
test('a lost submit response is resolved from its exact server receipt without another write',async()=>{
 const f=setup([{action:'click',arguments:{ref:'save'}}]);let calls=0
 const next=f.persistence.next;f.persistence.next=async(...args)=>{if(++calls>1)throw Error('must not retry the business');return next(...args)}
 f.persistence.receipts=async()=>[{id:'permit-1',status:'SUCCEEDED'}]
 f.engine.options.driver.act=async()=>{const t=f.engine.tasks[0];t.lastFailure={code:'WRITE_UNKNOWN',permitId:'permit-1'};t.steps.push({action:'submit',status:'FAILED',detail:'response lost'});return '已点击保存'}
 const t=await f.engine.create('创建训练草稿'),result=await done(f.engine,t.id);assert.equal(result.status,'SUCCEEDED');assert.equal(calls,1);assert.ok(result.receipts.includes('permit-1'))
})
test('time spent awaiting the user does not consume the active execution budget',async()=>{
 const f=setup([{action:'ask_user',arguments:{question:'请选择具体对象'}},{action:'navigate',arguments:{path:'/training-plans'}},{action:'finish',arguments:{}}]);f.engine.options.timeout=70
 const t=await f.engine.create('打开训练计划'),waiting=await done(f.engine,t.id);assert.equal(waiting.status,'WAITING_INPUT')
 await new Promise(r=>setTimeout(r,130));assert.equal(f.engine.get(t.id).status,'WAITING_INPUT')
 f.engine.input(t.id,'训练计划');assert.equal((await done(f.engine,t.id)).status,'SUCCEEDED')
})
test('an unrelated receipt cannot resolve an uncertain submit',async()=>{
 const f=setup([{action:'click',arguments:{ref:'save'}}]);f.engine.options.receiptRecoveryDelay=0;let calls=0
 const next=f.persistence.next;f.persistence.next=async(...args)=>{if(++calls>1)throw Error('must pause before a second business plan');return next(...args)}
 f.persistence.receipts=async()=>[{id:'other-permit',status:'SUCCEEDED'}]
 f.engine.options.driver.act=async()=>{const t=f.engine.tasks[0];t.lastFailure={code:'WRITE_UNKNOWN',permitId:'permit-1'};return '提交结果不确定'}
 const t=await f.engine.create('创建训练草稿'),result=await done(f.engine,t.id);assert.equal(result.status,'PAUSED');assert.ok(!result.receipts.includes('other-permit'));f.engine.stop(t.id)
})
test('a definite rejected receipt gives the model its original business conflict instead of a blind retry',async()=>{
 const f=setup([{action:'click',arguments:{ref:'save'}},{action:'ask_user',arguments:{question:'该时间已被占用，请提供其他时间'}}]);let calls=0
 const next=f.persistence.next;f.persistence.next=async(...args)=>{if(++calls===2)assert.ok(args[1].history.some(h=>h.summary.includes('接待顾问在此时间已有体验预约')));return next(...args)}
 f.persistence.receipts=async()=>[{id:'permit-conflict',status:'FAILED',result:{code:2600,message:'接待顾问在此时间已有体验预约，请调整时间或接待人'}}]
 f.engine.options.driver.act=async()=>{f.engine.tasks[0].lastFailure={code:'WRITE_UNKNOWN',permitId:'permit-conflict'};return '已请求提交'}
 const t=await f.engine.create('安排指定时间的体验'),result=await done(f.engine,t.id);assert.equal(result.status,'WAITING_INPUT');assert.equal(calls,2);f.engine.stop(t.id)
})
test('explicit recovery checks committed outcomes before asking the model to act again',async()=>{
 const f=setup([]);f.persistence.create=async goal=>({id:'remote-task',version:8,goal,status:'PAUSED',plan:{}});f.persistence.next=async()=>{throw Error('Recovered committed work must be verified before new actions')}
 const t=await f.engine.create('把课程价格改成99',{recover:{id:'remote-task',version:7}});assert.equal(t.status,'PAUSED');f.engine.resume(t.id)
 assert.equal((await done(f.engine,t.id)).status,'SUCCEEDED');assert.ok(f.events.includes('verify'))
})
test('a transient identity-check network error pauses a recoverable task instead of closing it as revoked',async()=>{
 const f=setup([{action:'ask_user',arguments:{question:'请补充对象'}}]);let offline=false;f.engine.options.waitTimeout=10000;f.engine.options.timeout=10000;f.engine.options.session=async()=>{if(offline)throw new TypeError('Failed to fetch');return session}
 const t=await f.engine.create('打开训练计划');assert.equal((await done(f.engine,t.id)).status,'WAITING_INPUT');offline=true;await new Promise(r=>setTimeout(r,2200))
 try{assert.equal(f.engine.get(t.id).status,'PAUSED');assert.equal(f.engine.get(t.id).pauseReason,'OFFLINE')}finally{f.engine.stop(t.id)}
})
test('resuming an uncertain in-flight confirmation returns to receipt reconciliation without a stale confirmation card',async()=>{
 const f=setup([{action:'ask_user',arguments:{question:'请选择对象'}}]);f.persistence.resume=async()=>({version:3,status:'RUNNING'});f.persistence.receipts=async()=>[{id:'committed-permit',status:'SUCCEEDED'}]
 const t=await f.engine.create('登记指定预约');await done(f.engine,t.id);f.engine.pause(t.id);const raw=f.engine.raw(t.id);raw.pausedStatus='WAITING_CONFIRMATION';raw.lastFailure={code:'WRITE_UNKNOWN',permitId:'committed-permit'}
 await f.engine.resume(t.id);assert.equal((await done(f.engine,t.id)).status,'SUCCEEDED')
})
