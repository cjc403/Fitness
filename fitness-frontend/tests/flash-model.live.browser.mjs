// Verify the shared default model and existing same-tab actions for all roles.
// Only the marked isolated database is allowed; no business form is submitted.
import assert from 'node:assert/strict'
import {mkdir,writeFile} from 'node:fs/promises'
import {fileURLToPath,pathToFileURL} from 'node:url'
import {assertIsolatedBrowserTarget,restrictIsolatedBrowserNetwork} from './helpers/isolated-browser.mjs'
await assertIsolatedBrowserTarget()
const {chromium}=await import(pathToFileURL(process.env.FITCHAIN_PLAYWRIGHT_MODULE).href)
const base='http://127.0.0.1:4177',out=fileURLToPath(new URL('../../docs/qa/flash-v41-20261016/',import.meta.url))
await mkdir(out,{recursive:true})
const browser=await chromium.launch({channel:'msedge',headless:true,args:['--no-proxy-server']})
const report={at:new Date().toISOString(),configuredModel:'deepseek-flash',roles:[],errors:[],blockedOrigins:[],scope:'Isolated role logins and read-only same-page actions; no form submission'}
try{
 for(const [role,user,home,target,goal] of [
  ['SUPER_ADMIN','admin','/dashboard','/training-plans','打开本网页的训练计划，看到页面后结束任务'],
  ['MANAGER','manager','/dashboard','/training-plans','打开本网页的训练计划，看到页面后结束任务'],
  ['RECEPTION','reception','/members','/schedule-week','打开本网页的周课表，看到页面后结束任务'],
  ['COACH','coach','/coach-workbench','/training-plans','打开本网页的训练计划，看到页面后结束任务'],
  ['SALES','advisor','/advisor-workbench','/leads','打开本网页的潜客与体验课，看到页面后结束任务'],
  ['MEMBER','member','/member-home','/member-home','在当前会员中心点击我的训练页签，看到训练计划区域后结束任务']
 ]){
  const context=await browser.newContext({viewport:{width:1536,height:1000}});await restrictIsolatedBrowserNetwork(context,report.blockedOrigins)
  const page=await context.newPage();page.on('pageerror',e=>report.errors.push(e.message))
  await page.goto(base+'/login');await page.getByPlaceholder('请输入用户名').fill(user);await page.getByPlaceholder('请输入密码').fill('password');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(base+home)
  const model=await page.evaluate(async()=>{const r=await fetch('/api/v1/agent/profile',{headers:{authorization:'Bearer '+localStorage.token}});const v=await r.json();if(v.code!==0)throw Error(v.message);return v.data.model})
  assert.equal(model.model,'deepseek-flash');assert.equal(model.configured,true)
  const documentTime=await page.evaluate(()=>performance.timeOrigin)
  await page.getByRole('button',{name:'打开业务助手',exact:true}).click();const panel=page.getByRole('dialog',{name:'FitChain 业务助手'})
  await page.getByLabel('业务问题',{exact:true}).fill(goal);await panel.getByRole('button',{name:'发送',exact:true}).click()
  let task
  for(let n=0;n<150;n++){
   task=await page.evaluate(async()=>{const url=performance.getEntriesByType('resource').map(e=>e.name).find(u=>u.includes('/src/agent/currentPageAgent.ts?t='))||'/src/agent/currentPageAgent.ts';return(await(await import(url)).listComputerTasks()).data[0]})
   if(task&&['SUCCEEDED','FAILED','WAITING_INPUT','WAITING_CONFIRMATION'].includes(task.status))break
   await page.waitForTimeout(600)
  }
  assert.equal(task?.status,'SUCCEEDED',task?.error||task?.question);assert.equal(new URL(page.url()).pathname,target);assert.equal(await page.evaluate(()=>performance.timeOrigin),documentTime);assert.equal(context.pages().length,1)
  if(role==='MEMBER')assert.equal(await page.getByRole('tab',{name:'我的训练',exact:true}).getAttribute('aria-selected'),'true')
  report.roles.push({role,model:model.model,configured:model.configured,status:task.status,path:target,sameTab:true,sameDocument:true,steps:task.steps})
  await page.screenshot({path:out+role.toLowerCase()+'-flash.png'});console.log(role+' Flash default and same-tab PASS');await context.close()
 }
 assert.deepEqual(report.errors,[]);assert.deepEqual(report.blockedOrigins,[])
}catch(error){report.failure=error.message;process.exitCode=1}
finally{await browser.close();await writeFile(out+'six-roles.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report))}
