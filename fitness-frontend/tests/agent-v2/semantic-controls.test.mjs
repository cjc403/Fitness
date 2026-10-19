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
   window.vm=Vue.createApp({data:()=>({open:false,selected:0,student:null,goal:"GENERAL",rating:0,course:null,fee:88,capacity:20}),template:`<main><table><tr data-agent-type="student" data-agent-id="7" data-agent-label="宁安"><td><el-radio v-model="student" :value="7" data-agent-action="select">{{ '' }}</el-radio></td><td>宁安</td><td>FC-0007</td></tr></table><article v-for="id in [1,2]" :data-agent-id="id" data-agent-type="course" :data-agent-venue="id" data-agent-label="同名瑜伽"><h2>同名瑜伽</h2><el-button data-agent-action="edit" @click="selected=id;open=true">编辑课程</el-button></article><el-dialog v-model="open" title="编辑课程"><el-form data-agent-type="course" :data-agent-id="selected" :data-agent-venue="selected" data-agent-label="同名瑜伽"><el-form-item label="训练目标" data-agent-field="goal"><el-select v-model="goal"><el-option label="综合训练" value="GENERAL"/><el-option label="增肌" value="MUSCLE_GAIN"/></el-select></el-form-item><el-form-item label="测试排课" data-agent-field="courseId" :data-agent-value-id="course"><el-select v-model="course"><el-option v-for="id in 100" :key="id" :value="id" :label="(id===99?'精确目标课程':'一般课程'+id)+' · 2026/10/20 14:00 · 0/20'" data-agent-type="schedule" :data-agent-id="id" :data-agent-label="id===99?'精确目标课程':'一般课程'+id" data-agent-action="select"/></el-select></el-form-item><el-form-item label="价格" data-agent-field="fee"><el-input-number v-model="fee"/></el-form-item><el-form-item label="容量" data-agent-field="maxCapacity" error="容量已被其他人修改"><el-input-number v-model="capacity"/></el-form-item><el-form-item label="训练评分" data-agent-field="rating"><el-rate v-model="rating"/></el-form-item></el-form></el-dialog></main>`}).use(ElementPlus).mount('#app')
   window.driver=createPageDriver({document,window,router:{},allowed:()=>true,contextAdapter})
  })
  await page.waitForTimeout(300)
  await page.evaluate(async()=>{driver.observe({v2:true,goal:'选择学员宁安'});await driver.act('use_skill',{kind:'entity_action',entityType:'student',id:'7',action:'select'})});assert.equal(await page.evaluate(()=>vm.student),7)
  await page.evaluate(async()=>{driver.observe({v2:true,goal:'编辑门店2同名瑜伽'});await driver.act('use_skill',{kind:'entity_action',entityType:'course',id:'2',venueId:'2',action:'edit'})})
  await page.getByRole('dialog',{name:'编辑课程'}).waitFor();await page.waitForTimeout(200)
  const before=await page.evaluate(()=>driver.observe({v2:true}));assert.equal(before.context.scope.entityId,'2');assert.ok(before.context.errors.includes('容量已被其他人修改'))
  await page.evaluate(()=>driver.act('use_skill',{kind:'fill_fields',fields:[{field:'fee',value:'99'},{field:'goal',value:'增肌'},{field:'maxCapacity',value:'21'}]}))
  assert.deepEqual(await page.evaluate(()=>[vm.selected,vm.fee,vm.capacity,vm.goal]),[2,99,21,"MUSCLE_GAIN"]);assert.equal(await page.locator('[data-page-agent-target]').count(),1)
  const actualGoal=await page.evaluate(()=>driver.observe({v2:true}));assert.equal(actualGoal.context.fields.find(f=>f.key==='goal').value,'增肌','The selected label must not be read from ElSelect hidden input wrapper')
  await page.evaluate(()=>{const o=driver.observe({v2:true});return driver.act('select',{ref:o.context.fields.find(f=>f.key==='goal').ref,value:'综合训练'})});assert.equal(await page.evaluate(()=>vm.goal),'GENERAL','The public select action must adapt a registered ElSelect instead of asking the model to repair an API mismatch')
  const rating=await page.evaluate(()=>driver.observe({v2:true}));assert.equal(rating.context.fields.find(f=>f.key==='rating')?.controlType,'slider')
  await page.evaluate(()=>driver.act('use_skill',{kind:'fill_field',field:'rating',value:'5'}));assert.equal(await page.evaluate(()=>vm.rating),5)
  await assert.rejects(page.evaluate(()=>{const o=driver.observe({v2:true});return driver.act('fill',{ref:o.context.fields.find(f=>f.key==='rating').ref,value:'9'})}),/范围/)
  await page.evaluate(()=>{const o=driver.observe({v2:true});return driver.act('click',{ref:o.context.fields.find(f=>f.key==='courseId').ref})});await page.waitForTimeout(200)
  const options=await page.evaluate(()=>driver.observe({v2:true,goal:'为会员预约精确目标课程'}));assert.ok(options.context.entities.some(e=>e.type==='schedule'&&e.id==='99'),'A late typed option must remain inside the bounded snapshot')
  await page.evaluate(()=>driver.act('use_skill',{kind:'entity_action',entityType:'schedule',id:'99',action:'select'}));assert.equal(await page.evaluate(()=>vm.course),99)
  await page.evaluate(async()=>{vm.course=null;await Vue.nextTick();document.querySelector('[data-agent-field="courseId"]').setAttribute('data-agent-option-type','schedule');driver.observe({v2:true,goal:'选择精确目标课程'})})
  await page.evaluate(()=>driver.act('use_skill',{kind:'fill_field',field:'courseId',value:'99'}));assert.equal(await page.evaluate(()=>vm.course),99,'A typed option ID must work despite date/capacity suffixes')
  const selected=await page.evaluate(()=>driver.observe({v2:true}));assert.equal(selected.context.fields.find(f=>f.key==='courseId').selectedId,'99')
  assert.ok(!(await page.evaluate(()=>driver.feedback())).includes('[只读]'),'An enabled non-filterable ElSelect is selectable, even though its internal input is readonly')
  await page.evaluate(()=>{window.redundantClicks=0;document.querySelector('[data-agent-field="courseId"]').addEventListener('click',()=>window.redundantClicks++);driver.observe({v2:true})})
  await page.evaluate(()=>driver.act('use_skill',{kind:'fill_field',field:'courseId',value:'99'}));assert.equal(await page.evaluate(()=>window.redundantClicks),0,'Already selected IDs must not reopen or reselect an option')

  await page.evaluate(async()=>{await Vue.nextTick();driver.observe({v2:true});await driver.act('use_skill',{kind:'fill_field',field:'courseId',value:'1'})});assert.equal(await page.evaluate(()=>vm.course),1,'The ID selection must not prefer the former goal label or truncate the requested option')
  await page.evaluate(async()=>{await Vue.nextTick();document.querySelector('[data-agent-type="schedule"][data-agent-id="98"]').setAttribute('aria-disabled','true');driver.observe({v2:true})})
  await assert.rejects(page.evaluate(()=>driver.act('use_skill',{kind:'fill_field',field:'courseId',value:'98'})),/页面或操作对象已变化/);assert.equal(await page.evaluate(()=>vm.course),1,'A typed disabled option must never be selected')
  await page.evaluate(()=>{
   vm.open=false
   document.querySelector('main').innerHTML='<section role="dialog" aria-modal="true" aria-label="复杂表单">'+Array.from({length:70},(_,i)=>`<button>列表操作${i}</button>`).join('')+'<label data-agent-field="first">第一个字段<input></label><label data-agent-field="second">第二个字段<input></label></section>'
  })
  const crowded=await page.evaluate(()=>driver.observe({v2:true}));assert.equal(crowded.context.truncated,true);assert.ok(crowded.context.fields.some(f=>f.key==='second'));assert.ok(!crowded.text.includes('当前控件状态（实际值）'))
  await page.evaluate(()=>{document.querySelector('[data-agent-field="first"] input').addEventListener('input',()=>history.pushState({},'','/different-page'));driver.observe({v2:true})})
  await assert.rejects(page.evaluate(()=>driver.act('use_skill',{kind:'fill_fields',fields:[{field:'first',value:'one'},{field:'second',value:'two'}]})),/页面或操作对象已变化/)
  assert.equal(await page.locator('[data-agent-field="second"] input').inputValue(),'')
  await page.evaluate(()=>document.querySelector('section[role="dialog"]').classList.add('el-message-box'))
  assert.equal((await page.evaluate(()=>driver.observe({v2:true}))).context.scope.nativeConfirmation,true)
  await page.evaluate(()=>{document.querySelector('section[role="dialog"]').remove();void ElementPlus.ElMessageBox.confirm('确认取消该预约？开课前2小时内不可自助取消。','取消预约',{type:'warning',confirmButtonText:'确定',cancelButtonText:'取消'}).catch(()=>{})})
  await page.getByRole('button',{name:'确定',exact:true}).waitFor();await page.waitForTimeout(200)
  const confirmation=await page.evaluate(()=>driver.observe({v2:true}));assert.equal(confirmation.context.scope.nativeConfirmation,true)
  assert.ok(confirmation.nodes.some(n=>n.name==='确定'),'The real native confirmation submit must be observable')
  assert.ok(confirmation.text.includes('开课前2小时内不可自助取消'),'The confirmation terms must be observable')
 }finally{await browser.close()}
})
