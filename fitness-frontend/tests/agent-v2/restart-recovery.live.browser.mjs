// Real commit + withheld response + owned QA restart + new document recovery.
// Run alone: it intentionally restarts ONLY the verified QA runtime, never daily services.
import assert from 'node:assert/strict'
import {readFile,writeFile} from 'node:fs/promises'
import {fileURLToPath} from 'node:url'
import {execFile} from 'node:child_process'
import {promisify} from 'node:util'
import {createBrowser,fixtureApi,loginPage,startTask,waitTask,call,assertV2Target} from './fixtures.mjs'
const scratch=new URL('../../../../.agent-v2-20261017/',import.meta.url),report={startedAt:new Date().toISOString(),errors:[],blocked:[]},browser=await createBrowser();let context,release
try{
 const api=await fixtureApi('admin'),name='重启恢复验收'+Date.now().toString(36),course=await api('/admin/courses','POST',{name,type:'REGULAR',venueId:1,durationMinutes:45,maxCapacity:20,fee:88,intensity:'LOW'})
 let page,panel;({page,panel,context}=await loginPage(browser,'admin','/dashboard',report));page.on('pageerror',e=>{(report.errorDetails??=[]).push({message:e.message,stack:e.stack})});page.on('response',r=>{if(r.status()>=500)(report.failedResponses??=[]).push({path:new URL(r.url()).pathname,status:r.status()})});const firstDocument=await page.evaluate(()=>performance.timeOrigin);let committed,originalBody,originalHeaders,attempts=0,providerRequests=0,notify
 const saved=new Promise(r=>notify=r),held=new Promise(r=>release=r)
 page.on('request',r=>{if(new URL(r.url()).pathname.endsWith('/next'))providerRequests++})
 await page.route('**/api/v1/admin/courses/'+course.id,async route=>{
  if(route.request().method()!=='PUT')return route.continue()
  attempts++;originalBody=route.request().postData();originalHeaders=route.request().headers()
  const response=await route.fetch();committed=await response.json();assert.equal(committed.code,0);notify();await held
  try{await route.fulfill({response})}catch{report.oldResponseDiscarded=true}
 })
 const t=await startTask(page,panel,'把'+name+'价格改成99'),pending=await waitTask(page,t.id);assert.equal(pending.status,'WAITING_CONFIRMATION');assert.equal(attempts,0)
 assert.ok(pending.pendingConfirmation.fields.some(f=>f.label==='fee'&&f.value==='99'));await panel.getByRole('button',{name:'确认并提交这一次',exact:true}).click()
 let commitTimer;try{await Promise.race([saved,new Promise((_,reject)=>commitTimer=setTimeout(()=>reject(Error('No committed write before recovery')),100000))])}finally{clearTimeout(commitTimer)}assert.equal(attempts,1);assert.equal(Number((await api('/admin/courses')).find(c=>c.id===course.id).fee),99)
 const buildRoot=(await readFile(new URL('qa-build-root.txt',scratch),'utf8')).trim();assert.match(buildRoot,/\\Temp\\fitchain-agent-v2-[a-f0-9]{32}$/i)
 await promisify(execFile)('powershell.exe',['-NoProfile','-ExecutionPolicy','Bypass','-File',fileURLToPath(new URL('restart-qa.ps1',scratch)),'-BuildRoot',buildRoot],{windowsHide:true,timeout:60000,maxBuffer:30000});report.backendRestarted=true;await assertV2Target()
 await page.reload();await page.waitForURL('**/courses');assert.notEqual(await page.evaluate(()=>performance.timeOrigin),firstDocument);release();await page.unroute('**/api/v1/admin/courses/'+course.id)
 panel=page.getByRole('dialog',{name:'FitChain 业务助手'});if(!await panel.isVisible())await page.getByRole('button',{name:'打开业务助手',exact:true}).click()
 // Initialize capabilities, then explicitly recover and resume under a new document lease.
 await call(page,'getComputerAgentStatus');const callsBefore=providerRequests;const recovered=await call(page,'recoverComputerTask',t.id);assert.equal(recovered.status,'PAUSED');await call(page,'resumeComputerTask',t.id)
 const done=await waitTask(page,t.id);assert.equal(done.status,'SUCCEEDED',done.error||done.question);assert.equal(providerRequests,callsBefore,'Already committed goal must be checked before new model actions')
 assert.equal(attempts,1);assert.equal(Number((await api('/admin/courses')).find(c=>c.id===course.id).fee),99)
 const replay=await page.evaluate(async({id,body,permit})=>{const r=await fetch('/api/v1/admin/courses/'+id,{method:'PUT',headers:{authorization:'Bearer '+localStorage.token,'content-type':'application/json','X-FitChain-Agent-Permit':permit},body});return r.json()},{id:course.id,body:originalBody,permit:originalHeaders['x-fitchain-agent-permit']});assert.notEqual(replay.code,0)
 assert.equal(Number((await api('/admin/courses')).find(c=>c.id===course.id).fee),99);report.result={status:done.status,backendRestarted:true,newDocument:true,committedWrites:1,replayedBusinessWrites:0,oldPermitRejected:true,modelCallsAfterRecovery:0,verification:done.verification};assert.deepEqual(report.errors,[]);assert.deepEqual(report.blocked,[])
}catch(e){report.failure=e.message;throw e}finally{release?.();await context?.close();await browser.close();report.endedAt=new Date().toISOString();await writeFile(new URL('restart-recovery-report.json',scratch),JSON.stringify(report,null,2));console.log(JSON.stringify(report.result||{failure:report.failure}))}
