<template>
  <div class="dashboard" v-loading="loading">
    <WorkspaceHero>
      <template #heading><span>GOOD {{ period }}, {{ userInfo?.realName || '管理员' }}</span><h1>今天也要让每一次训练更有价值。</h1><p>{{ today }} · {{ userInfo?.organizationName }} 经营概览</p></template>
      <template #actions><el-button @click="$router.push('/courses')"><el-icon><Calendar /></el-icon> 查看排课</el-button><el-button type="primary" @click="$router.push('/bookings')"><el-icon><Plus /></el-icon> 新增预约</el-button></template>
    </WorkspaceHero>
    <el-alert v-if="overdueCount>0" type="warning" :closable="false" class="overdue-alert">
      <template #title><div class="overdue-title"><span>还有 <strong>{{ overdueCount }}</strong> 条预约已结束但未核销 —— 这些记录会一直挡住会员申请退款</span><el-button type="warning" size="small" @click="$router.push('/bookings')">去处理</el-button></div></template>
    </el-alert>
    <section class="metric-grid wb-rise">
      <article><div class="metric-icon blue"><el-icon><UserFilled /></el-icon></div><div><span>会员总数</span><strong>{{ shown.totalMembers }}</strong><small><b>{{ data.activeMembers }}</b> 位正常会员</small></div></article>
      <article><div class="metric-icon green"><el-icon><Calendar /></el-icon></div><div><span>今日课程</span><strong>{{ shown.todaySchedules }}</strong><small>共 {{ data.courses }} 个课程模板</small></div></article>
      <article><div class="metric-icon orange"><el-icon><Tickets /></el-icon></div><div><span>待完成预约</span><strong>{{ shown.booked }}</strong><small>累计 {{ data.attended }} 次签到</small></div></article>
      <article><div class="metric-icon purple"><el-icon><TrendCharts /></el-icon></div><div><span>综合出勤率</span><strong>{{ shown.attendanceRate }}<em>%</em></strong><small>取消预约 {{ data.cancelled }} 次</small></div></article>
    </section>
    <section class="content-grid wb-rise">
      <el-card shadow="never" class="trend-card"><template #header><div class="card-head"><div><h3>近 7 天排课趋势</h3><p>课程资源安排概览</p></div><el-tag effect="plain">实时数据</el-tag></div></template><div v-if="data.weeklyTrend.length" class="chart" role="img" aria-label="近七天排课数量，柱高按实际课程数量等比例展示">
        <div class="chart-scale" aria-hidden="true"><span>{{ trendMaximum }} 节</span><span>{{ trendMaximum / 2 }}</span><span>0</span></div>
        <div class="chart-plot">
          <el-tooltip v-for="point in data.weeklyTrend" :key="point.date" :content="point.date + ' · ' + point.schedules + ' 节课程'" placement="top">
            <div class="bar-wrap" :aria-label="point.date + '，' + point.schedules + ' 节课程'">
              <span class="bar-value">{{ point.schedules }}</span>
              <div class="bar-track"><div v-if="point.schedules > 0" class="bar" :style="{height: trendBarPercent(point.schedules, trendMaximum) + '%'}" /><i v-else class="zero-point" /></div>
              <small>{{ shortDate(point.date) }}</small>
            </div>
          </el-tooltip>
        </div>
      </div><el-empty v-else description="暂无排课趋势" :image-size="72" /></el-card>
      <el-card shadow="never" class="health-card"><template #header><div class="card-head"><div><h3>运营健康度</h3><p>会员与门店关键状态</p></div></div></template><div class="health-score"><el-progress type="dashboard" :percentage="healthScore" :stroke-width="10" color="#2768ff"><template #default="{percentage}"><strong>{{ percentage }}</strong><span>健康指数</span></template></el-progress></div><div class="health-list"><div><span><i class="green-dot"/>正常会员</span><b>{{ data.activeMembers }}</b></div><div><span><i class="orange-dot"/>暂停会员</span><b>{{ data.pausedMembers }}</b></div><div><span><i class="red-dot"/>风险会员</span><b>{{ data.blacklistedMembers }}</b></div><div><span><i class="blue-dot"/>运营场馆</span><b>{{ data.venues }}</b></div></div></el-card>
    </section>
    <section class="quick-section wb-rise"><div class="section-title"><h3>快捷工作台</h3><span>常用功能直达</span></div><div class="quick-grid"><button @click="$router.push('/members')"><el-icon><User /></el-icon><span>会员档案<small>新增与维护会员</small></span><el-icon><ArrowRight /></el-icon></button><button @click="$router.push('/courses')"><el-icon><Collection /></el-icon><span>课程排期<small>设置课程与教练</small></span><el-icon><ArrowRight /></el-icon></button><button @click="$router.push('/bookings')"><el-icon><CircleCheck /></el-icon><span>到店签到<small>处理预约与出勤</small></span><el-icon><ArrowRight /></el-icon></button><button @click="$router.push('/venues')"><el-icon><OfficeBuilding /></el-icon><span>场馆运营<small>维护连锁门店</small></span><el-icon><ArrowRight /></el-icon></button></div></section>
  </div>
