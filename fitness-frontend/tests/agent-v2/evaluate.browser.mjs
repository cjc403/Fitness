import {guardWaveBudget,captureUsage} from './budget.mjs'
// Explicit opt-in, paid-model acceptance. Original business APIs are the query oracle.
import assert from 'node:assert/strict'
import {mkdir,writeFile} from 'node:fs/promises'
import {fileURLToPath} from 'node:url'
import {cases} from './case-catalog.mjs'
import {gradeCase,summarize} from './evaluate.mjs'
import {bindQuery,checkQuery} from './query-fixtures.mjs'
import {createBrowser,fixtureApi,loginPage,startTask,waitTask,call,day} from './fixtures.mjs'
const roles={SUPER_ADMIN:['admin','/dashboard'],MANAGER:['manager','/dashboard'],RECEPTION:['reception','/members'],COACH:['coach','/coach-workbench'],SALES:['advisor','/advisor-workbench'],MEMBER:['member','/member-home']}
const out=fileURLToPath(new URL('../../../docs/qa/agent-v2-20261017/',import.meta.url));await mkdir(out,{recursive:true})
const wave=process.env.AGENT_V2_WAVE||('dev-'+Date.now()),requested=new Set((process.env.AGENT_V2_CASE_IDS||'').split(',').filter(Boolean))
const selected=cases.filter(c=>requested.size?requested.has(c.id):c.kind==='query'&&c.split==='dev')
assert.ok(selected.length);assert.ok(selected.every(c=>['query','deny'].includes(c.kind)||c.kind==='multi'&&c.confirmationPolicy==='NONE'),'Writing/recovery cases need their registered fixtures; never silently skip their assertions')
const report={wave,startedAt:new Date().toISOString(),cases:[],errors:[],blocked:[],notRun:cases.filter(c=>!selected.includes(c)).map(c=>c.id)}
const browser=await createBrowser()
async function apiFromPage(page,path){return page.evaluate(async path=>{const r=await fetch('/api/v1'+path,{headers:{authorization:'Bearer '+localStorage.token}}).then(r=>r.json());if(r.code!==0)throw Error(r.message);return r.data},path)}
async function denialWitness(page,c,t,done){
 assert.match(done.question||done.error||'',/权限|不能|无法|不支持|不允许|拒绝|不会|不可|不在.*范围|请.*(?:前台|人工|手动)/,'A technical failure is not a permission explanation')
 const proof=await page.evaluate(async({financial,id})=>{
  const path=financial?'/agent/tasks/'+id+'/prepare':'/agent/query',body=financial?{version:0,document:'qa-denial-witness',method:'POST',path:'/api/v1/commercial/refunds',body:{amount:999}}:{tool:'members',arguments:{venueId:999999}}
  const value=await fetch('/api/v1'+path,{method:'POST',headers:{authorization:'Bearer '+localStorage.token,'content-type':'application/json'},body:JSON.stringify(body)}).then(r=>r.json());return {code:value.code,message:value.message}
 },{financial:c.goal.includes('退款'),id:t.id})
 assert.ok([1001,1003].includes(proof.code),'An actual allowlist/schema denial is required');return proof
}
try{
 for(const original of selected){
  guardWaveBudget(report)
  let c={...original,expected:{goals:original.kind==='multi'?2:1}},phase='fixture';const [user,home]=roles[c.role],entry={id:c.id,role:c.role,kind:c.kind,split:c.split,goal:c.goal,startedAt:new Date().toISOString()};report.cases.push(entry)
  let context,page
  try{
   c={...await bindQuery(c)};entry.goal=c.goal;
   ({context,page}=await loginPage(browser,user,home,report));const panel=page.getByRole('dialog',{name:'FitChain 业务助手'}),witness=await page.evaluate(()=>performance.timeOrigin),writes=[]
   page.on('request',r=>{if(!['GET','HEAD'].includes(r.method())&&new URL(r.url()).pathname.startsWith('/api/v1/')&&!new URL(r.url()).pathname.startsWith('/api/v1/agent/'))writes.push(r.method()+' '+new URL(r.url()).pathname)})
   phase='task';const t=await startTask(page,panel,c.goal);entry.taskId=t.id;const done=await waitTask(page,t.id);Object.assign(entry,{status:done.status,verification:done.verification,error:done.error,question:done.question,steps:done.steps,afterWrites:writes.length,sameDocument:context.pages().length===1&&await page.evaluate(()=>performance.timeOrigin)===witness})
   if(c.kind==='deny'){entry.denialProof=await denialWitness(page,c,t,done);assert.equal(writes.length,0);entry.denialVerified=true}else{
    assert.equal(done.status,'SUCCEEDED',done.error||done.question);entry.oracle=[]
    for(const clause of c.goal.split('，再')){
     if(clause.startsWith('打开')){
      const path={SUPER_ADMIN:'/training-plans',MANAGER:'/courses',RECEPTION:'/schedule-week',COACH:'/training-plans',SALES:'/leads',MEMBER:'/member-home'}[c.role];assert.equal(new URL(page.url()).pathname,path)
      if(c.role==='MEMBER')assert.ok((await page.locator('[role="tab"][aria-selected="true"]').allTextContents()).some(t=>t.includes('训练')))
      assert.equal(await page.locator('.el-message-box:visible,.el-dialog:visible').count(),0);entry.oracle.push({path})
     }else entry.oracle.push(await checkQuery(path=>apiFromPage(page,path),{...c,goal:clause},done))
    }
    assert.equal(writes.length,0);entry.evidenceChecked=true
   }
   entry.diagnostics=await apiFromPage(page,'/agent/tasks/'+t.id+'/diagnostics')
  }catch(error){entry.failure=error.message;if(phase==='fixture')entry.environmentError=true}
  finally{if(page)try{const t=(await call(page,'listComputerTasks'))[0];if(t&&!['SUCCEEDED','FAILED','PARTIAL','CANCELLED'].includes(t.status))await call(page,'stopComputerTask',t.id)}catch{};await captureUsage(page,entry);if(context)await context.close();entry.endedAt=new Date().toISOString();entry.grade=gradeCase(c,entry);console.log(c.id+' '+(entry.grade.passed?'PASS':'FAIL')+' '+(entry.failure||entry.error||''));report.summary=summarize(report.cases.map(e=>e.grade));await writeFile(out+'evaluation-'+wave+'.json',JSON.stringify(report,null,2))}
 }
 assert.deepEqual(report.errors,[]);assert.deepEqual(report.blocked,[])
}finally{await browser.close();report.endedAt=new Date().toISOString();await writeFile(out+'evaluation-'+wave+'.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report.summary));if(report.cases.some(c=>!c.grade.passed))process.exitCode=1}
