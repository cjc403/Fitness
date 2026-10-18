import test from 'node:test'
import assert from 'node:assert/strict'
import {usageOf,usageStats} from './budget.mjs'
test('matrix usage counts actual provider tokens, including failed tasks',()=>{
 assert.deepEqual(usageOf([{id:'ok',taskId:'a',status:'SUCCEEDED',diagnostics:{totalTokens:12,modelCalls:1,usageComplete:true}},{id:'failed',taskId:'b',status:'FAILED',diagnostics:{totalTokens:30,modelCalls:2,usageComplete:true}}]),{tokens:42,calls:3,unknown:[]})
})
test('a reserved provider call with no usage cannot silently be billed as zero',()=>{
 assert.deepEqual(usageOf([{id:'timeout',taskId:'t',diagnostics:{totalTokens:0,modelCalls:1,usageComplete:false}}]).unknown,['timeout'])
})
test('missing or invalid usage cannot silently become a zero-cost task',()=>{
 const r=usageOf([{id:'missing',taskId:'x'},{id:'bad',taskId:'y',diagnostics:{totalTokens:-1,modelCalls:2}},{id:'setup',environmentError:true}]);assert.deepEqual(r.unknown,['missing','bad']);assert.equal(r.tokens,0)
})
test('cost and latency percentiles include failed measured tasks without inventing unknown usage',()=>{
 const rows=[{id:'a',taskId:'1',startedAt:'2026-10-18T01:00:00Z',endedAt:'2026-10-18T01:00:10Z',diagnostics:{totalTokens:10,modelCalls:1,usageComplete:true}},{id:'b',taskId:'2',status:'FAILED',startedAt:'2026-10-18T01:00:00Z',endedAt:'2026-10-18T01:00:30Z',diagnostics:{totalTokens:50,modelCalls:3,usageComplete:true}},{id:'missing',taskId:'3'}]
 const s=usageStats(rows);assert.equal(s.measured,2);assert.deepEqual(s.tokens,{p50:10,p95:50,max:50});assert.deepEqual(s.calls,{p50:1,p95:3,max:3});assert.deepEqual(s.durationSeconds,{p50:10,p95:30,max:30});assert.equal(s.totals.tokens,60);assert.deepEqual(s.totals.unknown,['missing'])
})
