import assert from 'node:assert/strict'
import {mkdir,writeFile} from 'node:fs/promises'
import {pathToFileURL,fileURLToPath} from 'node:url'
import {assertIsolatedBrowserTarget,restrictIsolatedBrowserNetwork} from './helpers/isolated-browser.mjs'
const modulePath=process.env.FITCHAIN_PLAYWRIGHT_MODULE || 'playwright'
const {chromium}=await import(modulePath.includes(':')?pathToFileURL(modulePath).href:modulePath)
const base='http://127.0.0.1:4177',api='http://127.0.0.1:18080/api/v1'
const out=fileURLToPath(new URL('../../docs/qa/mobile-agent-20261015/real-api',import.meta.url))
await mkdir(out,{recursive:true})
const marker=await fetch('http://127.0.0.1:18091/actuator/info').then(r=>r.json())
assert.equal(marker.app?.qaDatabase,'agent_browser','Writes allowed only against the explicitly marked isolated browser database')
await assertIsolatedBrowserTarget()
const call=async(session,path,body)=>{const r=await fetch(api+path,{method:body===undefined?'GET':'POST',headers:{'Content-Type':'application/json',...(session?{Authorization:'Bearer '+session.accessToken}:{})},body:body===undefined?undefined:JSON.stringify(body)});const j=await r.json();assert.equal(j.code,0,`${path}: ${j.message}`);return j.data}
const homes={SUPER_ADMIN:'/dashboard',MANAGER:'/dashboard',RECEPTION:'/members',COACH:'/coach-workbench',SALES:'/advisor-workbench',MEMBER:'/member-home'}
const accounts={SUPER_ADMIN:'admin',MANAGER:'manager',RECEPTION:'reception',COACH:'coach',SALES:'advisor',MEMBER:'member'}
const sessions={};for(const[role,username]of Object.entries(accounts))sessions[role]=await call(null,'/auth/login',{username,password:'password'})
const regular=(await call(sessions.MEMBER,'/agent/query',{tool:'courses',arguments:{}})).data.find(s=>s.courseType==='REGULAR'&&!s.booked&&!s.full&&new Date(s.startTime).getTime()>Date.now()+7200000)
assert.ok(regular,'isolated demo includes a future regular course')
const students=(await call(sessions.COACH,'/agent/query',{tool:'students',arguments:{}})).data.records;assert.ok(students.length)
const lead=await call(sessions.SALES,'/leads',{name:'浏览器隔离体验潜客',phone:'139'+String(Date.now()).slice(-8),source:'WALK_IN',intent:'HIGH',ownerId:sessions.SALES.userInfo.id,venueId:sessions.SALES.userInfo.organizationId,idempotencyKey:'browser_'+Date.now()})
const start=new Date(Date.now()+5*86400000);start.setHours(14,0,0,0);const iso=d=>new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,19)
const report={kind:'Real HTTP browser integration against isolated PostgreSQL agent_browser; no mock business endpoints; no physical device',base,database:'127.0.0.1:55451/agent_browser',cases:[],errors:[],model:null}
const browser=await chromium.launch({channel:'msedge',headless:true})
try{
 for(const [role,session]of Object.entries(sessions)){
  const context=await browser.newContext({viewport:{width:390,height:844},isMobile:true,hasTouch:true});await restrictIsolatedBrowserNetwork(context,report.errors);await context.addInitScript(s=>{localStorage.setItem('token',s.accessToken);localStorage.setItem('refresh_token',s.refreshToken);localStorage.setItem('userInfo',JSON.stringify(s.userInfo))},session)
  const page=await context.newPage();page.on('pageerror',e=>report.errors.push(role+': '+e.message));await page.goto(base+homes[role]);await page.locator('.mobile-navigation').waitFor();await page.waitForTimeout(350)
  for(const width of [390,360,320]){await page.setViewportSize({width,height:844});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),role+' horizontal overflow '+width)}
  await page.setViewportSize({width:390,height:844});await page.screenshot({path:out+'/'+role.toLowerCase()+'-home.png'})
  await page.locator('.mobile-navigation').getByRole('button',{name:'打开业务助手'}).click();await page.getByRole('dialog',{name:'FitChain 业务助手'}).waitFor();await page.getByRole('button',{name:'业务工具',exact:true}).click()
  await page.locator('.tool-field>select').first().selectOption('courses');const queryResponse=page.waitForResponse(r=>r.url().endsWith('/agent/query')&&r.request().method()==='POST');await page.getByRole('button',{name:'查询业务数据',exact:true}).click();assert.equal((await(await queryResponse).json()).code,0)
  if(['MEMBER','SALES','COACH'].includes(role)){
   const tool={MEMBER:'book_course',SALES:'book_trial',COACH:'training_draft'}[role];await page.locator('.tool-field>select').first().selectOption(tool)
   if(role==='MEMBER')await page.locator('#agent-field-scheduleId').fill(String(regular.scheduleId))
   if(role==='SALES'){for(const[k,v]of Object.entries({leadId:lead.lead.id,expectedVersion:lead.lead.version,hostId:session.userInfo.id,startsAt:iso(start).slice(0,16),endsAt:iso(new Date(start.getTime()+3600000)).slice(0,16),content:'真实API隔离测试：器械基础和门店参观'}))await page.locator('#agent-field-'+k).fill(String(v))}
   if(role==='COACH'){for(const[k,v]of Object.entries({memberId:students[0].id,title:'真实API隔离训练草稿',startDate:iso(new Date()).slice(0,10),endDate:iso(new Date(Date.now()+14*86400000)).slice(0,10)}))await page.locator('#agent-field-'+k).fill(String(v));await page.locator('#agent-field-goal').selectOption('GENERAL');await page.getByRole('button',{name:'添加训练动作'}).click();await page.getByLabel('动作名称').fill('深蹲')}
   const p=page.waitForResponse(r=>r.url().endsWith('/agent/actions/preview'));await page.getByRole('button',{name:'生成操作预览',exact:true}).click();const preview=await(await p).json();assert.equal(preview.code,0,role+' preview: '+preview.message);assert.equal(preview.data.status,'PENDING');await page.getByRole('button',{name:'确认执行',exact:true}).waitFor();await page.screenshot({path:out+'/'+role.toLowerCase()+'-pending.png'})
   const c=page.waitForResponse(r=>r.url().endsWith('/confirm'));await page.getByRole('button',{name:'确认执行',exact:true}).click();const response=await c;assert.deepEqual(response.request().postDataJSON(),{});const receipt=await response.json();assert.equal(receipt.code,0);assert.equal(receipt.data.status,'SUCCEEDED',role+' confirm '+receipt.data.error);await page.getByText('业务操作已成功，以下为服务器回执。').waitFor();await page.screenshot({path:out+'/'+role.toLowerCase()+'-receipt.png'});report.cases.push({role,tool,preview:'PENDING',confirm:receipt.data.status,resultId:receipt.data.result.id||receipt.data.result.plan?.id||receipt.data.result.trials?.[0]?.id,passed:true})
  }else report.cases.push({role,tool:'courses',passed:true})
  await page.getByRole('button',{name:'关闭业务助手'}).click();await page.setViewportSize({width:1366,height:900});assert.equal(await page.locator('.aside').isVisible(),true);await page.screenshot({path:out+'/'+role.toLowerCase()+'-desktop.png'});await context.close()
 }
 // One real, scoped model round-trip; it may only query, never confirm a business action.
 const before=await call(sessions.MEMBER,'/agent/profile');const chat=await call(sessions.MEMBER,'/agent/chat',{message:'请调用课程查询工具，查看今天的课程。只查询，不安排预约。'});const after=await call(sessions.MEMBER,'/agent/profile');report.model={configured:before.model.configured,mode:chat.mode,resultCount:chat.results.length,actionCount:chat.actions.length,status:after.model};assert.equal(chat.actions.length,0);assert.ok(chat.results.length>0);assert.equal(report.errors.length,0)
}catch(e){report.failure=String(e.stack||e);throw e}finally{await writeFile(out+'/verification.json',JSON.stringify(report,null,2));await browser.close();console.log(JSON.stringify(report,null,2))}
