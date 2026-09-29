<template>
  <div class="week-page">
    <div class="page-title">
      <div><span>SCHEDULE BOARD</span><h1>周课表</h1><p>按周查看排课与占用，一眼看出空档与冲突。</p></div>
      <div class="actions">
        <el-button @click="shift(-1)"><el-icon><ArrowLeft/></el-icon> 上一周</el-button>
        <el-button @click="goToday">本周</el-button>
        <el-button @click="shift(1)">下一周 <el-icon><ArrowRight/></el-icon></el-button>
        <el-button type="primary" @click="$router.push('/courses')">去排课</el-button>
      </div>
    </div>

    <div class="controls">
      <el-radio-group v-model="mode">
        <el-radio-button v-if="canPickResource" value="ROOM">按教室</el-radio-button>
        <el-radio-button v-if="canPickCoach" value="COACH">按教练</el-radio-button>
        <el-radio-button value="MINE">我的课</el-radio-button>
      </el-radio-group>
      <el-select v-if="mode !== 'MINE'" v-model="selectedId" style="width:220px" :placeholder="mode === 'ROOM' ? '选择教室' : '选择教练'" clearable>
        <el-option v-for="o in resources" :key="o.id" :label="o.name" :value="o.id"/>
      </el-select>
      <span class="range">{{ rangeLabel }}</span>
      <el-tag effect="plain">本周 {{ visibleSchedules.length }} 场</el-tag>
      <el-tag v-if="mode !== 'MINE' && selectedId" :type="occupancyRate > 60 ? 'warning' : 'success'" effect="plain">
        {{ mode === 'ROOM' ? '教室' : '教练' }}占用 {{ occupancyRate }}%
      </el-tag>
    </div>

    <el-card shadow="never" class="grid-card" v-loading="loading">
      <div class="tt">
        <div class="tt-corner"></div>
        <div v-for="d in days" :key="d.date" class="tt-day" :class="{today: d.date === today}">
          <strong>{{ d.weekday }}</strong><small>{{ d.label }}</small>
        </div>
        <div class="tt-hours" :style="{height: gridHeight + 'px'}">
          <div v-for="m in marks" :key="m.label" class="tt-hour-label" :style="{top: m.top + 'px'}">{{ m.label }}</div>
        </div>
        <div v-for="d in days" :key="d.date" class="tt-col" :style="{height: gridHeight + 'px'}">
          <div v-for="m in marks" :key="m.label" class="tt-line" :style="{top: m.top + 'px'}"></div>
          <div v-for="s in schedulesOf(d.date)" :key="s.id" class="tt-block" :class="{mine: s.coachId === myUserId}"
               :style="blockStyle(s)" @click="openDetail(s)">
            <strong>{{ s.course?.name }}</strong>
            <small>{{ timeText(s) }}</small>
            <small class="line2">{{ s.coachName }}<template v-if="s.room?.name"> · {{ s.room.name }}</template></small>
          </div>
        </div>
      </div>
      <el-empty v-if="!loading && !visibleSchedules.length" description="本周没有排课（换个人/教室看看，或去排课）" :image-size="70"/>
    </el-card>

    <el-dialog v-model="detailVisible" title="排课详情" width="440px">
      <div v-if="current" class="detail">
        <div><span>课程</span><strong>{{ current.course?.name }}</strong></div>
        <div><span>时间</span><strong>{{ fullTime(current) }}</strong></div>
        <div><span>教练</span><strong>{{ current.coachName || '未绑定账号' }}</strong></div>
        <div><span>教室</span><strong>{{ current.room?.name || '未指定' }}</strong></div>
        <div><span>预约</span><strong>{{ current.enrolledCount }} / {{ current.maxStudents }}</strong></div>
        <div><span>状态</span><strong>{{ current.status === 'SCHEDULED' ? '可预约' : current.status }}</strong></div>
      </div>
      <template #footer><el-button @click="detailVisible = false">关闭</el-button><el-button type="primary" @click="$router.push('/courses')">去编辑</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { listSchedules, type Schedule } from '@/api/modules/course'
