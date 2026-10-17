import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequestGate } from '../../src/agent/requestGate.mjs'
const tick=()=>new Promise(r=>setTimeout(r,5))
test('server permit is prepared before preview and approved only after the human confirms',async()=>{
  const gate=createRequestGate(),events=[];let preview
  gate.bind({verify:async()=>{},prepare:async request=>{events.push('prepare');assert.equal(request.method,'PUT');return {id:'permit-1',before:{fee:88},request:{fee:99}}},approve:async id=>{events.push('approve');assert.equal(id,'permit-1')},onPending:p=>{events.push('preview');preview=p},onReceipt:()=>{}})
  const pending=gate.intercept({method:'put',url:'/v1/admin/courses/1',baseURL:'/api',data:{fee:99},headers:{}})
  await tick();assert.deepEqual(events,['prepare','preview']);assert.equal(preview.serverPermitId,'permit-1')
  await gate.confirm(preview.id);const config=await pending;assert.deepEqual(events,['prepare','preview','approve']);assert.equal(config.headers['X-FitChain-Agent-Permit'],'permit-1')
  gate.settle(config,true,'ok');gate.unbind()
})
test('stopping during delayed server approval cannot release the business request',async()=>{
  const gate=createRequestGate();let preview,release
  gate.bind({verify:async()=>{},prepare:async()=>({id:'p'}),approve:()=>new Promise(r=>{release=r}),onPending:p=>preview=p,onReceipt:()=>{}})
  const pending=gate.intercept({method:'post',url:'/v1/messages/send',baseURL:'/api',data:{content:'你好'},headers:{}})
  const rejected=assert.rejects(pending,/停止|失效/);await tick();const confirming=gate.confirm(preview.id).catch(e=>e);await tick();gate.cancel();release();await confirming;await rejected
})
test('pause during approval and refreshed preview require a new human confirmation',async()=>{
  const gate=createRequestGate();let preview,release,n=0;const approvals=[]
  gate.bind({verify:async()=>{},prepare:async()=>({id:`permit-${++n}`}),approve:id=>{approvals.push(id);return n===1?new Promise(r=>{release=r}):Promise.resolve()},onPending:p=>preview=p,onReceipt:()=>{}})
  const pending=gate.intercept({method:'post',url:'/v1/messages/send',baseURL:'/api',data:{content:'你好'},headers:{}})
  await tick();const oldId=preview.id,confirming=gate.confirm(oldId);await tick();gate.suspend();release();await assert.rejects(confirming,/暂停/)
  await gate.refreshPending();assert.notEqual(preview.id,oldId);await assert.rejects(gate.confirm(oldId),/失效/)
  await gate.confirm(preview.id);assert.equal((await pending).headers['X-FitChain-Agent-Permit'],'permit-2');assert.deepEqual(approvals,['permit-1','permit-2']);gate.cancel()
})
test('cancelling a pending old goal during reservation cannot create a preview',async()=>{
  const gate=createRequestGate();let release,shown=0
  gate.bind({verify:async()=>{},prepare:()=>new Promise(r=>{release=r}),onPending:()=>shown++,onReceipt:()=>{}})
  const pending=gate.intercept({method:'put',url:'/v1/admin/courses/1',baseURL:'/api',data:{fee:99},headers:{}})
  const rejected=assert.rejects(pending,/失效|停止/);await tick();gate.cancelPending();release({id:'old-permit'});await rejected;assert.equal(shown,0);assert.equal(gate.pending,undefined);gate.cancel()
})
