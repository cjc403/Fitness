<template>
  <div class="analytics-page">
    <WorkspaceHero compact>
      <template #heading><span>BUSINESS INSIGHT</span><h1>经营分析</h1><p>教练绩效、会员消费与到店、会员流失风险（评分规则可解释，不用大模型）。</p></template>
      <template #actions><div class="actions">
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始" end-placeholder="结束" style="width:260px" @change="load"/>
        <el-button @click="quick(30)">近 30 天</el-button>
        <el-button @click="quick(90)">近 90 天</el-button>
        <el-button @click="load"><el-icon><Refresh/></el-icon> 刷新</el-button>
      </div></template>
    </WorkspaceHero>

    <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" style="margin-bottom:16px" />
    <div class="metric-grid" v-loading="loading">
      <article><span>新增会员</span><strong>{{ overview?.newMembers ?? '—' }}</strong><small>区间内注册</small></article>
      <article><span>到店人次</span><strong>{{ overview?.totalAttendances ?? '—' }}</strong><small>区间内已签到</small></article>
      <article><span>区间营收</span><strong>¥{{ money(overview?.revenue) }}</strong><small>已支付订单</small></article>
      <article><span>活跃 / 沉默</span><strong>{{ overview?.activeMembers ?? '—' }} / {{ overview?.silentMembers ?? '—' }}</strong><small>按最近到店 30/60 天</small></article>
      <article><span>已流失</span><strong>{{ overview?.churnedMembers ?? '—' }}</strong><small>超 60 天未到店</small></article>
      <article class="alert-card"><span>高风险会员</span><strong>{{ overview?.highRiskMembers ?? '—' }}</strong><small>建议尽快跟进</small></article>
    </div>

    <div class="section-head"><div><h2>教练绩效</h2><p>私教课时与产值取已签课记录，团课取已签到预约；评分为累计评价</p></div><el-tag effect="plain">{{ coaches.length }} 位教练</el-tag></div>
    <el-card shadow="never" class="table-card">
      <el-table :data="coaches" stripe>
        <el-table-column label="教练" min-width="150"><template #default="{row}"><strong>{{ row.coachName }}</strong></template></el-table-column>
        <el-table-column label="私教课时" width="110" prop="privateLessons" sortable/>
        <el-table-column label="私教产值" width="130" sortable><template #default="{row}">¥{{ money(row.privateRevenue) }}</template></el-table-column>
        <el-table-column label="团课签到" width="110" prop="groupAttendances" sortable/>
        <el-table-column label="评价数" width="100" prop="reviews" sortable/>
        <el-table-column label="平均评分" width="180"><template #default="{row}"><template v-if="row.averageRating != null"><el-rate :model-value="Number(row.averageRating)" disabled size="small"/><small class="muted">{{ row.averageRating }}</small></template><span v-else class="muted">暂无评价</span></template></el-table-column>
      </el-table>
      <el-empty v-if="!coaches.length" description="区间内没有教练数据" :image-size="60"/>
    </el-card>

    <div class="section-head">
      <div><h2>会员消费与流失风险</h2><p>风险分 = 最近到店距今 + 近 60 天到店次数 + 是否有有效权益；≥80 高风险</p></div>
      <div class="risk-actions"><el-switch v-model="riskOnly" active-text="只看高风险" @change="loadMembers"/><el-tag type="danger" effect="plain">高风险 {{ members.highRisk }}</el-tag><el-tag type="warning" effect="plain">中风险 {{ members.mediumRisk }}</el-tag></div>
    </div>
    <el-card shadow="never" class="table-card">
      <el-table :data="members.members" stripe max-height="520">
        <el-table-column label="会员" min-width="160"><template #default="{row}"><strong>{{ row.realName }}</strong><small>{{ row.memberNo }}<template v-if="row.phone"> · {{ row.phone }}</template></small></template></el-table-column>
        <el-table-column label="归属顾问" width="120"><template #default="{row}">{{ row.advisorName || '—' }}</template></el-table-column>
        <el-table-column label="消费" width="120" sortable :sort-by="'spending'"><template #default="{row}">¥{{ money(row.spending) }}</template></el-table-column>
        <el-table-column label="最近到店" width="130"><template #default="{row}">{{ row.lastVisit || '从未到店' }}</template></el-table-column>
        <el-table-column label="近 60 天到店" width="120" prop="visits60" sortable/>
        <el-table-column label="风险" width="140" sortable :sort-by="'riskScore'"><template #default="{row}"><el-tag :type="row.riskLevel==='HIGH'?'danger':row.riskLevel==='MEDIUM'?'warning':'success'" size="small">{{ riskText(row.riskLevel) }} {{ row.riskScore }}</el-tag></template></el-table-column>
        <el-table-column label="原因" min-width="240"><template #default="{row}"><small class="muted">{{ row.reason }}</small></template></el-table-column>
      </el-table>
      <el-empty v-if="!members.members.length" description="没有符合条件的会员" :image-size="60"/>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import WorkspaceHero from '@/components/WorkspaceHero.vue'
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import {
  getAnalyticsOverview, getCoachRanking, getMemberAnalytics,
  type AnalyticsOverview, type CoachRank, type MemberAnalytics,
} from '@/api/modules/analytics'

