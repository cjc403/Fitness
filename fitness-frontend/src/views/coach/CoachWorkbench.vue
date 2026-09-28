<template>
  <div class="coach-page" v-loading="loading">
    <div class="page-title"><div><span>COACH WORKBENCH</span><h1>{{ userInfo?.realName }}的教学工作台</h1><p>查看自己的课程安排、学员名单并完成到店签到。</p></div><el-button @click="load"><el-icon><Refresh /></el-icon> 刷新数据</el-button></div>
    <div class="stats-grid">
      <article><div class="icon blue"><el-icon><Calendar /></el-icon></div><div><span>今日课程</span><strong>{{ summary.todaySchedules }}</strong></div></article>
      <article><div class="icon green"><el-icon><Clock /></el-icon></div><div><span>待上课程</span><strong>{{ summary.upcomingSchedules }}</strong></div></article>
      <article><div class="icon orange"><el-icon><User /></el-icon></div><div><span>待到店学员</span><strong>{{ summary.bookedStudents }}</strong></div></article>
      <article><div class="icon purple"><el-icon><CircleCheck /></el-icon></div><div><span>已签到人次</span><strong>{{ summary.attendedStudents }}</strong></div></article>
      <article><div class="icon blue"><el-icon><CircleCheck /></el-icon></div><div><span>完成课次</span><strong>{{ summary.completedSessions }}</strong></div></article>
      <article><div class="icon green"><el-icon><TrendCharts /></el-icon></div><div><span>已授课时</span><strong>{{ summary.deliveredHours }}h</strong></div></article>
      <article><div class="icon purple"><el-icon><TrendCharts /></el-icon></div><div><span>服务产值</span><strong>¥{{ money(summary.serviceRevenue) }}</strong></div></article>
      <article><div class="icon orange"><el-icon><Money /></el-icon></div><div><span>已签课提成</span><strong>¥{{ money(summary.estimatedCommission) }}</strong></div></article>
    </div>

    <div class="section-head"><div><h2>我的排课</h2><p>最近 7 天至未来 30 天</p></div><el-tag effect="plain">{{ schedules.length }} 场</el-tag></div>
    <el-collapse v-model="expanded" class="schedule-list">
      <el-collapse-item v-for="schedule in schedules" :key="schedule.id" :name="schedule.id">
        <template #title>
          <div class="schedule-title"><div class="date-box"><strong>{{ day(schedule.startTime) }}</strong><small>{{ month(schedule.startTime) }}</small></div><div><strong>{{ schedule.courseName }}</strong><small>{{ dateTime(schedule.startTime) }} - {{ time(schedule.endTime) }}</small></div><div class="schedule-meta"><el-tag :type="schedule.status==='SCHEDULED'?'success':'info'">{{ schedule.status==='SCHEDULED'?'待上课':'已结束' }}</el-tag><span>{{ schedule.students.length }}/{{ schedule.maxStudents }} 人</span></div></div>
        </template>
        <el-table :data="schedule.students" class="student-table">
          <el-table-column label="学员" min-width="160"><template #default="{row}"><div class="student"><span>{{ row.realName.slice(0,1) }}</span><div><strong>{{ row.realName }}</strong><small>{{ row.memberNo }}</small></div></div></template></el-table-column>
          <el-table-column prop="phone" label="手机号" min-width="140" />
          <el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="studentStatusType(row.status)">{{ studentStatusText(row.status) }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="170"><template #default="{row}"><el-button v-if="row.status==='BOOKED'" link type="success" @click="checkStudent(row)">签到</el-button><el-button v-if="row.status==='BOOKED'&&new Date(schedule.startTime)<new Date()" link type="warning" @click="noShow(row)">爽约</el-button><span v-if="row.status!=='BOOKED'" class="muted">—</span></template></el-table-column>
        </el-table>
        <el-empty v-if="!schedule.students.length" description="本场课程暂无预约学员" :image-size="60" />
      </el-collapse-item>
    </el-collapse>
    <el-empty v-if="!loading&&!schedules.length" description="当前没有分配给你的排课" />
    <p class="muted">上方服务产值与提成为已确认私教消课累计金额；团课和历史未关联预约不计入实际私教结算。</p>
    <div class="section-head"><div><h2>我的评分</h2><p>会员对我所授课程的评价（只有已签到的课能评价）</p></div><el-tag v-if="reviewSummary" effect="plain">{{ reviewSummary.reviewCount }} 条</el-tag></div>
    <el-card v-if="reviewSummary && reviewSummary.reviewCount" shadow="never" class="review-card">
      <div class="review-overview">
        <div class="review-score"><strong>{{ reviewSummary.averageRating ?? '—' }}</strong><span>平均分 / 5</span><el-rate :model-value="Number(reviewSummary.averageRating || 0)" disabled /></div>
        <ul class="review-dist"><li v-for="star in [5,4,3,2,1]" :key="star"><span>{{ star }} 星</span><div class="dist-bar"><i :style="{width: distPercent(star)}"/></div><small>{{ reviewSummary.distribution[star] || 0 }}</small></li></ul>
      </div>
      <ul class="recent-reviews"><li v-for="r in reviewSummary.recent" :key="r.id"><div class="recent-head"><strong>{{ r.courseName || '课程' }}</strong><el-rate :model-value="r.rating" disabled /></div><p v-if="r.content">{{ r.content }}</p><small>{{ r.memberName || '会员' }} · {{ reviewDate(r.createdAt) }}<template v-if="r.tags"> · {{ r.tags }}</template></small></li></ul>
    </el-card>
    <el-empty v-else description="还没有收到评价（会员只有在签到的课后才能评价）" :image-size="60" />
    <TrainingPanel @changed="load" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Calendar, CircleCheck, Clock, Money, Refresh, TrendCharts, User } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCoachWorkbench, type CoachSchedule, type CoachStudentBooking } from '@/api/modules/coach'
