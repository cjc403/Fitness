<template>
  <div class="dashboard" v-loading="loading">
    <section class="hero">
      <div><span class="eyebrow">GOOD {{ period }}, {{ userInfo?.realName || '管理员' }}</span><h1>今天也要让每一次训练更有价值。</h1><p>{{ today }} · {{ userInfo?.organizationName }} 经营概览</p></div>
      <div class="hero-actions"><el-button size="large" @click="$router.push('/courses')"><el-icon><Calendar /></el-icon> 查看排课</el-button><el-button type="primary" size="large" @click="$router.push('/bookings')"><el-icon><Plus /></el-icon> 新增预约</el-button></div>
    <el-alert v-if="overdueCount>0" type="warning" :closable="false" class="overdue-alert">
      <template #title><div class="overdue-title"><span>还有 <strong>{{ overdueCount }}</strong> 条预约已结束但未核销 —— 这些记录会一直挡住会员申请退款</span><el-button type="warning" size="small" @click="$router.push('/bookings')">去处理</el-button></div></template>
    </el-alert>
    </section>
    <section class="metric-grid wb-rise">
      <article><div class="metric-icon blue"><el-icon><UserFilled /></el-icon></div><div><span>会员总数</span><strong>{{ shown.totalMembers }}</strong><small><b>{{ data.activeMembers }}</b> 位正常会员</small></div></article>
      <article><div class="metric-icon green"><el-icon><Calendar /></el-icon></div><div><span>今日课程</span><strong>{{ shown.todaySchedules }}</strong><small>共 {{ data.courses }} 个课程模板</small></div></article>
      <article><div class="metric-icon orange"><el-icon><Tickets /></el-icon></div><div><span>待完成预约</span><strong>{{ shown.booked }}</strong><small>累计 {{ data.attended }} 次签到</small></div></article>
      <article><div class="metric-icon purple"><el-icon><TrendCharts /></el-icon></div><div><span>综合出勤率</span><strong>{{ shown.attendanceRate }}<em>%</em></strong><small>取消预约 {{ data.cancelled }} 次</small></div></article>
    </section>
    <section class="content-grid wb-rise">
      <el-card shadow="never" class="trend-card"><template #header><div class="card-head"><div><h3>近 7 天排课趋势</h3><p>课程资源安排概览</p></div><el-tag effect="plain">实时数据</el-tag></div></template><div class="chart"><div v-for="point in data.weeklyTrend" :key="point.date" class="bar-wrap"><span>{{ point.schedules }}</span><div class="bar" :style="{height: `${Math.max(12, point.schedules * 42)}px`}"/><small>{{ shortDate(point.date) }}</small></div></div></el-card>
      <el-card shadow="never" class="health-card"><template #header><div class="card-head"><div><h3>运营健康度</h3><p>会员与门店关键状态</p></div></div></template><div class="health-score"><el-progress type="dashboard" :percentage="healthScore" :stroke-width="10" color="#2768ff"><template #default="{percentage}"><strong>{{ percentage }}</strong><span>健康指数</span></template></el-progress></div><div class="health-list"><div><span><i class="green-dot"/>正常会员</span><b>{{ data.activeMembers }}</b></div><div><span><i class="orange-dot"/>暂停会员</span><b>{{ data.pausedMembers }}</b></div><div><span><i class="red-dot"/>风险会员</span><b>{{ data.blacklistedMembers }}</b></div><div><span><i class="blue-dot"/>运营场馆</span><b>{{ data.venues }}</b></div></div></el-card>
    </section>
    <section class="quick-section wb-rise"><div class="section-title"><h3>快捷工作台</h3><span>常用功能直达</span></div><div class="quick-grid"><button @click="$router.push('/members')"><el-icon><User /></el-icon><span>会员档案<small>新增与维护会员</small></span><el-icon><ArrowRight /></el-icon></button><button @click="$router.push('/courses')"><el-icon><Collection /></el-icon><span>课程排期<small>设置课程与教练</small></span><el-icon><ArrowRight /></el-icon></button><button @click="$router.push('/bookings')"><el-icon><CircleCheck /></el-icon><span>到店签到<small>处理预约与出勤</small></span><el-icon><ArrowRight /></el-icon></button><button @click="$router.push('/venues')"><el-icon><OfficeBuilding /></el-icon><span>场馆运营<small>维护连锁门店</small></span><el-icon><ArrowRight /></el-icon></button></div></section>
  </div>
