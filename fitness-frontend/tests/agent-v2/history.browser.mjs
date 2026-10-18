// Real Vue and HTTP, no model call and no business mutation. Marked QA only.
import assert from 'node:assert/strict'
import {writeFile} from 'node:fs/promises'
import {fileURLToPath} from 'node:url'
import {createBrowser,loginPage,call} from './fixtures.mjs'
const report={errors:[],blocked:[],nextCalls:0,businessWrites:0},browser=await createBrowser()
async function request(page,path,body){return page.evaluate(async({path,body})=>fetch('/api/v1/agent/tasks'+path,{method:body?'POST':'GET',headers:{authorization:'Bearer '+localStorage.token,'content-type':'application/json'},...(body?{body:JSON.stringify(body)}:{})}).then(r=>r.json()),{path,body})}
try{
 const first=await loginPage(browser,'coach','/coach-workbench',report)
 const created=await request(first.page,'',{document:'qa-history-original',goal:'打开训练计划'});assert.equal(created.code,0);report.taskId=created.data.id
 await first.context.close()
 const second=await loginPage(browser,'coach','/coach-workbench',report),page=second.page
 page.on('request',r=>{const p=new URL(r.url()).pathname;if(p.endsWith('/next'))report.nextCalls++;if(!['GET','HEAD'].includes(r.method())&&p.startsWith('/api/v1/')&&!p.startsWith('/api/v1/agent/'))report.businessWrites++})
 await second.panel.getByRole('button',{name:'网页任务',exact:true}).click()
 const history=second.panel.getByLabel('最近任务');await history.selectOption(report.taskId)
 await second.panel.getByText('这是上次登录的历史记录，仅供查看。旧会话的确认和执行许可不会恢复；需要继续办理时请创建新任务。',{exact:true}).waitFor()
 const view=await call(page,'getComputerTask',report.taskId);assert.equal(view.readOnly,true);assert.equal(view.restorable,false)
 assert.equal(await second.panel.getByRole('button',{name:'继续',exact:true}).count(),0);assert.equal(await second.panel.getByRole('button',{name:'在当前网页恢复',exact:true}).count(),0)
 const old=await request(page,'/'+report.taskId+'/recover',{version:created.data.version,document:'qa-history-new'});assert.notEqual(old.code,0);report.oldLeaseDenied=true
 const fresh=await request(page,'',{document:'qa-history-new',goal:'查询我的身份'});assert.equal(fresh.code,0);report.newTaskAllowed=true
 assert.equal((await request(page,'/'+fresh.data.id+'/cancel',{version:fresh.data.version,document:'qa-history-new'})).code,0)
 const other=await loginPage(browser,'manager','/dashboard',report);assert.notEqual((await request(other.page,'/history/'+report.taskId)).code,0);report.otherRoleDenied=true;await other.context.close();await second.context.close()
 assert.equal(report.nextCalls,0);assert.equal(report.businessWrites,0);assert.deepEqual(report.errors,[]);assert.deepEqual(report.blocked,[]);report.passed=true
}finally{await browser.close();await writeFile(fileURLToPath(new URL('../../../docs/qa/agent-v2-20261017/history-browser-report.json',import.meta.url)),JSON.stringify(report,null,2));console.log(JSON.stringify(report))}
