<template>
  <div class="retention-page">
    <WorkspaceHero compact>
      <template #heading><span>MEMBER RETENTION</span><h1>续费与回访</h1><p>{{ scopeText }}。把到期提醒、回访约定与跟进结果放在同一处，及时完成下一步联系。</p></template>
      <template #actions><el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon> 刷新数据</el-button><router-link v-if="auth.userInfo?.role === 'SALES'" to="/advisor-workbench"><el-button plain>会籍工作台</el-button></router-link></template>
      <template #footer><div class="hero-footer"><span>跟进结果只记录沟通进展；实际续费依据已支付续费订单及其退款后的净额统计。</span><small v-if="data.asOf">统计时间 {{ dateText(data.asOf) }}</small></div></template>
    </WorkspaceHero>

    <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" class="load-error" />
    <div class="metric-grid" :aria-busy="loading">
      <article><span>{{ auth.userInfo?.role === 'SALES' ? '我的会员' : '范围内会员' }}</span><strong>{{ metric('totalMembers') }}</strong><small>按当前账号权限统计</small></article>
      <article class="attention"><span>今日待回访</span><strong>{{ metric('todayFollowUps') }}</strong><small>已约定今天联系</small></article>
      <article class="overdue"><span>逾期回访</span><strong>{{ metric('overdueFollowUps') }}</strong><small>约定时间已过</small></article>
      <article><span>7 天内到期</span><strong>{{ metric('expiring7') }}</strong><small>30 天内 {{ metric('expiring30') }} 人</small></article>
      <article class="risk"><span>高风险会员</span><strong>{{ metric('highRisk') }}</strong><small>查看原因后安排回访</small></article>
      <article><span>本月已跟进</span><strong>{{ metric('followedThisMonth') }}</strong><small>按会员去重</small></article>
      <article class="conversion"><span>本月实际续费</span><strong>{{ metric('renewedThisMonth') }}<em> 人</em></strong><small>续费净额 ¥{{ amountMetric }}</small></article>
    </div>

    <section class="task-section">
      <div class="section-head"><div><h2>待办与会员</h2><p>先处理逾期与今日任务，再关注权益到期和高风险会员。</p></div><span class="row-count">当前筛选 {{ data.members.length }} 人</span></div>
      <el-card shadow="never" class="task-card">
        <div class="filters">
          <div class="filter-tabs" role="group" aria-label="回访任务筛选"><button v-for="option in filterOptions" :key="option.value" type="button" :class="{ selected: filter === option.value }" :aria-pressed="filter === option.value" @click="selectFilter(option.value)">{{ option.label }}</button></div>
          <div class="search"><el-input v-model="keyword" clearable maxlength="80" placeholder="姓名、会员号或手机号" aria-label="搜索会员" @keyup.enter="load" @clear="load"/><el-button type="primary" plain @click="load">查询</el-button></div>
        </div>
        <p class="scope-note">顶部总数与顾问概览统计整个权限范围；任务筛选和搜索只影响下面的会员列表。</p>
        <div v-loading="loading" class="member-list">
          <article v-for="row in data.members" :key="row.memberId" class="member-task">
            <div class="member-heading"><div><strong>{{ row.realName || '未填姓名' }}</strong><span class="member-no">{{ row.memberNo }}</span><small>{{ row.phone || '未填手机' }} · {{ row.venueName || '未分配场馆' }} · {{ row.advisorName || '未分配顾问' }}</small></div><el-tag :type="followStatusType(row.followStatus)" effect="light">{{ followStatusText(row.followStatus) }}</el-tag></div>
            <div class="task-detail">
              <div><span class="detail-label">权益到期</span><strong>{{ expiryText(row) }}</strong><small v-if="row.expiresAt">{{ expiryDateText(row.expiresAt) }}</small></div>
              <div><span class="detail-label">下次回访</span><strong>{{ row.nextFollowAt ? dateText(row.nextFollowAt) : '尚未约定' }}</strong><small>{{ statusText(row.status) }}</small></div>
              <div class="risk-detail"><span class="detail-label">{{ riskText(row.riskLevel) }} · {{ row.riskScore }} 分</span><p>{{ row.riskReason || '暂无风险说明' }}</p></div>
            </div>
            <div class="follow-detail"><span class="detail-label">最近跟进</span><template v-if="row.lastFollowAt"><span>{{ dateText(row.lastFollowAt) }}</span><el-tag size="small" effect="plain">{{ outcomeText(row.lastOutcome) }}</el-tag><p>{{ row.lastFollowContent }}</p></template><span v-else class="muted">尚无记录</span></div>
            <div class="member-footer"><div class="actual-renewal"><span :class="{ renewed: row.renewedThisMonth }">{{ row.renewedThisMonth ? '本月已实际续费' : '本月暂无实际续费' }}</span><small v-if="row.renewedThisMonth">¥{{ money(row.renewalNetAmount) }} 净额 · {{ dateText(row.lastRenewedAt) }}</small></div><div class="row-actions"><el-button :disabled="saving" @click="openHistory(row)" data-agent-type="retention" :data-agent-id="row.memberId" :data-agent-label="row.realName" data-agent-action="open">跟进记录</el-button><el-button type="primary" :disabled="saving" @click="openFollow(row)" data-agent-type="retention" :data-agent-id="row.memberId" :data-agent-label="row.realName" data-agent-action="follow-up">记跟进</el-button></div></div>
          </article>
          <el-empty v-if="!loading && !loadError && !data.members.length" description="当前范围和筛选下没有会员" :image-size="70" />
          <div v-if="!loading && loadError" class="unavailable"><p>暂时无法显示当前任务，请刷新重试。</p><el-button @click="load">重新加载</el-button></div>
        </div>
      </el-card>
    </section>

    <section v-if="showAdvisorOverview" class="advisor-section">
      <div class="section-head"><div><h2>顾问跟进概览</h2><p>按会员归属展示当前任务、本月跟进与实际续费人数，便于安排工作。</p></div></div>
      <el-card shadow="never" class="advisor-card"><el-table :data="loadError ? [] : data.advisors" stripe><el-table-column prop="advisorName" label="顾问" min-width="150"/><el-table-column prop="memberCount" label="会员数" width="95"/><el-table-column prop="todayFollowUps" label="今日回访" width="100"/><el-table-column prop="overdueFollowUps" label="逾期回访" width="100"/><el-table-column prop="followedThisMonth" label="本月跟进人数" width="125"/><el-table-column prop="renewedThisMonth" label="本月实际续费人数" width="160"/></el-table><el-empty v-if="!loading && !loadError && !data.advisors.length" description="暂无顾问统计" :image-size="60" /></el-card>
    </section>

    <el-dialog v-model="followDialog" :title="`跟进 ${current?.realName || ''}`" width="min(540px, calc(100vw - 24px))" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving">
      <div class="dialog-member"><strong>{{ current?.memberNo }} · {{ current?.phone || '未填手机' }}</strong><span>{{ current?.venueName }} · {{ current?.advisorName || '未分配顾问' }}</span></div>
      <p class="dialog-note">请记录已进行的沟通。选择“考虑续费”等结果不会产生续费订单或改变成交数据。</p>
      <el-form label-width="84px" :disabled="saving">
        <el-form-item label="跟进方式"><el-select v-model="followForm.followType" style="width:100%"><el-option label="电话" value="CALL"/><el-option label="微信" value="WECHAT"/><el-option label="到店" value="VISIT"/><el-option label="其他" value="OTHER"/></el-select></el-form-item>
        <el-form-item label="跟进结果"><el-select v-model="followForm.outcome" style="width:100%"><el-option v-for="option in outcomeOptions" :key="option.value" :label="option.label" :value="option.value"/></el-select></el-form-item>
        <el-form-item label="跟进内容"><el-input v-model="followForm.content" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="记录沟通内容与下一步约定"/></el-form-item>
        <el-form-item label="下次回访"><el-date-picker v-model="followForm.nextFollowAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="可留空；填写未来时间" style="width:100%"/></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="saving" @click="followDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitFollow">保存跟进</el-button></template>
    </el-dialog>

    <el-dialog v-model="historyDialog" :title="`${current?.realName || ''} 的跟进记录`" width="min(580px, calc(100vw - 24px))">
      <p class="dialog-note">{{ current?.memberNo }} · {{ current?.phone || '未填手机' }}。历史结果反映当时的沟通进展。</p>
      <div v-loading="historyLoading" class="history-body">
        <el-alert v-if="historyError" :title="historyError" type="error" show-icon :closable="false"/>
        <el-timeline v-else-if="history.length"><el-timeline-item v-for="item in history" :key="item.id" :timestamp="dateText(item.createdAt)" placement="top"><div class="history-heading"><strong>{{ typeText(item.followType) }} · {{ item.staffName || '员工' }}</strong><el-tag size="small" effect="plain">{{ outcomeText(item.outcome) }}</el-tag></div><p class="history-content">{{ item.content }}</p><small v-if="item.nextFollowAt" class="muted">下次回访：{{ dateText(item.nextFollowAt) }}</small></el-timeline-item></el-timeline>
        <el-empty v-else-if="!historyLoading" description="暂无跟进记录" :image-size="60"/>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import WorkspaceHero from '@/components/WorkspaceHero.vue'
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/modules/auth'
import { secureUuid } from '@/utils/financeUi.mjs'
import { addMemberFollowUp, getRetentionCenter, listMemberFollowUps, type RetentionCenter, type RetentionMember, type RetentionFilter, type FollowUp, type FollowOutcome, type FollowUpRequest } from '@/api/modules/advisor'

