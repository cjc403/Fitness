import test from 'node:test'
import assert from 'node:assert/strict'
import {checkQuery} from './query-fixtures.mjs'
test('the independent member oracle reads actual PageResult items and rejects a missing member',async()=>{
 const rows=[{id:1},{id:2}],api=async()=>({items:rows,total:2,page:1,size:20}),c={role:'SUPER_ADMIN',goal:'查询会员'}
 const facts=[{tool:'members',asOf:new Date().toISOString(),data:{items:rows,total:2,page:1,size:20},pagination:{page:1,size:20,total:2,contentTruncated:false}}]
 assert.equal((await checkQuery(api,c,{facts})).expectedIds.length,2)
 facts[0].data.items=[{id:1}];await assert.rejects(checkQuery(api,c,{facts}),/differs from original scoped API/)
})
test('the independent oracle checks every original API page and rejects a missing evidence page',async()=>{
 const all=Array.from({length:45},(_,i)=>({id:i+1})),calls=[],c={role:'SUPER_ADMIN',goal:'查询会员'}
 const api=async path=>{calls.push(path);const page=Number(new URL(path,'http://fixture').searchParams.get('page'));return {records:all.slice((page-1)*20,page*20),total:45}}
 const facts=[1,2,3].map(page=>({tool:'members',asOf:new Date().toISOString(),data:all.slice((page-1)*20,page*20),pagination:{page,size:20,total:45,contentTruncated:false}}))
 assert.equal((await checkQuery(api,c,{facts})).expectedIds.length,45);assert.equal(calls.length,3)
 await assert.rejects(checkQuery(api,c,{facts:[facts[0],facts[2]]}))
 facts[1].data[0]={id:900};await assert.rejects(checkQuery(api,c,{facts}),/differs from original scoped API/)
})
test('a named course query uses the default date range and the requested keyword',async()=>{
 const local=d=>[d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-'),date=local(new Date()),c={role:'MEMBER',goal:'查询名称为瑜伽验收的课程'}
 const api=async path=>path==='/agent/computer/session'?{userId:1}:[{scheduleId:1,courseName:'瑜伽验收',startTime:date+'T14:00:00'},{scheduleId:2,courseName:'其他课程',startTime:date+'T14:00:00'}]
 const facts=[{tool:'courses',asOf:new Date().toISOString(),data:[{scheduleId:1}],truncated:false}]
 assert.deepEqual((await checkQuery(api,c,{facts})).actualIds,[1])
})
