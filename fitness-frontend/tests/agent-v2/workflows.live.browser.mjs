// Paid real-model writes; exclusively on the verified marked QA database.
import assert from 'node:assert/strict'
import {mkdir,writeFile} from 'node:fs/promises'
import {fileURLToPath} from 'node:url'
import {cases} from './case-catalog.mjs'
import {gradeCase,summarize} from './evaluate.mjs'
import {createBrowser,loginPage,startTask,waitTask,call} from './fixtures.mjs'
import {prepareWriteFixture} from './workflow-fixtures.mjs'
import {checkQuery} from './query-fixtures.mjs'
const wave=process.env.AGENT_V2_WAVE||('write-dev-'+Date.now()),ids=new Set((process.env.AGENT_V2_CASE_IDS||'reception-08,reception-09,coach-09,sales-09,member-09,super_admin-09').split(','))
const selected=cases.filter(c=>ids.has(c.id));assert.equal(selected.length,ids.size);assert.ok(selected.every(c=>c.kind==='write'||c.kind==='multi'&&c.confirmationPolicy==='ONE'))
const out=fileURLToPath(new URL('../../../docs/qa/agent-v2-20261017/',import.meta.url));await mkdir(out,{recursive:true});const report={wave,startedAt:new Date().toISOString(),cases:[],errors:[],blocked:[],notRun:cases.filter(c=>!selected.includes(c)).map(c=>c.id)},browser=await createBrowser()
async function api(page,path){return page.evaluate(async path=>{const v=await fetch('/api/v1'+path,{headers:{authorization:'Bearer '+localStorage.token}}).then(r=>r.json());if(v.code!==0)throw Error(v.message);return v.data},path)}
try{
 for(const c of selected){
  let context,page,fixture,phase='fixture';const entry={id:c.id,role:c.role,kind:c.kind,split:c.split,startedAt:new Date().toISOString()};report.cases.push(entry)
  try{
   fixture=await prepareWriteFixture(c.kind==='multi'?{...c,id:c.role.toLowerCase()+'-07',kind:'write'}:c);entry.goal=fixture.goal;const before=await fixture.read();let panel;({context,page,panel}=await loginPage(browser,fixture.user,fixture.home,report));const witness=await page.evaluate(()=>performance.timeOrigin),writes=[]
   page.on('request',r=>{const p=new URL(r.url()).pathname;if(!['GET','HEAD'].includes(r.method())&&p.startsWith('/api/v1/')&&!p.startsWith('/api/v1/agent/'))writes.push({method:r.method(),path:p})})
   phase='task';const t=await startTask(page,panel,fixture.goal),pending=await waitTask(page,t.id);Object.assign(entry,{status:pending.status,steps:pending.steps,error:pending.error,question:pending.question});assert.equal(pending.status,'WAITING_CONFIRMATION',pending.error||pending.question)
   const preview=pending.pendingConfirmation;assert.equal(preview.path.split('?')[0],fixture.path);assert.equal(preview.method,fixture.method)
   for(const [name,value] of Object.entries(fixture.fields))assert.ok(preview.fields.some(f=>f.label===name&&f.value===String(value)),'Frozen request mismatch: '+name)
   assert.equal(writes.length,0);assert.deepEqual(await fixture.read(),before);Object.assign(entry,{beforeWrites:0,previewMatches:true});await page.screenshot({path:out+c.id+'-'+wave+'-preview.png'})
   await panel.getByRole('button',{name:'确认并提交这一次',exact:true}).click();const done=await waitTask(page,t.id,preview.id);Object.assign(entry,{status:done.status,steps:done.steps,error:done.error,question:done.question,verification:done.verification});assert.equal(done.status,'SUCCEEDED',done.error||done.question||JSON.stringify(done.verification));assert.equal(writes.length,1);assert.deepEqual(writes[0],{method:fixture.method,path:fixture.path})
   fixture.check(await fixture.read(),before);if(c.kind==='multi')entry.queryOracle=await checkQuery(path=>api(page,path),{...c,goal:fixture.goal.split('，再')[0]},done);entry.evidenceChecked=true;entry.afterWrites=1
   await assert.rejects(call(page,'confirmComputerTask',t.id,preview.id));entry.duplicateRejected=true;assert.equal(writes.length,1);entry.sameDocument=context.pages().length===1&&await page.evaluate(()=>performance.timeOrigin)===witness;assert.equal(entry.sameDocument,true)
   entry.diagnostics=await api(page,'/agent/tasks/'+t.id+'/diagnostics');await page.screenshot({path:out+c.id+'-'+wave+'-complete.png'})
  }catch(error){entry.failure=error.message;if(phase==='fixture')entry.environmentError=true;if(page)await page.screenshot({path:out+c.id+'-'+wave+'-failure.png'})}
  finally{
   if(page)try{const t=(await call(page,'listComputerTasks'))[0];if(t&&!['SUCCEEDED','FAILED','PARTIAL','CANCELLED'].includes(t.status))await call(page,'stopComputerTask',t.id)}catch{}
   if(context)await context.close();entry.endedAt=new Date().toISOString();entry.grade=gradeCase({...c,expected:{goals:c.kind==='multi'?2:1}},entry);console.log(c.id+' '+(entry.grade.passed?'PASS':'FAIL')+' '+(entry.failure||''));report.summary=summarize(report.cases.map(c=>c.grade));await writeFile(out+'evaluation-'+wave+'.json',JSON.stringify(report,null,2))
  }
 }
}finally{await browser.close();report.endedAt=new Date().toISOString();await writeFile(out+'evaluation-'+wave+'.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report.summary));if(report.cases.some(c=>!c.grade.passed)||report.errors.length||report.blocked.length)process.exitCode=1}
