<template>
  <div class="bmp">
    <div class="bmp-head">
      <div><h3>训练目标</h3><p>{{ headerHint }}</p></div>
      <el-radio-group v-model="goal" size="small" @change="saveGoal">
        <el-radio-button value="FAT_LOSS">减脂</el-radio-button>
        <el-radio-button value="MUSCLE_GAIN">增肌</el-radio-button>
        <el-radio-button value="POSTURE">体态</el-radio-button>
        <el-radio-button value="ENDURANCE">体能</el-radio-button>
        <el-radio-button value="NONE">未设定</el-radio-button>
      </el-radio-group>
    </div>

    <div v-if="trend && trend.changes.length" class="change-row">
      <div v-for="c in trend.changes" :key="c.metric" class="change-card">
        <span>{{ c.metric }}</span>
        <strong>{{ fmt(c.from) }} → {{ fmt(c.to) }}</strong>
        <small :class="deltaClass(c)">{{ deltaText(c) }}</small>
      </div>
    </div>

    <div class="chart-block">
      <div class="chart-head">
        <el-radio-group v-model="metric" size="small">
          <el-radio-button value="weightKg">体重</el-radio-button>
          <el-radio-button value="bodyFatRate">体脂率</el-radio-button>
          <el-radio-button value="muscleKg">肌肉量</el-radio-button>
          <el-radio-button value="waistCm">腰围</el-radio-button>
        </el-radio-group>
        <span class="muted">共 {{ trend?.recordCount || 0 }} 次测量</span>
      </div>
      <svg v-if="chart" :viewBox="`0 0 ${chart.w} ${chart.h}`" class="chart" preserveAspectRatio="none">
        <g v-for="g in chart.grid" :key="g.label">
          <line :x1="chart.padL" :x2="chart.w - chart.padR" :y1="g.y" :y2="g.y" stroke="#eef2f9" stroke-width="1"/>
          <text :x="chart.padL - 8" :y="g.y + 4" text-anchor="end" font-size="11" fill="#9aa5b8">{{ g.label }}</text>
        </g>
        <path :d="chart.area" fill="rgba(45,107,243,.08)"/>
        <path :d="chart.path" fill="none" stroke="#2d6bf3" stroke-width="2.5" stroke-linejoin="round"/>
        <g v-for="(d, i) in chart.dots" :key="i">
          <circle :cx="d.x" :cy="d.y" r="4" fill="#fff" stroke="#2d6bf3" stroke-width="2"/>
        </g>
        <g v-if="chart.first">
          <text :x="chart.first.x" :y="chart.h - 8" text-anchor="start" font-size="11" fill="#9aa5b8">{{ chart.first.d }}</text>
          <text :x="chart.first.x" :y="chart.first.y - 10" text-anchor="start" font-size="11" fill="#2d6bf3">{{ chart.first.v }}</text>
        </g>
        <g v-if="chart.last">
          <text :x="chart.last.x" :y="chart.h - 8" text-anchor="end" font-size="11" fill="#9aa5b8">{{ chart.last.d }}</text>
          <text :x="chart.last.x" :y="chart.last.y - 10" text-anchor="end" font-size="11" fill="#2d6bf3">{{ chart.last.v }}</text>
        </g>
      </svg>
      <el-empty v-else :description="points.length ? '该指标至少需要两次测量才能画出趋势' : '还没有体测记录，先在下方录入第一条'" :image-size="60" />
    </div>

    <el-card shadow="never" class="form-card">
      <template #header><div class="form-head"><strong>{{ editingId ? '修正 ' + form.measuredOn + ' 的记录' : '录入体测' }}</strong><small class="muted">同一日期重复提交会覆盖当天记录</small></div></template>
      <el-form label-width="88px">
        <div class="form-grid">
          <el-form-item label="测量日期"><el-date-picker v-model="form.measuredOn" type="date" value-format="YYYY-MM-DD" :disabled-date="disableFuture" style="width:100%"/></el-form-item>
          <el-form-item label="体重(kg)"><el-input-number v-model="form.weightKg" :min="20" :max="300" :precision="2" :step="0.1" style="width:100%"/></el-form-item>
          <el-form-item label="体脂率(%)"><el-input-number v-model="form.bodyFatRate" :min="1" :max="70" :precision="1" :step="0.1" style="width:100%"/></el-form-item>
          <el-form-item label="肌肉量(kg)"><el-input-number v-model="form.muscleKg" :min="5" :max="200" :precision="2" :step="0.1" style="width:100%"/></el-form-item>
          <el-form-item label="腰围(cm)"><el-input-number v-model="form.waistCm" :min="20" :max="250" :precision="1" :step="0.5" style="width:100%"/></el-form-item>
          <el-form-item label="胸围(cm)"><el-input-number v-model="form.chestCm" :min="20" :max="250" :precision="1" :step="0.5" style="width:100%"/></el-form-item>
          <el-form-item label="臀围(cm)"><el-input-number v-model="form.hipCm" :min="20" :max="250" :precision="1" :step="0.5" style="width:100%"/></el-form-item>
          <el-form-item label="静息心率"><el-input-number v-model="form.restingHeartRate" :min="30" :max="220" style="width:100%"/></el-form-item>
        </div>
        <el-form-item label="备注"><el-input v-model="form.note" maxlength="300" placeholder="例如：私教课后测量，空腹"/></el-form-item>
        <div class="form-actions">
          <el-button v-if="editingId" @click="resetForm">取消修正</el-button>
          <el-button type="primary" :loading="saving" @click="submit">{{ editingId ? '保存修正' : '保存记录' }}</el-button>
        </div>
      </el-form>
    </el-card>

    <el-table v-if="records.length" :data="records" stripe class="history">
      <el-table-column label="日期" width="120" prop="measuredOn"/>
      <el-table-column label="体重" width="90"><template #default="{row}">{{ fmt(row.weightKg) }}</template></el-table-column>
      <el-table-column label="体脂率" width="90"><template #default="{row}">{{ fmt(row.bodyFatRate) }}</template></el-table-column>
      <el-table-column label="肌肉量" width="90"><template #default="{row}">{{ fmt(row.muscleKg) }}</template></el-table-column>
      <el-table-column label="腰围" width="90"><template #default="{row}">{{ fmt(row.waistCm) }}</template></el-table-column>
      <el-table-column label="心率" width="80"><template #default="{row}">{{ row.restingHeartRate || '—' }}</template></el-table-column>
      <el-table-column label="备注" min-width="160"><template #default="{row}"><span class="muted">{{ row.note || '—' }}</span></template></el-table-column>
      <el-table-column label="录入" width="120"><template #default="{row}"><span class="muted">{{ row.recordedByName || '—' }}</span></template></el-table-column>
      <el-table-column label="操作" width="140" fixed="right"><template #default="{row}"><el-button link type="primary" @click="editRow(row)">修正</el-button><el-button link type="danger" @click="removeRow(row)">删除</el-button></template></el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ElMessageBox } from 'element-plus'
