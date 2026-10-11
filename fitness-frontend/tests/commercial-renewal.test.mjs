import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { transformSync } from 'esbuild'
import * as vue from 'vue'
import * as financeUi from '../src/utils/financeUi.mjs'
function runtime(renewEntitlement) {
  const source=readFileSync(new URL('../src/views/admin/CommercialCenter.vue',import.meta.url),'utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
  const code=transformSync(source+'\nexport { openRenew,submitRenew,renewForm }',{loader:'ts',format:'cjs'}).code
  const deps={vue:{...vue,onMounted(){}},'element-plus':{ElMessage:{success(){},error(){},warning(){}},ElMessageBox:{}},'@element-plus/icons-vue':{},'@/store/modules/auth':{useAuthStore:()=>({userInfo:{role:'MANAGER',permissions:[]}})},'@/utils/financeUi.mjs':financeUi,'@/api/modules/commercial':{renewEntitlement,getCommercialOverview:async()=>({data:{}}),listProducts:async()=>({data:[]}),listOrders:async()=>({data:[]}),listEntitlements:async()=>({data:[]})},'@/api/modules/member':{listMembers:async()=>({data:{items:[]}})},'@/api/modules/venue':{},'@/api/modules/coupon':{}}
  const module={exports:{}}
  new Function('require','module','exports',code)(name=>{if(name in deps)return deps[name];if(name.startsWith('@/components/'))return {};throw Error(name)},module,module.exports)
  return module.exports
}
const entitlement={id:42,productId:5}
test('renewal retries preserve the original key and successful next intent gets a new key',async()=>{
  const calls=[];let fails=true
  const r=runtime(async(id,body)=>{calls.push({id,...body});if(fails)throw Error('network timeout');return {data:{}}})
  r.openRenew(entitlement)
  await assert.rejects(r.submitRenew());await assert.rejects(r.submitRenew())
  assert.ok(calls[0].idempotencyKey);assert.equal(calls[0].idempotencyKey,calls[1].idempotencyKey)
  fails=false;await r.submitRenew();r.openRenew(entitlement);await r.submitRenew()
  assert.notEqual(calls[2].idempotencyKey,calls[3].idempotencyKey)
})
test('renewal ignores duplicate clicks while the original request is pending',async()=>{
  let release,count=0;const pending=new Promise(resolve=>{release=resolve})
  const r=runtime(async()=>{count++;await pending;return {data:{}}});r.openRenew(entitlement)
  const first=r.submitRenew();const duplicate=r.submitRenew();assert.equal(count,1)
  release();await Promise.all([first,duplicate]);assert.equal(count,1)
})
test('editing failed renewal content creates a distinct submission intent',async()=>{
  const calls=[];const r=runtime(async(id,body)=>{calls.push({...body});throw Error('timeout')});r.openRenew(entitlement)
  await assert.rejects(r.submitRenew());r.renewForm.amount=600;await assert.rejects(r.submitRenew())
  assert.notEqual(calls[0].idempotencyKey,calls[1].idempotencyKey)
})