import { checkIn, markNoShow } from '@/api/modules/booking'
import { useAuthStore } from '@/store/modules/auth'
import TrainingPanel from '@/components/TrainingPanel.vue'
import { getCoachReviewSummary, type RatingSummary } from '@/api/modules/review'

const authStore = useAuthStore()
const userInfo = computed(() => authStore.userInfo)
const loading = ref(false)
const schedules = ref<CoachSchedule[]>([])
const expanded = ref<number[]>([])
const summary = ref({ todaySchedules: 0, upcomingSchedules: 0, bookedStudents: 0, attendedStudents: 0, completedSessions:0, deliveredHours:0, serviceRevenue:0, estimatedCommission:0 })
const reviewSummary = ref<RatingSummary>()

const reviewDate=(v?:string)=>v?new Date(v).toLocaleDateString('zh-CN',{month:'2-digit',day:'2-digit'}):'—';const distPercent=(star:number)=>{const total=reviewSummary.value?.reviewCount||0;const n=reviewSummary.value?.distribution?.[star]||0;return total?Math.round(n/total*100)+'%':'0%'}
const load = async () => {
  loading.value = true
  try {
    const response = await getCoachWorkbench()
    summary.value = response.data.summary
    schedules.value = response.data.schedules
    // 评分接口与工作台分别失败时互不影响
    try { reviewSummary.value = (await getCoachReviewSummary()).data } catch { reviewSummary.value = undefined }
    if (!expanded.value.length && schedules.value.length) expanded.value = [schedules.value[0].id]
  } finally { loading.value = false }
}

const checkStudent = async (student: CoachStudentBooking) => {
  await ElMessageBox.confirm(`确认 ${student.realName} 已到店参加课程？`, '学员签到', { type: 'success' })
  await checkIn(student.bookingId)
  ElMessage.success('签到成功')
  await load()
}
const noShow = async (student: CoachStudentBooking) => { await ElMessageBox.confirm(`确认 ${student.realName} 未到课并记录爽约处罚？`,'爽约确认',{type:'warning'});await markNoShow(student.bookingId);ElMessage.success('已记录爽约');await load() }

const asDate = (value: string) => new Date(value)
const day = (value: string) => String(asDate(value).getDate()).padStart(2, '0')
const month = (value: string) => `${asDate(value).getMonth() + 1}月`
const time = (value: string) => asDate(value).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
const dateTime = (value: string) => `${asDate(value).toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' })} ${time(value)}`
const money = (value: number) => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const studentStatusText = (value: string) => ({ BOOKED:'待签到', ATTENDED:'已签到', NO_SHOW:'已爽约' }[value] || value)
const studentStatusType = (value: string) => value === 'ATTENDED' ? 'success' : value === 'NO_SHOW' ? 'danger' : 'warning'
onMounted(load)
</script>

