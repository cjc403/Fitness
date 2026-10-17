<template>
  <div class="cash-center" v-if="canRead && active">
    <WorkspaceHero compact>
      <template #heading><span>门店资金核对</span><h1>交班与日结</h1><p>逐渠道点验实际收款与退款，交班后由另一位管理者复核。营业日按上海开班日期，与资金发生日不同。</p></template>
      <template #actions><div class="cash-filters"><el-select v-if="isHeadquarters" v-model="venueId" clearable placeholder="全部门店" aria-label="选择门店" :disabled="saving" @change="search"><el-option v-for="v in venues" :key="v.id" :value="v.id" :label="v.name" /></el-select><el-date-picker v-model="businessDate" type="date" value-format="YYYY-MM-DD" placeholder="营业日" aria-label="营业日" :clearable="false" @change="search" /><el-button type="primary" plain :loading="loading" @click="load">刷新</el-button><el-button v-if="canWrite" type="primary" plain :disabled="saving || loading || !data || !!data.currentShift || (isHeadquarters && !venueId)" @click="openAction('open')">开班</el-button></div></template>
      <template #footer>首班从开班后的新登记流水开始；后续班次包含上一交班后、开班前待交班流水。此处核对不产生付款或退款。</template>
    </WorkspaceHero>
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" show-icon />
    <template v-if="data">
      <div class="metrics"><div v-for="item in metricItems" :key="item.key" class="metric"><small>{{ item.label }}</small><strong>{{ item.amount ? money(data.summary[item.key]) : data.summary[item.key] }}</strong></div></div>
      <section class="panel"><h2>当前未交班</h2><p class="hint">独立显示所选门店任何营业日的未交班班次；未交班实时预览不进入已交班日汇总。</p><div v-if="data.currentShift" class="current"><div><strong>{{ data.currentShift.shiftNo }} · {{ data.currentShift.venueName }}</strong><p>营业日 {{ data.currentShift.businessDate }} · {{ data.currentShift.openedName }} · {{ dateText(data.currentShift.openedAt) }}</p></div><div><el-button type="primary" plain @click="openDetail(data.currentShift)">查看实时预览</el-button><el-button v-if="canSubmit(data.currentShift)" type="primary" plain :disabled="saving" @click="openAction('submit', data.currentShift)">核对并交班</el-button></div></div><el-empty v-else :description="isHeadquarters && !venueId ? '选择具体门店查看当前班次' : '本店当前没有未交班班次'" :image-size="45" /></section>
      <section class="panel"><h2>已交班渠道汇总 · {{ data.businessDate }}</h2><p class="hint">{{ dailyShiftLabel }}；未交班 {{ data.summary.openCount }}，待复核 {{ data.summary.pendingReviewCount }}，退回 {{ data.summary.returnedCount }}，通过 {{ data.summary.approvedCount }}。状态筛选和分页不改变汇总范围。</p><el-table :data="data.channels"><el-table-column label="渠道" min-width="145"><template #default="{ row }">{{ channelText(row.channel) }}</template></el-table-column><el-table-column v-for="col in channelColumns" :key="col.key" :label="col.label" min-width="115"><template #default="{ row }">{{ money(row[col.key]) }}</template></el-table-column></el-table></section>
      <section class="panel"><div class="panel-heading"><h2>班次记录</h2><el-select v-model="status" aria-label="班次状态" @change="search"><el-option value="ALL" label="全部状态"/><el-option v-for="s in statuses" :key="s.value" :value="s.value" :label="s.label"/></el-select></div><el-table :data="data.shifts.records" v-loading="loading"><el-table-column prop="shiftNo" label="班次" min-width="160"/><el-table-column prop="venueName" label="门店" min-width="110"/><el-table-column prop="businessDate" label="营业日" min-width="110"/><el-table-column label="状态" width="95"><template #default="{ row }"><el-tag>{{ statusText(row.status) }}</el-tag></template></el-table-column><el-table-column label="系统净额" width="115"><template #default="{ row }">{{ money(row.systemNet) }}</template></el-table-column><el-table-column label="实际净额" width="115"><template #default="{ row }">{{ money(row.actualNet) }}</template></el-table-column><el-table-column label="操作" min-width="230"><template #default="{ row }"><el-button type="primary" plain size="small" @click="openDetail(row)" data-agent-type="cash-shift" :data-agent-id="row.id" :data-agent-label="row.name || row.realName || row.title || row.memberName || ''">详情</el-button><el-button v-if="canSubmit(row)" type="primary" plain size="small" @click="openAction('submit', row)" data-agent-type="cash-shift" :data-agent-id="row.id" :data-agent-label="row.name || row.realName || row.title || row.memberName || ''">{{ row.status === 'RETURNED' ? '修正并重交' : '交班' }}</el-button><el-button v-if="canReview(row)" type="primary" plain size="small" @click="openAction('review', row)" data-agent-type="cash-shift" :data-agent-id="row.id" :data-agent-label="row.name || row.realName || row.title || row.memberName || ''">复核</el-button></template></el-table-column></el-table><el-pagination v-model:current-page="page" :page-size="20" :total="data.shifts.total" layout="total, prev, pager, next" @current-change="load" /></section>
    </template>
    <el-dialog v-model="detailDialog" title="班次详情与资金快照" width="1080px" :before-close="closeDetail" destroy-on-close>
      <div v-loading="detailLoading"><el-alert v-if="detailError" :title="detailError" type="error" :closable="false"/><template v-if="detailData"><div class="detail-heading"><h3>{{ detailData.shift.shiftNo }} · {{ detailData.shift.venueName }}</h3><el-tag>{{ statusText(detailData.shift.status) }}</el-tag><el-button type="primary" plain @click="openDetail(detailData.shift)">重新载入详情</el-button></div><p>营业日 {{ detailData.shift.businessDate }} · 开班 {{ dateText(detailData.shift.openedAt) }} · 关闭 {{ dateText(detailData.shift.closedAt) }}</p><p class="hint">{{ detailData.shift.status === 'OPEN' ? '实时预览，交班前请重新核对' : '交班时固定快照，之后新增流水归下一班；退回修正也使用原快照' }}。流水 {{ detailData.preview.paymentCount }} 笔，含开班前待交班 {{ detailData.preview.carriedOverCount }} 笔；首班排除历史 {{ detailData.preview.historicalExcludedCount }} 笔。</p><p v-if="detailData.shift.reviewReason">复核说明：{{ detailData.shift.reviewReason }}</p><p v-if="detailData.shift.differenceReason">差异说明：{{ detailData.shift.differenceReason }}</p><p v-if="detailData.shift.handoverNote">交班备注：{{ detailData.shift.handoverNote }}</p><el-table :data="detailData.channels"><el-table-column label="渠道" min-width="140"><template #default="{ row }">{{ channelText(row.channel) }}</template></el-table-column><el-table-column v-for="col in channelColumns" :key="col.key" :label="col.label" min-width="115"><template #default="{ row }">{{ money(row[col.key]) }}</template></el-table-column></el-table><h3>逐笔流水</h3><el-alert v-if="paymentError" :title="paymentError" type="error" :closable="false"/><el-table :data="paymentData.records" v-loading="paymentLoading"><el-table-column prop="transactionNo" label="流水号" min-width="170"/><el-table-column prop="orderId" label="订单ID" width="90"/><el-table-column label="类型" width="75"><template #default="{ row }">{{ row.transactionType === 'REFUND' ? '退款' : '收款' }}</template></el-table-column><el-table-column label="渠道" min-width="130"><template #default="{ row }">{{ channelText(row.channel) }}</template></el-table-column><el-table-column label="金额" width="110"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column><el-table-column label="发生时间（上海）" min-width="180"><template #default="{ row }">{{ dateText(row.occurredAt) }}</template></el-table-column><el-table-column label="班前登记" width="90"><template #default="{ row }">{{ row.carriedOver ? '是' : '否' }}</template></el-table-column></el-table><el-pagination :current-page="paymentData.page" :page-size="100" :total="paymentData.total" layout="total, prev, pager, next" @current-change="loadPayments"/><h3>交班与复核审计</h3><div v-for="a in detailData.audit" :key="a.id" class="audit"><strong>{{ actionText(a.action) }} · {{ a.actorName }} · {{ dateText(a.createdAt) }}</strong><p v-if="a.content">{{ a.content }}</p><p v-for="(line, i) in auditChanges(a.changesJson)" :key="i">{{ line }}</p></div><el-empty v-if="!detailData.audit.length" description="暂无审计记录" :image-size="40"/></template></div>
    </el-dialog>
    <el-dialog v-model="operationDialog" :title="operation === 'open' ? '开班' : operation === 'review' ? '独立复核' : current?.status === 'RETURNED' ? '修正并重新交班' : '实际点验并交班'" :width="operation === 'submit' ? '1040px' : '620px'" :before-close="closeOperation" :close-on-click-modal="false" :close-on-press-escape="!saving" destroy-on-close>
      <div v-loading="detailLoading"><el-alert v-if="operationError" :title="operationError" type="error" :closable="false" show-icon/><el-button v-if="conflict || detailError" type="primary" plain :disabled="saving || detailLoading" @click="reloadConflict">重新载入并核对</el-button><el-form :disabled="saving || detailLoading || conflict" label-position="top"><template v-if="operation === 'open'"><p>首班从开班后的新登记流水开始，不追溯旧资金；后续班次包含上班关闭后的待交班登记。营业日使用上海今天，不能回填过去。</p><el-form-item label="开班备注（选填，最多1000字）"><el-input v-model="form.note" type="textarea" maxlength="1000" show-word-limit/></el-form-item></template><template v-else-if="operation === 'submit' && detailData"><p>{{ current?.venueName }} · {{ current?.shiftNo }} · {{ current?.status === 'RETURNED' ? '使用原交班资金快照' : '本次实时预览' }}</p><p class="hint">登记 {{ detailData.preview.paymentCount }} 笔，含班前 {{ detailData.preview.carriedOverCount }} 笔，首班排除历史 {{ detailData.preview.historicalExcludedCount }} 笔。每行实际收款与退款必须明确输入，零也请输入0。</p><el-alert v-if="form.channels.some(c => c.channel === 'SELF_SERVICE')" title="自助购买目前仅登记，未接支付；请按真实收退款情况点验自助渠道。" type="info" :closable="false"/><el-table :data="form.channels"><el-table-column label="渠道" min-width="150"><template #default="{ row }">{{ channelText(row.channel) }}</template></el-table-column><el-table-column label="系统收款" width="110"><template #default="{ row }">{{ money(row.systemReceipts) }}</template></el-table-column><el-table-column label="系统退款" width="110"><template #default="{ row }">{{ money(row.systemRefunds) }}</template></el-table-column><el-table-column label="实际收款（元）" min-width="145"><template #default="{ row }"><el-input v-model="row.actualReceipts" :aria-label="`${channelText(row.channel)}实际收款`" placeholder="明确输入，允许0" inputmode="decimal" :disabled="saving || conflict"/></template></el-table-column><el-table-column label="实际退款（元）" min-width="145"><template #default="{ row }"><el-input v-model="row.actualRefunds" :aria-label="`${channelText(row.channel)}实际退款`" placeholder="明确输入，允许0" inputmode="decimal" :disabled="saving || conflict"/></template></el-table-column><el-table-column label="收款/退款差额" min-width="145"><template #default="{ row }">{{ differenceText(row) }}</template></el-table-column></el-table><p class="totals">系统收款 {{ money(detailData.preview.systemReceipts) }}，系统退款 {{ money(detailData.preview.systemRefunds) }}，系统净额 {{ money(detailData.preview.systemNet) }}<br/>实际收款 {{ centsText(formTotals.actualReceipts) }}，实际退款 {{ centsText(formTotals.actualRefunds) }}，实际净额 {{ centsText(formTotals.actualNet) }}，净差额 {{ centsText(formTotals.netDifference) }}</p><el-form-item :label="formTotals.hasDifference ? '差异说明（必填；即使净差额抵消为0也必须说明）' : '差异说明（无差异可留空）'"><el-input v-model="form.differenceReason" type="textarea" maxlength="1000" show-word-limit/></el-form-item><el-form-item label="交班备注（选填，最多1000字）"><el-input v-model="form.handoverNote" type="textarea" maxlength="1000" show-word-limit/></el-form-item></template><template v-else-if="operation === 'review'"><p>核对 {{ current?.shiftNo }} 的固定资金快照与实际金额。最后提交人不能自审；复核不会产生任何资金流水。</p><el-radio-group v-model="form.decision"><el-radio value="APPROVE">通过</el-radio><el-radio value="RETURN">退回修正</el-radio></el-radio-group><el-form-item :label="form.decision === 'RETURN' ? '退回原因（必填）' : '复核说明（选填）'"><el-input v-model="form.reason" type="textarea" maxlength="1000" show-word-limit/></el-form-item><el-button type="primary" plain @click="showReviewDetail">查看固定快照与审计</el-button></template></el-form></div>
      <template #footer><el-button :disabled="saving" @click="closeOperation()">取消</el-button><el-button type="primary" plain :loading="saving" :disabled="!actionReady || conflict || detailLoading" @click="submitOperation">{{ operation === 'review' ? '确认复核' : operation === 'open' ? '确认开班' : '确认交班' }}</el-button></template>
    </el-dialog>
  </div><el-empty v-else description="当前会话没有交班查看权限，请重新进入页面"/>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import WorkspaceHero from '@/components/WorkspaceHero.vue'
