// Production preview only: verifies actual Service Worker cache contents without accounts or a business backend.
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
const modulePath=process.env.FITCHAIN_PLAYWRIGHT_MODULE || 'playwright'
const {chromium}=await import(modulePath.includes(':')?pathToFileURL(modulePath).href:modulePath)
const base=process.env.FITCHAIN_PWA_BASE || 'http://127.0.0.1:4179', out=resolve('qa/mobile-agent-20261015')
await mkdir(out,{recursive:true})
const browser=await chromium.launch({channel:'msedge',headless:true}), context=await browser.newContext({viewport:{width:390,height:844}}), page=await context.newPage()
const report={kind:'Production preview Service Worker isolation test; no real account or business API',base,passed:false}
try {
  await context.route(base+'/api/**',route=>route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({code:0,data:{privateMarker:'PRIVATE_API_MUST_NOT_CACHE'}})}))
  await context.route(base+'/private-test',route=>route.fulfill({status:200,contentType:'text/html',body:'<!doctype html><p>PRIVATE_HTML_MUST_NOT_CACHE</p>'}))
  await page.goto(base+'/login')
  await page.waitForFunction(async()=>!!(await navigator.serviceWorker.getRegistration())?.active,undefined,{timeout:15000})
  await page.reload(); await page.waitForFunction(()=>!!navigator.serviceWorker.controller)
  const sensitive=await page.evaluate(async()=>{localStorage.setItem('pwa-test-marker','TOKEN_MUST_NOT_CACHE');return (await fetch('/api/v1/agent/profile',{headers:{Authorization:'Bearer frontend-mock-only'}})).json()})
  assert.equal(sensitive.data.privateMarker,'PRIVATE_API_MUST_NOT_CACHE')
  await page.goto(base+'/private-test'); assert.ok((await page.locator('body').innerText()).includes('PRIVATE_HTML_MUST_NOT_CACHE'))
  const cachesState=await page.evaluate(async()=>Promise.all((await caches.keys()).map(async key=>({key,urls:(await(await caches.open(key)).keys()).map(r=>new URL(r.url).pathname)}))))
  const allowed=['/offline.html','/manifest.webmanifest','/icons/fitchain.svg','/icons/fitchain-192.png','/icons/fitchain-512.png']
  assert.ok(cachesState.length>0)
  assert.ok(cachesState.every(cache=>cache.urls.every(url=>allowed.includes(url))))
  assert.ok(cachesState.some(cache=>cache.urls.includes('/offline.html')))
  await context.unroute(base+'/private-test'); await context.setOffline(true); await page.goto(base+'/private-test')
  await page.getByRole('heading',{name:'当前无法连接 FitChain'}).waitFor()
  const offlineText=await page.locator('body').innerText(); assert.doesNotMatch(offlineText,/PRIVATE_|TOKEN_/)
  await page.screenshot({path:resolve(out,'pwa-offline-shell.png')})
  report.passed=true;report.caches=cachesState;report.offlineText=offlineText
}catch(error){report.failure=String(error.stack||error);throw error}
finally{await writeFile(resolve(out,'pwa-verification.json'),JSON.stringify(report,null,2),'utf8');await browser.close();console.log(JSON.stringify(report,null,2))}
