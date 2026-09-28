<template>
  <div class="advisor-page">
    <div class="page-title"><div><span>MEMBERSHIP ADVISOR</span><h1>{{ data.advisorName || userInfo?.realName }}的会籍工作台</h1><p>我负责的会员、跟进记录与开卡业绩。</p></div><el-button @click="load"><el-icon><Refresh /></el-icon> 刷新</el-button></div>

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
        <el-table-column label="最近跟进" min-width="240"><template #default="{row}"><template v-if="row.lastFollowAt"><small class="muted">{{ dateText(row.lastFollowAt) }}</small><div>{{ row.lastFollowContent }}</div></template><span v-else class="muted">还没有跟进记录</span></template></el-table-column>
        <el-table-column label="下次跟进" width="150"><template #default="{row}"><template v-if="row.nextFollowAt"><el-tag :type="row.due?'danger':'info'" size="small">{{ row.due?'已到期':'已约定' }}</el-tag><small class="muted">{{ dateText(row.nextFollowAt) }}</small></template><span v-else class="muted">—</span></template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right"><template #default="{row}"><el-button link type="primary" @click="openFollow(row)">记跟进</el-button><el-button link @click="openHistory(row)">跟进记录</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!loading && !data.members.length" description="还没有归属到我的会员，请让店长或总部在会员档案里把会员指派给你" :image-size="70" />
    </el-card>

    <el-dialog v-model="followDialog" :title="`跟进 ${current?.realName || ''}`" width="520px">
      <el-form label-width="96px">
        <el-form-item label="跟进方式"><el-select v-model="followForm.followType" style="width:100%"><el-option label="电话" value="CALL"/><el-option label="微信" value="WECHAT"/><el-option label="到店" value="VISIT"/><el-option label="其它" value="OTHER"/></el-select></el-form-item>
        <el-form-item label="跟进内容"><el-input v-model="followForm.content" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="例如：客户想练体态，已约周六体验课" /></el-form-item>
        <el-form-item label="下次跟进"><el-date-picker v-model="followForm.nextFollowAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="可留空" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="followDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitFollow">保存跟进</el-button></template>
    </el-dialog>

    <el-dialog v-model="historyDialog" :title="`${current?.realName || ''} 的跟进记录`" width="560px">
      <el-timeline v-if="history.length"><el-timeline-item v-for="h in history" :key="h.id" :timestamp="dateText(h.createdAt)" placement="top"><strong>{{ typeText(h.followType) }}</strong><span class="muted"> · {{ h.staffName || '员工' }}</span><p class="history-content">{{ h.content }}</p><small v-if="h.nextFollowAt" class="muted">下次跟进：{{ dateText(h.nextFollowAt) }}</small></el-timeline-item></el-timeline>
      <el-empty v-else description="暂无跟进记录" :image-size="60" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Bell, ChatDotRound, Money, Plus, Refresh, Tickets, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/modules/auth'
import { addMemberFollowUp, getAdvisorWorkbench, listMemberFollowUps, type AdvisorMember, type AdvisorWorkbench, type FollowUp } from '@/api/modules/advisor'

const auth = useAuthStore()
const userInfo = computed(() => auth.userInfo)
const loading = ref(false), saving = ref(false)
const data = ref<AdvisorWorkbench>({ advisorName: '', summary: { myMembers: 0, newMembersThisMonth: 0, followUpsThisWeek: 0, dueFollowUps: 0, salesOrders: 0, salesAmount: 0 }, members: [] })
const followDialog = ref(false), historyDialog = ref(false)
const current = ref<AdvisorMember | null>(null), history = ref<FollowUp[]>([])
const followForm = reactive({ followType: 'CALL', content: '', nextFollowAt: undefined as string | undefined })

const money = (v?: number) => (Number(v || 0)).toFixed(2)
const dateText = (v?: string) => v ? new Date(v).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '—'
const statusText = (v?: string) => ({ ACTIVE: '正常', PAUSED: '暂停', EXPIRED: '已过期', BLACKLIST: '黑名单' }[v || ''] || v || '—')
const typeText = (v: string) => ({ CALL: '电话', WECHAT: '微信', VISIT: '到店', OTHER: '其它' }[v] || v)

const load = async () => {
  loading.value = true
  try { data.value = (await getAdvisorWorkbench()).data } finally { loading.value = false }
}
const openFollow = (row: AdvisorMember) => { current.value = row; followForm.followType = 'CALL'; followForm.content = ''; followForm.nextFollowAt = undefined; followDialog.value = true }
const submitFollow = async () => {
  if (!current.value || !followForm.content.trim()) return ElMessage.warning('请填写跟进内容')
  saving.value = true
  try {
    await addMemberFollowUp(current.value.memberId, { followType: followForm.followType, content: followForm.content.trim(), nextFollowAt: followForm.nextFollowAt })
    ElMessage.success('跟进已记录'); followDialog.value = false; await load()
  } finally { saving.value = false }
}
const openHistory = async (row: AdvisorMember) => { current.value = row; history.value = (await listMemberFollowUps(row.memberId)).data; historyDialog.value = true }
onMounted(load)
</script>

<style scoped>
.advisor-page{max-width:1200px;margin:0 auto}.page-title{display:flex;align-items:center;justify-content:space-between;margin-bottom:24px}.page-title>div>span{color:#3474ff;font-size:11px;font-weight:800;letter-spacing:1.8px}.page-title h1{margin:7px 0;color:#172033;font-size:29px}.page-title p,.section-head p{margin:0;color:#8792a5}.stats-grid{display:grid;grid-template-columns:repeat(6,1fr);gap:12px}.stats-grid article{display:flex;align-items:center;gap:12px;padding:18px;border:1px solid #e7ecf4;border-radius:17px;background:#fff}.stats-grid article.alert{border-color:#f6c9c9;background:#fff7f7}.icon{display:grid;place-items:center;flex:none;width:42px;height:42px;border-radius:13px;font-size:19px}.icon.blue{background:#eaf1ff;color:#2e70ff}.icon.green{background:#e6f8f2;color:#1eb88a}.icon.orange{background:#fff0e6;color:#f27b31}.icon.purple{background:#f2eafd;color:#8c5be0}.stats-grid span,.stats-grid strong{display:block}.stats-grid span{color:#8792a5;font-size:11px}.stats-grid strong{margin-top:4px;color:#172033;font-size:20px}.section-head{display:flex;align-items:center;justify-content:space-between;margin:28px 0 14px}.section-head h2{margin:0 0 5px;color:#1b263b}.section-head p{font-size:12px}.table-card{border-radius:16px}.member-cell strong,.member-cell small{display:block}.member-cell small{margin-top:3px;color:#8b96a8}.muted{color:#9aa5b8}.history-content{margin:6px 0 4px;color:#5f6d85;font-size:13px}@media(max-width:1150px){.stats-grid{grid-template-columns:repeat(3,1fr)}}
</style>
