import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequestGate } from '../src/agent/requestGate.mjs'
import { PageTaskEngine } from '../src/agent/pageTaskEngine.mjs'

const tick = () => new Promise(resolve => setTimeout(resolve, 1))
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
const session = { userId: 1, role: 'SUPER_ADMIN', venueId: 1, sessionKey: 's', permissions: ['ASSISTANT_USE'] }
const observation = () => ({url:'http://localhost:5173/dashboard',title:'FitChain',text:'经营看板',revision:'r1',nodes:[]})

test('write gate freezes exact data before sending; confirmation is single use and disables refresh replay', async () => {
  let preview, verified = 0
  const gate = createRequestGate()
  gate.bind({ verify: async () => { verified++ }, onPending: value => { preview = value }, onReceipt: () => {} })
  const body = { name: '本页潜客', phone: '13900000001' }
  const config = { method: 'post', baseURL: '/api', url: '/v1/leads', data: body, headers: {} }
  let sent = false
  const pending = gate.intercept(config).then(value => { sent = true; return value })
  await tick(); assert.equal(sent, false); assert.equal(preview.path, '/api/v1/leads')
  body.name = '被修改'; assert.equal(preview.fields.find(f => f.label === 'name').value, '本页潜客')
  const id = preview.id; await gate.confirm(id); const frozen = await pending
  assert.equal(JSON.parse(frozen.data).name, '本页潜客'); assert.equal(frozen.skipAuthRefresh, true)
  await assert.rejects(gate.confirm(id), /失效|确认/); assert.ok(verified >= 2)
  gate.settle(frozen, true, '真实成功'); gate.unbind()
})

test('stop rejects frozen writes; concurrent writes cannot share one confirmation', async () => {
  const gate = createRequestGate(); gate.bind({verify:async()=>{},onPending:()=>{},onReceipt:()=>{}})
  const first = gate.intercept({method:'post',baseURL:'/api',url:'/v1/leads',data:{name:'a'},headers:{}})
  const caught = assert.rejects(first, /停止/)
  await tick(); await assert.rejects(gate.intercept({method:'post',baseURL:'/api',url:'/v1/leads',data:{name:'b'},headers:{}}), /同时|确认/)
  gate.cancel('任务已停止'); await caught
})

test('identity revocation rejects confirmation; external APIs and authentication writes are blocked', async () => {
  let revoked = false, preview
  const gate = createRequestGate(); gate.bind({verify:async()=>{if(revoked)throw Error('身份失效')},onPending:p=>preview=p,onReceipt:()=>{}})
  const pending=gate.intercept({method:'post',baseURL:'/api',url:'/v1/leads',data:{name:'a'},headers:{}})
  const caught=assert.rejects(pending,/身份/); await tick(); revoked=true
  await assert.rejects(gate.confirm(preview.id),/身份/); await caught
  revoked=false
  await assert.rejects(gate.intercept({method:'post',baseURL:'https://other.example/api',url:'/v1/leads',data:{},headers:{}}), /范围|本页/)
  await assert.rejects(gate.intercept({method:'post',baseURL:'/api',url:'/v1/auth/logout',data:{},headers:{}}), /身份|账号/)
  gate.unbind()
})

function fixture(next) {
  let identity='a', route='/dashboard'; const gate=createRequestGate(), actions=[]
  const engine=new PageTaskEngine({identity:()=>identity, session:async()=>session, next,
    driver:{observe:()=>({...observation(),url:'http://localhost:5173'+route}),act:async(action,args)=>{actions.push(action);if(action==='navigate')route=args.path;return '当前页 '+route},clear:()=>{}},
    gate, interval:0, settle:async()=>{}, timeout:1000, waitTimeout:1000})
  return {engine,gate,actions, route:()=>route, changeIdentity:()=>identity='b'}
}
async function terminal(engine,id) {for(let i=0;i<150;i++){const t=engine.get(id);if(['SUCCEEDED','FAILED','CANCELLED','WAITING_CONFIRMATION'].includes(t.status))return t;await tick()}throw Error('timeout')}