import {
  deleteMemberBody, deleteMyBody, getMemberBodyTrend, getMyBodyTrend, recordMemberBody, recordMyBody,
  setMemberTrainingGoal, setMyTrainingGoal,
  type BodyMeasurement, type BodyTrend, type TrendPoint,
} from '@/api/modules/bodyMeasurement'

const props = defineProps<{ memberId?: number; memberName?: string }>()
const isStaff = computed(() => props.memberId != null)

const trend = ref<BodyTrend>()
const records = ref<BodyMeasurement[]>([])
const metric = ref<'weightKg' | 'bodyFatRate' | 'muscleKg' | 'waistCm'>('weightKg')
const goal = ref('NONE')
const saving = ref(false)
const editingId = ref<number | null>(null)

const blank = () => ({ measuredOn: new Date().toISOString().slice(0, 10), weightKg: undefined as number | undefined,
  bodyFatRate: undefined as number | undefined, muscleKg: undefined as number | undefined,
  chestCm: undefined as number | undefined, waistCm: undefined as number | undefined,
  hipCm: undefined as number | undefined, restingHeartRate: undefined as number | undefined,
  note: '' as string })
const form = reactive(blank())

const headerHint = computed(() => isStaff.value
  ? `正在查看 ${props.memberName || '该会员'} 的体测档案`
  : '设定目标后，教练会据此给出训练建议')
const points = computed<TrendPoint[]>(() => trend.value?.points || [])
const fmt = (v?: number | null) => (v == null ? '—' : String(v))
const disableFuture = (d: Date) => d.getTime() > Date.now()

const deltaText = (c: { delta: number; metric: string }) => {
  const v = Number(c.delta)
  const unit = c.metric === '体脂率' ? '%' : c.metric === '肌肉量' || c.metric === '体重' ? 'kg' : 'cm'
  return (v > 0 ? '+' : '') + v.toFixed(2) + unit
}
const deltaClass = (c: { delta: number; metric: string }) => {
  const v = Number(c.delta)
  const downIsGood = c.metric === '体重' || c.metric === '体脂率' || c.metric === '腰围'
  if (v === 0) return 'flat'
  return (v < 0) === downIsGood ? 'good' : 'warn'
}

