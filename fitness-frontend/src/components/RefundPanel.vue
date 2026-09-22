<template>
  <section class="refund-panel" v-loading="loading">
    <div class="heading"><div><span>REFUND WORKFLOW</span><h2>退款申请与审批</h2><p>退款金额、范围与权限均由服务端核定；原卡退款先获取报价。</p></div><el-button @click="load">刷新</el-button></div>
    <el-alert v-if="failed" title="退款列表加载失败，未将失败显示为空列表。" type="error" show-icon :closable="false"><template #default><el-button link type="primary" @click="load">重试</el-button></template></el-alert>
    <el-table v-else :data="refunds" stripe>
      <el-table-column label="退款单 / 权益" min-width="180"><template #default="{row}"><b>{{ row.refundNo }}</b><small>{{ row.entitlementNo }}</small></template></el-table-column>
      <el-table-column label="会员 / 范围" min-width="150"><template #default="{row}"><span>{{ row.memberName }}</span><small>场馆 #{{ row.venueId }}</small></template></el-table-column>
      <el-table-column label="状态" width="115"><template #default="{row}"><el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="服务端金额" width="125"><template #default="{row}">¥{{ money(row.amount) }}</template></el-table-column>
      <el-table-column label="申请 / 审核" min-width="185"><template #default="{row}"><span>{{ dateTime(row.requestedAt) }}</span><small>申请账号 #{{ row.requestedBy }}{{ row.approvedAt ? ` · 审核 ${dateTime(row.approvedAt)}` : '' }}</small></template></el-table-column>
      <el-table-column label="原因 / 审计" min-width="200"><template #default="{row}"><span>{{ row.decisionReason || row.reason }}</span><small v-for="item in row.audit" :key="`${item.action}-${item.occurredAt}`">{{ item.action }} · {{ item.reason || '—' }} · {{ dateTime(item.occurredAt) }}</small></template></el-table-column>
      <el-table-column label="操作" min-width="170" fixed="right"><template #default="{row}">
        <el-button v-if="can('REFUND_APPROVE') && row.status==='PENDING' && !isOwn(row)" link type="success" :loading="busyId===row.id" @click="decide(row,'approve')">批准</el-button>
        <el-button v-if="can('REFUND_APPROVE') && row.status==='PENDING' && !isOwn(row)" link type="danger" :loading="busyId===row.id" @click="decide(row,'reject')">拒绝</el-button>
        <el-button v-if="can('REFUND_REQUEST') && ['PENDING','APPROVED'].includes(row.status)" link :loading="busyId===row.id" @click="decide(row,'cancel')">撤回</el-button>
        <el-button v-if="can('REFUND_PAY') && row.status==='APPROVED'" link type="primary" :disabled="busyId===row.id" @click="openPay(row)">登记支付</el-button>
        <span v-if="row.status==='PAID'" class="muted">已登记 {{ dateTime(row.paidAt) }}</span>
      </template></el-table-column>
    </el-table>
    <el-empty v-if="!failed&&!loading&&!refunds.length" description="当前授权范围暂无退款单" :image-size="65" />

    <div v-if="can('REFUND_REQUEST')" class="quote-section"><h3>权益退款申请</h3><p>选择权益后由服务端报价。待核对权益不会显示或猜测退款金额。</p><el-select v-model="selectedEntitlementId" filterable placeholder="选择会员权益" style="width:min(480px,100%)"><el-option v-for="item in entitlements" :key="item.id" :value="item.id" :label="`${item.memberName} · ${item.productName} · ${item.entitlementNo}`"/></el-select><el-button type="primary" :disabled="!selectedEntitlementId" :loading="quoteLoading" @click="openQuote">获取退款报价</el-button></div>

    <el-dialog v-model="quoteVisible" title="退款报价与申请" width="min(700px,94vw)" @closed="resetQuote">
      <template v-if="quote"><el-alert v-if="quote.verificationStatus==='UNVERIFIED'" title="待核对：服务端不会提供可退金额。请由有权限人员依据来源订单和凭证核对。" type="warning" :closable="false"/><el-descriptions :column="2" border style="margin-top:14px"><el-descriptions-item label="核对状态">{{ quote.verificationStatus==='VERIFIED'?'已核对':'待核对' }}</el-descriptions-item><el-descriptions-item label="权益类型">{{ quote.productType || '历史记录待核对' }}</el-descriptions-item><el-descriptions-item label="可退金额">{{ quote.refundableAmount==null?'待核对':`¥${money(quote.refundableAmount)}` }}</el-descriptions-item><el-descriptions-item label="剩余次数 / 天数">{{ quote.remainingUses ?? '—' }} / {{ quote.remainingServiceDays ?? '—' }}</el-descriptions-item><el-descriptions-item label="报价版本">{{ quote.version }}</el-descriptions-item><el-descriptions-item label="处理中退款">{{ quote.activeRefundId || '无' }}</el-descriptions-item></el-descriptions><el-alert v-if="quote.blockedReasons.length" :title="`当前不可申请：${quote.blockedReasons.join('；')}`" type="error" :closable="false" style="margin-top:14px"/>
      <el-form v-if="quote.verificationStatus==='VERIFIED'" label-width="90px" style="margin-top:16px"><el-form-item label="申请原因"><el-input v-model="requestReason" type="textarea" maxlength="1000" show-word-limit/></el-form-item></el-form>
      <el-form v-if="can('VALUE_TRANSFER_VERIFY') && quote.verificationStatus==='VERIFIED'" label-width="110px"><el-divider>转卡受领核实</el-divider><el-alert title="仅核实本次转卡退款受领依据，不会重算余额、来源或次数；服务端仍会校验总部权限。" type="info" :closable="false"/><el-form-item label="核实原因" style="margin-top:14px"><el-input v-model="transferReason" maxlength="1000"/></el-form-item><el-form-item label="转卡依据"><el-input v-model="transferEvidence" type="textarea" maxlength="1000"/></el-form-item><el-button :loading="verifyingTransfer" @click="submitTransferVerification">提交转卡核实</el-button></el-form>
      <el-divider v-if="can('VALUE_RECONCILE')">核对权益价值</el-divider>
      <el-form v-if="can('VALUE_RECONCILE') && quote.verificationStatus==='UNVERIFIED'" label-width="110px"><el-form-item label="核对原因"><el-input v-model="reconcileForm.reason" type="textarea" maxlength="1000"/></el-form-item><el-form-item label="剩余次数"><el-input-number v-model="reconcileForm.remainingUses" :min="0" :precision="0" placeholder="按凭证填写"/></el-form-item><el-form-item label="转卡依据"><el-input v-model="reconcileForm.transferEvidence" maxlength="1000" placeholder="转卡时必须填写可核验依据"/></el-form-item><el-form-item label="来源订单"><div class="source-list"><div v-for="(source,index) in reconcileForm.sources" :key="index" class="source-row"><el-select v-model="source.orderId" placeholder="选择来源订单"><el-option v-for="fund in sourceOrders" :key="fund.orderId" :value="fund.orderId" :label="`${fund.orderNo}（不自动填金额）`"/></el-select><el-input-number v-model="source.unusedAmount" :min="0" :precision="2" placeholder="未使用金额"/><el-select v-model="source.productType" placeholder="历史商品类型"><el-option label="次卡" value="VISIT_CARD"/><el-option label="私教课包" value="PRIVATE_PACKAGE"/><el-option label="月卡" value="MONTH_CARD"/><el-option label="季卡" value="QUARTER_CARD"/><el-option label="年卡" value="YEAR_CARD"/></el-select><el-input-number v-model="source.totalUnits" :min="0" :precision="0" placeholder="剩余天/次数依据"/><el-date-picker v-model="source.startDate" type="date" value-format="YYYY-MM-DD" placeholder="服务起始日"/><el-button link type="danger" @click="reconcileForm.sources.splice(index,1)">删除</el-button></div><el-button link type="primary" @click="addSource">添加来源</el-button></div></el-form-item><el-button type="warning" :loading="reconciling" @click="submitReconcile">提交核对（以服务端结果为准）</el-button></el-form>
      </template><template #footer><el-button @click="quoteVisible=false">关闭</el-button><el-button v-if="quote?.verificationStatus==='VERIFIED'" type="primary" :disabled="!canRequest" :loading="submitting" @click="submitRequest">提交退款申请</el-button></template>
    </el-dialog>
    <el-dialog v-model="payVisible" title="登记线下退款" width="min(520px,94vw)"><el-alert title="仅登记已在线下完成的退款；本操作不会发起真实打款。" type="warning" :closable="false"/><el-form :model="payForm" label-width="100px" style="margin-top:16px"><el-form-item label="退款金额"><el-input-number v-model="payForm.amount" :precision="2" :min="0" style="width:100%"/></el-form-item><el-form-item label="退款渠道"><el-input v-model="payForm.channel" maxlength="30" placeholder="如 WECHAT / CASH"/></el-form-item><el-form-item label="外部凭证"><el-input v-model="payForm.externalReference" maxlength="100"/></el-form-item><el-form-item label="完成时间"><el-date-picker v-model="payForm.occurredAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%"/></el-form-item><el-form-item label="登记说明"><el-input v-model="payForm.note" type="textarea" maxlength="1000"/></el-form-item></el-form><template #footer><el-button @click="payVisible=false">取消</el-button><el-button type="primary" :loading="paying" @click="submitPay">确认登记，不发起打款</el-button></template></el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { approveRefund, cancelRefund, createRefund, getEntitlementQuote, listRefunds, reconcileEntitlement, registerRefundPayment, rejectRefund, verifyTransfer, type EntitlementQuote, type Refund } from '@/api/modules/finance'
