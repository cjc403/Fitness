// Actual application form observation, zero model calls and zero business writes.
import assert from 'node:assert/strict'
import {writeFile} from 'node:fs/promises'
import {fileURLToPath} from 'node:url'
import {createBrowser,loginPage} from './fixtures.mjs'
const browser=await createBrowser(),report={errors:[],blocked:[],businessWrites:0,modelCalls:0}
try{
 const {page}=await loginPage(browser,'coach','/coach-workbench',report)
 await page.goto('http://127.0.0.1:4177/training-plans');await page.getByRole('button',{name:'创建计划',exact:true}).click()
 page.on('request',r=>{const p=new URL(r.url()).pathname;if(p.endsWith('/next'))report.modelCalls++;if(!['GET','HEAD'].includes(r.method())&&p.startsWith('/api/v1/')&&!p.startsWith('/api/v1/agent/'))report.businessWrites++})
 await page.waitForTimeout(300)
 const state=await page.evaluate(async()=>{
  const {createPageDriver}=await import('/src/agent/pageDriver.mjs'),context=await import('/src/agent/pageContext.mjs')
  window.trainingFormDriver=createPageDriver({document,window,router:{},allowed:p=>p==='/training-plans',contextAdapter:{build:context.buildPageContext,choose:context.chooseSemanticAction}})
  return trainingFormDriver.observe({v2:true,goal:'为学员宁安创建训练计划草稿，综合体能，不发布'})
 })
 report.scope=state.context.scope;report.keys=[...new Set(state.context.fields.map(f=>f.key))]
 const missing=['memberId','startDate','endDate'].filter(k=>!report.keys.includes(k));report.missing=missing
 assert.deepEqual({missing,scopeMisidentified:state.context.scope.entityType==='student'},{missing:[],scopeMisidentified:false},'Candidate options are not the current form identity; required fields need stable keys')
 await page.evaluate(async()=>{await trainingFormDriver.act('use_skill',{kind:'fill_fields',fields:[{field:'memberId',value:'17'},{field:'goal',value:'综合体能'},{field:'startDate',value:'2026-10-18'},{field:'endDate',value:'2026-10-25'}]});await new Promise(r=>setTimeout(r,100))})
 const after=await page.evaluate(()=>trainingFormDriver.observe({v2:true}));assert.equal(after.context.fields.find(f=>f.key==='memberId'&&f.value==='17')?.checked,true);assert.equal(after.context.fields.find(f=>f.key==='goal')?.value,'体能');assert.equal(after.context.fields.find(f=>f.key==='endDate')?.value,'2026-10-25')
 assert.equal(report.businessWrites,0);assert.equal(report.modelCalls,0);assert.deepEqual(report.errors,[]);assert.deepEqual(report.blocked,[]);report.passed=true
}finally{await browser.close();await writeFile(fileURLToPath(new URL('../../../docs/qa/agent-v2-20261017/training-form-report.json',import.meta.url)),JSON.stringify(report,null,2));console.log(JSON.stringify(report))}
