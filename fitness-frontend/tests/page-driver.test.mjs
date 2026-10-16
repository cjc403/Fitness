import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import http from 'node:http'
import { createRequire } from 'node:module'
const require = createRequire(new URL('../../fitness-agent-runner/package.json',import.meta.url))
const {chromium}=require('playwright-core')

test('DOM driver operates the same page, excludes assistant, fills real fields and rejects stale refs and external links',async()=>{
  const code=await readFile(new URL('../src/agent/pageDriver.mjs',import.meta.url),'utf8')
  const server=http.createServer((req,res)=>{res.setHeader('content-type',req.url==='/driver.mjs'?'text/javascript;charset=utf-8':'text/html;charset=utf-8');res.end(req.url==='/driver.mjs'?code:`<main><h1>经营看板</h1><label>姓名<input id="name"></label><button id="save">保存</button><a href="https://other.example/">外部链接</a><input type="password" aria-label="密码"></main><div data-page-agent-exclude><button>确认并提交这一次</button></div>`)}).listen(0,'127.0.0.1')
  await new Promise(r=>server.once('listening',r))
  const browser=await chromium.launch({channel:'msedge',headless:true,args:['--no-proxy-server']})
  try{
    const context=await browser.newContext(),page=await context.newPage();await page.goto(`http://127.0.0.1:${server.address().port}`)
    const result=await page.evaluate(async()=>{
      const {createPageDriver}=await import('/driver.mjs')
      const router={push:async path=>{history.pushState({},'',path);document.querySelector('h1').textContent='训练计划'}}
      const driver=createPageDriver({document,window,router,allowed:path=>['/','/training-plans'].includes(path)})
      let obs=driver.observe();const names=obs.nodes.map(n=>n.name)
      const input=obs.nodes.find(n=>n.name==='姓名'),external=obs.nodes.find(n=>n.name==='外部链接')
      await driver.act('fill',{ref:input.ref,value:'当前页填写'});const value=document.querySelector('input').value
      let blocked=false;try{await driver.act('click',{ref:external.ref})}catch{blocked=true}
      obs=driver.observe();const stale=obs.nodes.find(n=>n.name==='保存');document.querySelector('#save').textContent='删除'
      let staleBlocked=false;try{await driver.act('click',{ref:stale.ref})}catch(e){staleBlocked=e.code==='STALE_PAGE'}
      driver.observe();await driver.act('navigate',{path:'/training-plans'});driver.clear()
      return {names,value,blocked,staleBlocked,path:location.pathname,title:document.querySelector('h1').textContent}
    })
    assert.equal(context.pages().length,1);assert.equal(result.path,'/training-plans');assert.equal(result.title,'训练计划')
    assert.equal(result.value,'当前页填写');assert.equal(result.blocked,true);assert.equal(result.staleBlocked,true)
    assert.equal(result.names.includes('确认并提交这一次'),false);assert.equal(result.names.includes('密码'),false)
  }finally{await browser.close();await new Promise(r=>server.close(r))}
})
