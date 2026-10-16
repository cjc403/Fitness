import test from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import {fileURLToPath} from 'node:url'
import {createRequire} from 'node:module'
const require=createRequire(new URL('../../fitness-agent-runner/package.json',import.meta.url)),{chromium}=require('playwright-core')
const source=await readFile(new URL('../src/agent/pageDriver.mjs',import.meta.url),'utf8')
async function fixture(withBusiness,run,range=false){
 const browser=await chromium.launch({channel:'msedge',headless:true,args:['--no-proxy-server']})
 try{
  const page=await browser.newPage();await page.route('http://picker.fixture/**',r=>r.fulfill({body:'<div id="app"></div>',contentType:'text/html'}));await page.goto('http://picker.fixture/')
  await page.addScriptTag({path:fileURLToPath(new URL('../node_modules/vue/dist/vue.global.prod.js',import.meta.url))})
  await page.addScriptTag({path:fileURLToPath(new URL('../node_modules/element-plus/dist/index.full.js',import.meta.url))})
  await page.addScriptTag({path:fileURLToPath(new URL('../node_modules/element-plus/dist/locale/zh-cn.js',import.meta.url))})
  await page.addStyleTag({path:fileURLToPath(new URL('../node_modules/element-plus/dist/index.css',import.meta.url))})
  await page.addScriptTag({content:source.replace('export const','const').replace('export function','function')+';window.createPageDriver=createPageDriver;'})
  await page.evaluate(({withBusiness,range})=>{
   const form='<el-form><el-form-item label="日期"><el-date-picker v-model="date" type="'+(range?'daterange':'date')+'" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="时段"><el-time-picker v-model="time" is-range format="HH:mm" value-format="HH:mm" /></el-form-item></el-form><el-button @click="cancelled=true">取消</el-button><el-button>保存</el-button>'
   window.vm=Vue.createApp({data:()=>({open:true,date:range?['2026-10-01','2026-11-30']:'2026-10-16',time:['09:00','10:00'],cancelled:false}),template:withBusiness?'<el-dialog v-model="open" title="排课">'+form+'</el-dialog>':'<main>'+form+'</main>'}).use(ElementPlus,{locale:ElementPlusLocaleZhCn}).mount('#app')
   window.driver=createPageDriver({document,window,router:{push:async p=>history.pushState({},'',p)},allowed:()=>true})
  },{withBusiness,range})
  await page.waitForTimeout(300);await run(page)
 }finally{await browser.close()}
}
for(const withBusiness of [true,false])test(`real date picker keeps its parent form and permits exact date selection (business dialog=${withBusiness})`,async()=>fixture(withBusiness,async page=>{
 await page.locator('.el-date-editor input').first().click();await page.waitForTimeout(300)
 let obs=await page.evaluate(()=>window.driver.observe({goal:'将日期设为2026-10-20'}))
 assert.ok(obs.nodes.some(n=>n.name==='取消'),'Picker role=dialog must not hide parent cancellation')
 assert.ok(obs.nodes.some(n=>n.name==='日期'),'Picker must keep the original field accessible')
 const day=obs.nodes.find(n=>n.name==='选择日期：2026-10-20');assert.ok(day,'Actual date cell needs an unambiguous executable date')
 await page.evaluate(ref=>window.driver.act('click',{ref}),day.ref);await page.waitForTimeout(200)
 assert.equal(await page.evaluate(()=>window.vm.date),'2026-10-20')
 obs=await page.evaluate(()=>window.driver.observe());assert.ok(obs.text.includes('2026-10-20'));assert.ok(obs.nodes.some(n=>n.name==='保存'))
 await page.locator('.el-date-editor input').first().click();await page.waitForTimeout(200)
 obs=await page.evaluate(()=>window.driver.observe());await page.evaluate(ref=>window.driver.act('click',{ref}),obs.nodes.find(n=>n.name==='取消').ref)
 assert.equal(await page.evaluate(()=>window.vm.cancelled),true)
}))
test('real time range endpoints are distinct and preserve their controlled values',async()=>fixture(true,async page=>{
 const obs=await page.evaluate(()=>window.driver.observe())
 const start=obs.nodes.find(n=>n.name==='时段：开始时间'),end=obs.nodes.find(n=>n.name==='时段：结束时间')
 assert.ok(start&&end,'Two empty-placeholder inputs must retain their start/end meaning')
 assert.ok(obs.text.includes('09:00')&&obs.text.includes('10:00'))
 await page.evaluate(ref=>window.driver.act('fill',{ref,value:'11:30'}),end.ref);await page.waitForTimeout(200)
 assert.deepEqual(await page.evaluate(()=>window.vm.time),['09:00','11:30'])
}))
test('two-month date range keeps the goal date, original endpoints and cancel inside the sixty-node budget',async()=>fixture(true,async page=>{
 await page.locator('.el-date-editor--daterange input').first().click();await page.waitForTimeout(300)
 const obs=await page.evaluate(()=>window.driver.observe({goal:'将日期区间结束日期设为2026-11-30'}))
 assert.ok(obs.nodes.length<=60);assert.ok(obs.nodes.some(n=>n.name==='选择日期：2026-11-30'),'Bare goal date must survive the calendar budget')
 for(const name of ['日期：开始日期','日期：结束日期','取消','保存'])assert.ok(obs.nodes.some(n=>n.name===name),'Parent recovery control missing: '+name)
 await page.evaluate(ref=>window.driver.act('fill',{ref,value:'2026-11-29'}),obs.nodes.find(n=>n.name==='日期：结束日期').ref);await page.waitForTimeout(200)
 assert.deepEqual(await page.evaluate(()=>window.vm.date),['2026-10-01','2026-11-29'])
},true))