import { useAuthStore } from '@/store/modules/auth'
import { getCashWorkbench, getCashDetail, getCashPayments, openCashShift, submitCashShift, reviewCashShift } from '@/api/modules/cashShifts'
import type { CashWorkbench, CashDetail, CashShift, CashPaymentPage, CashSummary, ShiftStatus, SubmitCashRequest, ReviewCashRequest } from '@/api/modules/cashShifts'

const auth = useAuthStore(), active = ref(true)
const scopeText = () => JSON.stringify([auth.userInfo?.id, auth.userInfo?.role, auth.userInfo?.organizationId, (auth.userInfo as any)?.venueId, auth.userInfo?.permissions, auth.userInfo?.mustChangePassword])
const initialGeneration = auth.sessionGeneration, initialScope = scopeText()
const allowedRole = computed(() => ['RECEPTION','MANAGER','SUPER_ADMIN'].includes(auth.userInfo?.role || ''))
const has = (permission: string) => auth.userInfo?.permissions?.includes(permission) || false
const canRead = computed(() => allowedRole.value && has('CASH_SHIFT_READ'))
const canWrite = computed(() => canRead.value && has('CASH_SHIFT_WRITE'))
const isHeadquarters = computed(() => auth.userInfo?.role === 'SUPER_ADMIN')
const manager = () => ['MANAGER','SUPER_ADMIN'].includes(auth.userInfo?.role || '')
const sameVenue = (row: CashShift) => isHeadquarters.value || row.venueId === auth.userInfo?.organizationId
const currentSession = () => active.value && auth.sessionGeneration === initialGeneration && scopeText() === initialScope && canRead.value
const canSubmit = (row: CashShift) => currentSession() && canWrite.value && sameVenue(row) && ['OPEN','RETURNED'].includes(row.status) && (manager() || row.openedBy === auth.userInfo?.id || (row.status === 'RETURNED' && row.submittedBy === auth.userInfo?.id))
const canReview = (row: CashShift) => currentSession() && manager() && has('CASH_SHIFT_REVIEW') && sameVenue(row) && row.status === 'SUBMITTED' && row.submittedBy !== auth.userInfo?.id
const shanghaiToday = () => new Date(Date.now() + 8 * 3600000).toISOString().slice(0,10)
const businessDate = ref(shanghaiToday()), venueId = ref<number>(), status = ref<ShiftStatus | 'ALL'>('ALL'), page = ref(1)
const data = ref<CashWorkbench | null>(null), venues = ref<{ id: number; name: string }[]>([]), loading = ref(false), loadError = ref('')
const dailyShiftLabel = computed(() => data.value ? `当日共${data.value.summary.shiftCount}班` : '当日班次尚未载入')
const detailData = ref<CashDetail | null>(null), current = ref<CashShift | null>(null), detailDialog = ref(false), detailLoading = ref(false), detailError = ref('')
const paymentData = ref<CashPaymentPage>({ records: [], total: 0, page: 1, size: 100 }), paymentLoading = ref(false), paymentError = ref('')
type Action = 'open' | 'submit' | 'review'
type FormChannel = { channel: string; systemReceipts: number; systemRefunds: number; actualReceipts: string; actualRefunds: string }
const operation = ref<Action>('open'), operationDialog = ref(false), operationError = ref(''), saving = ref(false), conflict = ref(false)
const blankForm = () => ({ note: '', differenceReason: '', handoverNote: '', decision: 'APPROVE' as 'APPROVE' | 'RETURN', reason: '', channels: [] as FormChannel[] })
const form = reactive(blankForm())
let listSequence = 0, detailSequence = 0, paymentSequence = 0, actionSequence = 0, scopeSequence = 0
let retryIntent: { fingerprint: string; key: string } | null = null
const clearDetail = () => { detailSequence++; paymentSequence++; actionSequence++; detailData.value = null; current.value = null; detailDialog.value = false; operationDialog.value = false; detailLoading.value = false; paymentLoading.value = false; detailError.value = ''; paymentError.value = ''; operationError.value = ''; conflict.value = false; paymentData.value = { records: [], total: 0, page: 1, size: 100 }; Object.assign(form, blankForm()); retryIntent = null }
const invalidateSession = () => { active.value = false; scopeSequence++; listSequence++; clearDetail(); data.value = null; venues.value = []; venueId.value = undefined; loading.value = false; loadError.value = ''; saving.value = false; businessDate.value = ''; page.value = 1; status.value = 'ALL' }
watch(() => [auth.sessionGeneration, scopeText()], () => { if (auth.sessionGeneration !== initialGeneration || scopeText() !== initialScope) invalidateSession() }, { flush: 'sync' })
watch(venueId, () => { scopeSequence++; listSequence++; clearDetail(); data.value = null; loading.value = false; saving.value = false; loadError.value = '' }, { flush: 'sync' })
onUnmounted(invalidateSession)
const statuses = [{ value: 'OPEN', label: '未交班' }, { value: 'SUBMITTED', label: '待复核' }, { value: 'RETURNED', label: '已退回' }, { value: 'APPROVED', label: '已通过' }]
const statusText = (s: string) => statuses.find(x => x.value === s)?.label || s
const actionText = (s: string) => ({ OPEN: '开班', SUBMIT: '交班', RESUBMIT: '修正重交', APPROVE: '复核通过', RETURN: '退回修正' }[s] || s)
const channelText = (s: string) => ({ CASH: '现金', WECHAT: '微信', ALIPAY: '支付宝', CARD: '银行卡', SELF_SERVICE: '自助登记', UNKNOWN: '未标明渠道' }[s] || s)
const money = (n?: number | null) => n === null || n === undefined ? '—' : n.toFixed(2)
const dateText = (s?: string | null) => s ? s.slice(0,19).replace('T', ' ') : '—'
const cents = (s: string) => { if (!/^(?:0|[1-9]\d{0,9})(?:\.\d{1,2})?$/.test(s)) return null; const [whole, fraction = ''] = s.split('.'); const n = Number(whole) * 100 + Number(fraction.padEnd(2,'0')); return n <= 999999999999 ? n : null }
const systemCents = (n: number) => Math.round(n * 100)
const centsText = (n: number | null) => n === null ? '待填写' : (n / 100).toFixed(2)
const formTotals = computed(() => {
  let ar = 0, af = 0, sr = 0, sf = 0, valid = form.channels.length > 0, hasDifference = false
  for (const c of form.channels) { const a = cents(c.actualReceipts), b = cents(c.actualRefunds); valid = valid && a !== null && b !== null; ar += a || 0; af += b || 0; sr += systemCents(c.systemReceipts); sf += systemCents(c.systemRefunds); if (a !== null && b !== null && (a !== systemCents(c.systemReceipts) || b !== systemCents(c.systemRefunds))) hasDifference = true }
  return { valid, hasDifference, actualReceipts: valid ? ar : null, actualRefunds: valid ? af : null, actualNet: valid ? ar - af : null, netDifference: valid ? ar - af - sr + sf : null }
})
const differenceText = (c: FormChannel) => { const a = cents(c.actualReceipts), b = cents(c.actualRefunds); return a === null || b === null ? '待填写' : `${centsText(a - systemCents(c.systemReceipts))} / ${centsText(b - systemCents(c.systemRefunds))}` }
const metricItems: { key: keyof CashSummary; label: string; amount?: boolean }[] = [{ key: 'systemReceipts', label: '系统收款（元）', amount: true }, { key: 'systemRefunds', label: '系统退款（元）', amount: true }, { key: 'systemNet', label: '系统净额（元）', amount: true }, { key: 'actualNet', label: '实际净额（元）', amount: true }, { key: 'netDifference', label: '净差额（元）', amount: true }, { key: 'approvedNet', label: '已复核净额（元）', amount: true }]
const channelColumns = [{ key: 'systemReceipts', label: '系统收款' }, { key: 'systemRefunds', label: '系统退款' }, { key: 'systemNet', label: '系统净额' }, { key: 'actualReceipts', label: '实际收款' }, { key: 'actualRefunds', label: '实际退款' }, { key: 'actualNet', label: '实际净额' }, { key: 'netDifference', label: '净差额' }]
const safeError = (e: any, fallback: string) => { const message = String(e?.response?.data?.message || e?.message || ''); return e?.response?.status !== 500 && message.length <= 160 && /[\u4e00-\u9fff]/.test(message) && !/SQL|Exception|constraint|Jdbc|Hibernate|password|token|[<>]/i.test(message) ? message : fallback }
const isConflict = (e: any) => e?.code === 2600 || e?.response?.data?.code === 2600 || e?.response?.status === 409
const load = async () => {
  if (!currentSession()) return
  const sequence = ++listSequence, scope = scopeSequence
  const params = { ...(venueId.value ? { venueId: venueId.value } : {}), businessDate: businessDate.value || shanghaiToday(), status: status.value, page: page.value, size: 20 }
  const valid = () => currentSession() && sequence === listSequence && scope === scopeSequence
  loading.value = true; loadError.value = ''; data.value = null
  try { const result = await getCashWorkbench(params, { isRequestCurrent: valid }); if (valid()) { data.value = result.data; venues.value = [...result.data.venues] } }
  catch (e) { if (valid()) loadError.value = safeError(e, '交班数据加载失败，请重试') }
  finally { if (valid()) loading.value = false }
}
const search = () => { page.value = 1; return load() }
const fetchDetail = async (row: CashShift, forAction = false) => {
  const sequence = ++detailSequence, scope = scopeSequence
  current.value = Object.freeze({ ...row }); detailData.value = null; detailError.value = ''; detailLoading.value = true; paymentData.value = { records: [], total: 0, page: 1, size: 100 }; paymentSequence++
  const valid = () => currentSession() && sequence === detailSequence && scope === scopeSequence && current.value?.id === row.id
  try { const result = await getCashDetail(row.id, { isRequestCurrent: valid }); if (valid()) { detailData.value = result.data; current.value = Object.freeze({ ...result.data.shift }); paymentData.value = { records: result.data.payments, total: result.data.paymentTotal, page: 1, size: 100 }; if (forAction && operation.value === 'submit') fillBlankActuals(result.data) } }
  catch (e) { if (valid()) { detailError.value = safeError(e, '班次详情加载失败，请重新载入'); if (forAction) operationError.value = detailError.value } }
  finally { if (valid()) detailLoading.value = false }
}
const openDetail = async (row: CashShift) => { if (!currentSession() || saving.value) return; operationDialog.value = false; clearDetail(); detailDialog.value = true; return fetchDetail(row) }
const closeDetail = (done: () => void) => { if (saving.value) return; detailDialog.value = false; if (!operationDialog.value) clearDetail(); done() }
const loadPayments = async (pageNumber: number) => {
  if (!currentSession() || !detailData.value || !current.value) return
  const sequence = ++paymentSequence, detailSeq = detailSequence, scope = scopeSequence, id = current.value.id
  const valid = () => currentSession() && sequence === paymentSequence && detailSeq === detailSequence && scope === scopeSequence && current.value?.id === id
  paymentLoading.value = true; paymentError.value = ''; paymentData.value.records = []
  try { const result = await getCashPayments(id, pageNumber, { isRequestCurrent: valid }); if (valid()) paymentData.value = result.data }
  catch (e) { if (valid()) paymentError.value = safeError(e, '流水页加载失败，请重试') }
  finally { if (valid()) paymentLoading.value = false }
}
const fillBlankActuals = (d: CashDetail) => { form.channels = d.channels.map(c => ({ channel: c.channel, systemReceipts: c.systemReceipts, systemRefunds: c.systemRefunds, actualReceipts: '', actualRefunds: '' })); form.differenceReason = ''; form.handoverNote = '' }
const actionReady = computed(() => currentSession() && (operation.value === 'open' ? canWrite.value && !!data.value && !data.value.currentShift && (!isHeadquarters.value || !!venueId.value) : !!current.value && !!detailData.value && (operation.value === 'submit' ? canSubmit(current.value) : canReview(current.value))))
const openAction = async (type: Action, row?: CashShift) => {
  if (!currentSession() || saving.value || (type === 'open' ? !canWrite.value || !data.value || !!data.value.currentShift || (isHeadquarters.value && !venueId.value) : !row || !(type === 'submit' ? canSubmit(row) : canReview(row)))) return
  clearDetail(); operation.value = type; operationDialog.value = true
  if (row) await fetchDetail(row, true)
}
const closeOperation = (done?: () => void) => { if (saving.value) return; clearDetail(); done?.() }
const showReviewDetail = () => { if (detailData.value) detailDialog.value = true }
const reloadConflict = async () => {
  if (!currentSession() || saving.value || !operationDialog.value) return
  const action = actionSequence, scope = scopeSequence, type = operation.value, id = current.value?.id
  const sameAction = () => currentSession() && operationDialog.value && action === actionSequence && scope === scopeSequence && type === operation.value && id === current.value?.id
  retryIntent = null; conflict.value = true; operationError.value = ''; Object.assign(form, blankForm())
  if (current.value) {
    const pending = fetchDetail(current.value, true), detail = detailSequence
    await pending
    if (sameAction() && detail === detailSequence && detailData.value?.shift.id === id && !detailError.value) conflict.value = false
  } else {
    const pending = load(), list = listSequence
    await pending
    if (sameAction() && list === listSequence && data.value && !loadError.value) conflict.value = false
  }
}
const submitOperation = async () => {
  if (!currentSession() || saving.value || conflict.value || detailLoading.value || !operationDialog.value || !actionReady.value) return
  let body: any, id = current.value?.id, type = operation.value
  operationError.value = ''
  if ([form.note, form.differenceReason, form.handoverNote, form.reason].some(s => s.trim().length > 1000)) { operationError.value = '说明或备注最多1000字'; return }
  if (type === 'open') body = { ...(venueId.value ? { venueId: venueId.value } : {}), note: form.note.trim() }
  else if (type === 'submit') {
    if (!formTotals.value.valid) { operationError.value = '每个渠道的实际收款和退款都必须明确输入；非负、最多两位小数，单值不超过9999999999.99元'; return }
    if (formTotals.value.hasDifference && !form.differenceReason.trim()) { operationError.value = '有收款或退款差异时必须填写说明，净差额抵消为0也需要'; return }
    body = { expectedVersion: current.value!.version, ledgerFingerprint: detailData.value!.preview.ledgerFingerprint, channels: form.channels.map(c => Object.freeze({ channel: c.channel, actualReceipts: cents(c.actualReceipts)! / 100, actualRefunds: cents(c.actualRefunds)! / 100 })), differenceReason: form.differenceReason.trim(), handoverNote: form.handoverNote.trim() }; Object.freeze(body.channels)
  } else { if (!['APPROVE','RETURN'].includes(form.decision) || (form.decision === 'RETURN' && !form.reason.trim())) { operationError.value = '退回必须填写原因'; return }; body = { expectedVersion: current.value!.version, decision: form.decision, reason: form.reason.trim() } }
  const fingerprint = JSON.stringify([type, id, body]); if (!retryIntent || retryIntent.fingerprint !== fingerprint) retryIntent = { fingerprint, key: crypto.randomUUID() }
  body = Object.freeze({ ...body, idempotencyKey: retryIntent.key })
  const sequence = actionSequence, scope = scopeSequence
  const valid = () => currentSession() && sequence === actionSequence && scope === scopeSequence && operationDialog.value
  saving.value = true
  try { const result = await (type === 'open' ? openCashShift(body, { isRequestCurrent: valid }) : type === 'submit' ? submitCashShift(id!, body as SubmitCashRequest, { isRequestCurrent: valid }) : reviewCashShift(id!, body as ReviewCashRequest, { isRequestCurrent: valid })); if (valid()) { saving.value = false; clearDetail(); detailData.value = result.data; current.value = Object.freeze({ ...result.data.shift }); paymentData.value = { records: result.data.payments, total: result.data.paymentTotal, page: 1, size: 100 }; detailDialog.value = true; ElMessage.success(type === 'open' ? '已开班' : type === 'review' ? '复核已保存' : '交班已保存'); await load() } }
  catch (e) { if (valid()) { conflict.value = isConflict(e); operationError.value = conflict.value ? '班次版本或资金流水已变化，请重新载入后逐渠道核对' : safeError(e, '操作未完成，同内容重试会复用请求编号') } }
  finally { if (valid()) saving.value = false }
}
const auditChanges = (raw: string | null) => {
  if (!raw) return []
  try { const value = JSON.parse(raw), lines: string[] = [], before = Array.isArray(value.before) ? value.before : [], after = Array.isArray(value.after) ? value.after : []; for (const c of after) { const b = before.find((x: any) => x.channel === c.channel); lines.push(`${channelText(c.channel)}：实际收款 ${money(b?.actualReceipts)} → ${money(c.actualReceipts)}；实际退款 ${money(b?.actualRefunds)} → ${money(c.actualRefunds)}`) }; if (value.previousDifferenceReason || value.differenceReason) lines.push(`差异说明：${value.previousDifferenceReason || '无'} → ${value.differenceReason || '无'}`); if (value.previousHandoverNote || value.handoverNote) lines.push(`交班备注：${value.previousHandoverNote || '无'} → ${value.handoverNote || '无'}`); return lines }
  catch { return ['变更记录暂无法解析，请联系管理者核对'] }
}
onMounted(load)
</script>

