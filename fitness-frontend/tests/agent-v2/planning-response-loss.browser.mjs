// A real provider response is received by the server and lost only at the browser.
// The identical retry must recover that turn without another model call or write.
import assert from 'node:assert/strict'
import {writeFile} from 'node:fs/promises'
import {fileURLToPath} from 'node:url'
import {createBrowser,loginPage,startTask,waitTask} from './fixtures.mjs'
const report={errors:[],blocked:[],businessWrites:0,requests:0},browser=await createBrowser()
let context
try{
 const opened=await loginPage(browser,'admin','/dashboard',report);context=opened.context
 const {page,panel}=opened,witness=await page.evaluate(()=>performance.timeOrigin)
 const bodies=[],turns=[]
 page.on('request',r=>{const p=new URL(r.url()).pathname;if(!['GET','HEAD'].includes(r.method())&&p.startsWith('/api/v1/')&&!p.startsWith('/api/v1/agent/'))report.businessWrites++})
 await page.route('**/api/v1/agent/tasks/*/next',async route=>{
  report.requests++;bodies.push(route.request().postData())
  const response=await route.fetch(),body=await response.json();assert.equal(body.code,0)
  turns.push(body.data)
  if(report.requests===1)return route.abort('connectionreset')
  return route.fulfill({response})
 })
 const task=await startTask(page,panel,'查询我的身份'),done=await waitTask(page,task.id)
 assert.equal(done.status,'SUCCEEDED',done.error||done.question)
 assert.equal(report.requests,2,'The lost turn is retried once, without replanning')
 assert.equal(bodies[0],bodies[1]);assert.deepEqual(turns[0],turns[1])
 const diagnostics=await page.evaluate(async id=>(await fetch('/api/v1/agent/tasks/'+id+'/diagnostics',{headers:{authorization:'Bearer '+localStorage.token}}).then(r=>r.json())).data,task.id)
 assert.equal(diagnostics.usageComplete,true);assert.equal(diagnostics.modelCalls,1)
 report.taskId=task.id;report.diagnostics=diagnostics;report.sameTurn=true
 report.sameDocument=context.pages().length===1&&await page.evaluate(()=>performance.timeOrigin)===witness
 assert.equal(report.sameDocument,true);assert.equal(report.businessWrites,0);assert.deepEqual(report.errors,[]);assert.deepEqual(report.blocked,[]);report.passed=true
}finally{
 await context?.close();await browser.close()
 await writeFile(fileURLToPath(new URL('../../../docs/qa/agent-v2-20261017/planning-response-loss-report.json',import.meta.url)),JSON.stringify(report,null,2))
 console.log(JSON.stringify(report))
}
