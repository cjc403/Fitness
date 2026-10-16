import assert from 'node:assert/strict'
import {readFile,writeFile} from 'node:fs/promises'
import {createHash} from 'node:crypto'
import {fileURLToPath,pathToFileURL} from 'node:url'
import {assertIsolatedBrowserTarget,restrictIsolatedBrowserNetwork} from './helpers/isolated-browser.mjs'
await assertIsolatedBrowserTarget()
const {chromium}=await import(pathToFileURL(process.env.FITCHAIN_PLAYWRIGHT_MODULE).href)
const base='http://127.0.0.1:4177',out=fileURLToPath(new URL('../../docs/qa/current-page-agent-20261016/',import.meta.url))
const browser=await chromium.launch({channel:'msedge',headless:true,args:['--no-proxy-server']})
const report={at:new Date().toISOString(),kind:'Real model, same-page file generation and viewer request, isolated backend',errors:[],blockedOrigins:[]}
try{
 const context=await browser.newContext({viewport:{width:1536,height:1000}});await restrictIsolatedBrowserNetwork(context,report.blockedOrigins)
 const page=await context.newPage();page.on('pageerror',e=>report.errors.push(e.message))
 await page.goto(base+'/login');await page.getByPlaceholder('请输入用户名').fill('manager');await page.getByPlaceholder('请输入密码').fill('password');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(base+'/dashboard')
 const origin=await page.evaluate(()=>performance.timeOrigin)
 await page.getByRole('button',{name:'打开业务助手',exact:true}).click();const panel=page.getByRole('dialog',{name:'FitChain 业务助手'})
 await page.getByLabel('业务问题',{exact:true}).fill('导出2026-10-16到2026-10-22本店可见课程，生成CSV课表文件并请求系统打开，完成后结束任务');await panel.getByRole('button',{name:'发送',exact:true}).click()
 let task
 for(let n=0;n<150;n++){
  task=await page.evaluate(async()=>{const url=performance.getEntriesByType('resource').map(e=>e.name).find(u=>u.includes('/src/agent/currentPageAgent.ts?t='))||'/src/agent/currentPageAgent.ts';return(await(await import(url)).listComputerTasks()).data[0]})
  if(task&&['SUCCEEDED','FAILED','WAITING_INPUT','WAITING_CONFIRMATION'].includes(task.status))break
  await page.waitForTimeout(600)
 }
 assert.equal(task?.status,'SUCCEEDED',task?.error||task?.question);assert.equal(task.artifacts.length,1)
 assert.ok(task.steps.some(s=>s.action==='open_artifact'&&s.status==='SUCCEEDED'))
 const a=task.artifacts[0],bytes=await readFile(a.path);assert.equal(bytes.subarray(0,3).toString('hex'),'efbbbf');assert.equal(bytes.length,a.bytes);assert.equal(createHash('sha256').update(bytes).digest('hex'),a.sha256)
 assert.equal(context.pages().length,1);assert.equal(await page.evaluate(()=>performance.timeOrigin),origin)
 const old=await page.evaluate(async()=>{const r=await fetch('/local-agent/v1/tasks',{headers:{authorization:'Bearer '+localStorage.token}});return(await r.json()).data});assert.deepEqual(old,[],'Export must not start an independent browser task')
 report.result={status:task.status,sameDocument:true,pages:1,independentTasks:0,steps:task.steps,artifact:{name:a.name,bytes:a.bytes,sha256:a.sha256},bom:true}
 await page.screenshot({path:out+'same-page-file.png'});assert.deepEqual(report.errors,[]);assert.deepEqual(report.blockedOrigins,[])
}catch(error){report.failure=error.message;process.exitCode=1}
finally{await browser.close();await writeFile(out+'real-file.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report))}