</template>
<script setup lang="ts">
import { computed,onMounted,reactive,ref } from 'vue';import { useAuthStore } from '@/store/modules/auth';import { getDashboard,type DashboardData } from '@/api/modules/report';
import { listOverdueBookings,type OverdueBooking } from '@/api/modules/booking';import { hasPermission } from '@/utils/auth'
import { ArrowRight,Calendar,CircleCheck,Collection,OfficeBuilding,Plus,Tickets,TrendCharts,User,UserFilled } from '@element-plus/icons-vue'
const auth=useAuthStore(),userInfo=computed(()=>auth.userInfo),loading=ref(false);const overdue=ref<OverdueBooking[]>([]),overdueCount=computed(()=>overdue.value.length),canCheckIn=hasPermission('CHECKIN_WRITE');const data=reactive<DashboardData>({totalMembers:0,activeMembers:0,pausedMembers:0,blacklistedMembers:0,venues:0,courses:0,todaySchedules:0,booked:0,attended:0,cancelled:0,attendanceRate:0,weeklyTrend:[],generatedAt:''});const healthScore=computed(()=>data.totalMembers?Math.max(0,Math.round((data.activeMembers-data.blacklistedMembers)/data.totalMembers*100)):100);const today=new Date().toLocaleDateString('zh-CN',{year:'numeric',month:'long',day:'numeric',weekday:'long'});const period=new Date().getHours()<12?'MORNING':new Date().getHours()<18?'AFTERNOON':'EVENING';const shortDate=(v:string)=>new Date(v).toLocaleDateString('zh-CN',{month:'numeric',day:'numeric'});/* 数字滚动：数据到位后从 0 数到目标值（纯展示逻辑，不影响任何业务计算） */
const shown=reactive({totalMembers:0,todaySchedules:0,booked:0,attendanceRate:0})
const runCountUp=()=>{const keys=['totalMembers','todaySchedules','booked','attendanceRate'] as const
 const dur=650,start=performance.now()
 const tick=(now:number)=>{const k=Math.min(1,(now-start)/dur),e=1-Math.pow(1-k,3)
  keys.forEach(key=>{const target=Number((data as unknown as Record<string,number>)[key])||0
   shown[key]=key==='attendanceRate'?Number((target*e).toFixed(1)):Math.round(target*e)})
  if(k<1)requestAnimationFrame(tick)}
 requestAnimationFrame(tick)}
