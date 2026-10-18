import {guardWaveBudget,captureUsage} from './budget.mjs'
// Explicit paid-model recovery tests. Faults are injected at the browser/RPC boundary,
// never claimed to be real provider outages; final outcomes use the real page and server.
import assert from 'node:assert/strict'
import {mkdir,writeFile} from 'node:fs/promises'
import {fileURLToPath} from 'node:url'
import {cases} from './case-catalog.mjs'
import {gradeCase,summarize} from './evaluate.mjs'
import {createBrowser,loginPage,startTask,waitTask,call} from './fixtures.mjs'
import {roleAccounts} from './workflow-fixtures.mjs'
const wave=process.env.AGENT_V2_WAVE||('recovery-'+Date.now()),ids=new Set((process.env.AGENT_V2_CASE_IDS||'super_admin-15,manager-16,reception-17').split(','))
const selected=cases.filter(c=>ids.has(c.id));assert.equal(selected.length,ids.size);assert.ok(selected.every(c=>c.kind==='recovery'))
const out=fileURLToPath(new URL('../../../docs/qa/agent-v2-20261017/',import.meta.url));await mkdir(out,{recursive:true})
const report={wave,startedAt:new Date().toISOString(),cases:[],errors:[],blocked:[],notRun:cases.filter(c=>!selected.includes(c)).map(c=>c.id)},browser=await createBrowser()
try{
 for(const c of selected){
  guardWaveBudget(report)
  let context,page,t,release;const entry={id:c.id,role:c.role,kind:c.kind,split:c.split,goal:c.goal,fault:c.fault,startedAt:new Date().toISOString()};report.cases.push(entry)
  try{
   const [user,home]=roleAccounts[c.role];let panel;({context,page,panel}=await loginPage(browser,user,home,report));const witness=await page.evaluate(()=>performance.timeOrigin),writes=[]
   page.on('request',r=>{const p=new URL(r.url()).pathname;if(!['GET','HEAD'].includes(r.method())&&p.startsWith('/api/v1/')&&!p.startsWith('/api/v1/agent/'))writes.push(r.method()+' '+p)})
   let injected=0,notify;const observed=new Promise(r=>notify=r),held=new Promise(r=>release=r)
   if(c.fault==='wrong-dialog'){
    await page.evaluate(()=>{const el=document.createElement('section');el.setAttribute('role','dialog');el.setAttribute('aria-modal','true');el.setAttribute('aria-label','无关操作：不是本次任务');el.style.cssText='position:fixed;inset:180px 30% auto 15%;padding:30px;background:white;z-index:2147482000';el.dataset.recoveryFault='wrong-dialog';el.innerHTML='<h2>无关操作：不是本次任务</h2><p>此前遗留的表单；尚未保存。请按当前用户目标处理。</p><button>保存无关修改</button><button>放弃并关闭</button>';el.children[2].onclick=()=>{window.__wrongRecoverySubmit=true;el.remove()};el.children[3].onclick=()=>el.remove();document.body.append(el)})
    injected=1
   }else await page.route('**/api/v1/agent/tasks/*/next',async route=>{
    const response=await route.fetch(),body=await response.json()
    if(injected++===0&&body.code===0){
     entry.injectedAt=new Date().toISOString()
     if(c.fault==='pause-resume'){notify();await held}
     if(c.fault==='stale-control'){body.data.decision={action:'click',arguments:{ref:'expired_rpc_boundary_ref'},summary:'Injected stale reference'};entry.injectedStale=true}
     if(c.fault==='rate-limit'){body.data.decision=null;body.data.failure={code:'RATE_LIMITED',retryable:true,traceId:'qa-rpc-boundary-rate-limit'};entry.injectedRateLimit=true}
     return route.fulfill({response,json:body})
    }return route.fulfill({response})
   })
   t=await startTask(page,panel,c.goal);entry.taskId=t.id
   if(c.fault==='pause-resume'){
    await Promise.race([observed,new Promise((_,reject)=>setTimeout(()=>reject(Error('No genuine provider response to pause')),100000))]);await call(page,'pauseComputerTask',t.id);const pausedPath=page.url();release();await page.waitForTimeout(900)
    assert.equal((await call(page,'getComputerTask',t.id)).status,'PAUSED');assert.equal(page.url(),pausedPath);entry.pausedCallbackBlocked=true;await call(page,'resumeComputerTask',t.id)
   }
   const done=await waitTask(page,t.id);Object.assign(entry,{status:done.status,verification:done.verification,steps:done.steps,error:done.error,question:done.question});assert.equal(done.status,'SUCCEEDED',done.error||done.question)
   const target={SUPER_ADMIN:'/training-plans',MANAGER:'/courses',RECEPTION:'/schedule-week',COACH:'/training-plans',SALES:'/leads',MEMBER:'/member-home'}[c.role];assert.equal(new URL(page.url()).pathname,target)
   if(c.role==='MEMBER')assert.ok((await page.locator('[role="tab"][aria-selected="true"]').allTextContents()).some(t=>t.includes('训练')))
   assert.equal(await page.locator('[data-recovery-fault]:visible,.el-message-box:visible,.el-dialog:visible').count(),0);assert.notEqual(await page.evaluate(()=>window.__wrongRecoverySubmit),true)
   assert.ok(injected>0);assert.equal(writes.length,0);entry.afterWrites=0;entry.sameDocument=context.pages().length===1&&await page.evaluate(()=>performance.timeOrigin)===witness;assert.equal(entry.sameDocument,true);entry.evidenceChecked=true
  }catch(error){entry.failure=error.message}
  finally{release?.();if(page&&t)try{const current=await call(page,'getComputerTask',t.id);if(!['SUCCEEDED','FAILED','PARTIAL','CANCELLED'].includes(current.status))await call(page,'stopComputerTask',t.id)}catch{};await captureUsage(page,entry);if(context)await context.close();entry.endedAt=new Date().toISOString();entry.grade=gradeCase({...c,expected:{goals:1}},entry);console.log(c.id+' '+(entry.grade.passed?'PASS':'FAIL')+' '+(entry.failure||''));report.summary=summarize(report.cases.map(c=>c.grade));await writeFile(out+'evaluation-'+wave+'.json',JSON.stringify(report,null,2))}
 }
}finally{await browser.close();report.endedAt=new Date().toISOString();await writeFile(out+'evaluation-'+wave+'.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report.summary));if(report.cases.some(c=>!c.grade.passed)||report.errors.length||report.blocked.length)process.exitCode=1}
