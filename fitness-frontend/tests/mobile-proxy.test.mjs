import test from 'node:test'
import assert from 'node:assert/strict'
import { createServer as httpServer, request as httpRequest } from 'node:http'
import { once } from 'node:events'
import { createServer as viteServer, loadConfigFromFile } from 'vite'
import { fileURLToPath } from 'node:url'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

test('real Vite proxy accepts LAN same-origin login without widening backend CORS', async t => {
  const requests=[]
  const backend=httpServer((req,res)=>{
    requests.push({origin:req.headers.origin,host:req.headers.host})
    if(req.headers.origin && req.headers.origin!==`http://${req.headers.host}`){res.writeHead(403,{'content-type':'text/plain'});res.end('Invalid CORS request');return}
    res.writeHead(400,{'content-type':'application/json'});res.end(JSON.stringify({code:1001,message:'参数校验失败'}))
  })
  backend.listen(0,'127.0.0.1');await once(backend,'listening')
  const target=`http://127.0.0.1:${backend.address().port}`
  const old=process.env.FITCHAIN_DEV_API_TARGET;process.env.FITCHAIN_DEV_API_TARGET=target
  let loaded
  try{loaded=await loadConfigFromFile({command:'serve',mode:'development'},fileURLToPath(new URL('../vite.config.ts',import.meta.url)))}
  finally{if(old===undefined)delete process.env.FITCHAIN_DEV_API_TARGET;else process.env.FITCHAIN_DEV_API_TARGET=old}
  const cacheDir=await mkdtemp(join(tmpdir(),'fitchain-proxy-cache-'))
  const vite=await viteServer({configFile:false,cacheDir,root:fileURLToPath(new URL('../',import.meta.url)),logLevel:'silent',appType:'custom',server:{middlewareMode:true,hmr:false,proxy:loaded.config.server.proxy}})
  const frontend=httpServer(vite.middlewares);frontend.listen(0,'127.0.0.1');await once(frontend,'listening')
  t.after(async()=>{frontend.closeAllConnections();frontend.close();await vite.close();backend.closeAllConnections();backend.close();assert.ok(cacheDir.startsWith(join(tmpdir(),'fitchain-proxy-cache-')));await rm(cacheDir,{recursive:true,force:true})})
  const url=`http://127.0.0.1:${frontend.address().port}/api/v1/auth/login`
  // Node's fetch normalizes Host to its URL; use raw HTTP to exercise real LAN Host/Origin pairs.
  const post=(host,origin)=>new Promise((resolve,reject)=>{
    const req=httpRequest(url,{method:'POST',headers:{Host:host,Origin:origin,'Content-Type':'application/json'}},res=>{
      let body='';res.setEncoding('utf8');res.on('data',chunk=>body+=chunk);res.on('end',()=>resolve({status:res.statusCode,json:async()=>JSON.parse(body)}))
    });req.on('error',reject);req.end('{}')
  })
  await t.test('phone LAN Origin reaches login field validation',async()=>{const r=await post('192.168.1.71:5174','http://192.168.1.71:5174');assert.equal(r.status,400);assert.equal((await r.json()).code,1001);assert.equal(requests.at(-1).origin,target)})
  await t.test('localhost works through the same proxy',async()=>{const r=await post('localhost:5173','http://localhost:5173');assert.equal(r.status,400)})
  await t.test('an unrelated Origin is never rewritten into an authorized one',async()=>{const r=await post('192.168.1.71:5174','https://untrusted.example');assert.equal(r.status,403);assert.equal(requests.at(-1).origin,'https://untrusted.example')})
  await t.test('a different port and null Origin remain rejected',async()=>{for(const origin of ['http://192.168.1.71:5175','null']){const r=await post('192.168.1.71:5174',origin);assert.equal(r.status,403);assert.equal(requests.at(-1).origin,origin)}})
})
