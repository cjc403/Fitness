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
