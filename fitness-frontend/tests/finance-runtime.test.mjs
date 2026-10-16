import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { transformSync } from 'esbuild'
import axios from 'axios'
import * as vue from 'vue'
import * as helpers from '../src/utils/financeUi.mjs'
import { pageRequestGate } from '../src/agent/requestGate.mjs'

// Run actual TS modules and SFC setup bodies; stub only browser and authenticated transport boundaries.
function execute(path, dependencies, exports = [], props = {}) {
  let source = readFileSync(new URL('../src/' + path, import.meta.url), 'utf8')
  if (path.endsWith('.vue')) source = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
  source += exports.length ? `\nexport { ${exports.join(',')} }` : ''
  const code = transformSync(source, { loader:'ts', format:'cjs', define:{'import.meta.env':'{}'} }).code
  const module = { exports:{} }
  new Function('require','module','exports','defineProps','defineEmits','defineExpose', code)(name => {
    if(name==='@/agent/requestGate.mjs') return {pageRequestGate}
    if (!(name in dependencies)) throw new Error('Uncontrolled import: ' + name)
    return dependencies[name]
  }, module, module.exports, () => props, () => () => {}, () => {})
  return module.exports
}
function runtime(adapter) {
  const messages = [], downloads = [], calls = []
  const auth = {token:'old', userInfo:{permissions:['REFUND_REQUEST','VALUE_RECONCILE']}, applySession(s){this.token=s.accessToken}, clearSession(){this.token=null}}
  const notices = {error:m=>messages.push(m), success:m=>messages.push(m), warning:m=>messages.push(m), closeAll(){}}
  const client = axios.create({adapter:async config => { calls.push(config); return adapter(config) }})
  const deps = {'axios':{...axios, create:()=>client, post:async()=>({data:{code:0,data:{accessToken:'new',refreshToken:'refresh'}}})}, 'element-plus':{ElMessage:notices,ElMessageBox:{}}, '@/store/modules/auth':{useAuthStore:()=>auth}, '@/utils/storage':{getRefreshToken:()=> 'refresh'}, '@/utils/financeUi.mjs':helpers, vue:{...vue,onMounted(){}}}
  const request = execute('utils/request.ts', deps)
  deps['@/utils/request'] = request
  const api = execute('api/modules/finance.ts', deps)
  deps['@/api/modules/finance'] = api
  globalThis.document = {createElement:()=>({click(){downloads.push(this.download)}})}
  globalThis.window = {location:{pathname:'/login',search:'',hash:'',replace(){}},setTimeout(){}}
  return {deps,api,messages,downloads,calls,auth}
}
const csv = '\uFEFF"amount"\n"30.00"\n'
const success = (config, contentType='text/csv') => ({data:new Blob([csv],{type:contentType}),status:200,statusText:'OK',headers:{'content-type':contentType},config})
const failure = (config,status,code=1001) => Promise.reject({config,response:{status,data:new Blob([JSON.stringify({code,message:'controlled failure'})],{type:'application/json'})}})