const auth = useAuthStore()
const pageGeneration = auth.sessionGeneration
const filter = ref<RetentionFilter>('ALL'), keyword = ref('')
const loading = ref(false), loadError = ref(''), loaded = ref(false), saving = ref(false)
const data = ref<RetentionCenter>({ asOf: '', summary: { totalMembers: 0, todayFollowUps: 0, overdueFollowUps: 0, expiring7: 0, expiring30: 0, highRisk: 0, followedThisMonth: 0, renewedThisMonth: 0, renewalNetAmount: 0 }, members: [], advisors: [] })
const showAdvisorOverview = computed(() => ['MANAGER', 'SUPER_ADMIN'].includes(auth.userInfo?.role || ''))
const scopeText = computed(() => auth.userInfo?.role === 'SALES' ? '只显示本场馆归属我的会员' : auth.userInfo?.role === 'MANAGER' ? '显示本场馆的会员与顾问任务' : '显示总部权限范围内的会员与顾问任务')
const filterOptions: { value: RetentionFilter; label: string }[] = [{ value: 'ALL', label: '全部会员' }, { value: 'TODAY', label: '今日回访' }, { value: 'OVERDUE', label: '逾期回访' }, { value: 'EXPIRING_7', label: '7 天到期' }, { value: 'EXPIRING_30', label: '30 天到期' }, { value: 'HIGH_RISK', label: '高风险' }]
const outcomeOptions = [{ value: 'NO_CONTACT', label: '未联系上' }, { value: 'VISIT_BOOKED', label: '已约到店' }, { value: 'RENEWAL_PENDING', label: '考虑续费' }, { value: 'DECLINED', label: '暂不续费' }, { value: 'OTHER', label: '其他' }]
const outcomeText = (value?: string | null) => outcomeOptions.find(option => option.value === value)?.label || (value && value !== 'NOT_RECORDED' ? value : '未记录结果')
const followDialog = ref(false), historyDialog = ref(false)
const current = ref<RetentionMember | null>(null), history = ref<FollowUp[]>([])
const historyLoading = ref(false), historyError = ref('')
const followForm = reactive({ followType: 'CALL', content: '', outcome: 'OTHER' as FollowOutcome, nextFollowAt: undefined as string | undefined })
let active = true, loadSequence = 0, historySequence = 0
let retryIntent: { fingerprint: string; key: string } | null = null
const sessionIsCurrent = (generation: number) => active && generation === auth.sessionGeneration && pageGeneration === auth.sessionGeneration
onUnmounted(() => { active = false; loadSequence++; historySequence++ })