test('engine navigates the existing driver and only completes after a real action', async () => {
  let n=0;const f=fixture(async()=>++n===1?{action:'navigate',arguments:{path:'/training-plans'}}:{action:'finish',arguments:{}})
  const t=await f.engine.create('打开本网页的训练计划');const done=await terminal(f.engine,t.id)
  assert.equal(done.status,'SUCCEEDED');assert.equal(f.route(),'/training-plans');assert.deepEqual(f.actions,['navigate'])
  const empty=fixture(async()=>({action:'finish',arguments:{}}));const e=await empty.engine.create('打开训练计划')
  assert.equal((await terminal(empty.engine,e.id)).status,'FAILED')
})

test('late model decisions after pause, stop or identity replacement never act', async () => {
  for(const mode of ['pause','stop','identity']) {
    const pending=deferred(),f=fixture(()=>pending.promise),t=await f.engine.create('打开训练计划');await tick()
    if(mode==='pause')f.engine.pause(t.id);else if(mode==='stop')f.engine.stop(t.id);else f.changeIdentity()
    pending.resolve({action:'navigate',arguments:{path:'/training-plans'}});await tick();await tick()
    assert.equal(f.actions.length,0,mode);f.engine.stop(t.id)
  }
})

test('a real page write waits for confirmation then requires a successful receipt', async () => {
  let f,n=0,request
  f=fixture(async()=>++n===1?{action:'click',arguments:{ref:'r1'}}:{action:'finish',arguments:{}})
  f.engine.options.driver.act=async()=>{request=f.gate.intercept({method:'post',baseURL:'/api',url:'/v1/leads',data:{name:'a'},headers:{}}).then(c=>{f.gate.settle(c,true,'已新增');return c});return '已点击保存'}
  const t=await f.engine.create('新建潜客');const waiting=await terminal(f.engine,t.id)
  assert.equal(waiting.status,'WAITING_CONFIRMATION');await f.engine.confirm(t.id,waiting.pendingConfirmation.id);await request
  assert.equal((await terminal(f.engine,t.id)).status,'SUCCEEDED')
})

test('filling a form without a business receipt cannot complete a requested save', async () => {
  let n=0;const f=fixture(async()=>++n===1?{action:'fill',arguments:{ref:'r1',value:'a'}}:{action:'finish',arguments:{}})
  const t=await f.engine.create('新建潜客并保存记录');assert.equal((await terminal(f.engine,t.id)).status,'FAILED')
})

test('delayed old submissions stay blocked even when manual takeover requests a fresh document', async () => {
  const gate=createRequestGate();gate.bind({verify:async()=>{},onPending:()=>{},onReceipt:()=>{}})
  gate.cancel('任务已停止');gate.unbind()
  await assert.rejects(gate.intercept({method:'post',baseURL:'/api',url:'/v1/leads',data:{name:'late'},headers:{}}),/停止|接管/)
  let reloaded=false;gate.takeOver(()=>{reloaded=true})
  assert.equal(reloaded,true)
  await assert.rejects(gate.intercept({method:'post',baseURL:'/api',url:'/v1/leads',data:{name:'late-again'},headers:{}}),/停止|接管|刷新/)
  const freshDocumentGate=createRequestGate(),config={method:'post',baseURL:'/api',url:'/v1/leads',data:{name:'human'},headers:{}}
  assert.equal(await freshDocumentGate.intercept(config),config)
})

test('closing or cancelling while initial session is loading cannot create a late running task', async () => {
  for(const change of ['suspend','invalidate','caller']){
    const pending=deferred(),f=fixture(async()=>({action:'navigate',arguments:{path:'/training-plans'}}))
    f.engine.options.session=()=>pending.promise
    const controller=new AbortController(),creating=f.engine.create('打开训练计划',{signal:controller.signal}).then(()=>true,()=>false)
    if(change==='caller')controller.abort();else f.engine[change]()
    pending.resolve(session);assert.equal(await creating,false,change);await tick()
    assert.equal(f.actions.length,0);assert.equal(f.engine.list().length,0)
    // No task lease was acquired: ordinary reads still work.
    assert.equal((await f.gate.intercept({method:'get',url:'/v1/leads',baseURL:'/api'})).method,'get')
  }
})

