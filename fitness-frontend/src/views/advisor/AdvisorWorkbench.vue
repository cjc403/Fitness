<template>
  <div class="advisor-page">
    <WorkspaceHero>
      <template #heading><span>MEMBERSHIP ADVISOR</span><h1>{{ data.advisorName || userInfo?.realName }}的会籍工作台</h1><p>我负责的会员、跟进记录与开卡业绩。</p></template>
      <template #actions><router-link to="/retention"><el-button type="primary" plain>续费与回访</el-button></router-link><el-button @click="load"><el-icon><Refresh /></el-icon> 刷新</el-button></template>
    </WorkspaceHero>

    <el-alert v-if="loadError" type="error" :closable="false" :title="loadError" show-icon class="load-error" />
    <div class="stats-grid">
      <article><div class="icon blue"><el-icon><User /></el-icon></div><div><span>我的会员</span><strong>{{ data.summary.myMembers }}</strong></div></article>
      <article><div class="icon green"><el-icon><Plus /></el-icon></div><div><span>本月新增</span><strong>{{ data.summary.newMembersThisMonth }}</strong></div></article>
      <article><div class="icon orange"><el-icon><ChatDotRound /></el-icon></div><div><span>本周跟进</span><strong>{{ data.summary.followUpsThisWeek }}</strong></div></article>
      <article :class="['stat', data.summary.dueFollowUps ? 'alert' : '']"><div class="icon purple"><el-icon><Bell /></el-icon></div><div><span>待跟进</span><strong>{{ data.summary.dueFollowUps }}</strong></div></article>
      <article><div class="icon blue"><el-icon><Tickets /></el-icon></div><div><span>本月开卡</span><strong>{{ data.summary.salesOrders }}</strong></div></article>
      <article><div class="icon green"><el-icon><Money /></el-icon></div><div><span>本月业绩</span><strong>¥{{ money(data.summary.salesAmount) }}</strong></div></article>
    </div>

    <div class="section-head"><div><h2>我的会员</h2><p>归属到我的会员；待跟进的排在最前</p></div><el-tag effect="plain">{{ data.members.length }} 人</el-tag></div>
    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="data.members">
        <el-table-column label="会员" min-width="170"><template #default="{row}"><div class="member-cell"><strong>{{ row.realName }}</strong><small>{{ row.memberNo }} · {{ row.phone || '未填手机' }}</small></div></template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="row.status==='ACTIVE'?'success':'info'">{{ statusText(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="风险" width="130"><template #default="{row}"><el-tag v-if="row.riskLevel" :type="row.riskLevel==='HIGH'?'danger':row.riskLevel==='MEDIUM'?'warning':'success'" size="small">{{ riskText(row.riskLevel) }} {{ row.riskScore }}</el-tag><span v-else class="muted">—</span></template></el-table-column>
        <el-table-column label="最近跟进" min-width="240"><template #default="{row}"><template v-if="row.lastFollowAt"><small class="muted">{{ dateText(row.lastFollowAt) }}</small><div>{{ row.lastFollowContent }}</div></template><span v-else class="muted">还没有跟进记录</span></template></el-table-column>
        <el-table-column label="下次跟进" width="150"><template #default="{row}"><template v-if="row.nextFollowAt"><el-tag :type="row.due?'danger':'info'" size="small">{{ row.due?'已到期':'已约定' }}</el-tag><small class="muted">{{ dateText(row.nextFollowAt) }}</small></template><span v-else class="muted">—</span></template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right"><template #default="{row}"><el-button link type="primary" @click="openFollow(row)">记跟进</el-button><el-button link @click="openHistory(row)">跟进记录</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!loading && !data.members.length" description="还没有归属到我的会员，请让店长或总部在会员档案里把会员指派给你" :image-size="70" />
    </el-card>

    <el-dialog v-model="followDialog" :title="`跟进 ${current?.realName || ''}`" width="min(520px, calc(100vw - 24px))" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving">
      <p class="follow-note">{{ current?.memberNo }} · {{ current?.phone || '未填手机' }}。记录跟进结果不会产生实际续费成交。</p>
      <el-form label-width="84px" :disabled="saving">
        <el-form-item label="跟进方式"><el-select v-model="followForm.followType" style="width:100%"><el-option label="电话" value="CALL"/><el-option label="微信" value="WECHAT"/><el-option label="到店" value="VISIT"/><el-option label="其它" value="OTHER"/></el-select></el-form-item>
        <el-form-item label="跟进结果"><el-select v-model="followForm.outcome" style="width:100%"><el-option v-for="option in outcomeOptions" :key="option.value" :label="option.label" :value="option.value"/></el-select></el-form-item>
        <el-form-item label="跟进内容"><el-input v-model="followForm.content" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="例如：客户想练体态，已约周六体验课" /></el-form-item>
        <el-form-item label="下次跟进"><el-date-picker v-model="followForm.nextFollowAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="可留空" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="saving" @click="followDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitFollow">保存跟进</el-button></template>
    </el-dialog>

    <el-dialog v-model="historyDialog" :title="`${current?.realName || ''} 的跟进记录`" width="min(560px, calc(100vw - 24px))">
      <div v-loading="historyLoading" class="history-body">
        <el-alert v-if="historyError" type="error" :closable="false" :title="historyError" show-icon />
        <el-timeline v-else-if="history.length"><el-timeline-item v-for="h in history" :key="h.id" :timestamp="dateText(h.createdAt)" placement="top"><strong>{{ typeText(h.followType) }}</strong><span class="muted"> · {{ h.staffName || '员工' }}</span><el-tag size="small" class="outcome-tag">{{ outcomeText(h.outcome) }}</el-tag><p class="history-content">{{ h.content }}</p><small v-if="h.nextFollowAt" class="muted">下次跟进：{{ dateText(h.nextFollowAt) }}</small></el-timeline-item></el-timeline>
        <el-empty v-else-if="!historyLoading" description="暂无跟进记录" :image-size="60" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import WorkspaceHero from '@/components/WorkspaceHero.vue'
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Bell, ChatDotRound, Money, Plus, Refresh, Tickets, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/modules/auth'
import { addMemberFollowUp, getAdvisorWorkbench, listMemberFollowUps, type AdvisorMember, type AdvisorWorkbench, type FollowUp, type FollowOutcome, type FollowUpRequest } from '@/api/modules/advisor'
import { secureUuid } from '@/utils/financeUi.mjs'