const money = (value?: number | null) => Number(value || 0).toFixed(2)
const metric = (key: keyof RetentionCenter['summary']) => loaded.value && !loadError.value ? data.value.summary[key] : '—'
const amountMetric = computed(() => loaded.value && !loadError.value ? money(data.value.summary.renewalNetAmount) : '—')
const dateText = (value?: string | null) => value ? new Date(value).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '—'
// expiresAt is a LocalDate, so keep its calendar day without adding a timezone or a clock time.
const expiryDateText = (value?: string | null) => value ? value.slice(0, 10).replace(/-/g, '/') : '—'
const statusText = (value: string) => ({ ACTIVE: '会员正常', PAUSED: '会员暂停', EXPIRED: '会员已过期', BLACKLIST: '黑名单' }[value] || value)
const riskText = (value: string) => ({ HIGH: '高风险', MEDIUM: '中风险', LOW: '低风险' }[value] || value)
const typeText = (value: string) => ({ CALL: '电话', WECHAT: '微信', VISIT: '到店', OTHER: '其他' }[value] || value)
const followStatusText = (value: string) => ({ TODAY: '今日回访', OVERDUE: '逾期回访', UPCOMING: '已约回访', UNSCHEDULED: '未约回访' }[value] || value)
const followStatusType = (value: string): 'danger' | 'warning' | 'primary' | 'info' => value === 'OVERDUE' ? 'danger' : value === 'TODAY' ? 'warning' : value === 'UPCOMING' ? 'primary' : 'info'
const expiryText = (row: RetentionMember) => row.expiryDays === null ? '无到期时间' : row.expiryDays < 0 ? `已到期 ${Math.abs(row.expiryDays)} 天` : row.expiryDays === 0 ? '今日到期' : `${row.expiryDays} 天后到期`