import type { Entitlement, Order } from '@/api/modules/commercial'
import { useAuthStore } from '@/store/modules/auth'
import { formatMoney, localCalendarDateTime, refundIntent } from '@/utils/financeUi.mjs'

const { entitlements, orders } = defineProps<{ entitlements: Entitlement[]; orders: Order[] }>()
const sourceOrders = computed(() => {
  const entitlement = entitlements.find(e => e.id === quote.value?.entitlementId)
  if (!entitlement) return []
  const opening = orders.find(o => o.id === entitlement.sourceOrderId)
  const candidates = new Map<number, {orderId:number;orderNo:string}>()
  candidates.set(entitlement.sourceOrderId, {orderId:entitlement.sourceOrderId, orderNo:opening?.orderNo || `原订单 #${entitlement.sourceOrderId}`})
  for (const order of orders) {
    if (order.status === 'PAID' && order.venueId === entitlement.venueId && order.productId === entitlement.productId &&
        (order.memberId === entitlement.memberId || order.memberId === opening?.memberId) &&
        (order.id === entitlement.sourceOrderId || order.orderType === 'RENEW')) {
      candidates.set(order.id, {orderId:order.id,orderNo:order.orderNo})
    }
  }
  for (const source of quote.value?.funding || []) candidates.set(source.orderId, {orderId:source.orderId,orderNo:source.orderNo})
  return Array.from(candidates.values())
})
const emit = defineEmits<{ changed: [] }>()
const auth = useAuthStore(); const can=(permission:string)=>auth.userInfo?.permissions?.includes(permission)===true
const refunds=ref<Refund[]>([]), loading=ref(false), failed=ref(false), busyId=ref<number>(), selectedEntitlementId=ref<number>(), quote=ref<EntitlementQuote>(), quoteVisible=ref(false), quoteLoading=ref(false), submitting=ref(false), reconciling=ref(false), verifyingTransfer=ref(false)
const requestReason=ref(''), transferReason=ref(''), transferEvidence=ref(''), applicationIntent=ref<Readonly<{idempotencyKey:string}>>(), payVisible=ref(false), paying=ref(false), payRefund=ref<Refund>()
const reconcileForm=reactive<{reason:string;remainingUses:number|undefined;transferEvidence:string;sources:Array<{orderId:number|undefined;unusedAmount:number|undefined;productType:string;totalUnits:number|undefined;startDate:string|undefined}>}>({reason:'',remainingUses:undefined,transferEvidence:'',sources:[]})
const payForm=reactive({amount:0,channel:'',externalReference:'',occurredAt:'',note:''})
const canRequest=computed(()=>!!requestReason.value.trim()&&!!quote.value&&!quote.value.blockedReasons.length&&!quote.value.activeRefundId)
const load=async()=>{loading.value=true;failed.value=false;try{refunds.value=(await listRefunds()).data}catch{failed.value=true}finally{loading.value=false}}
const money=(amount:number|null|undefined)=>formatMoney(amount); const dateTime=(value?:string)=>value?new Date(value).toLocaleString('zh-CN',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'}):'—'
const statusText=(status:string)=>({PENDING:'待审批',APPROVED:'已批准',REJECTED:'已拒绝',CANCELLED:'已撤回',PAID:'已登记支付'}[status]||status); const statusType=(status:string)=>status==='PAID'?'success':status==='APPROVED'?'primary':status==='PENDING'?'warning':'info'
const isOwn=(row:Refund)=>row.requestedBy===auth.userInfo?.id
const resetQuote=()=>{quote.value=undefined;requestReason.value='';transferReason.value='';transferEvidence.value='';applicationIntent.value=undefined;reconcileForm.reason='';reconcileForm.remainingUses=undefined;reconcileForm.transferEvidence='';reconcileForm.sources.splice(0)}
const openQuote=async()=>{if(!selectedEntitlementId.value)return;quoteLoading.value=true;try{quote.value=(await getEntitlementQuote(selectedEntitlementId.value)).data;try{applicationIntent.value=refundIntent()}catch{ElMessage.error('无法生成安全随机申请编号，请检查浏览器后重试');return}quoteVisible.value=true}catch{}finally{quoteLoading.value=false}}
const startFor=async(entitlementId:number)=>{selectedEntitlementId.value=entitlementId;await openQuote()}
const addSource=()=>reconcileForm.sources.push({orderId:undefined,unusedAmount:undefined,productType:'',totalUnits:undefined,startDate:undefined})
const submitReconcile=async()=>{if(!quote.value||!reconcileForm.reason.trim()||!reconcileForm.sources.length)return ElMessage.warning('请填写核对原因并逐来源添加订单与未使用金额'); if(reconcileForm.sources.some(source=>!source.orderId||source.unusedAmount==null))return ElMessage.warning('每个来源都必须选择订单并填写凭证中的未使用金额');reconciling.value=true;try{quote.value=(await reconcileEntitlement(quote.value.entitlementId,{version:quote.value.version,remainingUses:reconcileForm.remainingUses??null,reason:reconcileForm.reason.trim(),transferEvidence:reconcileForm.transferEvidence.trim()||undefined,sources:reconcileForm.sources.map(source=>({orderId:source.orderId!,unusedAmount:source.unusedAmount!,productType:source.productType||undefined,totalUnits:source.totalUnits,startDate:source.startDate}))})).data;ElMessage.success('核对结果已由服务端更新，请确认报价后申请')}catch{}finally{reconciling.value=false}}
const submitTransferVerification=async()=>{if(!quote.value||!transferReason.value.trim()||!transferEvidence.value.trim())return ElMessage.warning('请填写转卡核实原因和可核验依据');verifyingTransfer.value=true;try{quote.value=(await verifyTransfer(quote.value.entitlementId,{version:quote.value.version,reason:transferReason.value.trim(),transferEvidence:transferEvidence.value.trim()})).data;ElMessage.success('转卡受领依据已提交服务端核实')}catch{}finally{verifyingTransfer.value=false}}
const submitRequest=async()=>{if(!quote.value||!applicationIntent.value||!canRequest.value||submitting.value)return;submitting.value=true;try{await createRefund({entitlementId:quote.value.entitlementId,version:quote.value.version,reason:requestReason.value.trim(),idempotencyKey:applicationIntent.value.idempotencyKey});ElMessage.success('退款申请已提交');quoteVisible.value=false;await load();emit('changed')}catch{}finally{submitting.value=false}}
const decide=async(row:Refund, action:'approve'|'reject'|'cancel')=>{const labels={approve:'批准',reject:'拒绝',cancel:'撤回'};try{const {value}=await ElMessageBox.prompt(`请填写${labels[action]}原因（必填）。`,`${labels[action]}退款`,{inputValidator:value=>!!value?.trim()&&value.trim().length<=1000||'请填写 1–1000 字原因'});busyId.value=row.id;if(action==='approve')await approveRefund(row.id,value.trim());else if(action==='reject')await rejectRefund(row.id,value.trim());else await cancelRefund(row.id,value.trim());ElMessage.success(`已${labels[action]}`);await load();emit('changed')}catch{}finally{busyId.value=undefined}}
const openPay=(row:Refund)=>{if(row.status==='PAID')return;payRefund.value=row;Object.assign(payForm,{amount:Number(row.amount),channel:'',externalReference:'',occurredAt:localCalendarDateTime(),note:''});payVisible.value=true}
const submitPay=async()=>{if(!payRefund.value||paying.value)return;if(!payForm.channel.trim()||!payForm.externalReference.trim()||!payForm.occurredAt||!payForm.note.trim())return ElMessage.warning('请完整填写线下完成渠道、凭证、时间和登记说明');paying.value=true;try{await registerRefundPayment(payRefund.value.id,{...payForm,channel:payForm.channel.trim(),externalReference:payForm.externalReference.trim(),note:payForm.note.trim()});ElMessage.success('已登记线下退款；未发起真实打款');payVisible.value=false;await load();emit('changed')}catch{}finally{paying.value=false}}
onMounted(load); defineExpose({refresh:load,startFor})
</script>

<style scoped>
.refund-panel{padding:20px;border:1px solid #e3eaf5;border-radius:18px;background:#fff}.heading{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:16px}.heading span{font-size:10px;font-weight:800;letter-spacing:2px;color:#3474ff}.heading h2{margin:7px 0;color:#17243c}.heading p,.refund-panel small,.muted{display:block;color:#8792a5;font-size:12px;line-height:1.6}.quote-section{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin-top:22px;padding-top:20px;border-top:1px solid #edf0f5}.quote-section h3,.quote-section p{width:100%;margin:0}.quote-section p{color:#8792a5;font-size:12px}.source-list{width:100%}.source-row{display:grid;grid-template-columns:1.2fr 1fr 1.1fr 1fr 1.1fr auto;gap:7px;margin-bottom:8px}@media(max-width:900px){.source-row{grid-template-columns:1fr 1fr}.quote-section{align-items:stretch}}
</style>