<style scoped>
.cash-center{color:#244463}.cash-center :deep(.el-button--primary.is-plain){color:#245b95;background:#edf5ff;border-color:#98bae4}.cash-center :deep(.el-button--primary.is-plain:hover){color:#fff;background:#3068ae;border-color:#3068ae}.cash-center :deep(.el-select){width:180px}.metrics{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:14px;margin:18px 0}.metric,.panel{border:1px solid #d5e3f4;background:#fff;border-radius:16px;box-shadow:0 5px 20px #36597b09}.metric{padding:20px 16px}.metric small{display:block;color:#617c98;font-size:12px}.metric strong{display:block;margin-top:12px;font-size:24px;color:#20486e;font-variant-numeric:tabular-nums}.panel{padding:22px;margin:18px 0;overflow:auto}.panel h2{font-size:18px;margin:0 0 14px}.panel-heading,.current,.detail-heading{display:flex;align-items:center;justify-content:space-between;gap:16px}.hint{color:#64809c;font-size:12px;line-height:1.8}.current p{font-size:13px;color:#64809c}.el-pagination{justify-content:flex-end;margin-top:20px}.audit{padding:15px 0;border-bottom:1px solid #e6eef8}.audit p{white-space:pre-wrap;overflow-wrap:anywhere;margin:8px 0;color:#536e89;font-size:13px}.totals{padding:14px;border-radius:10px;background:#edf5ff;line-height:1.9;font-variant-numeric:tabular-nums}.detail-heading h3{margin:0}.cash-center :deep(.el-dialog__body){line-height:1.7;color:#365574}.cash-center :deep(.el-table){font-variant-numeric:tabular-nums}.cash-center :deep(.el-dialog){max-width:calc(100vw - 70px)}
@media(max-width:1250px){.metrics{grid-template-columns:repeat(3,minmax(0,1fr))}}
</style>
