// Real model and UI acceptance. Writes only to the explicitly marked isolated database.
import assert from 'node:assert/strict'
import { mkdir,writeFile } from 'node:fs/promises'
import { fileURLToPath,pathToFileURL } from 'node:url'
import { assertIsolatedBrowserTarget,restrictIsolatedBrowserNetwork } from './helpers/isolated-browser.mjs'
await assertIsolatedBrowserTarget()
const {chromium}=await import(pathToFileURL(process.env.FITCHAIN_PLAYWRIGHT_MODULE).href)
const base='http://127.0.0.1:4177',out=fileURLToPath(new URL('../../docs/qa/current-page-agent-20261016/',import.meta.url))
await mkdir(out,{recursive:true})
const report={at:new Date().toISOString(),roles:[],errors:[],blockedOrigins:[]}
const browser=await chromium.launch({channel:'msedge',headless:true,args:['--no-proxy-server']})
async function login(username,home){
 const context=await browser.newContext({viewport:{width:1536,height:1000}});await restrictIsolatedBrowserNetwork(context,report.blockedOrigins)
 const page=await context.newPage();page.on('pageerror',e=>report.errors.push(e.message))
 await page.goto(base+'/login');await page.getByPlaceholder('请输入用户名').fill(username);await page.getByPlaceholder('请输入密码').fill('password');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(base+home)
 await page.getByRole('button',{name:'打开业务助手',exact:true}).click();const panel=page.getByRole('dialog',{name:'FitChain 业务助手'})
 await panel.getByRole('button',{name:'网页任务',exact:true}).click();await panel.getByText('当前标签页执行 · 页面操作可见',{exact:true}).waitFor({timeout:15000})
 assert.ok(await panel.evaluate(e=>Number(getComputedStyle(e.closest('.assistant')).zIndex))>10000,'Assistant confirmation must stay above business dialogs')
 return {context,page,panel}
}
// Import the exact Vite module URL already loaded by the panel (including its
// HMR revision), rather than accidentally constructing a second task singleton.
async function task(page,id){return page.evaluate(async id=>{const url=performance.getEntriesByType('resource').map(e=>e.name).find(u=>u.includes('/src/agent/currentPageAgent.ts?t='))||'/src/agent/currentPageAgent.ts';const m=await import(url);return id?(await m.getComputerTask(id)).data:(await m.listComputerTasks()).data[0]},id)}
async function send(page,panel,goal){await panel.getByRole('button',{name:'会话',exact:true}).click();await panel.getByRole('button',{name:'网页执行',exact:true}).click();await page.getByLabel('业务问题',{exact:true}).fill(goal);await panel.getByRole('button',{name:'发送',exact:true}).click();for(let n=0;n<50;n++){const t=await task(page);if(t)return t.id;await page.waitForTimeout(300)}await page.screenshot({path:out+'task-create-failure.png'});throw Error('Task not created: '+await panel.innerText())}
async function until(page,id,states){for(let n=0;n<250;n++){const t=await task(page,id);if(states.includes(t.status))return t;await page.waitForTimeout(800)}throw Error('Task timed out')}
async function business(page,path){return page.evaluate(async path=>{const r=await fetch('/api/v1'+path,{headers:{authorization:'Bearer '+localStorage.token}});const v=await r.json();if(v.code!==0)throw Error(v.message);return v.data},path)}
try{
 const cases=[
 ['SUPER_ADMIN','admin','/dashboard','/training-plans','打开本网页的训练计划，看到训练计划页面后结束任务'],
 ['MANAGER','manager','/dashboard','/training-plans','打开本网页的训练计划，看到训练计划页面后结束任务'],
 ['RECEPTION','reception','/members','/schedule-week','打开本网页的周课表，看到课表页面后结束任务'],
 ['COACH','coach','/coach-workbench','/training-plans','打开本网页的训练计划，看到训练计划页面后结束任务'],
 ['SALES','advisor','/advisor-workbench','/leads','打开本网页的潜客与体验课，看到列表后结束任务'],
 ['MEMBER','member','/member-home','/member-home','在当前会员中心点击我的训练页签，看到训练计划区域后结束任务']]
 for(const [role,user,home,target,goal] of (process.env.FITCHAIN_PAGE_QA_CASES==='write'?[]:cases)){
  const {context,page,panel}=await login(user,home);const identity=await page.evaluate(()=>{window.samePageWitness='witness';return performance.timeOrigin})
  const id=await send(page,panel,goal),done=await until(page,id,['SUCCEEDED','FAILED','WAITING_INPUT','WAITING_CONFIRMATION'])
  const entry={role,status:done.status,url:new URL(page.url()).pathname,steps:done.steps,error:done.error,question:done.question,pages:context.pages().length};report.roles.push(entry)
  assert.equal(done.status,'SUCCEEDED',done.error||done.question);assert.equal(entry.url,target);assert.equal(entry.pages,1)
  assert.equal(await page.evaluate(()=>window.samePageWitness),'witness');assert.equal(await page.evaluate(()=>performance.timeOrigin),identity,'Must keep the same document and tab')
  if(role==='MEMBER')assert.equal(await page.getByRole('tab',{name:'我的训练',exact:true}).getAttribute('aria-selected'),'true')
  else assert.ok((await page.locator('main').innerText()).includes(role==='RECEPTION'?'课表':role==='SALES'?'潜客':'训练计划'))
  await page.screenshot({path:out+role.toLowerCase()+'-same-page.png'});console.log(role+' same-page PASS');await context.close()
 }
 {
  const {context,page,panel}=await login('advisor','/advisor-workbench');const suffix=String(Date.now()).slice(-6),name='网页验收'+suffix,phone='13900'+suffix
  const path='/leads/workbench?stage=ALL&filter=ALL&page=1&size=20&keyword='+encodeURIComponent(name)
  assert.equal((await business(page,path)).leads.total,0)
  const id=await send(page,panel,`打开当前网页的潜客与体验课，新建潜客。姓名字段原样填写“${name}”，联系电话${phone}，咨询来源选转介绍，意向程度选高意向；门店和顾问保留默认，不填下次联系时间，备注填“隔离测试”。点击保存记录，等待我确认，确认后核对成功并结束任务。`)
  const waiting=await until(page,id,['WAITING_CONFIRMATION','FAILED','WAITING_INPUT','SUCCEEDED'])
  report.write={status:waiting.status,url:new URL(page.url()).pathname,steps:waiting.steps,error:waiting.error,question:waiting.question}
  assert.equal(waiting.status,'WAITING_CONFIRMATION',waiting.error||waiting.question);assert.equal(new URL(page.url()).pathname,'/leads');assert.equal(context.pages().length,1)
  assert.equal((await business(page,path)).leads.total,0);assert.equal(await page.locator('main').count(),1)
  assert.ok(waiting.pendingConfirmation.fields.some(f=>f.label==='phone'&&f.value===phone))
  assert.ok(waiting.pendingConfirmation.fields.some(f=>f.label==='name'&&f.value===name),'Frozen preview must match the explicitly requested name')
  await page.screenshot({path:out+'same-page-form-frozen.png'})
  await panel.getByRole('button',{name:'确认并提交这一次',exact:true}).click()
  const done=await until(page,id,['SUCCEEDED','FAILED','WAITING_INPUT','WAITING_CONFIRMATION']);report.write.receipt=done;assert.equal(done.status,'SUCCEEDED',done.error||done.question)
  const after=await business(page,path);report.write.actualCount=after.leads.total;assert.equal(after.leads.total,1);assert.equal(after.leads.records[0].source,'REFERRAL');assert.equal(after.leads.records[0].intent,'HIGH')
  const duplicate=await page.evaluate(async({id,cid})=>{try{const url=performance.getEntriesByType('resource').map(e=>e.name).find(u=>u.includes('/src/agent/currentPageAgent.ts?t='))||'/src/agent/currentPageAgent.ts';await(await import(url)).confirmComputerTask(id,cid);return false}catch{return true}},{id,cid:waiting.pendingConfirmation.id})
  assert.equal(duplicate,true);assert.equal((await business(page,path)).leads.total,1)
  report.write={...report.write,status:done.status,steps:done.steps,before:0,after:1,duplicateRejected:true,oneTab:true}
  await page.screenshot({path:out+'same-page-write-receipt.png'});console.log('same-page form 0 -> 1 PASS');await context.close()
 }
 assert.deepEqual(report.errors,[]);assert.deepEqual(report.blockedOrigins,[])
}catch(error){report.failure=error.message;process.exitCode=1}
finally{await browser.close();await writeFile(out+'real-browser.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report))}