const auth = useAuthStore()
const pageGeneration = auth.sessionGeneration
const userInfo = computed(() => auth.userInfo)
const loading = ref(false), saving = ref(false)
const data = ref<AdvisorWorkbench>({ advisorName: '', summary: { myMembers: 0, newMembersThisMonth: 0, followUpsThisWeek: 0, dueFollowUps: 0, salesOrders: 0, salesAmount: 0 }, members: [] })
const followDialog = ref(false), historyDialog = ref(false)
const current = ref<AdvisorMember | null>(null), history = ref<FollowUp[]>([])
const historyLoading = ref(false), historyError = ref(''), loadError = ref('')
const followForm = reactive({ followType: 'CALL', content: '', outcome: 'OTHER' as FollowOutcome, nextFollowAt: undefined as string | undefined })
const outcomeOptions = [{ value: 'NO_CONTACT', label: '未联系上' }, { value: 'VISIT_BOOKED', label: '已约到店' }, { value: 'RENEWAL_PENDING', label: '考虑续费' }, { value: 'DECLINED', label: '暂不续费' }, { value: 'OTHER', label: '其他' }]
const outcomeText = (value?: string | null) => outcomeOptions.find(option => option.value === value)?.label || (value && value !== 'NOT_RECORDED' ? value : '未记录结果')
let active = true, loadSequence = 0, historySequence = 0
let retryIntent: { fingerprint: string; key: string } | null = null
const sessionIsCurrent = (generation: number) => active && generation === auth.sessionGeneration && pageGeneration === auth.sessionGeneration
onUnmounted(() => { active = false; loadSequence++; historySequence++ })

