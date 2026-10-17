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
async function done(engine,id){for(let i=0;i<100;i++){const t=engine.get(id);if(['SUCCEEDED','FAILED','WAITING_INPUT','PARTIAL'].includes(t.status))return t;await tick()}throw Error('task did not settle')}
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