const loading = ref(false)
const riskOnly = ref(false)
const overview = ref<AnalyticsOverview>()
const coaches = ref<CoachRank[]>([])
const members = ref<MemberAnalytics>({ members: [], highRisk: 0, mediumRisk: 0 })

const fmt = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const daysAgo = (n: number) => { const d = new Date(); d.setDate(d.getDate() - n); return fmt(d) }
const range = ref<[string, string]>([daysAgo(29), fmt(new Date())])
const money = (v?: number | null) => (Number(v || 0)).toFixed(2)
const riskText = (v: string) => ({ HIGH: '高风险', MEDIUM: '中风险', LOW: '低风险' }[v] || v)
const params = computed(() => ({ from: range.value?.[0], to: range.value?.[1] }))

const loadError = ref('')
const loadMembers = async () => {
  try { members.value = (await getMemberAnalytics(riskOnly.value)).data }
  catch (e: any) { loadError.value = e?.message || '会员数据加载失败' }
}
const load = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const [o, c] = await Promise.all([getAnalyticsOverview(params.value), getCoachRanking(params.value)])
    overview.value = o.data
    coaches.value = c.data
  } catch (e: any) {
    // 任一接口失败时给出可读提示，避免整页空白让人以为"没有数据"
    loadError.value = e?.message || '经营数据加载失败，请稍后重试'
  } finally {
    loading.value = false
    await loadMembers()
  }
}
const quick = (n: number) => { range.value = [daysAgo(n - 1), fmt(new Date())]; load() }
onMounted(load)
</script>

<style scoped>
.analytics-page{max-width:1280px;margin:0 auto}.page-title{display:flex;align-items:center;justify-content:space-between;margin-bottom:20px;gap:16px;flex-wrap:wrap}.page-title>div>span{color:#3474ff;font-size:11px;font-weight:800;letter-spacing:1.8px}.page-title h1{margin:7px 0;color:#172033;font-size:29px}.page-title p{margin:0;color:#8792a5}.actions{display:flex;gap:8px;align-items:center}.metric-grid{display:grid;grid-template-columns:repeat(6,1fr);gap:12px;margin-bottom:8px}.metric-grid article{padding:16px;border:1px solid #e7ecf4;border-radius:15px;background:#fff}.metric-grid article.alert-card{border-color:#f6c9c9;background:#fff7f7}.metric-grid span,.metric-grid strong,.metric-grid small{display:block}.metric-grid span{color:#8792a5;font-size:12px}.metric-grid strong{margin:6px 0 4px;color:#172033;font-size:20px}.metric-grid small{color:#9aa5b8;font-size:11px}.section-head{display:flex;align-items:center;justify-content:space-between;margin:26px 0 12px;gap:12px;flex-wrap:wrap}.section-head h2{margin:0 0 5px;color:#1b263b}.section-head p{margin:0;color:#8792a5;font-size:12px}.risk-actions{display:flex;align-items:center;gap:12px}.table-card{border-radius:16px}.muted{color:#9aa5b8}.el-table small{display:block;color:#8b96a8;margin-top:3px}@media(max-width:1200px){.metric-grid{grid-template-columns:repeat(3,1fr)}}@media(max-width:760px){.metric-grid{grid-template-columns:repeat(2,1fr)}}
</style>