import { listRooms, type Room } from '@/api/modules/room'
import { listCoaches, type CoachOption } from '@/api/modules/course'
import { hasPermission } from '@/utils/auth'
import { useAuthStore } from '@/store/modules/auth'

const HOUR_PX = 46
const DAY_START = 8
const DAY_END = 22

const auth = useAuthStore()
const myUserId = computed(() => auth.userInfo?.id)
const canPickResource = hasPermission('VENUE_READ')
const canPickCoach = hasPermission('SCHEDULE_WRITE')

const mode = ref<'ROOM' | 'COACH' | 'MINE'>(canPickResource ? 'ROOM' : 'MINE')
const selectedId = ref<number | undefined>(undefined)
const loading = ref(false)
const schedules = ref<Schedule[]>([])
const rooms = ref<Room[]>([])
const coaches = ref<CoachOption[]>([])
const anchor = ref(startOfWeek(new Date()))

const detailVisible = ref(false)
const current = ref<Schedule | null>(null)

const resources = computed(() => mode.value === 'ROOM'
  ? rooms.value.map(r => ({ id: r.id, name: r.name }))
  : coaches.value.map(c => ({ id: c.id, name: c.name })))

const from = computed(() => fmtDate(anchor.value))
const to = computed(() => fmtDate(addDays(anchor.value, 6)))
const rangeLabel = computed(() => `${from.value} ~ ${to.value}`)
const today = computed(() => fmtDate(new Date()))

const days = computed(() => Array.from({ length: 7 }, (_, i) => {
  const d = addDays(anchor.value, i)
  return {
    date: fmtDate(d),
    label: `${d.getMonth() + 1}/${d.getDate()}`,
    weekday: ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][d.getDay()],
  }
}))

/** 纵轴范围：优先贴合本周实际排课，没有排课时用 08:00–22:00。 */
const windowRange = computed(() => {
  const list = visibleSchedules.value
  if (!list.length) return { start: DAY_START, end: DAY_END }
  let start = DAY_END, end = DAY_START
  for (const s of list) {
    const sh = new Date(s.scheduledStartTime).getHours()
    const eh = new Date(s.scheduledEndTime).getHours() + (new Date(s.scheduledEndTime).getMinutes() > 0 ? 1 : 0)
    start = Math.min(start, Math.max(0, sh))
    end = Math.max(end, Math.min(24, eh))
  }
  return { start, end: Math.max(end, start + 1) }
})
const gridHeight = computed(() => (windowRange.value.end - windowRange.value.start) * HOUR_PX)
const marks = computed(() => Array.from({ length: windowRange.value.end - windowRange.value.start + 1 }, (_, i) => {
  const hour = windowRange.value.start + i
  return { label: `${String(hour).padStart(2, '0')}:00`, top: i * HOUR_PX }
}))

const visibleSchedules = computed(() => {
  if (mode.value === 'MINE') return schedules.value.filter(s => s.coachId === myUserId.value)
  if (!selectedId.value) return schedules.value
  return schedules.value.filter(s => mode.value === 'ROOM' ? s.roomId === selectedId.value : s.coachId === selectedId.value)
})
const occupancyRate = computed(() => {
  if (!selectedId.value || mode.value === 'MINE') return 0
  const minutes = visibleSchedules.value.reduce((sum, s) => {
    const ms = new Date(s.scheduledEndTime).getTime() - new Date(s.scheduledStartTime).getTime()
    return sum + Math.max(0, ms / 60000)
  }, 0)
  const capacity = 7 * (DAY_END - DAY_START) * 60
  return Math.min(100, Math.round(minutes * 100 / capacity))
})