const chart = computed(() => {
  const pts = points.value
    .map(p => ({ d: p.date, v: p[metric.value] }))
    .filter(p => p.v != null) as { d: string; v: number }[]
  if (pts.length < 2) return null
  const w = 640, h = 200, padL = 48, padR = 16, padT = 18, padB = 28
  const xs = pts.map(p => new Date(p.d + 'T00:00:00').getTime())
  const ys = pts.map(p => Number(p.v))
  const minX = Math.min(...xs), maxX = Math.max(...xs)
  const minY = Math.min(...ys), maxY = Math.max(...ys)
  const span = (maxY - minY) || Math.max(1, maxY * 0.1)
  const lo = minY - span * 0.2, hi = maxY + span * 0.2
  const px = (v: number) => (maxX === minX ? padL + (w - padL - padR) / 2 : padL + (v - minX) / (maxX - minX) * (w - padL - padR))
  const py = (v: number) => padT + (hi - v) / (hi - lo) * (h - padT - padB)
  const path = pts.map((p, i) => (i ? 'L' : 'M') + px(xs[i]).toFixed(1) + ' ' + py(Number(p.v)).toFixed(1)).join(' ')
  const area = `${path} L ${px(maxX).toFixed(1)} ${py(lo).toFixed(1)} L ${px(minX).toFixed(1)} ${py(lo).toFixed(1)} Z`
  const dots = pts.map((p, i) => ({ x: px(xs[i]), y: py(Number(p.v)), v: Number(p.v), d: p.d.slice(5) }))
  const grid = [0, 1, 2, 3].map(i => {
    const v = lo + (hi - lo) * (i / 3)
    return { y: py(v), label: v.toFixed(1) }
  })
  return { w, h, padL, padR, path, area, dots, grid, first: dots[0], last: dots[dots.length - 1] }
})

const load = async () => {
  const t = isStaff.value ? await getMemberBodyTrend(props.memberId!) : await getMyBodyTrend()
  trend.value = t.data
  goal.value = t.data.trainingGoal || 'NONE'
  records.value = t.data.records || []
}
const saveGoal = async () => {
  try {
    const r = isStaff.value
      ? await setMemberTrainingGoal(props.memberId!, { trainingGoal: goal.value })
      : await setMyTrainingGoal({ trainingGoal: goal.value })
    trend.value = r.data
    ElMessage.success('训练目标已更新')
  } catch { await load() }
}
const resetForm = () => { editingId.value = null; Object.assign(form, blank()) }
const editRow = (row: BodyMeasurement) => {
  editingId.value = row.id
  Object.assign(form, { measuredOn: row.measuredOn, weightKg: row.weightKg ?? undefined,
    bodyFatRate: row.bodyFatRate ?? undefined, muscleKg: row.muscleKg ?? undefined,
    chestCm: row.chestCm ?? undefined, waistCm: row.waistCm ?? undefined, hipCm: row.hipCm ?? undefined,
    restingHeartRate: row.restingHeartRate ?? undefined, note: row.note || '' })
}
const removeRow = async (row: BodyMeasurement) => {
  try { await ElMessageBox.confirm(`确认删除 ${row.measuredOn} 的体测记录？删除后曲线会重新计算。`, '删除体测记录', { type: 'warning' }) } catch { return }
  if (isStaff.value) await deleteMemberBody(props.memberId!, row.id)
  else await deleteMyBody(row.id)
  ElMessage.success('已删除')
  if (editingId.value === row.id) resetForm()
  await load()
}
const submit = async () => {
  if (!form.measuredOn) return ElMessage.warning('请选择测量日期')
  const payload = { ...form, note: form.note?.trim() || undefined }
  saving.value = true
  try {
    if (isStaff.value) await recordMemberBody(props.memberId!, payload)
    else await recordMyBody(payload)
    ElMessage.success('体测记录已保存')
    resetForm()
    await load()
  } finally { saving.value = false }
}
onMounted(load)
</script>

<style scoped>
.bmp{display:flex;flex-direction:column;gap:18px}.bmp-head{display:flex;align-items:center;justify-content:space-between;gap:12px}.bmp-head h3{margin:0 0 5px;color:#1b263b}.bmp-head p{margin:0;color:#8792a5;font-size:12px}.change-row{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}.change-card{padding:14px;border:1px solid #e7ecf4;border-radius:14px;background:#fff}.change-card span,.change-card strong,.change-card small{display:block}.change-card span{color:#8792a5;font-size:12px}.change-card strong{margin:6px 0 4px;color:#172033;font-size:16px}.change-card small{font-size:12px}.change-card small.good{color:#1eb88a}.change-card small.warn{color:#e85d6a}.change-card small.flat{color:#9aa5b8}.chart-block{padding:16px 18px;border:1px solid #e7ecf4;border-radius:16px;background:#fff}.chart-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:8px}.chart{width:100%;height:220px}.form-card{border-radius:16px}.form-head{display:flex;align-items:center;justify-content:space-between}.form-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:0 18px}.form-actions{display:flex;justify-content:flex-end;gap:10px}.history{border-radius:14px}.muted{color:#9aa5b8}@media(max-width:900px){.change-row{grid-template-columns:repeat(2,1fr)}.form-grid{grid-template-columns:1fr}}
</style>
