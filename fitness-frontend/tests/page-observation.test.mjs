import test,{before,after} from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import http from 'node:http'
import {createRequire} from 'node:module'
const require=createRequire(new URL('../../fitness-agent-runner/package.json',import.meta.url))
const {chromium}=require('playwright-core')
let server,browser,base
before(async()=>{
 const code=await readFile(new URL('../src/agent/pageDriver.mjs',import.meta.url),'utf8')
 server=http.createServer((req,res)=>{res.setHeader('content-type',req.url==='/driver.mjs'?'text/javascript;charset=utf-8':'text/html;charset=utf-8');res.end(req.url==='/driver.mjs'?code:'<main></main>')}).listen(0,'127.0.0.1')
 await new Promise(r=>server.once('listening',r));base='http://127.0.0.1:'+server.address().port
 browser=await chromium.launch({channel:'msedge',headless:true,args:['--no-proxy-server']})
})
after(async()=>{await browser?.close();if(server)await new Promise(r=>server.close(r))})
async function fixture(html,run){
 const context=await browser.newContext(),page=await context.newPage()
 try{await page.goto(base);await page.setContent(html);await page.evaluate(async()=>{const {createPageDriver}=await import('/driver.mjs');window.driver=createPageDriver({document,window,router:{push:async path=>history.pushState({},'',path)},allowed:p=>['/','/courses'].includes(p)})});await run(page)}finally{await context.close()}
}
const cards='<main><article><h3>功能性力量</h3><p>总部 ¥128</p><button id="wrong">编辑课程</button></article><article><h3>流瑜伽</h3><p>滨江店 ¥88</p><button id="target" onclick="window.chosen=\'流瑜伽\'">编辑课程</button></article></main>'
test('repeated controls carry their entity and stale refs reject a reused row with a different entity',async()=>fixture(cards,async page=>{
 const obs=await page.evaluate(()=>window.driver.observe({goal:'把流瑜伽价格改为99'}))
 const edits=obs.nodes.filter(n=>n.name.includes('编辑课程'))
 assert.equal(new Set(edits.map(n=>n.name)).size,2,'Same-name buttons need distinct entity context')
 const target=edits.find(n=>n.name.includes('流瑜伽'));assert.ok(target)
 await page.evaluate(ref=>window.driver.act('click',{ref}),target.ref);assert.equal(await page.evaluate(()=>window.chosen),'流瑜伽')
 const result=await page.evaluate(async()=>{const obs=window.driver.observe();const ref=obs.nodes.find(n=>n.name.includes('编辑课程')&&n.name.includes('流瑜伽')).ref;document.querySelectorAll('h3')[1].textContent='普拉提';try{await window.driver.act('click',{ref});return false}catch(e){return e.code==='STALE_PAGE'}})
 assert.equal(result,true,'Retained DOM element must not silently switch business object')
}))
test('active dialog excludes background controls and its fields take priority over long page text',async()=>fixture(cards+`<div role="dialog" aria-label="编辑课程"><label>课程名称<input value="流瑜伽"></label><label>价格<input role="spinbutton" type="text" value="88" aria-valuemin="0"></label><button>保存</button><button>取消</button></div>`,async page=>{
 const obs=await page.evaluate(()=>{document.querySelector('main').prepend(document.createTextNode('无关背景'.repeat(3000)));return window.driver.observe()})
 assert.ok(!obs.nodes.some(n=>n.name.includes('编辑课程')),'Background edit buttons must not be actionable under a dialog')
 assert.ok(obs.nodes.some(n=>n.name==='价格'&&n.role==='spinbutton'))
 assert.ok(obs.text.includes('流瑜伽')&&obs.text.includes('88'),'Actual form values must survive the text budget')
 assert.ok(obs.text.indexOf('流瑜伽')<1000,'Dialog fields must precede background prose')
 const blocked=await page.evaluate(async()=>{window.driver.observe();try{await window.driver.act('navigate',{path:'/courses'});return false}catch{return true}})
 assert.equal(blocked,true,'Navigation cannot sidestep the active dialog')
}))
test('old background refs stop working as soon as a modal appears; only the upper business dialog is exposed',async()=>fixture(cards,async page=>{
 const blocked=await page.evaluate(async()=>{const old=window.driver.observe().nodes.find(n=>n.name.includes('编辑课程')).ref;document.body.insertAdjacentHTML('beforeend','<div role="dialog" aria-label="下层弹窗" style="position:fixed;inset:20px;z-index:10"><button>下层保存</button></div><div role="dialog" aria-label="上层弹窗" style="position:fixed;inset:40px;z-index:20"><button>上层确认</button></div>');try{await window.driver.act('click',{ref:old});return false}catch(e){return e.code==='STALE_PAGE'}})
 assert.equal(blocked,true)
 const obs=await page.evaluate(()=>window.driver.observe());assert.deepEqual(obs.nodes.map(n=>n.name),['上层确认'])
}))
test('dialog-linked teleported options remain actionable while unrelated popups and background stay excluded',async()=>fixture(`<main><button>背景保存</button></main><div role="dialog" aria-label="编辑会员"><label>状态<input role="combobox" aria-expanded="true" aria-controls="status-options" value="正常"></label></div><ul id="status-options" role="listbox"><li role="option" onclick="window.selected=true">暂停</li></ul><ul role="listbox"><li role="option">外部无关选项</li></ul><div data-page-agent-exclude><button>确认并提交这一次</button></div>`,async page=>{
 const obs=await page.evaluate(()=>window.driver.observe());assert.ok(obs.nodes.some(n=>n.name==='暂停'));assert.ok(!obs.nodes.some(n=>n.name==='外部无关选项'||n.name==='背景保存'||n.name==='确认并提交这一次'))
 await page.evaluate(ref=>window.driver.act('click',{ref}),obs.nodes.find(n=>n.name==='暂停').ref);assert.equal(await page.evaluate(()=>window.selected),true)
}))
test('a target late in a large list is grounded inside the 60-node budget',async()=>fixture('<main>'+Array.from({length:100},(_,i)=>`<article><h3>${i===99?'目标晚间瑜伽':'夹具课程'+i}</h3><button>编辑课程</button></article>`).join('')+'</main>',async page=>{
 const obs=await page.evaluate(()=>window.driver.observe({goal:'打开课程与排课，把目标晚间瑜伽的价格改为99'}))
 assert.ok(obs.nodes.length<=60);assert.ok(obs.nodes.some(n=>n.name.includes('目标晚间瑜伽')),'Goal entity must not disappear behind first-60 truncation')
 assert.ok(obs.text.includes('目标晚间瑜伽'))
}))
test('current values, checked and disabled state are explicit, sensitive fields and assistant text never enter observations',async()=>fixture(`<main><label>价格<input role="spinbutton" aria-valuemin="0" value="128"></label><label>已启用<input type="checkbox" checked></label><label>只读备注<input readonly value="不可填写"></label><button disabled>不可保存</button><label>密码<input type="password" value="private-password"></label><input name="api_key" aria-label="私钥" value="private-key"></main><aside data-page-agent-exclude>不要把这个助手消息传回模型<button>确认并提交这一次</button></aside>`,async page=>{
 const obs=await page.evaluate(()=>window.driver.observe());assert.ok(obs.text.includes('128')&&obs.text.includes('已选中')&&obs.text.includes('禁用')&&obs.text.includes('只读'))
 assert.ok(!obs.text.includes('private-password')&&!obs.text.includes('private-key')&&!obs.text.includes('这个助手消息'))
 assert.ok(!obs.nodes.some(n=>n.name==='密码'||n.name==='私钥'))
 const report=await page.evaluate(()=>window.driver.feedback());assert.ok(report.includes('价格')&&report.includes('128'),'Outcome feedback must contain actual field state')
}))
test('equal entity titles keep venue context and field refs reject changed observed values',async()=>fixture('<main><article><h3>流瑜伽</h3><p>总部 ¥88</p><button>编辑课程</button></article><article><h3>流瑜伽</h3><p>滨江店 ¥78</p><button>编辑课程</button></article><label>价格<input value="88"></label></main>',async page=>{
 const obs=await page.evaluate(()=>window.driver.observe({goal:'把滨江店流瑜伽价格改为99'}))
 const buttons=obs.nodes.filter(n=>n.name.includes('编辑课程'));assert.equal(new Set(buttons.map(n=>n.name)).size,2)
 assert.ok(buttons[0].name.includes('滨江店'),'Venue-qualified goal should rank the matching entity first')
 const index=obs.text.split('页面正文：')[0];assert.ok(index.includes('滨江店 ¥78')&&index.includes('总部 ¥88'),'Same-title entities must not overwrite each other in the object index')
 const feedback=await page.evaluate(()=>window.driver.feedback());assert.ok(feedback.includes('滨江店 ¥78'));assert.ok(feedback.indexOf('滨江店')<feedback.indexOf('总部'),'Outcome feedback should retain goal-ranked venue context')
 const blocked=await page.evaluate(async()=>{const field=window.driver.observe().nodes.find(n=>n.name==='价格');document.querySelector('input').value='120';try{await window.driver.act('fill',{ref:field.ref,value:'99'});return false}catch(e){return e.code==='STALE_PAGE'}})
 assert.equal(blocked,true,'A changed field invalidates its previously observed reference')
}))
test('radio choices and multi-input ranges retain both group and option or endpoint labels',async()=>fixture('<main><div class="el-form-item"><span class="el-form-item__label">强度</span><label><input type="radio" value="LOW" name="level">低</label><label><input type="radio" value="HIGH" name="level">高</label></div><div class="el-form-item"><span class="el-form-item__label">有效期</span><input placeholder="开始日期"><input placeholder="结束日期"></div></main>',async page=>{
 const obs=await page.evaluate(()=>window.driver.observe())
 assert.ok(obs.nodes.some(n=>n.name==='强度：低'));assert.ok(obs.nodes.some(n=>n.name==='强度：高'))
 assert.ok(obs.nodes.some(n=>n.name==='有效期：开始日期'));assert.ok(obs.nodes.some(n=>n.name==='有效期：结束日期'))
}))
test('pausing during input focus preparation prevents the value change after the render turn',async()=>fixture('<main><label>价格<input value="88"></label></main>',async page=>{
 const result=await page.evaluate(async()=>{window.allowed=true;document.querySelector('input').addEventListener('focus',()=>{window.allowed=false});const field=window.driver.observe().nodes.find(n=>n.name==='价格');let interrupted=false;try{await window.driver.act('fill',{ref:field.ref,value:'99'},()=>window.allowed)}catch(e){interrupted=e.code==='ACTION_INTERRUPTED'}return {value:document.querySelector('input').value,interrupted}})
 assert.deepEqual(result,{value:'88',interrupted:true})
}))