test('Excel export: interceptor -> API -> component downloads actual bytes, including refreshed 401 replay', async()=>{
  // CSV 与 Excel 两种返回类型都必须能下载（此前拦截器写死白名单，Excel 被误判成"没有返回文件"）
  for (const contentType of ['text/csv','application/vnd.openxmlformats-officedocument.spreadsheetml.sheet']) {
  for (const replay of [false,true]) {
    let attempts=0
    const r=runtime(config=>replay&&attempts++===0?failure(config,401):success(config,contentType))
    const panel=execute('components/FinanceReportPanel.vue',r.deps,['exportExcel'])
    const original=URL.createObjectURL; const received=[]
    URL.createObjectURL=blob=>{assert.ok(blob instanceof Blob);received.push(blob);return 'blob:controlled'}
    try {await panel.exportExcel()} finally {URL.createObjectURL=original}
    assert.equal(r.downloads.length,1);assert.deepEqual(new Uint8Array(await received[0].arrayBuffer()),new TextEncoder().encode(csv))
    assert.equal(r.calls.length,replay?2:1)
    if(replay) assert.equal(r.calls[1].headers.Authorization,'Bearer new')
  }
  }
})
test('JSON Blob business error never downloads; 403 remains throttled and repeated 401 ends session',async()=>{
  for(const mode of ['business',403,401]){
    const r=runtime(config=> mode==='business'?{...success(config),data:new Blob(['{"code":1001,"message":"business failure"}']),headers:{'content-type':'application/json'}}:failure(config,mode))
    const panel=execute('components/FinanceReportPanel.vue',r.deps,['exportExcel'])
    await panel.exportExcel();await panel.exportExcel()
    assert.equal(r.downloads.length,0);assert.ok(r.messages.length>0)
    if(mode===403) assert.equal(r.messages.length,1)
    if(mode===401) assert.equal(r.auth.token,null)
  }
})
test('HTTP-origin quote opens with cryptographic fallback and stable retry intent; generation failure is visible',async()=>{
  const original=Object.getOwnPropertyDescriptor(globalThis,'crypto');let sequence=0
  try{
    Object.defineProperty(globalThis,'crypto',{configurable:true,value:{getRandomValues(bytes){bytes.fill(++sequence);return bytes}}})
    const r=runtime(config=>({...success(config),data:{code:0,data:{entitlementId:8,verificationStatus:'UNVERIFIED',version:1,blockedReasons:[],funding:[]}},headers:{}}))
    const panel=execute('components/RefundPanel.vue',r.deps,['startFor','quoteVisible','applicationIntent','resetQuote'],{entitlements:[],orders:[]})
    await panel.startFor(8); assert.equal(panel.quoteVisible.value,true)
    const key=panel.applicationIntent.value.idempotencyKey;assert.match(key,/^[\da-f]{8}-[\da-f]{4}-4[\da-f]{3}-[89ab][\da-f]{3}-[\da-f]{12}$/)
    assert.equal(panel.applicationIntent.value.idempotencyKey,key)
    panel.resetQuote();await panel.startFor(8);assert.notEqual(panel.applicationIntent.value.idempotencyKey,key)
    Object.defineProperty(globalThis,'crypto',{configurable:true,value:{}})
    panel.resetQuote();await panel.startFor(8);assert.ok(r.messages.some(m=>m.includes('安全')||m.includes('随机')))
  }finally{if(original)Object.defineProperty(globalThis,'crypto',original);else delete globalThis.crypto}
})
test('historical quote offers opening and scoped attested renewals, with blank amounts',async()=>{
  for(const known of [[],[{orderId:12,orderNo:'R12',unusedAmount:5}]]){
    let submitted
    const r=runtime(config=>{const reconciling=config.url.endsWith('/reconcile');if(reconciling)submitted=JSON.parse(config.data);return {...success(config),data:{code:0,data:{entitlementId:8,verificationStatus:reconciling?'VERIFIED':'UNVERIFIED',version:1,blockedReasons:[],funding:known}},headers:{}}})
    const props={entitlements:[{id:8,sourceOrderId:11,memberId:2,productId:3,venueId:4}],orders:[{id:11,orderNo:'O11',memberId:2,productId:3,venueId:4,orderType:'NEW',status:'PAID'},{id:12,orderNo:'R12',memberId:2,productId:3,venueId:4,orderType:'RENEW',status:'PAID'},{id:13,orderNo:'OTHER',memberId:9,productId:3,venueId:4,orderType:'RENEW',status:'PAID'}]}
    const panel=execute('components/RefundPanel.vue',r.deps,['startFor','sourceOrders','addSource','reconcileForm','submitReconcile','quote'],props)
    await panel.startFor(8);assert.deepEqual(panel.sourceOrders.value.map(o=>o.orderId),[11,12])
    panel.addSource();assert.equal(panel.reconcileForm.sources[0].unusedAmount,undefined)
    Object.assign(panel.reconcileForm.sources[0],{orderId:11,unusedAmount:4,productType:'VISIT_CARD'})
    if(known.length){panel.addSource();Object.assign(panel.reconcileForm.sources[1],{orderId:12,unusedAmount:5,productType:'VISIT_CARD'})}
    panel.reconcileForm.reason='Checked documentary evidence';panel.reconcileForm.remainingUses=2
    await panel.submitReconcile()
    assert.deepEqual(submitted.sources.map(source=>[source.orderId,source.unusedAmount]),known.length?[[11,4],[12,5]]:[[11,4]])
    assert.equal(panel.quote.value.verificationStatus,'VERIFIED')
  }
})
