import assert from 'node:assert/strict'
import {mkdir,writeFile,readFile} from 'node:fs/promises'
import {fileURLToPath,pathToFileURL} from 'node:url'
import {assertIsolatedBrowserTarget,restrictIsolatedBrowserNetwork} from './helpers/isolated-browser.mjs'
await assertIsolatedBrowserTarget()
const {chromium}=await import(pathToFileURL(process.env.FITCHAIN_PLAYWRIGHT_MODULE).href)
const base='http://127.0.0.1:4177',out=fileURLToPath(new URL('../../docs/qa/agent-grounding-20261016/',import.meta.url))
await mkdir(out,{recursive:true})
const browser=await chromium.launch({channel:'msedge',headless:true,args:['--no-proxy-server']})
const report={at:new Date().toISOString(),cases:[],errors:[],blockedOrigins:[]}
async function login(user,home){const context=await browser.newContext({viewport:{width:1536,height:1000}});await restrictIsolatedBrowserNetwork(context,report.blockedOrigins);const page=await context.newPage();page.on('pageerror',e=>report.errors.push(e.message));await page.goto(base+'/login');await page.getByPlaceholder('请输入用户名').fill(user);await page.getByPlaceholder('请输入密码').fill('password');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(base+home);return {context,page}}
async function business(page,path,method='GET',body){return page.evaluate(async({path,method,body})=>{const r=await fetch('/api/v1'+path,{method,headers:{authorization:'Bearer '+localStorage.token,'content-type':'application/json'},...(body?{body:JSON.stringify(body)}:{})}),v=await r.json();if(v.code!==0)throw Error(v.message);return v.data},{path,method,body})}
async function call(page,method,...args){return page.evaluate(async({method,args})=>{const url=performance.getEntriesByType('resource').map(e=>e.name).find(u=>u.includes('/src/agent/currentPageAgent.ts?t='))||'/src/agent/currentPageAgent.ts';return(await(await import(url))[method](...args)).data},{method,args})}
async function until(page,id,states,previous){let last;for(let n=0;n<320;n++){const t=await call(page,'getComputerTask',id),progress=t.status+':'+t.steps.length;if(last!==progress){console.log(id+' '+progress);last=progress}if(states.includes(t.status)&&!(t.status==='WAITING_CONFIRMATION'&&t.pendingConfirmation?.id===previous))return t;await page.waitForTimeout(700)}throw Error('Task timed out')}
async function start(page,goal){await page.getByRole('button',{name:'打开业务助手',exact:true}).click();const panel=page.getByRole('dialog',{name:'FitChain 业务助手'});await page.getByLabel('业务问题',{exact:true}).fill(goal);await panel.getByRole('button',{name:'发送',exact:true}).click();for(let n=0;n<50;n++){const t=(await call(page,'listComputerTasks'))[0];if(t)return {id:t.id,panel};await page.waitForTimeout(200)}throw Error('Task not created')}
const stripAudit=({updatedAt,updatedBy,...value})=>value
async function editCase({name,user='admin',home='/dashboard',goal,select,expected,wrongDialog=false,late=false}){
 const {context,page}=await login(user,home),before=await business(page,'/admin/courses'),target=before.find(select);assert.ok(target,'Target fixture course not found')
 if(late){assert.ok(before.findIndex(c=>c.id===target.id)>60,'Target must be beyond the first sixty course cards')}
 if(wrongDialog){await page.goto(base+'/courses');await page.locator('.course-card').filter({has:page.getByRole('heading',{name:'功能性力量',exact:true})}).getByRole('button',{name:'编辑课程',exact:true}).click();await page.getByRole('dialog',{name:'编辑课程',exact:true}).waitFor()}
 const documentTime=await page.evaluate(()=>{window.groundingWitness='same-document';return performance.timeOrigin}),writes=[],requests=[]
 page.on('request',r=>{const path=new URL(r.url()).pathname;if(r.method()!=='GET'&&/^\/api\/v1\/admin\/courses/.test(path))writes.push({method:r.method(),path,body:r.postDataJSON()});if(path==='/api/v1/agent/computer/next')requests.push(r.postDataJSON())})
 const entry={name,user,target:{id:target.id,name:target.name,venueId:target.venueId},expected,goal,wrongDialog,late,steps:[]};report.cases.push(entry)
 try{
  const {id,panel}=await start(page,goal),waiting=await until(page,id,['WAITING_CONFIRMATION','SUCCEEDED','FAILED','WAITING_INPUT','PAUSED']);Object.assign(entry,{status:waiting.status,steps:waiting.steps,error:waiting.error,question:waiting.question})
  assert.equal(waiting.status,'WAITING_CONFIRMATION',waiting.error||waiting.question);assert.equal(writes.length,0,'No API update before explicit confirmation')
  assert.equal(waiting.pendingConfirmation.method,'PUT');assert.equal(waiting.pendingConfirmation.path,'/api/v1/admin/courses/'+target.id)
  for(const [key,value] of Object.entries(expected))assert.ok(waiting.pendingConfirmation.fields.some(f=>f.label===key&&String(f.value)===String(value)),`Frozen ${key} must equal requested ${value}`)
  assert.ok(waiting.pendingConfirmation.fields.some(f=>f.label==='name'&&f.value===target.name),'Correct entity must be visible in frozen payload')
  assert.deepEqual(await business(page,'/admin/courses'),before,'No courses may change before confirmation')
  entry.preview=waiting.pendingConfirmation;entry.beforeFee=target.fee;entry.beforeFields=Object.fromEntries(Object.keys(expected).map(key=>[key,target[key]]));entry.beforeWrites=0
  await panel.getByRole('button',{name:'确认并提交这一次',exact:true}).waitFor({state:'visible',timeout:8000})
  await panel.getByRole('button',{name:'确认并提交这一次',exact:true}).scrollIntoViewIfNeeded()
  await page.screenshot({path:out+name+'-frozen.png'})
  await panel.getByRole('button',{name:'确认并提交这一次',exact:true}).click()
  const done=await until(page,id,['SUCCEEDED','FAILED','WAITING_INPUT','WAITING_CONFIRMATION','PAUSED'],waiting.pendingConfirmation.id);Object.assign(entry,{status:done.status,steps:done.steps,error:done.error,question:done.question})
  assert.equal(done.status,'SUCCEEDED',done.error||done.question);assert.equal(writes.length,1);assert.equal(writes[0].path,'/api/v1/admin/courses/'+target.id)
  const after=await business(page,'/admin/courses');assert.equal(after.length,before.length)
  for(const original of before){const actual=after.find(c=>c.id===original.id);assert.deepEqual(stripAudit(actual),original.id===target.id?{...stripAudit(original),...expected}:stripAudit(original),'Unexpected changes to course '+original.name)}
  const duplicate=await call(page,'confirmComputerTask',id,waiting.pendingConfirmation.id).then(()=>false,()=>true);assert.equal(duplicate,true);assert.equal(writes.length,1)
  assert.equal(context.pages().length,1);assert.equal(await page.evaluate(()=>performance.timeOrigin),documentTime);assert.equal(await page.evaluate(()=>window.groundingWitness),'same-document')
  await page.waitForFunction(()=>document.querySelector('.computer-task-title > span')?.textContent==='已完成',{},{timeout:8000});await panel.locator('.computer-body').evaluate(e=>{e.scrollTop=0})
  entry.observationCount=requests.length;entry.observedTarget=requests.some(r=>r.observation.nodes.some(n=>n.name.includes(target.name)));entry.confirmedReceipt=requests.some(r=>r.history.some(h=>h.summary.includes('人工确认已完成；业务提交成功')))
  entry.dialogFieldValues=requests.some(r=>r.observation.text.includes('当前作用域：弹窗')&&r.observation.text.includes('价格：'))
  assert.ok(entry.observedTarget&&entry.confirmedReceipt&&entry.dialogFieldValues)
  Object.assign(entry,{afterWrites:writes.length,afterFee:after.find(c=>c.id===target.id).fee,otherRecordsUnchanged:true,otherFieldsUnchanged:true,duplicateRejected:true,sameTab:true,sameDocument:true,visibleCompleted:true})
  await page.screenshot({path:out+name+'-complete.png'});console.log(name+' PASS ('+done.steps.length+' steps)')
 }catch(error){entry.planningTrace=requests;throw error}finally{await context.close()}
}
try{
 if(process.env.FITCHAIN_GROUNDING_MODE==='probe'){
  const {context,page}=await login('admin','/dashboard');await page.goto(base+'/courses');await page.locator('.course-card').first().waitFor()
  const observation=await page.evaluate(async()=>{const {createPageDriver}=await import('/src/agent/pageDriver.mjs');window.probeDriver=createPageDriver({document,window,router:{},allowed:p=>p==='/courses'});return window.probeDriver.observe({goal:'打开课程与排课，把流瑜伽的价格改为99'})})
  report.probe={courses:await business(page,'/admin/courses'),observation}
  assert.ok(observation.nodes.some(n=>n.name.includes('编辑课程')&&n.name.includes('流瑜伽')),'Actual repeated course edit buttons lose their course identity')
  await context.close()
 }else if(process.env.FITCHAIN_GROUNDING_MODE==='multi'){
  const current=await login('admin','/dashboard'),rows=await business(current.page,'/admin/courses'),target=rows.find(c=>c.name==='流瑜伽'&&c.venueId===1),maxCapacity=target.maxCapacity===21?22:21,fee=Number(target.fee)===104.5?105.5:104.5;await current.context.close()
  await editCase({name:'multi-field-recovery',user:'manager',goal:`只把流瑜伽的价格改成${fee}，并把容量改为${maxCapacity}，其他字段不变。保存后等待我确认，确认成功后核对课程并结束任务。`,select:c=>c.id===target.id,expected:{fee,maxCapacity},wrongDialog:true})
  assert.ok(report.cases[0].beforeFields.fee!==fee&&report.cases[0].beforeFields.maxCapacity!==maxCapacity)
  assert.deepEqual(report.errors,[]);assert.deepEqual(report.blockedOrigins,[])
 }else if(process.env.FITCHAIN_GROUNDING_MODE==='permission'){
  const admin=await login('admin','/dashboard'),before=await business(admin.page,'/admin/courses'),{context,page}=await login('coach','/coach-workbench'),writes=[]
  try{
   await page.goto(base+'/courses');await page.locator('.course-card').first().waitFor()
   assert.equal(await page.getByRole('button',{name:'编辑课程',exact:true}).count(),0,'Coach cannot edit course templates')
   page.on('request',r=>{if(r.method()!=='GET'&&/^\/api\/v1\/admin\/courses/.test(new URL(r.url()).pathname))writes.push(r.url())})
   const {id}=await start(page,'打开课程与排课，把流瑜伽的价格改为99'),done=await until(page,id,['WAITING_INPUT','FAILED','SUCCEEDED','WAITING_CONFIRMATION','PAUSED'])
   report.permission={role:'COACH',status:done.status,steps:done.steps,question:done.question,error:done.error,writes:writes.length}
   assert.ok(['WAITING_INPUT','FAILED'].includes(done.status),'Unauthorized write must not finish or request submission confirmation')
   assert.equal(writes.length,0);assert.deepEqual(await business(admin.page,'/admin/courses'),before)
   report.permission.allCoursesUnchanged=true
   await page.screenshot({path:out+'coach-permission.png'})
  }finally{await context.close();await admin.context.close()}
  assert.deepEqual(report.errors,[]);assert.deepEqual(report.blockedOrigins,[])
 }else{
  if(process.env.FITCHAIN_GROUNDING_MODE!=='large'){
   await editCase({name:'exact-price-99',goal:'打开课程与排课，把流瑜伽的价格改为99',select:c=>c.name==='流瑜伽'&&c.venueId===1,expected:{fee:99}})
   await editCase({name:'wrong-dialog-recovery',user:'manager',goal:'只把流瑜伽的价格改成101.5，并把容量改为20，其他字段不变。保存后等待我确认，确认成功后核对课程并结束任务。',select:c=>c.name==='流瑜伽',expected:{fee:101.5,maxCapacity:20},wrongDialog:true})
  }
  if(process.env.FITCHAIN_GROUNDING_MODE!=='basic'){
   if(process.env.FITCHAIN_GROUNDING_MODE==='final'){
    report.fixture=(JSON.parse(await readFile(out+'all.json','utf8'))).fixture
    const current=await login('admin','/dashboard'),rows=await business(current.page,'/admin/courses'),fee=Number(rows.find(c=>c.id===report.fixture.targetId)?.fee)===98?97:98;await current.context.close()
    await editCase({name:'large-list-target',goal:`打开课程与排课，只把“${report.fixture.targetName}”的价格设为${fee}，其他数据不变。保存并等待我确认，确认后核对成功并结束。`,select:c=>c.id===report.fixture.targetId,expected:{fee},late:true})
   }else{
    const {context,page}=await login('admin','/dashboard'),suffix=String(Date.now()).slice(-7),name='晚间普拉提验收'+suffix
    const body={name,type:'GROUP',description:'只用于独占隔离库的网页Agent验收',durationMinutes:45,intensity:'LOW',maxCapacity:18,fee:69,venueId:1,status:'ACTIVE'}
    const created=await business(page,'/admin/courses','POST',body)
    for(let i=0;i<65;i++)await business(page,'/admin/courses','POST',{...body,name:'定位夹具'+suffix+'-'+i,fee:10+i})
    report.fixture={targetId:created.id,targetName:name,addedDistractors:65,isolatedOnly:true};await context.close()
    await editCase({name:'large-list-target',goal:`打开课程与排课，只把“${name}”的价格设为99，其他数据不变。保存并等待我确认，确认后核对成功并结束。`,select:c=>c.id===created.id,expected:{fee:99},late:true})
   }
  }
  assert.deepEqual(report.errors,[]);assert.deepEqual(report.blockedOrigins,[])
 }
}catch(e){report.failure=e.message;process.exitCode=1}
finally{await browser.close();await writeFile(out+(process.env.FITCHAIN_GROUNDING_MODE||'model')+'.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report))}
