import assert from 'node:assert/strict'
import {mkdir,writeFile} from 'node:fs/promises'
import {fileURLToPath} from 'node:url'
import {createBrowser,fixtureApi,loginPage,startTask,waitTask,call,day,seedFreeSchedule} from './fixtures.mjs'
const out=fileURLToPath(new URL('../../../docs/qa/agent-v2-20261017/',import.meta.url));await mkdir(out,{recursive:true})
const report={at:new Date().toISOString(),cases:[],errors:[],blocked:[]},browser=await createBrowser(),suffix=Date.now().toString(36)
async function run(name,user,home,goal,checkPreview,read,checkResult){
 const {context,page,panel}=await loginPage(browser,user,home,report),entry={name,user,goal};report.cases.push(entry)
 try{
  const original=await read(),witness=await page.evaluate(()=>performance.timeOrigin),writes=[]
  page.on('request',r=>{const path=new URL(r.url()).pathname;if(!['GET','HEAD'].includes(r.method())&&path.startsWith('/api/v1/')&&!path.startsWith('/api/v1/agent/'))writes.push({path,method:r.method()})})
  const task=await startTask(page,panel,goal),pending=await waitTask(page,task.id);Object.assign(entry,{status:pending.status,steps:pending.steps,error:pending.error,question:pending.question})
  assert.equal(pending.status,'WAITING_CONFIRMATION',pending.error||pending.question);checkPreview(pending.pendingConfirmation);assert.equal(writes.length,0);assert.deepEqual(await read(),original);entry.beforeWrites=0
  await panel.getByRole('button',{name:'确认并提交这一次',exact:true}).waitFor({timeout:10000});await page.screenshot({path:out+name+'-preview.png'});await panel.getByRole('button',{name:'确认并提交这一次',exact:true}).click()
  const done=await waitTask(page,task.id,pending.pendingConfirmation.id);Object.assign(entry,{status:done.status,steps:done.steps,error:done.error,question:done.question,verification:done.verification});assert.equal(done.status,'SUCCEEDED',done.error||done.question||JSON.stringify(done.verification));assert.equal(writes.length,1);checkResult(await read(),original);assert.ok(await call(page,'confirmComputerTask',task.id,pending.pendingConfirmation.id).then(()=>false,()=>true));assert.equal(writes.length,1);assert.equal(context.pages().length,1);assert.equal(await page.evaluate(()=>performance.timeOrigin),witness);Object.assign(entry,{afterWrites:1,sameDocument:true,duplicateRejected:true});await page.screenshot({path:out+name+'-complete.png'});console.log(name+' PASS')
 }catch(err){entry.failure=err.message;process.exitCode=1;await page.screenshot({path:out+name+'-failure.png'});try{const t=(await call(page,'listComputerTasks'))[0];if(t)await call(page,'stopComputerTask',t.id)}catch{}}
 finally{await context.close();await writeFile(out+'real-business.json',JSON.stringify(report,null,2))}
}
try{
 const selected=process.env.AGENT_V2_BUSINESS||'all'
 if(['all','message'].includes(selected)){
  const api=await fixtureApi('coach'),contacts=await api('/messages/contacts'),recipient=contacts.staff.find(c=>c.name==='系统管理员');assert.ok(recipient);const message='AgentV2隔离验收'+suffix
  await run('coach-message','coach','/coach-workbench',`给系统管理员发送：${message}`,p=>{assert.equal(p.path,'/api/v1/messages/send');assert.ok(p.fields.some(f=>f.label==='recipientUserId'&&f.value===String(recipient.userId)));assert.ok(p.fields.some(f=>f.label==='content'&&f.value===message))},async()=> (await api('/messages/thread/'+recipient.userId)).messages.filter(m=>m.mine&&m.content===message),(after,before)=>assert.equal(after.length,before.length+1))
 }
 if(['all','lead'].includes(selected)){
  const api=await fixtureApi('advisor'),name='潜客验收'+suffix,phone='139'+String(Date.now()).slice(-8),path='/leads/workbench?stage=ALL&filter=ALL&page=1&size=20&keyword='+encodeURIComponent(name)
  await run('sales-lead','advisor','/advisor-workbench',`新建潜客，姓名${name}，电话${phone}，自然到店，高意向，其他默认`,p=>{assert.equal(p.path,'/api/v1/leads');for(const [key,value] of Object.entries({name,phone,source:'WALK_IN',intent:'HIGH'}))assert.ok(p.fields.some(f=>f.label===key&&f.value===value))},async()=> (await api(path)).leads.records,(after,before)=>{assert.equal(after.length,before.length+1);assert.equal(after[0].name,name);assert.equal(after[0].intent,'HIGH')})
 }
 if(['all','training'].includes(selected)){
  const api=await fixtureApi('coach'),students=await api('/training-plans/students?page=1&size=20'),student=students.records[0];assert.ok(student);const title='基础训练验收'+suffix,path='/training-plans/workbench?status=ALL&page=1&size=20&keyword='+encodeURIComponent(title)
  await run('coach-draft','coach','/coach-workbench',`为学员${student.realName}创建训练计划草稿，标题${title}，${day()}到${day(7)}，综合体能，深蹲3组每组10次，自重，不发布`,p=>{assert.equal(p.path,'/api/v1/training-plans');assert.ok(p.fields.some(f=>f.label==='memberId'&&f.value===String(student.id)));for(const [key,value] of Object.entries({title,goal:'ENDURANCE',startDate:day(),endDate:day(7),'exercises.0.name':'深蹲','exercises.0.sets':'3','exercises.0.reps':'10','exercises.0.weightKg':'0'}))assert.ok(p.fields.some(f=>f.label===key&&f.value===String(value)),key)},async()=> {const records=(await api(path)).plans.records;return Promise.all(records.map(async p=>({...p,qaDetail:await api('/training-plans/'+p.id)})))},(after,before)=>{assert.equal(after.length,before.length+1);assert.equal(after[0].status,'DRAFT');assert.equal(after[0].memberId,student.id);assert.equal(after[0].goal,'ENDURANCE');const e=after[0].qaDetail.exercises;assert.equal(e.length,1);for(const [key,value] of Object.entries({name:'深蹲',sets:3,reps:10,weightKg:0}))assert.equal(e[0][key],value,key)})
 }
 if(['all','booking'].includes(selected)){
  const {schedule,course}=await seedFreeSchedule('预约验收'+suffix),api=await fixtureApi('member')
  await run('member-booking','member','/member-home',`帮我预约${course.name}，时间${day(2)} 14:00`,p=>{assert.equal(p.path,'/api/v1/member/bookings');assert.ok(p.fields.some(f=>f.label==='scheduleId'&&f.value===String(schedule.id)))},async()=> (await api('/member/bookings')).filter(b=>b.scheduleId===schedule.id),(after,before)=>{assert.equal(before.length,0);assert.equal(after.length,1);assert.equal(after[0].status,'BOOKED')})
 }
 assert.deepEqual(report.errors,[]);assert.deepEqual(report.blocked,[])
}catch(err){report.failure=err.message;process.exitCode=1}
finally{await browser.close();await writeFile(out+'real-business.json',JSON.stringify(report,null,2));console.log(JSON.stringify({cases:report.cases.map(({name,status,failure})=>({name,status,failure})),failure:report.failure,errors:report.errors,blocked:report.blocked}))}