onMounted(async()=>{loading.value=true;try{Object.assign(data,(await getDashboard()).data)}catch{}finally{loading.value=false;runCountUp()}if(canCheckIn){try{overdue.value=(await listOverdueBookings()).data}catch{overdue.value=[]}}})
</script>
<style scoped>
.hero{display:flex;align-items:flex-end;justify-content:space-between;padding:10px 0 30px}.eyebrow{color:#2768ff;font-size:11px;font-weight:800;letter-spacing:1.6px}.hero h1{margin:7px 0 8px;color:#172033;font-size:30px;letter-spacing:-1px}.hero p{color:#8993a5}.hero-actions{display:flex;gap:10px}.metric-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:16px}.metric-grid article{display:flex;gap:16px;padding:21px;background:#fff;border:1px solid #e8edf5;border-radius:17px;box-shadow:0 5px 20px rgba(30,44,70,.04)}.metric-icon{display:grid;place-items:center;width:46px;height:46px;border-radius:13px;font-size:21px}.blue{background:#eaf1ff;color:#2768ff}.green{background:#e6f8f1;color:#20ad7d}.orange{background:#fff1e7;color:#f38335}.purple{background:#f1eafb;color:#8452d6}.metric-grid span{color:#7f8a9f;font-size:13px}.metric-grid strong{display:block;margin:5px 0 4px;color:#172033;font-size:27px}.metric-grid em{font-size:14px;font-style:normal}.metric-grid small{color:#9aa3b2}.metric-grid small b{color:#20ad7d}.content-grid{display:grid;grid-template-columns:2fr 1fr;gap:16px;margin-top:16px}.content-grid .el-card{border-radius:17px}.card-head{display:flex;align-items:center;justify-content:space-between}.card-head h3{color:#202a3d}.card-head p{margin-top:5px;color:#929bad;font-size:12px}.chart{height:220px;display:flex;align-items:flex-end;justify-content:space-around;padding:24px 12px 0}.bar-wrap{height:100%;display:flex;flex-direction:column;align-items:center;justify-content:flex-end;gap:8px;color:#6e7a90;font-size:12px}.bar{width:28px;max-height:150px;border-radius:7px 7px 3px 3px;background:linear-gradient(180deg,#2768ff,#73a0ff);box-shadow:0 8px 20px rgba(39,104,255,.2)}.health-card :deep(.el-card__body){display:flex;align-items:center;gap:22px}.health-score strong,.health-score span{display:block}.health-score strong{font-size:27px;color:#172033}.health-score span{font-size:11px;color:#8c96a8}.health-list{flex:1}.health-list div{display:flex;justify-content:space-between;padding:8px 0;color:#6e7a8f;font-size:13px}.health-list i{display:inline-block;width:7px;height:7px;margin-right:8px;border-radius:50%}.green-dot{background:#20ad7d}.orange-dot{background:#f38335}.red-dot{background:#e85d6a}.blue-dot{background:#2768ff}.health-list b{color:#1d273a}.overdue-alert{margin-bottom:18px;border-radius:14px}.overdue-title{display:flex;align-items:center;justify-content:space-between;gap:12px}.overdue-title strong{color:#d8871a}.quick-section{margin-top:24px}.section-title{display:flex;align-items:baseline;gap:12px;margin-bottom:13px}.section-title h3{color:#202a3d}.section-title span{color:#929bad;font-size:12px}.quick-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:14px}.quick-grid button{display:flex;align-items:center;gap:13px;padding:17px;border:1px solid #e8edf5;border-radius:14px;background:#fff;color:#2768ff;text-align:left;cursor:pointer;transition:.18s}.quick-grid button:hover{transform:translateY(-2px);border-color:#c7d8ff;box-shadow:0 8px 22px rgba(39,104,255,.08)}.quick-grid button>span{flex:1;color:#283349;font-weight:700}.quick-grid small{display:block;margin-top:4px;color:#929bad;font-weight:400}@media(max-width:1100px){.metric-grid,.quick-grid{grid-template-columns:repeat(2,1fr)}.content-grid{grid-template-columns:1fr}}@media(max-width:760px){.hero{align-items:flex-start;gap:18px;flex-direction:column}}

/* ===== 动漫风点缀（纯装饰，只加在留白处，不影响任何布局与交互）=====
   原则：装饰只在没有信息的地方；数据卡片与表格保持干净。 */
.hero{position:relative}
/* 欢迎区背后：极淡网点 + 品牌蓝光晕（呼应进入界面） */
.hero::before{
  content:"";position:absolute;left:-40px;top:-30px;right:40%;bottom:-10px;pointer-events:none;
  background-image:radial-gradient(rgba(72,132,214,.5) 1.1px,transparent 1.6px);
  background-size:7px 7px;opacity:.16;
  -webkit-mask-image:radial-gradient(circle at 22% 55%,#000 4%,transparent 68%);
  mask-image:radial-gradient(circle at 22% 55%,#000 4%,transparent 68%);
}
.hero::after{
  content:"";position:absolute;left:-90px;top:-70px;width:420px;height:260px;pointer-events:none;
  background:radial-gradient(circle at 40% 50%,rgba(88,152,255,.16),transparent 66%);
}
.hero>div{position:relative;z-index:1}
.hero h1{text-shadow:0 0 26px rgba(120,180,255,.22)}

/* 统计卡片：四角分镜小括号（进入界面登录卡片同款）+ 悬停微升起 */
.metric-grid article{position:relative;transition:transform .18s ease,box-shadow .18s ease}
.metric-grid article:hover{transform:translateY(-2px);box-shadow:0 12px 28px rgba(30,44,70,.1)}
.metric-grid article::before,.metric-grid article::after{
  content:"";position:absolute;width:14px;height:14px;border:2px solid rgba(64,132,224,.45);
  pointer-events:none;opacity:.9;
}
.metric-grid article::before{top:5px;left:5px;border-right:0;border-bottom:0;border-radius:10px 0 0 0}
.metric-grid article::after{bottom:5px;right:5px;border-left:0;border-top:0;border-radius:0 0 10px 0}

/* 其余卡片：只做悬停反馈，不加花纹（保持信息区干净） */
.trend-card,.health-card,.quick-grid>div{transition:transform .18s ease,box-shadow .18s ease}
.trend-card:hover,.health-card:hover,.quick-grid>div:hover{transform:translateY(-2px);box-shadow:0 12px 28px rgba(30,44,70,.1)}

@media (prefers-reduced-motion: reduce){
  .metric-grid article:hover,.trend-card:hover,.health-card:hover,.quick-grid>div:hover{transform:none}
}

/* ===== 全量改造：数字入场 + 图表增强 ===== */
.metric-grid strong{animation:wb-num .5s cubic-bezier(.22,.61,.36,1) both}
.metric-grid strong em{font-size:14px;font-style:normal}

/* 图表：淡网格线 + 基线 */
.chart{position:relative}
.chart::before{
  content:"";position:absolute;left:12px;right:12px;top:24px;bottom:34px;pointer-events:none;
  background-image:repeating-linear-gradient(to top,rgba(39,104,255,.08) 0 1px,transparent 1px 25%);
}
.chart::after{
  content:"";position:absolute;left:12px;right:12px;bottom:34px;height:1px;
  background:linear-gradient(90deg,rgba(39,104,255,.05),rgba(39,104,255,.22),rgba(39,104,255,.05));
}
.bar-wrap{position:relative;z-index:1}
/* 柱子从底部依次长出 */
.bar{transform-origin:50% 100%;animation:wb-bar .62s cubic-bezier(.22,.61,.36,1) both}
@keyframes wb-bar{from{transform:scaleY(0);opacity:.35}to{transform:scaleY(1);opacity:1}}
.bar-wrap:nth-child(1) .bar{animation-delay:.05s}
.bar-wrap:nth-child(2) .bar{animation-delay:.11s}
.bar-wrap:nth-child(3) .bar{animation-delay:.17s}
.bar-wrap:nth-child(4) .bar{animation-delay:.23s}
.bar-wrap:nth-child(5) .bar{animation-delay:.29s}
.bar-wrap:nth-child(6) .bar{animation-delay:.35s}
.bar-wrap:nth-child(7) .bar{animation-delay:.41s}
.bar-wrap:hover .bar{filter:brightness(1.08)}
.bar-wrap span{transition:transform .18s cubic-bezier(.22,.61,.36,1)}
.bar-wrap:hover span{transform:translateY(-3px) scale(1.06)}

/* 健康度环形：中心数字入场 */
.health-score strong{animation:wb-num .55s cubic-bezier(.22,.61,.36,1) both}

@media (prefers-reduced-motion: reduce){
  .bar,.bar-wrap span,.health-score strong,.metric-grid strong{animation:none;transition:none}
}
</style>
