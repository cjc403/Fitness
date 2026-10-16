// Opt-in real provider + controlled Edge proof. Business data uses only the marked isolated DB.
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { assertIsolatedBrowserTarget } from './helpers/isolated-browser.mjs'
await assertIsolatedBrowserTarget()
const base='http://127.0.0.1:4177', out=fileURLToPath(new URL('../../docs/qa/computer-agent-20261016/',import.meta.url))
await mkdir(out,{recursive:true})
const report={at:new Date().toISOString(),kind:'Real model, isolated PostgreSQL, actual controlled Edge DOM execution; no normal database writes',roles:[]}
const cases=[
 ['SUPER_ADMIN','admin','打开周课表，查看课程安排，然后结束任务'],
 ['MANAGER','manager','打开会员列表，查看本店会员，然后结束任务'],
 ['RECEPTION','reception','打开周课表，查看课程安排，然后结束任务'],
 ['COACH','coach','打开周课表，查看我的课程，然后结束任务'],
 ['SALES','advisor','打开潜客与体验课页面，查看我的潜客，然后结束任务'],
 ['MEMBER','member','点击我的训练，查看自己的训练计划，然后结束任务']
]
const delay=ms=>new Promise(r=>setTimeout(r,ms))
for(const [role,username,goal] of cases){
 const entry={role,goal};report.roles.push(entry)
 try{
  const login=await fetch(base+'/api/v1/auth/login',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({username,password:'password'})}).then(r=>r.json())
  assert.equal(login.code,0);assert.equal(login.data.userInfo.role,role)
  const token=login.data.accessToken
  const api=async(p,body)=>{const r=await fetch(base+'/local-agent/v1'+p,{method:body===undefined?'GET':'POST',headers:{authorization:'Bearer '+token,'content-type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});const v=await r.json();assert.equal(v.code,0,JSON.stringify({http:r.status,message:v.message}));return v.data}
  assert.equal((await api('/status')).available,true)
  const task=await api('/tasks',{goal});entry.id=task.id
  let result
  for(let i=0;i<240;i++){
   result=await api('/tasks/'+task.id)
   if(['SUCCEEDED','FAILED','CANCELLED','WAITING_INPUT','WAITING_CONFIRMATION'].includes(result.status))break
   await delay(750)
  }
  entry.status=result.status;entry.steps=result.steps;entry.error=result.error;entry.question=result.question
  if(result.screenshot)await writeFile(out+role.toLowerCase()+'.jpg',Buffer.from(result.screenshot.split(',')[1],'base64'))
  if(!['SUCCEEDED','FAILED','CANCELLED'].includes(result.status))await api('/tasks/'+task.id+'/stop',{})
  assert.equal(result.status,'SUCCEEDED',result.error||result.question||result.status)
  assert.ok(result.steps.some(s=>['click','navigate'].includes(s.action)&&s.status==='SUCCEEDED'))
 }catch(e){entry.failure=e.message;process.exitCode=1}
 await writeFile(out+'real-model-six-roles.json',JSON.stringify(report,null,2))
 process.stdout.write(JSON.stringify(entry)+'\n')
}
