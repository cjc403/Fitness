// Explicit opt-in, paid-model acceptance. Original business APIs are the query oracle.
import assert from 'node:assert/strict'
import {mkdir,writeFile} from 'node:fs/promises'
import {fileURLToPath} from 'node:url'
import {cases} from './case-catalog.mjs'
import {gradeCase,summarize} from './evaluate.mjs'
import {createBrowser,fixtureApi,loginPage,startTask,waitTask,call,day} from './fixtures.mjs'
const roles={SUPER_ADMIN:['admin','/dashboard'],MANAGER:['manager','/dashboard'],RECEPTION:['reception','/members'],COACH:['coach','/coach-workbench'],SALES:['advisor','/advisor-workbench'],MEMBER:['member','/member-home']}
const out=fileURLToPath(new URL('../../../docs/qa/agent-v2-20261017/',import.meta.url));await mkdir(out,{recursive:true})
const wave=process.env.AGENT_V2_WAVE||('dev-'+Date.now()),requested=new Set((process.env.AGENT_V2_CASE_IDS||'').split(',').filter(Boolean))
const selected=cases.filter(c=>requested.size?requested.has(c.id):c.kind==='query'&&c.split==='dev')
assert.ok(selected.length);assert.ok(selected.every(c=>['query','deny'].includes(c.kind)),'Write/multi/recovery cases need their registered fixtures; never silently skip their assertions')
const report={wave,startedAt:new Date().toISOString(),cases:[],errors:[],blocked:[],notRun:cases.filter(c=>!selected.includes(c)).map(c=>c.id)}
const browser=await createBrowser()
async function apiFromPage(page,path){return page.evaluate(async path=>{const r=await fetch('/api/v1'+path,{headers:{authorization:'Bearer '+localStorage.token}}).then(r=>r.json());if(r.code!==0)throw Error(r.message);return r.data},path)}
const rows=v=>Array.isArray(v)?v:v?.records||[]
const ids=(v,key='id')=>rows(v).map(x=>x[key]??x.id).sort((a,b)=>a-b)
function week(next=false){const now=new Date(),dow=now.getDay()||7;now.setDate(now.getDate()-dow+1+(next?7:0));const local=d=>[d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-');const from=local(now);now.setDate(now.getDate()+6);return {from,to:local(now)}}
async function oracle(page,c,task){
 const goal=c.goal,facts=task.facts||[],topic=goal.includes('身份')?'profile':goal.includes('接待顾问')?'lead_options':goal.includes('课程')?'courses':goal.includes('预约')?'bookings':goal.includes('训练')?'training_plans':goal.includes('学员')?'students':goal.includes('潜客')?'leads':goal.includes('回访')?'retention':goal.includes('交班')?'cash_summary':'members'
 let fact=facts.find(f=>f.tool===topic);assert.ok(fact,'Expected real query: '+topic);assert.ok(Date.now()-new Date(fact.asOf).getTime()<300000)
 if(fact.pagination){
  const pages=new Map();for(const f of facts.filter(f=>f.tool===topic&&f.pagination)){assert.equal(f.pagination.contentTruncated,false);pages.set(f.pagination.page,f)}
  const list=[...pages.values()].sort((a,b)=>a.pagination.page-b.pagination.page),key={training_plans:'plans',leads:'leads',cash_summary:'shifts',retention:'members',lead_options:'advisors'}[topic]
  if(Array.isArray(fact.data))fact={...fact,data:list.flatMap(f=>f.data)}
  else if(key){const combined=list.flatMap(f=>rows(f.data[key]));fact={...fact,data:{...fact.data,[key]:Array.isArray(fact.data[key])?combined:{...fact.data[key],records:combined}}}}
  else fact={...fact,data:{...fact.data,records:list.flatMap(f=>rows(f.data))}}
 }else assert.equal(fact.truncated,false)
 let expected,actual
 if(topic==='profile'){assert.equal(fact.data.role,c.role);return {topic,role:c.role}}
 if(topic==='members'){const v=await apiFromPage(page,'/admin/members?page=1&size=20');expected=ids(v);if(goal.includes('本店')){const session=await apiFromPage(page,'/agent/computer/session');expected=ids(rows(v).filter(x=>x.venueId===session.venueId))}actual=ids(fact.data)}
 if(topic==='students'){expected=ids(await apiFromPage(page,'/training-plans/students?page=1&size=20'));actual=ids(fact.data)}
 if(topic==='training_plans'){const v=await apiFromPage(page,c.role==='MEMBER'?'/member/training-plans?status=ALL&page=1&size=20':'/training-plans/workbench?status=ALL&page=1&size=20');expected=ids(v.plans);actual=ids(fact.data.plans)}
 if(topic==='leads'){expected=ids((await apiFromPage(page,'/leads/workbench?stage=ALL&filter=ALL&page=1&size=20')).leads);actual=ids(fact.data.leads)}
 if(topic==='bookings'){expected=ids(await apiFromPage(page,c.role==='MEMBER'?'/member/bookings':'/admin/bookings'));actual=ids(fact.data)}
 if(topic==='courses'){
  const {from,to}=week(goal.includes('下周')),member=c.role==='MEMBER';const v=await apiFromPage(page,member?'/member/schedules':'/admin/schedules?from='+from+'&to='+to);const session=await apiFromPage(page,'/agent/computer/session')
  expected=ids(rows(v).filter(x=>{const date=(x.startTime||x.scheduledStartTime||'').slice(0,10);return date>=from&&date<=to&&(c.role!=='COACH'||x.coachId===session.userId)}),member?'scheduleId':'id');actual=ids(fact.data,'scheduleId')
 }
 if(topic==='retention'){const v=await apiFromPage(page,'/admin/advisor/retention?filter=ALL');expected=ids(v.members,'memberId');actual=ids(fact.data.members,'memberId')}
 if(topic==='lead_options'){const v=await apiFromPage(page,'/leads/options');expected=ids(v.advisors,'userId');actual=ids(fact.data.advisors,'userId')}
 if(topic==='cash_summary'){const v=await apiFromPage(page,'/cash-shifts/workbench?day='+day()+'&status=ALL&page=1&size=20');expected=ids(v.shifts);actual=ids(fact.data.shifts)}
 assert.ok(expected&&actual,'Missing independent fixture oracle: '+topic);assert.deepEqual(actual,expected,'Business result differs from original scoped API');return {topic,expectedIds:expected,actualIds:actual}
}
try{
 for(const original of selected){
  const c={...original,expected:{goals:1}},[user,home]=roles[c.role],entry={id:c.id,role:c.role,kind:c.kind,split:c.split,goal:c.goal,startedAt:new Date().toISOString()};report.cases.push(entry)
  let context,page
  try{
   ({context,page}=await loginPage(browser,user,home,report));const panel=page.getByRole('dialog',{name:'FitChain 业务助手'}),witness=await page.evaluate(()=>performance.timeOrigin),writes=[]
   page.on('request',r=>{if(!['GET','HEAD'].includes(r.method())&&new URL(r.url()).pathname.startsWith('/api/v1/')&&!new URL(r.url()).pathname.startsWith('/api/v1/agent/'))writes.push(r.method()+' '+new URL(r.url()).pathname)})
   const t=await startTask(page,panel,c.goal),done=await waitTask(page,t.id);Object.assign(entry,{status:done.status,verification:done.verification,error:done.error,question:done.question,steps:done.steps,afterWrites:writes.length,sameDocument:context.pages().length===1&&await page.evaluate(()=>performance.timeOrigin)===witness})
   if(c.kind!=='deny'){assert.equal(done.status,'SUCCEEDED',done.error||done.question);entry.oracle=await oracle(page,c,done);assert.equal(writes.length,0);entry.evidenceChecked=true}
   entry.diagnostics=await apiFromPage(page,'/agent/tasks/'+t.id+'/diagnostics')
  }catch(error){entry.failure=error.message}
  finally{if(page)try{const t=(await call(page,'listComputerTasks'))[0];if(t&&!['SUCCEEDED','FAILED','PARTIAL','CANCELLED'].includes(t.status))await call(page,'stopComputerTask',t.id)}catch{};if(context)await context.close();entry.endedAt=new Date().toISOString();entry.grade=gradeCase(c,entry);console.log(c.id+' '+(entry.grade.passed?'PASS':'FAIL')+' '+(entry.failure||entry.error||''));report.summary=summarize(report.cases.map(e=>e.grade));await writeFile(out+'evaluation-'+wave+'.json',JSON.stringify(report,null,2))}
 }
 assert.deepEqual(report.errors,[]);assert.deepEqual(report.blocked,[])
}finally{await browser.close();report.endedAt=new Date().toISOString();await writeFile(out+'evaluation-'+wave+'.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report.summary));if(report.cases.some(c=>!c.grade.passed))process.exitCode=1}
