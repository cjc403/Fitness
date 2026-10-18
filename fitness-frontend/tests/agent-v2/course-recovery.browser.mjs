// No model calls and no business writes: exercise the actual Vue loading failure.
import assert from 'node:assert/strict'
import {createBrowser,loginPage,base} from './fixtures.mjs'
const report={errors:[],blocked:[]},browser=await createBrowser();let context
try {
 const view=await loginPage(browser,'admin','/dashboard',report);context=view.context;const page=view.page
 const writes=[];page.on('request',r=>{if(!['GET','HEAD'].includes(r.method())&&new URL(r.url()).pathname.startsWith('/api/v1/')&&!new URL(r.url()).pathname.startsWith('/api/v1/agent/'))writes.push(r.method())})
 await page.route('**/api/v1/admin/courses',route=>route.fulfill({status:500,contentType:'application/json',body:JSON.stringify({code:500,message:'QA transient outage'})}))
 await page.goto(base+'/courses');await page.getByRole('heading',{name:'课程与排课',exact:true}).waitFor();await page.waitForTimeout(800)
 assert.deepEqual(report.errors,[],'A temporary course load failure must not escape as an unhandled page error')
 const retry=page.getByRole('button',{name:'重新加载课程数据',exact:true});await retry.waitFor();const document=await page.evaluate(()=>performance.timeOrigin)
 await page.unroute('**/api/v1/admin/courses');await retry.click();await page.locator('[data-agent-type="course"]').first().waitFor();assert.equal(await retry.count(),0)
 assert.equal(await page.evaluate(()=>performance.timeOrigin),document);assert.deepEqual(writes,[]);assert.deepEqual(report.errors,[]);assert.deepEqual(report.blocked,[])
 console.log('PASS real course load 500 -> visible retry -> original data restored; no model or business writes')
}finally{await context?.close();await browser.close()}