const load = async () => {
  if (!sessionIsCurrent(pageGeneration)) return
  const sequence = ++loadSequence, generation = auth.sessionGeneration
  loading.value = true; loadError.value = ''; data.value.members = []
  try {
    const result = await getRetentionCenter(filter.value, keyword.value)
    if (sequence === loadSequence && sessionIsCurrent(generation)) { data.value = result.data; loaded.value = true }
  } catch (error: any) {
    if (sequence === loadSequence && sessionIsCurrent(generation)) loadError.value = error?.message || '续费与回访数据加载失败，请刷新重试'
  } finally { if (sequence === loadSequence && sessionIsCurrent(generation)) loading.value = false }
}
const selectFilter = (value: RetentionFilter) => { filter.value = value; load() }
const openFollow = (row: RetentionMember) => {
  if (saving.value || !sessionIsCurrent(pageGeneration)) return
  historySequence++; historyDialog.value = false; current.value = Object.freeze({ ...row }); retryIntent = null
  Object.assign(followForm, { followType: 'CALL', content: '', outcome: 'OTHER', nextFollowAt: undefined }); followDialog.value = true
}
const submitFollow = async () => {
  if (saving.value || !sessionIsCurrent(pageGeneration)) return
  if (!current.value || !followForm.content.trim()) return ElMessage.warning('请填写跟进内容')
  if (!outcomeOptions.some(option => option.value === followForm.outcome)) return ElMessage.warning('请选择有效的跟进结果')
  const memberId = current.value.memberId, generation = auth.sessionGeneration
  const payload: FollowUpRequest = { followType: followForm.followType, content: followForm.content.trim(), outcome: followForm.outcome, ...(followForm.nextFollowAt ? { nextFollowAt: followForm.nextFollowAt } : {}) }
  const fingerprint = JSON.stringify([memberId, payload])
  const isRetry = retryIntent?.fingerprint === fingerprint
  // A lost response may already have persisted this intent; let the original key replay after its scheduled time passes.
  if (!isRetry && payload.nextFollowAt && (!Number.isFinite(new Date(payload.nextFollowAt).getTime()) || new Date(payload.nextFollowAt).getTime() <= Date.now())) return ElMessage.warning('下次回访时间必须晚于现在')
  try { if (!isRetry) retryIntent = { fingerprint, key: secureUuid() } }
  catch (error: any) { return ElMessage.error(error?.message || '无法生成记录编号，请重试') }
  const request = Object.freeze({ ...payload, idempotencyKey: retryIntent!.key })
  saving.value = true
  try {
    await addMemberFollowUp(memberId, request)
    if (!sessionIsCurrent(generation)) return
    retryIntent = null; ElMessage.success('跟进已记录'); followDialog.value = false; await load()
  } catch (error: any) {
    if (sessionIsCurrent(generation)) ElMessage.error(`${error?.message || '保存失败'}，可重试；相同内容将避免重复记录`)
  } finally { saving.value = false }
}
const openHistory = async (row: RetentionMember) => {
  if (saving.value || !sessionIsCurrent(pageGeneration)) return
  const sequence = ++historySequence, generation = auth.sessionGeneration
  current.value = Object.freeze({ ...row }); followDialog.value = false; history.value = []; historyError.value = ''; historyLoading.value = true; historyDialog.value = true
  const selected = () => sequence === historySequence && historyDialog.value && current.value?.memberId === row.memberId && sessionIsCurrent(generation)
  try { const result = await listMemberFollowUps(row.memberId); if (selected()) history.value = result.data }
  catch (error: any) { if (selected()) historyError.value = error?.message || '跟进记录加载失败' }
  finally { if (selected()) historyLoading.value = false }
}
onMounted(load)
</script>

