// Business writes are permitted only in the explicitly marked, isolated fixture database.
import assert from 'node:assert/strict'
import {mkdir,writeFile} from 'node:fs/promises'
import {fileURLToPath,pathToFileURL} from 'node:url'
import {assertIsolatedBrowserTarget,restrictIsolatedBrowserNetwork} from './helpers/isolated-browser.mjs'
await assertIsolatedBrowserTarget()
const {chromium}=await import(pathToFileURL(process.env.FITCHAIN_PLAYWRIGHT_MODULE).href)
const base='http://127.0.0.1:4177',out=fileURLToPath(new URL('../../docs/qa/page-agent-chat-20261016/',import.meta.url))
await mkdir(out,{recursive:true})
const report={at:new Date().toISOString(),roles:[],errors:[],blockedOrigins:[]}
const browser=await chromium.launch({channel:'msedge',headless:true,args:['--no-proxy-server']})
async function business(page,path){return page.evaluate(async path=>{const r=await fetch('/api/v1'+path,{headers:{authorization:'Bearer '+localStorage.token}}),v=await r.json();if(v.code!==0)throw Error(v.message);return v.data},path)}
async function moduleCall(page,method,...args){return page.evaluate(async({method,args})=>{const url=performance.getEntriesByType('resource').map(e=>e.name).find(u=>u.includes('/src/agent/currentPageAgent.ts?t='))||'/src/agent/currentPageAgent.ts';return(await(await import(url))[method](...args)).data},{method,args})}
async function until(page,id,states,previousConfirmation){let last;for(let n=0;n<240;n++){const t=await moduleCall(page,'getComputerTask',id),progress=t.status+':'+t.steps.length;if(last!==progress){console.log(id+' '+progress);last=progress}if(states.includes(t.status)&&!(t.status==='WAITING_CONFIRMATION'&&t.pendingConfirmation?.id===previousConfirmation))return t;await page.waitForTimeout(700)}throw Error('Task timed out')}
async function login(user,home){
 const context=await browser.newContext({viewport:{width:1536,height:1000}});await restrictIsolatedBrowserNetwork(context,report.blockedOrigins)
 const page=await context.newPage();page.on('pageerror',e=>report.errors.push(e.message))
 await page.goto(base+'/login');await page.getByPlaceholder('请输入用户名').fill(user);await page.getByPlaceholder('请输入密码').fill('password');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(base+home)
 return {context,page}
}
try{
 if(process.env.FITCHAIN_CHAT_QA_MODE==='probe'){
  const {context,page}=await login('admin','/dashboard');const contacts=await business(page,'/messages/contacts'),target=contacts.members[0]
  assert.ok(target,'Fixture must have a member contact');await page.goto(base+'/chat');await page.getByRole('button',{name:'发起会话',exact:true}).click()
  const picker=page.getByRole('dialog',{name:'选择联系人'});await picker.getByRole('tab',{name:/会员/}).click();await picker.getByPlaceholder('搜索姓名').fill(target.name)
  const observed=await page.evaluate(async()=>{const {createPageDriver}=await import('/src/agent/pageDriver.mjs');window.chatProbe=createPageDriver({document,window,router:{},allowed:p=>p==='/chat'});return window.chatProbe.observe()})
  report.probe={target:{userId:target.userId,name:target.name},nodes:observed.nodes}
  assert.ok(observed.nodes.some(n=>n.role==='button'&&n.name.startsWith('选择联系人：'+target.name+'（')),'Contact is visible but missing from the Agent actionable controls')
  const selected=observed.nodes.find(n=>n.name.startsWith('选择联系人：'+target.name+'（'))
  await page.evaluate(ref=>window.chatProbe.act('click',{ref}),selected.ref);await page.locator('.main-head strong').filter({hasText:target.name}).waitFor()
  const conversation=await page.evaluate(()=>window.chatProbe.observe())
  assert.ok(conversation.nodes.some(n=>n.role==='textbox'&&n.name.includes('输入消息')),'Selected contact must expose the actual message composer')
  await context.close();console.log('Actual ChatPanel contact discovery PASS')
 }else{
  const cases=[
   ['SUPER_ADMIN','admin','/dashboard'],['MANAGER','manager','/dashboard'],['RECEPTION','reception','/members'],
   ['COACH','coach','/coach-workbench'],['SALES','advisor','/advisor-workbench'],['MEMBER','member','/member-home']
  ]
  for(const [role,user,home] of cases.filter(([role])=>process.env.FITCHAIN_CHAT_QA_MODE!=='visible'||role==='SUPER_ADMIN')){
   const {context,page}=await login(user,home),contacts=await business(page,'/messages/contacts'),target=contacts.members[0]||contacts.staff[0]
   assert.ok(target,'Fixture must have an authorized contact');const content='你好啊 · 网页验收'+Date.now()+'-'+role
   const before=(await business(page,'/messages/thread/'+target.userId)).messages.filter(m=>m.mine&&m.content===content).length
   assert.equal(before,0)
   const writes=[],decisions=[],plannerReceipts=[]
   page.on('request',r=>{if(r.method()!=='POST')return;const path=new URL(r.url()).pathname;if(path==='/api/v1/messages/send')writes.push(r.postDataJSON());if(path==='/api/v1/agent/computer/next')for(const row of r.postDataJSON().history||[])if(row.ok&&row.summary.includes('人工确认已完成；业务提交成功'))plannerReceipts.push(row.summary)})
   page.on('response',async r=>{if(new URL(r.url()).pathname==='/api/v1/agent/computer/next'){try{decisions.push((await r.json()).data)}catch{}}})
   const timeOrigin=await page.evaluate(()=>{window.chatWitness='same-tab';return performance.timeOrigin})
   await page.getByRole('button',{name:'打开业务助手',exact:true}).click();let panel=page.getByRole('dialog',{name:'FitChain 业务助手'})
   await page.getByLabel('业务问题',{exact:true}).fill(`在本网页打开站内消息，给联系人“${target.name}”（账号编号${target.userId}）发送“${content}”。原样填写消息并点击发送，等待我确认；确认后核对发送成功并结束任务。`)
   await panel.getByRole('button',{name:'发送',exact:true}).click()
   let created
   for(let n=0;n<50;n++){created=(await moduleCall(page,'listComputerTasks'))[0];if(created)break;await page.waitForTimeout(300)}
   assert.ok(created,'Task not created')
   const entry={role,target:{userId:target.userId,name:target.name},content,before,decisions,plannerReceipts};report.roles.push(entry)
   const waiting=await until(page,created.id,['WAITING_CONFIRMATION','SUCCEEDED','FAILED','WAITING_INPUT','PAUSED'])
   Object.assign(entry,{status:waiting.status,steps:waiting.steps,error:waiting.error,question:waiting.question})
   assert.equal(waiting.status,'WAITING_CONFIRMATION',waiting.error||waiting.question||JSON.stringify(waiting))
   assert.equal(writes.length,0,'No business send before confirmation')
   assert.equal((await business(page,'/messages/thread/'+target.userId)).messages.filter(m=>m.mine&&m.content===content).length,0)
   assert.equal(waiting.pendingConfirmation.path,'/api/v1/messages/send')
   assert.ok(waiting.pendingConfirmation.fields.some(f=>f.label==='recipientUserId'&&f.value===String(target.userId)))
   assert.ok(waiting.pendingConfirmation.fields.some(f=>f.label==='content'&&f.value===content))
   assert.equal(await page.locator('.main-head strong').innerText(),target.name)
   if(role==='SUPER_ADMIN'){
    await panel.getByRole('button',{name:'关闭业务助手',exact:true}).click()
    let paused=await moduleCall(page,'getComputerTask',created.id);assert.equal(paused.status,'PAUSED');assert.equal(paused.pauseReason,'ASSISTANT_CLOSED')
    assert.equal(writes.length,0);await page.getByRole('button',{name:'打开业务助手',exact:true}).click();panel=page.getByRole('dialog',{name:'FitChain 业务助手'})
    await panel.getByText(/助手面板已关闭或重新加载/).waitFor();await page.screenshot({path:out+'pause-reason.png'})
    await panel.getByRole('button',{name:'继续',exact:true}).click();assert.equal((await moduleCall(page,'getComputerTask',created.id)).status,'WAITING_CONFIRMATION')
    // Exercise the installed visibility listener without navigating/reloading the document.
    await page.evaluate(()=>{Object.defineProperty(document,'hidden',{configurable:true,value:true});document.dispatchEvent(new Event('visibilitychange'));delete document.hidden})
    paused=await moduleCall(page,'getComputerTask',created.id);assert.equal(paused.status,'PAUSED');assert.equal(paused.pauseReason,'PAGE_HIDDEN')
    await panel.getByText(/当前网页曾被隐藏/).waitFor();assert.equal(writes.length,0)
    await panel.getByRole('button',{name:'继续',exact:true}).click();assert.equal((await moduleCall(page,'getComputerTask',created.id)).status,'WAITING_CONFIRMATION')
    entry.pause={close:'ASSISTANT_CLOSED',visibility:'PAGE_HIDDEN',preservedConfirmation:true,zeroBeforeConfirm:true}
   }
   await panel.getByRole('button',{name:'确认并提交这一次',exact:true}).click()
   const done=await until(page,created.id,['SUCCEEDED','FAILED','WAITING_INPUT','WAITING_CONFIRMATION','PAUSED'],waiting.pendingConfirmation.id)
   Object.assign(entry,{status:done.status,steps:done.steps,error:done.error,question:done.question})
   assert.equal(done.status,'SUCCEEDED',done.error||done.question)
   assert.ok(plannerReceipts.length,'Subsequent model planning must receive the actual confirmed receipt')
   assert.deepEqual(writes,[{recipientUserId:target.userId,content}],'Exactly one actual request, with the requested recipient and exact text')
   const after=(await business(page,'/messages/thread/'+target.userId)).messages.filter(m=>m.mine&&m.content===content).length;assert.equal(after,1)
   const duplicate=await moduleCall(page,'confirmComputerTask',created.id,waiting.pendingConfirmation.id).then(()=>false,()=>true);assert.equal(duplicate,true)
   assert.equal((await business(page,'/messages/thread/'+target.userId)).messages.filter(m=>m.mine&&m.content===content).length,1)
   assert.equal(await page.evaluate(()=>performance.timeOrigin),timeOrigin);assert.equal(await page.evaluate(()=>window.chatWitness),'same-tab');assert.equal(context.pages().length,1)
   await page.getByRole('button',{name:new RegExp('^打开会话：'+target.name+'（')}).waitFor()
   await page.waitForFunction(()=>document.querySelector('.computer-task-title > span')?.textContent==='已完成',{},{timeout:8000})
   await panel.locator('.computer-body').evaluate(e=>{e.scrollTop=0})
   Object.assign(entry,{after,actualRequests:writes.length,duplicateRejected:duplicate,sameTab:true,sameDocument:true,visibleCompleted:true})
   await page.screenshot({path:out+role.toLowerCase()+'-chat-receipt.png'});console.log(role+' real-model chat 0 -> 1 PASS');await context.close()
  }
 }
 assert.deepEqual(report.errors,[]);assert.deepEqual(report.blockedOrigins,[])
}catch(error){report.failure=error.message;process.exitCode=1}
finally{await browser.close();const mode=process.env.FITCHAIN_CHAT_QA_MODE||'model';await writeFile(out+mode+'.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report))}