</template>
<script setup lang="ts">
import WorkspaceHero from '@/components/WorkspaceHero.vue'
import { workspaceMotionKey } from '@/utils/workspaceMotion'
import { trendBarPercent } from '@/utils/workspaceAppearance.mjs'
import { computed,inject,onMounted,onUnmounted,reactive,ref,watch } from 'vue';import { useAuthStore } from '@/store/modules/auth';import { getDashboard,type DashboardData } from '@/api/modules/report';
import { listOverdueBookings,type OverdueBooking } from '@/api/modules/booking';import { hasPermission } from '@/utils/auth'
import { ArrowRight,Calendar,CircleCheck,Collection,OfficeBuilding,Plus,Tickets,TrendCharts,User,UserFilled } from '@element-plus/icons-vue'
const auth=useAuthStore(),userInfo=computed(()=>auth.userInfo),loading=ref(false);const overdue=ref<OverdueBooking[]>([]),overdueCount=computed(()=>overdue.value.length),canCheckIn=hasPermission('CHECKIN_WRITE');const data=reactive<DashboardData>({totalMembers:0,activeMembers:0,pausedMembers:0,blacklistedMembers:0,venues:0,courses:0,todaySchedules:0,booked:0,attended:0,cancelled:0,attendanceRate:0,weeklyTrend:[],generatedAt:''});const healthScore=computed(()=>data.totalMembers?Math.max(0,Math.round((data.activeMembers-data.blacklistedMembers)/data.totalMembers*100)):100);const today=new Date().toLocaleDateString('zh-CN',{year:'numeric',month:'long',day:'numeric',weekday:'long'});const period=new Date().getHours()<12?'MORNING':new Date().getHours()<18?'AFTERNOON':'EVENING';const shortDate=(v:string)=>new Date(v).toLocaleDateString('zh-CN',{month:'numeric',day:'numeric'});/* 数字滚动：数据到位后从 0 数到目标值（纯展示逻辑，不影响任何业务计算） */
const shown=reactive({totalMembers:0,todaySchedules:0,booked:0,attendanceRate:0})
const trendMaximum = computed(() => Math.max(1, ...data.weeklyTrend.map(p => Number.isFinite(p.schedules) ? p.schedules : 0)))
const canAnimate = inject(workspaceMotionKey, computed(() => false))
const metricKeys = ['totalMembers','todaySchedules','booked','attendanceRate'] as const
let animationFrame = 0, disposed = false
const showActualValues = () => { metricKeys.forEach(key => { shown[key] = data[key] }) }
const stopCountUp = () => { cancelAnimationFrame(animationFrame); showActualValues() }
const runCountUp = () => {
  cancelAnimationFrame(animationFrame)
  if (disposed) return
  if (!canAnimate.value) { showActualValues(); return }
  const started = performance.now()
  const tick = (now: number) => {
    const progress = Math.min(1, (now - started) / 600), eased = 1 - Math.pow(1 - progress, 3)
    metricKeys.forEach(key => { shown[key] = key === 'attendanceRate' ? Number((data[key] * eased).toFixed(1)) : Math.round(data[key] * eased) })
    if (progress < 1) animationFrame = requestAnimationFrame(tick)
    else showActualValues()
  }
  animationFrame = requestAnimationFrame(tick)
}
watch(canAnimate, allowed => { if (!allowed) stopCountUp() })
onUnmounted(() => { disposed = true; cancelAnimationFrame(animationFrame) })

