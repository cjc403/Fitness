<template>
  <section class="finance-report" v-loading="loading">
    <div class="heading"><div><span>FINANCE RECONCILIATION</span><h2>现金报表与对账</h2><p>收退款按交易发生日统计；服务消耗含报表时只读预估，不会写入或修复账务。</p></div><div class="actions"><el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" :clearable="false"/><el-button :loading="loading" @click="load">查询</el-button><el-button type="primary" :loading="exporting" @click="exportExcel">下载 Excel</el-button></div></div>
    <el-alert v-if="failed" title="报表加载失败，未显示为零或空数据。" type="error" show-icon :closable="false"><template #default><el-button link type="primary" @click="load">重试</el-button></template></el-alert>
    <template v-else-if="report"><div class="metrics"><article><small>收款</small><strong>¥{{ money(report.receipts) }}</strong></article><article><small>退款</small><strong>¥{{ money(report.refunds) }}</strong></article><article><small>净现金</small><strong>¥{{ money(report.netCash) }}</strong></article><article><small>未使用服务价值</small><strong>¥{{ money(report.unusedVerifiedValue) }}</strong><em>截至 {{ report.valuationDate }}（含只读预估）</em></article><article class="warning"><small>待核对权益</small><strong>{{ report.unverifiedEntitlements }}</strong></article><article><small>服务消耗</small><strong>¥{{ money(report.serviceConsumed) }}</strong><em>含报表时只读预估</em></article></div>
      <h3>渠道现金</h3><el-table :data="report.channels" stripe><el-table-column prop="channel" label="渠道"/><el-table-column label="收款"><template #default="{row}">¥{{ money(row.receipts) }}</template></el-table-column><el-table-column label="退款"><template #default="{row}">¥{{ money(row.refunds) }}</template></el-table-column><el-table-column label="净现金"><template #default="{row}">¥{{ money(row.netCash) }}</template></el-table-column></el-table>
      <h3>完整性差异</h3><el-alert v-if="!report.differences.length" title="当前范围内未返回对账差异。" type="success" :closable="false"/><el-table v-else :data="report.differences" stripe><el-table-column prop="type" label="类型" width="170"><template #default="{row}"><el-tag type="warning">{{ row.type }}</el-tag></template></el-table-column><el-table-column label="原记录" min-width="200"><template #default="{row}">{{ ({ENTITLEMENT:'权益',ORDER:'订单',PAYMENT_TRANSACTION:'支付流水',REFUND:'退款单'} as Record<string,string>)[row.subjectType] || row.subjectType }} #{{ row.subjectId }}<small v-if="row.orderId"> · 订单 #{{ row.orderId }}</small><small v-if="row.paymentTransactionId"> · 流水 #{{ row.paymentTransactionId }}</small><small v-if="row.refundId"> · 退款单 #{{ row.refundId }}</small></template></el-table-column><el-table-column label="账户 / 事件"><template #default="{row}">{{ row.accountBalance==null?'—':`¥${money(row.accountBalance)}` }} / {{ row.eventBalance==null?'—':`¥${money(row.eventBalance)}` }}</template></el-table-column><el-table-column prop="message" label="说明" min-width="280"/></el-table>
    </template>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { exportFinanceReportExcel, getFinanceReport, type FinanceReport } from '@/api/modules/finance'
import { formatMoney, localCalendarDate } from '@/utils/financeUi.mjs'

const today=new Date(); const start=new Date(today.getFullYear(),today.getMonth(),1)
const range=ref<[string,string]>([localCalendarDate(start),localCalendarDate(today)]), report=ref<FinanceReport>(),loading=ref(false),failed=ref(false),exporting=ref(false)
const money=(amount:number|null|undefined)=>formatMoney(amount)
const load=async()=>{if(!range.value?.[0]||!range.value?.[1])return;loading.value=true;failed.value=false;try{report.value=(await getFinanceReport({from:range.value[0],to:range.value[1]})).data}catch{failed.value=true;report.value=undefined}finally{loading.value=false}}
const exportExcel = async () => {
  if (!range.value?.[0] || !range.value?.[1] || exporting.value) return
  exporting.value = true
  try {
    const blob = await exportFinanceReportExcel({ from: range.value[0], to: range.value[1] })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `财务对账报表_${range.value[0]}_${range.value[1]}.xlsx`
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success('Excel 已下载，可直接打开查看')
  } catch {
    // 失败时拦截器已经提示过，这里不重复打扰
  } finally {
    exporting.value = false
  }
}
onMounted(load)
defineExpose({ refresh: load })
</script>

<style scoped>
.finance-report{padding:20px;border:1px solid #e3eaf5;border-radius:18px;background:#fff}.heading{display:flex;justify-content:space-between;align-items:flex-start;gap:14px;margin-bottom:18px}.heading span{font-size:10px;font-weight:800;letter-spacing:2px;color:#3474ff}.heading h2{margin:7px 0;color:#17243c}.heading p{margin:0;color:#8792a5;font-size:12px;line-height:1.6}.actions{display:flex;gap:8px;flex-wrap:wrap;justify-content:flex-end}.metrics{display:grid;grid-template-columns:repeat(6,1fr);gap:10px;margin-bottom:24px}.metrics article{padding:14px;border-radius:12px;background:#f5f8fd}.metrics small,.metrics strong,.metrics em{display:block}.metrics small,.metrics em{color:#7e8ca2;font-size:11px}.metrics strong{margin:7px 0;color:#1d3155;font-size:19px}.metrics .warning strong{color:#d37a20}.finance-report h3{margin:20px 0 10px;color:#22324e;font-size:15px}@media(max-width:980px){.metrics{grid-template-columns:repeat(3,1fr)}.heading{display:block}.actions{justify-content:flex-start;margin-top:12px}}@media(max-width:560px){.metrics{grid-template-columns:repeat(2,1fr)}}
</style>
