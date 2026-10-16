// Actual conversation → local task UI → controlled Edge, only against the isolated fixture DB.
import assert from 'node:assert/strict'
import { mkdir, writeFile, readFile } from 'node:fs/promises'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { assertIsolatedBrowserTarget, restrictIsolatedBrowserNetwork } from './helpers/isolated-browser.mjs'
await assertIsolatedBrowserTarget()
const modulePath=process.env.FITCHAIN_PLAYWRIGHT_MODULE||'playwright'
const {chromium}=await import(modulePath.includes(':')?pathToFileURL(modulePath).href:modulePath)
const base='http://127.0.0.1:4177',out=fileURLToPath(new URL('../../docs/qa/computer-agent-20261016/',import.meta.url))
await mkdir(out,{recursive:true})
const report={at:new Date().toISOString(),kind:'Actual desktop conversation/task UI and real model; isolated database only',roles:[],errors:[],blockedOrigins:[]}
const browser=await chromium.launch({channel:'msedge',headless:true,args:['--no-proxy-server']})
async function login(username,home){
 const context=await browser.newContext({viewport:{width:1536,height:1000}})
 await restrictIsolatedBrowserNetwork(context,report.blockedOrigins)
 const page=await context.newPage();page.on('pageerror',e=>report.errors.push(e.message))
 await page.goto(base+'/login');await page.getByPlaceholder('请输入用户名').fill(username);await page.getByPlaceholder('请输入密码').fill('password');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(base+home)
 await page.getByRole('button',{name:'打开业务助手',exact:true}).click()
 const panel=page.getByRole('dialog',{name:'FitChain 业务助手'});await panel.waitFor()
 await panel.getByRole('button',{name:'电脑任务',exact:true}).click()
 await panel.getByText('本机执行器已连接 · 独立受控浏览器',{exact:true}).waitFor({timeout:15000})
 return {context,page,panel}
}
async function local(page,path,body){return page.evaluate(async({path,body})=>{const r=await fetch('/local-agent/v1'+path,{method:body===undefined?'GET':'POST',headers:{authorization:'Bearer '+localStorage.token,'content-type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});const v=await r.json();if(v.code!==0)throw new Error(v.message);return v.data},{path,body})}
async function business(page,path){return page.evaluate(async p=>{const r=await fetch('/api/v1'+p,{headers:{authorization:'Bearer '+localStorage.token}});const v=await r.json();if(v.code!==0)throw new Error(v.message);return v.data},path)}
async function send(page,panel,goal){
 await panel.getByRole('button',{name:'会话',exact:true}).click();await panel.getByRole('button',{name:'电脑执行',exact:true}).click();await page.getByLabel('业务问题',{exact:true}).fill(goal)
 const created=page.waitForResponse(r=>r.url().endsWith('/local-agent/v1/tasks')&&r.request().method()==='POST')
 await panel.getByRole('button',{name:'发送',exact:true}).click();const result=await (await created).json();assert.equal(result.code,0,result.message);return result.data.id
}
async function until(page,id,states){for(let i=0;i<320;i++){const t=await local(page,'/tasks/'+id);if(states.includes(t.status))return t;await page.waitForTimeout(750)}throw new Error('Task timed out')}
try{
 for(const [role,username,home,name] of [['SUPER_ADMIN','admin','/dashboard','总部管理员'],['MANAGER','manager','/dashboard','店长'],['RECEPTION','reception','/members','前台'],['COACH','coach','/coach-workbench','教练'],['SALES','advisor','/advisor-workbench','会籍顾问'],['MEMBER','member','/member-home','会员']]){
  const {context,page,panel}=await login(username,home)
  assert.equal(await panel.getByText(name+' · 电脑任务',{exact:true}).count(),1)
  await page.screenshot({path:out+role.toLowerCase()+'-panel.png'});report.roles.push({role,desktopPanelConnected:true})
  await context.close()
 }
 {
  const {context,page,panel}=await login('manager','/dashboard')
  const id=await send(page,panel,'导出2026-10-16到2026-10-22本店可见课程，生成CSV课表文件，并请求系统打开这个文件，完成后结束任务')
  const done=await until(page,id,['SUCCEEDED','FAILED','WAITING_INPUT','WAITING_CONFIRMATION']);report.file={status:done.status,steps:done.steps,error:done.error,artifacts:done.artifacts}
  assert.equal(done.status,'SUCCEEDED',done.error||done.question)
  assert.equal(done.artifacts.length,1);assert.ok(done.steps.some(s=>s.action==='open_artifact'&&s.status==='SUCCEEDED'))
  const bytes=await readFile(done.artifacts[0].path);assert.equal(bytes.subarray(0,3).toString('hex'),'efbbbf')
  await panel.getByText('任务已完成，以下为执行器返回的真实步骤和成果。',{exact:true}).waitFor({timeout:6000});await page.screenshot({path:out+'file-result.png'})
  await context.close()
 }
 {
  const {context,page,panel}=await login('advisor','/advisor-workbench')
  const suffix=String(Date.now()).slice(-6),phone='13900'+suffix,name='电脑Agent验收'+suffix
  const path='/leads/workbench?stage=ALL&filter=ALL&page=1&size=20&keyword='+encodeURIComponent(name)
  const before=await business(page,path);assert.equal(before.leads.total,0)
  const id=await send(page,panel,`打开潜客与体验课，新建潜客。姓名${name}，联系电话${phone}，咨询来源选转介绍，意向程度选高意向；门店和顾问保留默认，不填下次联系时间，备注填“隔离测试”。点击保存记录，等待我确认；确认后核对成功并结束任务。`)
  let pending=await until(page,id,['WAITING_CONFIRMATION','FAILED','WAITING_INPUT','SUCCEEDED']);report.write={id,name,status:pending.status,steps:pending.steps,error:pending.error,question:pending.question}
  if(pending.screenshot)await writeFile(out+'write-controlled-form.jpg',Buffer.from(pending.screenshot.split(',')[1],'base64'))
  assert.equal(pending.status,'WAITING_CONFIRMATION',pending.error||pending.question)
  assert.equal((await business(page,path)).leads.total,0,'Unconfirmed form must not create a lead')
  assert.equal(pending.pendingConfirmation.path,'/api/v1/leads')
  assert.ok(pending.pendingConfirmation.fields.some(f=>f.label==='phone'&&f.value===phone))
  await panel.getByRole('button',{name:'确认并提交这一次',exact:true}).waitFor({timeout:6000});await page.screenshot({path:out+'write-frozen-preview.png'})
  await panel.getByRole('button',{name:'确认并提交这一次',exact:true}).click()
  const done=await until(page,id,['SUCCEEDED','FAILED','WAITING_INPUT','WAITING_CONFIRMATION']);report.write.status=done.status;report.write.steps=done.steps;report.write.error=done.error
  assert.equal(done.status,'SUCCEEDED',done.error||done.question)
  const after=await business(page,path);assert.equal(after.leads.total,1,'Exactly one real isolated business write')
  const replay=await page.evaluate(async({id,confirmationId})=>{const r=await fetch('/local-agent/v1/tasks/'+id+'/confirm',{method:'POST',headers:{authorization:'Bearer '+localStorage.token,'content-type':'application/json'},body:JSON.stringify({confirmationId})});return r.status},{id,confirmationId:pending.pendingConfirmation.id})
  assert.equal(replay,409);assert.equal((await business(page,path)).leads.total,1)
  const row=after.leads.records[0];assert.equal(row.name,name);assert.equal(row.source,'REFERRAL');assert.equal(row.intent,'HIGH')
  report.write={...report.write,confirmedOnce:true,beforeCount:0,afterCount:1,duplicateConfirmationStatus:replay,source:row.source,intent:row.intent}
  await page.waitForTimeout(2000);await page.screenshot({path:out+'write-real-receipt.png'});await context.close()
 }
 assert.deepEqual(report.errors,[]);assert.deepEqual(report.blockedOrigins,[])
}catch(e){report.failure=e.message;process.exitCode=1}
finally{await browser.close();await writeFile(out+'desktop-real-workflows.json',JSON.stringify(report,null,2));process.stdout.write(JSON.stringify(report)+'\n')}