test('changing the current URL invalidates an already frozen confirmation', async () => {
  const original=globalThis.location
  globalThis.location={origin:'http://localhost',href:'http://localhost/leads'}
  try {
    let preview;const gate=createRequestGate();gate.bind({verify:async()=>{},onPending:p=>preview=p,onReceipt:()=>{}})
    const pending=gate.intercept({method:'post',baseURL:'/api',url:'/v1/leads',data:{name:'a'},headers:{}})
    const rejected=assert.rejects(pending,/页面/);await tick();globalThis.location.href='http://localhost/members'
    await assert.rejects(gate.confirm(preview.id),/页面/);await rejected;gate.unbind()
  } finally {if(original===undefined)delete globalThis.location;else globalThis.location=original}
})

test('closing the assistant during confirmation pauses it before the write can leave', async () => {
  const verification=deferred();let sent=false,n=0,request
  const f=fixture(async()=>++n===1?{action:'click',arguments:{ref:'save'}}:{action:'finish',arguments:{}})
  f.engine.options.driver.act=async()=>{request=f.gate.intercept({method:'post',baseURL:'/api',url:'/v1/leads',data:{name:'a'},headers:{}}).then(c=>{sent=true;f.gate.settle(c,true,'成功')});request.catch(()=>{});return '点击保存'}
  const t=await f.engine.create('新增潜客'),waiting=await terminal(f.engine,t.id)
  f.engine.options.session=()=>verification.promise
  const confirmation=f.engine.confirm(t.id,waiting.pendingConfirmation.id).catch(e=>e)
  f.engine.suspend();verification.resolve(session);await confirmation;await tick()
  assert.equal(sent,false);assert.equal(f.engine.get(t.id).status,'PAUSED')
  f.engine.options.session=async()=>session;f.engine.resume(t.id)
  assert.equal(f.engine.get(t.id).status,'WAITING_CONFIRMATION')
  await f.engine.confirm(t.id,waiting.pendingConfirmation.id);await request
  assert.equal((await terminal(f.engine,t.id)).status,'SUCCEEDED')
})

test('pause aborts the operation-specific file request and does not mark the paused step complete', async () => {
  let operation
  const f=fixture(async()=>({action:'export_schedule',arguments:{from:'2026-10-16',to:'2026-10-17'}}))
  f.engine.options.exportSchedule=t=>new Promise((resolve,reject)=>{operation=t.operationController;const timer=setTimeout(()=>resolve({id:'file',name:'课表.csv'}),100);operation?.signal.addEventListener('abort',()=>{clearTimeout(timer);reject(new DOMException('Aborted','AbortError'))})})
  const t=await f.engine.create('生成课表文件');for(let i=0;i<30&&!operation;i++)await tick()
  f.engine.pause(t.id);assert.equal(operation.signal.aborted,true);await tick();assert.equal(f.engine.get(t.id).status,'PAUSED');f.engine.stop(t.id)
})

test('generating a file cannot claim an explicit request to open it is complete', async () => {
  let n=0;const f=fixture(async()=>++n===1?{action:'export_schedule',arguments:{from:'2026-10-16',to:'2026-10-17'}}:{action:'finish',arguments:{}})
  f.engine.options.exportSchedule=async()=>({id:'artifact',name:'课表.csv'})
  const t=await f.engine.create('生成CSV课表文件并打开');assert.equal((await terminal(f.engine,t.id)).status,'FAILED')
})

test('completed tasks keep stray writes blocked while allowing another guarded agent task', async () => {
  const gate=createRequestGate();gate.bind({verify:async()=>{},onPending:()=>{},onReceipt:()=>{}});gate.unbind({completed:true})
  await assert.rejects(gate.intercept({method:'post',baseURL:'/api',url:'/v1/leads',data:{name:'old callback'},headers:{}}),/刷新/)
  gate.bind({verify:async()=>{},onPending:()=>{},onReceipt:()=>{}})
  const read={method:'get',baseURL:'/api',url:'/v1/leads'};assert.equal(await gate.intercept(read),read);gate.settle(read,true,'read');gate.unbind()
})

test('repeated export decisions reuse one artifact reference in subsequent planning', async () => {
  let n=0;const f=fixture(async()=>++n<3?{action:'export_schedule',arguments:{from:'2026-10-16',to:'2026-10-17'}}:{action:'finish',arguments:{}})
  f.engine.options.exportSchedule=async()=>({id:'same-artifact',name:'课表.csv'})
  const t=await f.engine.create('生成课表文件');const done=await terminal(f.engine,t.id)
  assert.equal(done.status,'SUCCEEDED');assert.equal(done.artifacts.length,1)
})