const schedulesOf = (date: string) => visibleSchedules.value.filter(s => s.scheduledStartTime.slice(0, 10) === date)
const blockStyle = (s: Schedule) => {
  const start = new Date(s.scheduledStartTime), end = new Date(s.scheduledEndTime)
  const base = windowRange.value.start * 60
  const topMin = Math.max(base, start.getHours() * 60 + start.getMinutes()) - base
  const endMin = Math.min(windowRange.value.end * 60, end.getHours() * 60 + end.getMinutes()) - base
  const height = Math.max(26, ((endMin - topMin) / 60) * HOUR_PX - 4)
  return { top: (topMin / 60) * HOUR_PX + 2 + 'px', height: height + 'px' }
}
const timeText = (s: Schedule) => `${hhmm(s.scheduledStartTime)}-${hhmm(s.scheduledEndTime)}`
const fullTime = (s: Schedule) => `${s.scheduledStartTime.slice(0, 10)} ${timeText(s)}`
const hhmm = (v: string) => v.slice(11, 16)
const openDetail = (s: Schedule) => { current.value = s; detailVisible.value = true }

const load = async () => {
  loading.value = true
  try {
    schedules.value = (await listSchedules({ from: from.value, to: to.value })).data
    if (canPickResource && !rooms.value.length) rooms.value = (await listRooms()).data
    if (canPickCoach && !coaches.value.length) coaches.value = (await listCoaches()).data
    if (mode.value !== 'MINE' && !selectedId.value && resources.value.length) selectedId.value = resources.value[0].id
  } finally { loading.value = false }
}
const shift = (weeks: number) => { anchor.value = addDays(anchor.value, weeks * 7); load() }
const goToday = () => { anchor.value = startOfWeek(new Date()); load() }
watch(mode, () => { selectedId.value = resources.value.length ? resources.value[0].id : undefined })
onMounted(load)

function startOfWeek(d: Date) {
  const copy = new Date(d)
  const day = copy.getDay() || 7
  copy.setDate(copy.getDate() - day + 1)
  copy.setHours(0, 0, 0, 0)
  return copy
}
function addDays(d: Date, n: number) { const c = new Date(d); c.setDate(c.getDate() + n); return c }
function fmtDate(d: Date) { return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}` }
</script>

<style scoped>
.week-page{max-width:1280px;margin:0 auto}.page-title{display:flex;align-items:center;justify-content:space-between;margin-bottom:20px}.page-title>div>span{color:#3474ff;font-size:11px;font-weight:800;letter-spacing:1.8px}.page-title h1{margin:7px 0;color:#172033;font-size:29px}.page-title p{margin:0;color:#8792a5}.actions{display:flex;gap:8px}.controls{display:flex;align-items:center;gap:14px;margin-bottom:16px;flex-wrap:wrap}.range{color:#5f6d85;font-size:13px}.grid-card{border-radius:16px}.tt{display:grid;grid-template-columns:58px repeat(7,1fr);gap:0}.tt-corner{border-bottom:1px solid #eef1f6}.tt-day{padding:8px 0 10px;text-align:center;border-bottom:1px solid #eef1f6}.tt-day strong{display:block;color:#243249;font-size:13px}.tt-day small{color:#9aa5b8;font-size:11px}.tt-day.today strong{color:#2d6bf3}.tt-hours{position:relative}.tt-hour-label{position:absolute;right:8px;transform:translateY(-50%);color:#9aa5b8;font-size:11px}.tt-col{position:relative;border-left:1px solid #f2f5fa}.tt-line{position:absolute;left:0;right:0;border-top:1px dashed #f2f5fa}.tt-block{position:absolute;left:4px;right:4px;padding:5px 7px;border-radius:8px;background:linear-gradient(135deg,#eaf1ff,#dbe7ff);border-left:3px solid #2d6bf3;color:#243249;font-size:11px;line-height:1.35;overflow:hidden;cursor:pointer;transition:.15s}.tt-block:hover{transform:translateY(-1px);box-shadow:0 6px 16px rgba(45,107,243,.16)}.tt-block strong{display:block;font-size:12px}.tt-block small{display:block;color:#5b6b86}.tt-block .line2{color:#7c8aa5}.tt-block.mine{background:linear-gradient(135deg,#e8f8f2,#d6f2e7);border-left-color:#1eb88a}.detail>div{display:flex;justify-content:space-between;padding:9px 0;border-bottom:1px dashed #eef1f6}.detail span{color:#8792a5;font-size:13px}.detail strong{color:#172033}@media(max-width:1100px){.tt{grid-template-columns:44px repeat(7,minmax(90px,1fr));overflow-x:auto}}
</style>