<style scoped>
.retention-page :deep(.wb-hero__heading p){color:#476481}
.retention-page :deep(.wb-hero__heading>span:first-child){color:#2461a4}
.retention-page :deep(.el-input__inner::placeholder),.retention-page :deep(.el-textarea__inner::placeholder),.retention-page :deep(.el-empty__description p){color:#4b6987}
.retention-page :deep(.el-table th.el-table__cell){color:#4b6987}
.retention-page :deep(.el-tag){--el-tag-text-color:#2461a4}
.retention-page :deep(.el-tag.el-tag--warning){--el-tag-text-color:#865b13}
.retention-page :deep(.el-tag.el-tag--danger){--el-tag-text-color:#a13937}
.retention-page :deep(.el-tag.el-tag--info){--el-tag-text-color:#4b6987}
.retention-page :deep(.el-alert--error){color:#a13937}
.retention-page :deep(.el-button.el-button--primary:not(.is-disabled):not(.is-plain)){background:#2461a4;border-color:#2461a4;color:#fff}
.retention-page :deep(.el-button.el-button--primary.is-plain:not(.is-disabled)){background:#edf4ff;border-color:#90b0d8;color:#2461a4}
.retention-page{max-width:1360px;margin:0 auto;color:#234565}.hero-footer{display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:8px;color:#476481;font-size:12px;line-height:1.7}.hero-footer small{color:#476481;font-size:11px}.load-error{margin-bottom:18px}.metric-grid{display:grid;grid-template-columns:repeat(7,minmax(0,1fr));gap:12px}.metric-grid article{min-width:0;padding:17px 14px;border:1px solid #d8e7f6;border-radius:16px;background:linear-gradient(140deg,#fff,#f6faff);box-shadow:0 4px 16px #24496b06}.metric-grid span,.metric-grid strong,.metric-grid small{display:block}.metric-grid span{color:#4b6987;font-size:12px}.metric-grid strong{margin:8px 0;font-size:27px;color:#21486e;line-height:1.2}.metric-grid small{font-size:11px;color:#4b6987;line-height:1.6}.metric-grid em{font-style:normal;font-size:12px;font-weight:500}.metric-grid .attention{background:#fffaf1;border-color:#f0dfb8}.metric-grid .overdue{background:#fff7f5;border-color:#edccc6}.metric-grid .risk{background:#fff9f7;border-color:#eedad2}.metric-grid .conversion{background:#f1fcf8;border-color:#c5e6d9}.section-head{display:flex;justify-content:space-between;align-items:center;gap:14px;flex-wrap:wrap;margin:28px 0 14px}.section-head h2{margin:0 0 5px;font-size:20px}.section-head p{margin:0;font-size:12px;color:#4b6987;line-height:1.8}.row-count{font-size:12px;color:#476481;background:#e2effe;border-radius:20px;padding:7px 12px}.task-card,.advisor-card{border:1px solid #d7e6f7;border-radius:18px;background:#fafdff}.filters{display:flex;justify-content:space-between;align-items:flex-start;gap:16px;flex-wrap:wrap}.filter-tabs{display:flex;gap:7px;flex-wrap:wrap}.filter-tabs button{border:1px solid #dbe8f7;border-radius:10px;padding:8px 12px;background:#fff;color:#4b6987;font:inherit;font-size:12px;cursor:pointer}.filter-tabs button.selected{background:#e8f2ff;border-color:#8db9ed;color:#2461a4;font-weight:700}.filter-tabs button:focus-visible{outline:2px solid #438ce6;outline-offset:3px}.search{display:flex;gap:8px;min-width:0;width:310px;max-width:100%}.scope-note{margin:12px 0 18px;color:#4b6987;font-size:11px;line-height:1.8}.member-list{min-height:100px;display:grid;gap:12px}.member-task{min-width:0;padding:20px;border:1px solid #deebf8;border-radius:15px;background:#fff;box-shadow:0 3px 12px #22528405}.member-heading{display:flex;justify-content:space-between;gap:12px;align-items:flex-start}.member-heading>div{min-width:0}.member-heading strong{font-size:16px}.member-no{margin-left:10px;font-size:11px;color:#4b6987}.member-heading small{display:block;margin-top:6px;color:#4b6987;font-size:11px;line-height:1.8;overflow-wrap:anywhere}.task-detail{display:grid;grid-template-columns:1fr 1fr 1.7fr;gap:18px;margin:18px 0;padding:14px;border-radius:10px;background:#f5f9fe}.task-detail>div{min-width:0}.detail-label{display:block;color:#4b6987;font-size:11px;margin-bottom:6px}.task-detail strong{font-size:12px;font-weight:600}.task-detail small{display:block;font-size:11px;color:#4b6987;margin-top:5px}.risk-detail p{margin:0;font-size:12px;line-height:1.7;color:#4b6987;overflow-wrap:anywhere}.follow-detail{display:flex;align-items:center;gap:8px;flex-wrap:wrap;font-size:11px;color:#4b6987}.follow-detail .detail-label{margin:0}.follow-detail p{flex-basis:100%;margin:2px 0 0;color:#456888;font-size:12px;line-height:1.8;white-space:pre-wrap;overflow-wrap:anywhere}.member-footer{display:flex;justify-content:space-between;align-items:center;gap:15px;flex-wrap:wrap;border-top:1px solid #edf3fa;padding-top:14px;margin-top:14px}.actual-renewal span{font-size:11px;color:#4b6987}.actual-renewal span.renewed{color:#356e57}.actual-renewal small{display:block;margin-top:5px;color:#356e57;font-size:11px}.row-actions{display:flex;gap:8px}.row-actions :deep(.el-button+.el-button){margin-left:0}.muted{color:#4b6987}.unavailable{text-align:center;padding:25px;color:#4b6987;font-size:13px}.dialog-member{display:flex;flex-direction:column;gap:6px;padding:12px;border:1px solid #deebf8;border-radius:10px;background:#f5f9ff;font-size:12px;color:#406585}.dialog-member span{font-size:11px;color:#4b6987}.dialog-note{font-size:12px;color:#4b6987;line-height:1.8;margin:14px 0 20px}.history-body{min-height:100px}.history-heading{display:flex;gap:10px;flex-wrap:wrap;align-items:center;color:#355e84;font-size:12px}.history-content{white-space:pre-wrap;overflow-wrap:anywhere;font-size:13px;line-height:1.8;color:#4b6987;margin:8px 0}.advisor-card :deep(.el-card__body){min-width:0}
@media(max-width:1200px){.metric-grid{grid-template-columns:repeat(4,minmax(0,1fr))}.metric-grid .conversion{grid-column:span 2}}
@media(max-width:760px){.metric-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.metric-grid .conversion{grid-column:auto}.metric-grid article{padding:14px}.metric-grid strong{font-size:24px}.task-detail{grid-template-columns:1fr 1fr;gap:14px}.risk-detail{grid-column:1/-1}.filters{gap:12px}.search{width:100%}.member-task{padding:16px}.task-card :deep(.el-card__body){padding:14px}.section-head{margin-top:22px}}
@media(max-width:420px){.member-heading{flex-direction:column;gap:8px}.member-footer{align-items:stretch;flex-direction:column;gap:12px}.row-actions{display:grid;grid-template-columns:1fr 1fr}.row-actions :deep(.el-button){width:100%;padding:8px}.task-detail{grid-template-columns:1fr;gap:12px}.risk-detail{grid-column:auto}.filter-tabs{gap:6px}.filter-tabs button{padding:8px 10px}.metric-grid article{padding:12px}.metric-grid span{font-size:11px}.metric-grid strong{font-size:22px}.metric-grid small{font-size:10px}}
</style>
