import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdtemp } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { createServer } from '../src/server.mjs'

test('same-page file capability creates no browser task and binds artifacts to actor, session and task',async t=>{
  let actor={userId:1,role:'COACH',venueId:1,sessionKey:'a'},opens=0,queries=0
  const server=createServer({bridgeToken:'private',frontendOrigin:'http://localhost:5173',backendOrigin:'http://localhost:8080',outputDirectory:await mkdtemp(path.join(tmpdir(),'fitchain-page-file-')),executablePath:'unused'}, {backend:{session:async()=>actor,schedule:async()=>{queries++;return [{courseName:'课程'}]}},openFile:async()=>{opens++}})
  await new Promise(r=>server.listen(0,'127.0.0.1',r));t.after(async()=>{await server.closeAgent();await new Promise(r=>server.close(r))})
  const api=async(p,body)=>{const r=await fetch(`http://127.0.0.1:${server.address().port}/v1`+p,{method:body?'POST':'GET',headers:{authorization:'Bearer fixture','x-fitchain-bridge-token':'private','content-type':'application/json'},body:body?JSON.stringify(body):undefined});return {status:r.status,...await r.json()}}
  const body={taskId:'current-page-1',from:'2026-10-16',to:'2026-10-17'}
  const result=await api('/page-files/export',body);assert.equal(result.code,0,result.message);assert.ok(result.data.sha256)
  assert.deepEqual((await api('/tasks')).data,[],'File capability must not start an independent browser')
  assert.equal((await api('/page-files/export',body)).data.id,result.data.id);assert.equal(queries,1)
  const open={taskId:body.taskId,artifactId:result.data.id}
  assert.equal((await api('/page-files/open',open)).code,0);assert.equal(opens,1)
  assert.equal((await api('/page-files/open',open)).code,0);assert.equal(opens,1,'Repeated file-open request must not create another OS window')
  assert.equal((await api('/page-files/open',{...open,taskId:'other-page'})).status,403)
  actor={...actor,sessionKey:'b'};assert.equal((await api('/page-files/open',open)).status,403)
})