<style scoped>
.coach-page{max-width:1200px;margin:0 auto}.page-title{display:flex;align-items:center;justify-content:space-between;margin-bottom:24px}.page-title>div>span{color:#3474ff;font-size:11px;font-weight:800;letter-spacing:1.8px}.page-title h1{margin:7px 0;color:#172033;font-size:29px}.page-title p,.section-head p{margin:0;color:#8792a5}.stats-grid{display:grid;grid-template-columns:repeat(6,1fr);gap:12px}.stats-grid article{display:flex;align-items:center;gap:12px;padding:18px;border:1px solid #e7ecf4;border-radius:17px;background:#fff}.icon{display:grid;place-items:center;flex:none;width:42px;height:42px;border-radius:13px;font-size:19px}.icon.blue{background:#eaf1ff;color:#2e70ff}.icon.green{background:#e6f8f2;color:#1eb88a}.icon.orange{background:#fff0e6;color:#f27b31}.icon.purple{background:#f2eafd;color:#8c5be0}.stats-grid span,.stats-grid strong{display:block}.stats-grid span{color:#8792a5;font-size:11px}.stats-grid strong{margin-top:4px;color:#172033;font-size:21px}.section-head{display:flex;align-items:center;justify-content:space-between;margin:28px 0 14px}.section-head h2{margin:0 0 5px;color:#1b263b}.section-head p{font-size:12px}.schedule-list{border:0}.schedule-list :deep(.el-collapse-item){margin-bottom:13px;border:1px solid #e7ecf4;border-radius:16px;background:#fff;overflow:hidden}.schedule-list :deep(.el-collapse-item__header){height:auto;padding:16px 20px;border:0}.schedule-list :deep(.el-collapse-item__wrap){border:0}.schedule-list :deep(.el-collapse-item__content){padding:0 20px 18px}.schedule-title{display:flex;align-items:center;width:100%;gap:14px}.date-box{display:grid;place-items:center;flex:none;width:52px;height:52px;border-radius:13px;background:#edf3ff;color:#2868f5}.date-box strong,.date-box small,.schedule-title>div:nth-child(2)>strong,.schedule-title>div:nth-child(2)>small{display:block}.date-box strong{font-size:19px;line-height:1}.date-box small{margin-top:3px;font-size:10px}.schedule-title>div:nth-child(2)>small{margin-top:5px;color:#8792a5}.schedule-meta{display:flex;align-items:center;gap:15px;margin-left:auto;margin-right:16px;color:#6f7c92}.student-table{border-top:1px solid #edf0f5}.student{display:flex;align-items:center;gap:10px}.student>span{display:grid;place-items:center;width:34px;height:34px;border-radius:10px;background:#eaf1ff;color:#2868f5;font-weight:800}.student strong,.student small{display:block}.student small{margin-top:3px;color:#8b96a8}.muted{color:#b4bbc7}@media(max-width:115.review-card{margin-bottom:24px;border-radius:16px}.review-overview{display:flex;align-items:center;gap:36px;padding-bottom:16px;border-bottom:1px solid #eef1f6}.review-score{text-align:center}.review-score strong{display:block;font-size:32px;color:#172033}.review-score span{display:block;margin:4px 0 6px;color:#8792a5;font-size:12px}.review-dist{flex:1;list-style:none;margin:0;padding:0}.review-dist li{display:flex;align-items:center;gap:10px;padding:3px 0;color:#6f7c92;font-size:12px}.review-dist li>span{width:34px}.dist-bar{flex:1;height:7px;border-radius:4px;background:#eef2f9;overflow:hidden}.dist-bar i{display:block;height:100%;border-radius:4px;background:linear-gradient(90deg,#f7b955,#f38335)}.review-dist small{width:26px;text-align:right}.recent-reviews{list-style:none;margin:14px 0 0;padding:0}.recent-reviews li{padding:10px 0;border-bottom:1px dashed #eef1f6}.recent-reviews li:last-child{border-bottom:0}.recent-head{display:flex;align-items:center;justify-content:space-between}.recent-head strong{color:#243249}.recent-reviews p{margin:6px 0 3px;color:#5f6d85;font-size:13px}.recent-reviews small{color:#9aa5b8;font-size:12px}0px){.stats-grid{grid-template-columns:repeat(3,1fr)}}@media(max-width:700px){.page-title{align-items:flex-end}.page-title p{display:none}.stats-grid{grid-template-columns:1fr 1fr}.schedule-meta span{display:none}}
@media(min-width:1151px){.stats-grid{grid-template-columns:repeat(4,1fr)}}
</style>
