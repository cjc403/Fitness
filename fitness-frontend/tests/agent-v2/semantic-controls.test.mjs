import test from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import {fileURLToPath} from 'node:url'
import {createRequire} from 'node:module'
const require=createRequire(new URL('../../../fitness-agent-runner/package.json',import.meta.url)),{chromium}=require('playwright-core')
test('real Element Plus semantic actions bind the venue, fill a bounded form, and retain validation errors',async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true,args:['--no-proxy-server']})
 try{
  const page=await browser.newPage();await page.route('http://semantic.fixture/**',r=>r.fulfill({body:'<div id="app"></div>',contentType:'text/html'}));await page.goto('http://semantic.fixture/')
  for(const path of ['vue/dist/vue.global.prod.js','element-plus/dist/index.full.js'])await page.addScriptTag({path:fileURLToPath(new URL('../../node_modules/'+path,import.meta.url))})
  await page.addStyleTag({path:fileURLToPath(new URL('../../node_modules/element-plus/dist/index.css',import.meta.url))})
  const driver=await readFile(new URL('../../src/agent/pageDriver.mjs',import.meta.url),'utf8'),context=await readFile(new URL('../../src/agent/pageContext.mjs',import.meta.url),'utf8')
  await page.addScriptTag({content:'(()=>{'+context.replaceAll('export ','')+';window.contextAdapter={build:buildPageContext,choose:chooseSemanticAction}})()'})
  await page.addScriptTag({content:'(()=>{'+driver.replaceAll('export ','')+';window.createPageDriver=createPageDriver})()'})
  await page.evaluate(()=>{
   window.vm=Vue.createApp({data:()=>({open:false,selected:0,fee:88,capacity:20}),template:`<main><article v-for="id in [1,2]" :data-agent-id="id" data-agent-type="course" :data-agent-venue="id" data-agent-label="同名瑜伽"><h2>同名瑜伽</h2><el-button data-agent-action="edit" @click="selected=id;open=true">编辑课程</el-button></article><el-dialog v-model="open" title="编辑课程"><el-form data-agent-type="course" :data-agent-id="selected" :data-agent-venue="selected" data-agent-label="同名瑜伽"><el-form-item label="价格" data-agent-field="fee"><el-input-number v-model="fee"/></el-form-item><el-form-item label="容量" data-agent-field="maxCapacity" error="容量已被其他人修改"><el-input-number v-model="capacity"/></el-form-item></el-form></el-dialog></main>`}).use(ElementPlus).mount('#app')
   window.driver=createPageDriver({document,window,router:{},allowed:()=>true,contextAdapter})
  })
  await page.waitForTimeout(300)
  await page.evaluate(async()=>{driver.observe({v2:true,goal:'编辑门店2同名瑜伽'});await driver.act('use_skill',{kind:'entity_action',entityType:'course',id:'2',venueId:'2',action:'edit'})})
  await page.getByRole('dialog',{name:'编辑课程'}).waitFor();await page.waitForTimeout(200)
  const before=await page.evaluate(()=>driver.observe({v2:true}));assert.equal(before.context.scope.entityId,'2');assert.ok(before.context.errors.includes('容量已被其他人修改'))
  await page.evaluate(()=>driver.act('use_skill',{kind:'fill_fields',fields:[{field:'fee',value:'99'},{field:'maxCapacity',value:'21'}]}))
  assert.deepEqual(await page.evaluate(()=>[vm.selected,vm.fee,vm.capacity]),[2,99,21]);assert.equal(await page.locator('[data-page-agent-target]').count(),1)
 }finally{await browser.close()}
})