const money = (v?: number) => (Number(v || 0)).toFixed(2)
const dateText = (v?: string) => v ? new Date(v).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '—'
const statusText = (v?: string) => ({ ACTIVE: '正常', PAUSED: '暂停', EXPIRED: '已过期', BLACKLIST: '黑名单' }[v || ''] || v || '—')
const riskText = (v: string) => ({ HIGH: '高风险', MEDIUM: '中风险', LOW: '低风险' }[v] || v)
const typeText = (v: string) => ({ CALL: '电话', WECHAT: '微信', VISIT: '到店', OTHER: '其它' }[v] || v)

const load = async () => {
  if (!sessionIsCurrent(pageGeneration)) return
  const sequence = ++loadSequence, generation = auth.sessionGeneration
  loading.value = true
  loadError.value = ''
  try { const result = await getAdvisorWorkbench(); if (sequence === loadSequence && sessionIsCurrent(generation)) data.value = result.data }
  catch (error: any) { if (sequence === loadSequence && sessionIsCurrent(generation)) loadError.value = error?.message || '工作台加载失败，请刷新重试' }
  finally { if (sequence === loadSequence && sessionIsCurrent(generation)) loading.value = false }
}
const openFollow = (row: AdvisorMember) => {
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
  if (!isRetry && payload.nextFollowAt && (!Number.isFinite(new Date(payload.nextFollowAt).getTime()) || new Date(payload.nextFollowAt).getTime() <= Date.now())) return ElMessage.warning('下次跟进时间必须晚于现在')
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
const openHistory = async (row: AdvisorMember) => {
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
.load-error{margin-bottom:16px}.follow-note{margin:0 0 20px;color:#577392;font-size:12px;line-height:1.7}.history-body{min-height:100px}.outcome-tag{margin-left:8px}.history-content{white-space:pre-wrap;overflow-wrap:anywhere}@media(max-width:540px){.stats-grid article{padding:12px;gap:8px}.icon{width:32px;height:32px}.stats-grid{grid-template-columns:repeat(2,minmax(0,1fr))!important}.stats-grid strong{font-size:18px}.section-head{gap:10px}.section-head p{line-height:1.7}.outcome-tag{display:table;margin:6px 0 0}}
.advisor-page{max-width:1200px;margin:0 auto}.page-title{display:flex;align-items:center;justify-content:space-between;margin-bottom:24px}.page-title>div>span{color:#3474ff;font-size:11px;font-weight:800;letter-spacing:1.8px}.page-title h1{margin:7px 0;color:#172033;font-size:29px}.page-title p,.section-head p{margin:0;color:#8792a5}.stats-grid{display:grid;grid-template-columns:repeat(6,1fr);gap:12px}.stats-grid article{display:flex;align-items:center;gap:12px;padding:18px;border:1px solid #e7ecf4;border-radius:17px;background:#fff}.stats-grid article.alert{border-color:#f6c9c9;background:#fff7f7}.icon{display:grid;place-items:center;flex:none;width:42px;height:42px;border-radius:13px;font-size:19px}.icon.blue{background:#eaf1ff;color:#2e70ff}.icon.green{background:#e6f8f2;color:#1eb88a}.icon.orange{background:#fff0e6;color:#f27b31}.icon.purple{background:#f2eafd;color:#8c5be0}.stats-grid span,.stats-grid strong{display:block}.stats-grid span{color:#8792a5;font-size:11px}.stats-grid strong{margin-top:4px;color:#172033;font-size:20px}.section-head{display:flex;align-items:center;justify-content:space-between;margin:28px 0 14px}.section-head h2{margin:0 0 5px;color:#1b263b}.section-head p{font-size:12px}.table-card{border-radius:16px}.member-cell strong,.member-cell small{display:block}.member-cell small{margin-top:3px;color:#8b96a8}.muted{color:#9aa5b8}.history-content{margin:6px 0 4px;color:#5f6d85;font-size:13px}@media(max-width:1150px){.stats-grid{grid-template-columns:repeat(3,1fr)}}
</style>
