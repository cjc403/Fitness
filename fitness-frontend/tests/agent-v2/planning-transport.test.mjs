import test from 'node:test'
import assert from 'node:assert/strict'
import {fetchPlanningTurn} from '../../src/agent/planningTransport.mjs'
const url='/api/v1/agent/tasks/task-a/next',init={method:'POST',body:'{"version":0,"document":"same"}'}
test('lost planning transport retries the exact same request and returns the authoritative turn',async()=>{
 const seen=[],response={ok:true,status:200};const result=await fetchPlanningTurn(url,init,{retryDelay:0,fetcher:async(u,options)=>{seen.push([u,options]);if(seen.length===1)throw new TypeError('Failed to fetch');return response}})
 assert.equal(result,response);assert.equal(seen.length,2);assert.equal(seen[0][1],seen[1][1]);assert.equal(seen[1][1].body,init.body)
})
test('preparation approval and ordinary business writes never inherit planning retries',async()=>{
 for(const path of ['/api/v1/agent/tasks/t/prepare','/api/v1/agent/tasks/t/approve/r','/api/v1/leads']){let calls=0;await assert.rejects(fetchPlanningTurn(path,init,{retryDelay:0,fetcher:async()=>{calls++;throw new TypeError('Failed to fetch')}}));assert.equal(calls,1)}
})
test('gateway failures are bounded while permission denial and cancellation are never retried',async()=>{
 let calls=0;const final={status:200};assert.equal(await fetchPlanningTurn(url,init,{retryDelay:0,fetcher:async()=>++calls===1?{status:503}:final}),final);assert.equal(calls,2)
 calls=0;const denied={status:403};assert.equal(await fetchPlanningTurn(url,init,{retryDelay:0,fetcher:async()=>{calls++;return denied}}),denied);assert.equal(calls,1)
 calls=0;await assert.rejects(fetchPlanningTurn(url,init,{retryDelay:0,fetcher:async()=>{calls++;throw new TypeError('Failed to fetch')}}));assert.equal(calls,3)
 calls=0;const controller=new AbortController();await assert.rejects(fetchPlanningTurn(url,{...init,signal:controller.signal},{retryDelay:0,fetcher:async()=>{calls++;controller.abort();throw new TypeError('Failed to fetch')}}));assert.equal(calls,1)
 calls=0;await assert.rejects(fetchPlanningTurn(url,init,{retryDelay:0,canRetry:()=>false,fetcher:async()=>{calls++;throw new TypeError('Failed to fetch')}}));assert.equal(calls,1)
})
test('an identity change during reconnect delay prevents the delayed request',async()=>{
 let calls=0,active=true
 await assert.rejects(fetchPlanningTurn(url,init,{retryDelay:25,canRetry:()=>active,fetcher:async()=>{calls++;setTimeout(()=>{active=false},0);throw new TypeError('Failed to fetch')}}))
 assert.equal(calls,1)
})