onMounted(async()=>{loading.value=true;try{Object.assign(data,(await getDashboard()).data)}catch{}finally{loading.value=false;runCountUp()}if(canCheckIn){try{overdue.value=(await listOverdueBookings()).data}catch{overdue.value=[]}}})
</script>
<style scoped>
.dashboard { width: 100%; }
.metric-grid { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 18px; }
.metric-grid article { display: flex; align-items: center; gap: 16px; padding: 23px 21px; background: #fff; border: 1px solid #dce7f4; border-radius: 18px; }
.metric-icon { display: grid; flex: none; place-items: center; width: 48px; height: 48px; border-radius: 15px; font-size: 21px; }
.blue { background: #e7f1ff; color: #397fe2; }.green { background: #e6f7f1; color: #269e85; }.orange { background: #fff0e4; color: #d88843; }.purple { background: #f0ebfb; color: #8871c3; }
.metric-grid span { color: #667d98; font-size: 12px; }.metric-grid strong { display: block; margin: 7px 0 5px; color: #203c59; font-size: 32px; font-weight: 800; }
.metric-grid em { font-size: 15px; font-style: normal; }.metric-grid small { color: #7e90a7; font-size: 11px; }.metric-grid small b { color: #269e85; }
.content-grid { display: grid; grid-template-columns: minmax(0,2fr) minmax(310px,1fr); gap: 18px; }
.card-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.card-head h3 { color: #234361; margin: 0; font-size: 18px; }.card-head p { margin-top: 5px; color: #7c91aa; font-size: 11px; }
.chart { display: flex; gap: 12px; height: 220px; padding-top: 12px; }
.chart-scale { display: flex; flex: none; width: 38px; flex-direction: column; justify-content: space-between; padding-top: 17px; padding-bottom: 18px; color: #8396ad; font-size: 10px; line-height: 12px; }
.chart-plot { position: relative; display: flex; align-items: flex-end; justify-content: space-around; gap: 10px; flex: 1; min-width: 0; }
.chart-plot::before { content: ''; position: absolute; inset: 23px 0 24px; pointer-events: none; background: repeating-linear-gradient(to top,transparent 0,transparent calc(25% - 1px),#e9f0f9 calc(25% - 1px),#e9f0f9 25%); border-block: 1px solid #e9f0f9; }
.bar-wrap { position: relative; display: flex; flex-direction: column; align-items: center; flex: 1; min-width: 0; height: 100%; gap: 6px; color: #6883a3; font-size: 11px; }
.bar-value { flex: none; height: 17px; }.bar-track { display: flex; align-items: flex-end; justify-content: center; position: relative; width: 100%; flex: 1; min-height: 0; }
.bar { width: clamp(15px,38%,35px); border-radius: 8px 8px 2px 2px; background: linear-gradient(180deg,#438aeb,#8abaf8); border: 1px solid #4284d924; box-shadow: 0 7px 16px #4b8ee329; transform-origin: bottom; animation: wb-bar .5s ease both; }
.zero-point { display: block; width: 6px; height: 6px; border-radius: 50%; background: #afc8e7; }.bar-wrap small { height: 18px; font-size: 10px; white-space: nowrap; }
@keyframes wb-bar { from { transform: scaleY(0); opacity: .5; } to { transform: scaleY(1); opacity: 1; } }
.health-card :deep(.el-card__body) { display: flex; align-items: center; justify-content: center; gap: 22px; min-height: 224px; }
.health-score strong,.health-score span { display: block; }.health-score strong { font-size: 30px; font-weight: 800; color: #254866; }.health-score span { font-size: 11px; color: #7c91aa; }
.health-list { flex: 1; max-width: 220px; }.health-list div { display: flex; justify-content: space-between; gap: 12px; padding: 10px 0; color: #667e98; font-size: 12px; }.health-list i { display: inline-block; width: 7px; height: 7px; margin-right: 8px; border-radius: 50%; }
.green-dot { background: #269e85; }.orange-dot { background: #d88843; }.red-dot { background: #e2637a; }.blue-dot { background: #397fe2; }.health-list b { color: #254866; }
.overdue-alert { margin-bottom: 18px; }.overdue-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.overdue-title strong { color: #bd7a21; }
.quick-section { margin-top: 26px; }.section-title { display: flex; align-items: baseline; gap: 12px; margin-bottom: 14px; }.section-title h3 { color: #234361; margin: 0; font-size: 18px; }.section-title span { color: #7c91aa; font-size: 11px; }
.quick-grid { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 16px; }.quick-grid button { display: flex; align-items: center; gap: 13px; padding: 20px; border: 1px solid #dce7f4; border-radius: 16px; background: #fff; color: #4386d7; text-align: left; cursor: pointer; transition: .18s; }
.quick-grid button>span { flex: 1; color: #2d4e6b; font-weight: 700; }.quick-grid small { display: block; margin-top: 5px; color: #7d91aa; font-size: 11px; font-weight: 400; }
@media (max-width: 1200px) { .content-grid { grid-template-columns: minmax(0,1fr); }.health-card :deep(.el-card__body) { min-height: 180px; }.health-list { max-width: 420px; } }
@media (max-width: 1000px) { .metric-grid,.quick-grid { grid-template-columns: repeat(2,minmax(0,1fr)); } }
@media (max-width: 540px) { .metric-grid { gap: 10px; }.metric-grid article { padding: 15px 12px; gap: 9px; }.metric-icon { width: 34px; height: 34px; border-radius: 11px; font-size: 17px; }.metric-grid strong { font-size: 25px; }.metric-grid small { font-size: 10px; }.quick-grid { grid-template-columns: minmax(0,1fr); }.health-card :deep(.el-card__body) { flex-wrap: wrap; }.chart { gap: 5px; }.chart-plot { gap: 2px; }.bar-wrap small { font-size: 9px; } }
@media (prefers-reduced-motion: reduce) { .bar { animation: none; } }
</style>
