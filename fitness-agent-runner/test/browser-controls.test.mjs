import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { ControlledBrowser } from '../src/browser.mjs';

test('styled radio labels and visible custom dropdown options remain operable from snapshot refs',async t=>{
 const server=http.createServer((req,res)=>{res.setHeader('content-type','text/html;charset=utf-8');res.end(`<h1>课程筛选</h1><label style="position:relative;display:block;width:120px;height:40px"><input type="radio" name="mode" style="width:20px;height:20px" onchange="document.querySelector('h1').textContent='我的课已选择'"><span style="position:absolute;inset:0;background:white">我的课</span></label><button role="combobox" aria-label="选择教练" onclick="document.querySelector('[role=listbox]').hidden=false">选择教练</button><ul role="listbox" hidden><li role="option" onclick="document.querySelector('h1').textContent='教练甲已选择'">教练甲</li><li role="option">教练乙</li></ul>`);});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const browser=new ControlledBrowser({frontendOrigin:`http://127.0.0.1:${server.address().port}`,headless:true,executablePath:'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'},{verify:async()=>{},awaitRunnable:async()=>{}});
 t.after(async()=>{await browser.close();server.closeAllConnections();await new Promise(r=>server.close(r));});
 await browser.start({home:'/coach-workbench',role:'COACH',permissions:['COACH_WORKBENCH'],userInfo:{}},'fixture');
 let view=(await browser.observe()).observation;
 assert.match(view.nodes[0].ref,/^r\d+_\d+$/);const stale=view.nodes[0].ref;
 await browser.act('click',{ref:view.nodes.find(n=>n.type==='radio').ref});
 view=(await browser.observe()).observation;assert.match(view.text,/我的课已选择/);
 await assert.rejects(browser.act('click',{ref:stale}),/失效/);
 await browser.act('click',{ref:view.nodes.find(n=>n.name==='选择教练').ref});
 view=(await browser.observe()).observation;
 const option=view.nodes.find(n=>n.role==='option'&&n.name==='教练甲');assert.ok(option,'Custom option must be exposed');
 await browser.act('click',{ref:option.ref});assert.match((await browser.observe()).observation.text,/教练甲已选择/);
});

test('installed Vue and Element Plus dropdown actually selects the requested option',async t=>{
 const [vue,element,css]=await Promise.all(['vue/dist/vue.global.prod.js','element-plus/dist/index.full.min.js','element-plus/dist/index.css'].map(p=>readFile(new URL('../../fitness-frontend/node_modules/'+p,import.meta.url),'utf8')));
 const server=http.createServer((req,res)=>{res.setHeader('content-type','text/html;charset=utf-8');res.end(`<style>${css}</style><div id="app"><el-form label-width="90px"><el-form-item label="课程类型"><el-select v-model="type" placeholder="请选择课程"><el-option label="团体课" value="GROUP"></el-option><el-option label="私教课" value="PRIVATE"></el-option></el-select></el-form-item></el-form><p>选中：{{type}}</p></div><script>${vue}</script><script>${element}</script><script>Vue.createApp({data:()=>({type:''})}).use(ElementPlus).mount('#app')</script>`);});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const browser=new ControlledBrowser({frontendOrigin:`http://127.0.0.1:${server.address().port}`,headless:true,executablePath:'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'},{verify:async()=>{},awaitRunnable:async()=>{},write:async()=>{throw new Error('No business writes in component fixture');}});
 t.after(async()=>{await browser.close();server.closeAllConnections();await new Promise(r=>server.close(r));});
 await browser.start({home:'/member-home',role:'MEMBER',permissions:['MEMBER_SELF_SERVICE'],userInfo:{}},'fixture');
 let view=(await browser.observe()).observation;
 await browser.act('click',{ref:view.nodes.find(n=>n.role==='combobox').ref});view=(await browser.observe()).observation;
 const option=view.nodes.find(n=>n.role==='option'&&n.name==='私教课');assert.ok(option);
 await browser.act('click',{ref:option.ref});assert.match((await browser.observe()).observation.text,/选中：PRIVATE/);
});
