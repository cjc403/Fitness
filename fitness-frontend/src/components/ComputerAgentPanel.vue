<template>
  <section class="computer-panel" aria-label="网页任务">
    <header class="computer-head">
      <div><strong>{{ roleName }} · 网页任务</strong><small>{{ connectionText }}</small></div>
      <button type="button" :disabled="!online || connecting || busy" @click="refresh">刷新</button>
    </header>
    <div class="computer-body">
      <p v-if="!online" class="computer-warning" role="status">当前离线，操作已停用。恢复网络后请查看任务状态，再手动继续或确认。</p>
      <div v-else-if="!available && !connecting" class="computer-startup" role="status">
        <strong>网页任务服务尚未连接</strong>
        <p>请确认已经登录、后端和模型服务可用，再点“刷新”。网页操作直接在当前标签页执行。</p>
      </div>
      <p class="computer-intro">我直接操作你正在看的这个网页，切换页面、点击和填写都会在这里发生。任务期间的业务提交（包括手动提交）均需逐次确认；关闭助手或切到其他标签页会暂停。</p>
      <p v-if="error" class="computer-error" role="alert">{{ error }}</p>
      <div v-if="!task" class="computer-suggestions" aria-label="推荐网页任务">
        <span>从你的工作开始</span>
        <button v-for="goal in suggestions" :key="goal" type="button" :disabled="!canWrite" @click="submitGoal(goal)">{{ goal }}</button>
      </div>
      <label v-if="tasks.length" class="computer-history">最近任务
        <select :value="task?.id || ''" :disabled="busy || !online || !available" @change="selectTask(($event.target as HTMLSelectElement).value)">
          <option v-for="item in tasks" :key="item.id" :value="item.id">{{ statusLabel(item.status) }} · {{ item.goal }}</option>
        </select>
      </label>
      <article v-if="task" class="computer-task" :class="task.status.toLowerCase()">
        <div class="computer-task-title"><h3>{{ task.goal }}</h3><span role="status">{{ statusLabel(task.status) }}</span></div>
        <small>{{ formatTime(task.updatedAt) }} · {{ task.steps.length }} 步</small>
        <p v-if="task.syncError" class="computer-warning">{{ task.syncError }}</p>
        <section v-if="task.plan?.steps?.length" class="computer-plan" aria-label="任务计划"><strong>任务计划 · 完成情况以结果核验为准</strong><ol><li v-for="item in task.plan.steps" :key="item.id">{{ item.label }}</li></ol></section>
        <section v-if="task.verification?.outcomes?.length" class="computer-plan" aria-label="目标核验"><strong>{{ task.verification.complete ? '目标核验通过' : task.verification.partial ? '已保留完成部分，继续处理剩余目标' : '目标尚未完成' }}</strong><ul><li v-for="item in task.verification.outcomes" :key="item.id">{{ item.status === 'SATISFIED' ? '✓' : '○' }} {{ item.label }}</li></ul></section>
        <section v-if="task.facts?.length" class="computer-plan" aria-label="真实业务查询结果"><strong>真实查询结果</strong><details v-for="(fact,index) in businessFacts" :key="index" :open="index === businessFacts.length-1"><summary>{{ fact.label }} · {{ formatTime(fact.asOf) }}</summary><p v-if="fact.pagination">第 {{ fact.pagination.page }} 页 · 本页 {{ fact.pagination.returned }} 条 / 共 {{ fact.pagination.total }} 条</p><p v-if="fact.pagination?.contentTruncated || fact.truncated && !fact.pagination" class="computer-warning">内容超出本次读取范围，请缩小筛选条件。</p><AgentData :value="fact.data" /></details></section>
        <p v-if="task.error" class="computer-error">{{ task.error }}</p>
        <p v-if="task.status === 'SUCCEEDED'" class="computer-success">任务已完成，请查看当前页面及下方实际操作结果。</p>
        <p v-if="task.status === 'CANCELLED'" class="computer-muted">任务已停止。</p>
        <p v-if="task.status === 'PAUSED'" class="computer-muted" role="status">{{ pauseExplanation(task.pauseReason) }}点击“继续”后才会继续执行；若有待确认的提交，继续后仍需单独确认。</p>
        <div class="computer-controls">
          <button v-if="task.status === 'RUNNING'" type="button" :disabled="!canWrite" @click="pauseTask">暂停</button>
          <button v-if="task.status === 'PAUSED'" type="button" :disabled="!canWrite" @click="resumeTask">{{ task.restorable ? '在当前网页恢复' : '继续' }}</button>
          <button v-if="isActiveTask(task)" type="button" class="computer-stop" :disabled="!canWrite" @click="stopTask">停止任务</button>
          <button type="button" :disabled="busy" @click="takeOverCurrentPage">手动接管（刷新当前页）</button>
        </div>
        <p v-if="['FAILED','PARTIAL','CANCELLED','SUCCEEDED'].includes(task.status)" class="computer-muted">旧任务的延迟提交仍被阻止。手动提交前请刷新接管；刷新会清除尚未提交的表单。成功后可直接继续新的网页任务，停止或未全部完成后须先刷新；已成功提交的业务仍保留。</p>
        <section v-if="task.status === 'WAITING_CONFIRMATION' && pendingPreview" class="computer-confirm" aria-label="待确认的实际请求">
          <h4>确认后提交这一次请求</h4>
          <p>{{ pendingPreview.summary }}</p>
          <p v-if="pendingPreview.before?.name">操作对象：{{ pendingPreview.before.name }}</p>
          <p v-if="pendingPreview.before?.memberName">会员：{{ pendingPreview.before.memberName }}</p><p v-if="pendingPreview.before?.courseName">课程：{{ pendingPreview.before.courseName }} · {{ pendingPreview.before.startTime || '' }}</p>
          <p v-if="pendingPreview.before?.recipientName">收件人：{{ pendingPreview.before.recipientName }}</p>
          <dl><template v-for="(field, index) in confirmationChanges" :key="index"><dt>{{ fieldLabel(field.label) }}</dt><dd><span v-if="field.before !== undefined">{{ fieldValue(field.before) }} → </span>{{ fieldValue(field.value) }}</dd></template></dl>
          <details><summary>核对完整请求</summary><code>{{ pendingPreview.method }} {{ pendingPreview.path }}</code><dl><template v-for="(field, index) in pendingPreview.fields" :key="index"><dt>{{ fieldLabel(field.label) }}</dt><dd>{{ fieldValue(field.value) }}</dd></template></dl></details>
          <small>预览参数已冻结。此按钮仅放行当前请求；后续提交仍需再次确认。</small>
          <button type="button" class="computer-primary" :disabled="!canWrite" @click="confirmTask">确认并提交这一次</button>
        </section>
        <form v-if="task.status === 'WAITING_INPUT'" class="computer-question" @submit.prevent="sendInput">
          <label for="computer-supplement">{{ task.question || '请补充任务所需的信息' }}</label>
          <textarea id="computer-supplement" v-model="supplement" maxlength="2000" rows="2" :disabled="!canWrite" placeholder="输入补充信息" />
          <button type="submit" class="computer-primary" :disabled="!canWrite || !supplement.trim()">补充并继续</button>
        </form>
        <ol v-if="task.steps.length" class="computer-steps" aria-label="实际执行步骤">
          <li v-for="step in task.steps" :key="step.index"><span class="computer-step-index">{{ step.index }}</span><div><strong>{{ step.label }}</strong><small>{{ stepStatusLabel(step.status) }}</small><p v-if="step.detail">{{ step.detail }}</p></div></li>
        </ol>
        <figure v-if="screenshotSource" class="computer-screenshot"><img :src="screenshotSource" alt="受控浏览器当前页面" /><figcaption>受控页面的最新截图</figcaption></figure>
        <section v-if="task.artifacts.length" class="computer-artifacts" aria-label="任务成果文件"><h4>成果文件</h4>
          <article v-for="artifact in task.artifacts" :key="artifact.id"><strong>{{ artifact.name }}</strong><small>{{ formatBytes(artifact.bytes) }}</small><p v-if="artifact.path">{{ artifact.path }}</p><details><summary>校验值 SHA-256</summary><code>{{ artifact.sha256 }}</code></details></article>
        </section>
      </article>
    </div>
    <form class="computer-goal" @submit.prevent="submitGoal(goalInput)">
      <label class="sr-only" for="computer-goal">网页任务目标</label>
      <textarea id="computer-goal" v-model="goalInput" :disabled="!online || busy || connecting" maxlength="2000" rows="2" :placeholder="task?.status === 'WAITING_INPUT' ? '补充任务所需的信息…' : '说说要在当前网页完成的任务…'" />
      <button type="submit" class="computer-primary" :disabled="!canWrite || !goalInput.trim()">{{ task?.status === 'WAITING_INPUT' ? '补充' : '执行' }}</button>
    </form>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '@/store/modules/auth'
import AgentData from '@/components/AgentData.vue'
import { getComputerAgentStatus, listComputerTasks, createComputerTask, getComputerTask, pauseComputerTask, resumeComputerTask, recoverComputerTask, stopComputerTask, confirmComputerTask, inputComputerTask, suspendPageTasks, takeOverCurrentPage } from '@/agent/currentPageAgent'
import type { ComputerTask, ComputerTaskStatus, ComputerConfirmation, ComputerAgentStatus, ComputerRequestConfig } from '@/api/modules/computerAgent'

const props = withDefaults(defineProps<{ active?: boolean }>(), { active: true })
const emit = defineEmits<{ reply: [text: string] }>()
const auth = useAuthStore()
const task = ref<ComputerTask | null>(null), tasks = ref<ComputerTask[]>([]), pendingPreview = ref<ComputerConfirmation | null>(null)
const service = ref<ComputerAgentStatus | null>(null), online = ref(typeof navigator === 'undefined' || navigator.onLine !== false)
const goalInput = ref(''), supplement = ref(''), error = ref(''), busy = ref(false), connecting = ref(false)
const available = computed(() => service.value?.available === true)
const roleName = computed(() => ({ SUPER_ADMIN: '总部管理员', MANAGER: '店长', RECEPTION: '前台', COACH: '教练', SALES: '会籍顾问', MEMBER: '会员' } as Record<string, string>)[auth.userInfo?.role || ''] || '当前账号')
const suggestions = computed(() => ({
  SUPER_ADMIN: ['打开总部总览，查看各门店概况', '打开周课表，查看本周课程'],
  MANAGER: ['打开门店总览，查看本店概况', '整理本店本周课程，生成课表文件保存并打开'],
  RECEPTION: ['打开会员列表，查看本店会员', '打开消息中心，查看待处理消息'],
  COACH: ['打开我的工作台，查看本周课程', '打开我的学员，查看学员信息'],
  SALES: ['打开我的工作台，查看我的潜客', '查看近期体验预约，整理跟进安排'],
  MEMBER: ['打开我的课程，查看我的预约', '导出本周我可预约的课程，生成课表文件并打开']
} as Record<string, string[]>)[auth.userInfo?.role || ''] || [])
const connectionText = computed(() => !online.value ? '网络已断开' : connecting.value ? '正在连接网页任务服务' : available.value ? service.value?.platform === 'current-page-v2' ? 'Agent V2 · 持续计划与真实结果核验' : '当前标签页执行 · 页面操作可见' : '等待连接网页任务服务')
const canWrite = computed(() => props.active && online.value && available.value && !busy.value && !connecting.value && !!auth.token)
const screenshotSource = computed(() => typeof task.value?.screenshot === 'string' && /^data:image\/jpeg;base64,[A-Za-z0-9+/=\s]+$/.test(task.value.screenshot) ? task.value.screenshot : undefined)
const statusLabel = (status: ComputerTaskStatus) => ({ RUNNING: '执行中', PAUSED: '已暂停', WAITING_CONFIRMATION: '待你确认', WAITING_INPUT: '待补充信息', SUCCEEDED: '已完成', PARTIAL:'部分完成', FAILED: '执行失败', CANCELLED: '已停止' })[status] || '状态未知'
const pauseExplanation = (reason?: ComputerTask['pauseReason']) => ({ USER: '你已手动暂停任务。', PAGE_HIDDEN: '当前网页曾被隐藏（如切换标签页或最小化），任务已自动暂停。', ASSISTANT_CLOSED: '助手面板已关闭或重新加载，任务已自动暂停。', OFFLINE: '网络已断开，任务已自动暂停。' } as Record<string,string>)[reason || ''] || '任务已暂停。'
const stepStatusLabel = (status: string) => ({ RUNNING: '执行中', SUCCEEDED: '完成', FAILED: '失败', CANCELLED: '已停止', PAUSED: '暂停', WAITING_CONFIRMATION: '等待确认', WAITING_INPUT: '等待补充', PENDING: '等待执行' } as Record<string, string>)[status] || status
const isActiveTask = (value: ComputerTask) => ['RUNNING', 'PAUSED', 'WAITING_CONFIRMATION', 'WAITING_INPUT'].includes(value.status)
const formatTime = (value: string) => value ? new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) : '—'
const formatBytes = (bytes: number) => bytes < 1024 ? `${bytes} B` : bytes < 1024 * 1024 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`
const fieldLabel = (label: string) => ({ fee: '价格（元）', maxCapacity: '容量', durationMinutes: '时长（分钟）', intensity: '强度', type: '类型', description: '简介', name: '姓名 / 名称', phone: '联系电话', source: '咨询来源', intent: '意向程度', ownerId: '负责顾问编号', venueId: '门店编号', memberId: '会员编号', recipientUserId: '收件人账号编号', coachId: '教练编号', scheduleId: '排课编号', note: '备注', content: '内容', title: '标题', nextFollowAt: '下次联系时间', startsAt: '开始时间', endsAt: '结束时间', idempotencyKey: '重复提交保护标识', expectedVersion: '资料版本', productId: '商品编号', paidAmount: '实收金额', paymentMethod: '收款方式' } as Record<string,string>)[label] || label
const fieldValue = (value: string) => ({ WALK_IN: '自然到店', REFERRAL: '转介绍', DOUYIN: '抖音', WECHAT: '微信', OTHER: '其他', HIGH: '高意向', MEDIUM: '中意向', LOW: '低意向' } as Record<string,string>)[value] || value
const businessFacts = computed(() => (task.value?.facts || []) as {label:string;tool:string;data:unknown;asOf:string;truncated:boolean;pagination?:{page:number;returned:number;total:number;contentTruncated:boolean}}[])
const confirmationChanges = computed(() => {
  const p=pendingPreview.value;if(!p)return []
  const before=p.before as Record<string,unknown>|undefined
  const aliases:Record<string,string>={maxCapacity:'max_capacity',durationMinutes:'duration_minutes',venueId:'venue_id'}
  return p.fields.map(f=>({...f,before:before?.[aliases[f.label]||f.label] === undefined ? undefined : String(before?.[aliases[f.label]||f.label]??'')})).filter(f=>f.before===undefined||f.before!==f.value)
})
const errorText = (failure: unknown) => failure instanceof Error ? failure.message : '网页任务连接失败，请检查后端服务后刷新'
const identityKey = () => { const user = auth.userInfo as (typeof auth.userInfo & { venueId?: number }); return JSON.stringify([auth.sessionGeneration, auth.token, user?.id, user?.role, user?.organizationId, user?.venueId]) }
let disposed = false, mounted = false, epoch = 0, revision = 0, readEpoch = 0, pollTimer: ReturnType<typeof setTimeout> | undefined
let refreshPromise: Promise<void> | null = null
const requests = new Set<AbortController>(), announced = new Set<string>()
const clearPoll = () => { if (pollTimer !== undefined) clearTimeout(pollTimer); pollTimer = undefined }
const invalidate = () => { epoch++; revision++; readEpoch++; clearPoll(); requests.forEach(controller => controller.abort()); requests.clear(); refreshPromise = null; busy.value = false; connecting.value = false }
const scope = () => {
  const requestEpoch = epoch, requestIdentity = identityKey(), controller = new AbortController()
  requests.add(controller)
  const current = () => !disposed && props.active && epoch === requestEpoch && identityKey() === requestIdentity && !controller.signal.aborted
  return { current, config: { signal: controller.signal, isRequestCurrent: current } satisfies ComputerRequestConfig, done: () => requests.delete(controller) }
}
const acceptTask = (value: ComputerTask, announce = false) => {
  // Copy the transport result so the displayed frozen request never aliases a later response.
  const copy: ComputerTask = JSON.parse(JSON.stringify(value))
  if (!copy?.id || !Array.isArray(copy.steps) || !Array.isArray(copy.artifacts) || !statusLabel(copy.status) || !['RUNNING', 'PAUSED', 'WAITING_CONFIRMATION', 'WAITING_INPUT', 'SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED'].includes(copy.status)) throw new Error('执行器返回了无效任务状态，请刷新查看')
  task.value = copy
  pendingPreview.value = copy.status === 'WAITING_CONFIRMATION' && copy.pendingConfirmation ? JSON.parse(JSON.stringify(copy.pendingConfirmation)) : null
  tasks.value = [copy, ...tasks.value.filter(item => item.id !== copy.id)].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 30)
  if (announce && !isActiveTask(copy) && !announced.has(copy.id)) {
    announced.add(copy.id)
    emit('reply', copy.status === 'SUCCEEDED' ? `网页任务已完成：${copy.goal}${copy.artifacts.length ? `。已生成 ${copy.artifacts.length} 个成果文件，请在网页任务中查看。` : '。请在网页任务中查看实际步骤。'}` : copy.status === 'PARTIAL' ? `任务部分完成，已成功提交的业务仍保留；剩余部分：${copy.error || '请查看目标核验'}。` : copy.status === 'FAILED' ? `网页任务执行失败：${copy.error || '请查看任务详情'}。` : `网页任务已停止：${copy.goal}`)
  }
}
const schedulePoll = () => {
  clearPoll()
  if (disposed || !props.active || !online.value || !available.value || busy.value || !task.value || !isActiveTask(task.value)) return
  pollTimer = setTimeout(async () => { pollTimer = undefined; await poll() }, 1800)
}
const poll = async () => {
  if (!task.value || !online.value || !props.active || disposed || busy.value) return
  const request = scope(), id = task.value.id, read = ++readEpoch, receipt = revision
  try { const result = await getComputerTask(id, request.config); if (request.current() && read === readEpoch && receipt === revision && task.value?.id === id) { acceptTask(result.data, true); error.value = '' } }
  catch (failure) { if (request.current() && read === readEpoch && receipt === revision) error.value = `${errorText(failure)}。不会自动重复提交，请刷新核对状态。` }
  finally { request.done(); if (request.current() && read === readEpoch && receipt === revision) schedulePoll() }
}
const loadTasks = async () => {
  if (disposed || !props.active || !auth.token || !online.value || busy.value) return
  clearPoll(); const request = scope(), read = ++readEpoch, receipt = revision
  connecting.value = true; error.value = ''
  try {
    const status = await getComputerAgentStatus(request.config)
    if (!request.current() || read !== readEpoch || receipt !== revision) return
    service.value = status.data
    if (!status.data.available) return
    const result = await listComputerTasks(request.config)
    if (!request.current() || read !== readEpoch || receipt !== revision) return
    tasks.value = JSON.parse(JSON.stringify(result.data))
    const selected = tasks.value.find(item => isActiveTask(item)) || tasks.value.find(item => item.id === task.value?.id) || tasks.value[0]
    if (selected) acceptTask(selected); else { task.value = null; pendingPreview.value = null }
  } catch (failure) { if (request.current() && read === readEpoch) { service.value = null; error.value = errorText(failure) } }
  finally { request.done(); if (request.current() && read === readEpoch) { connecting.value = false; schedulePoll() } }
}
const refresh = async (): Promise<void> => {
  if (refreshPromise) return refreshPromise
  const pending = loadTasks(); refreshPromise = pending
  try { await pending } finally { if (refreshPromise === pending) refreshPromise = null }
}
const requireWritable = () => {
  if (!online.value) { error.value = '当前离线，无法提交操作；恢复网络后请手动重试'; return false }
  if (!canWrite.value || disposed) { if (!available.value) error.value = '请先连接网页任务服务，再提交任务'; return false }
  return true
}
const mutate = async (operation: (config: ComputerRequestConfig) => Promise<{ data: ComputerTask }>) => {
  if (!requireWritable()) return
  clearPoll(); revision++; readEpoch++; const request = scope(); busy.value = true; error.value = ''
  try { const result = await operation(request.config); if (request.current()) { revision++; acceptTask(result.data, true) } }
  catch (failure) { if (request.current()) { error.value = `${errorText(failure)}。提交结果请刷新核对，不会自动重试。`; emit('reply', error.value) } }
  finally { request.done(); if (request.current()) { busy.value = false; schedulePoll() } }
}
const submitGoal = async (text: string): Promise<void> => {
  const submissionEpoch = epoch
  if (online.value && props.active && auth.token && !disposed && (connecting.value || !available.value)) await refresh()
  if (submissionEpoch !== epoch) return
  if (!requireWritable()) return
  const goal = text.trim()
  if (!goal || goal.length > 2000) { error.value = '请输入 1–2000 字的任务目标'; return }
  if (task.value?.status === 'WAITING_INPUT') { supplement.value = goal; await sendInput(); if (epoch === submissionEpoch && !error.value && goalInput.value.trim() === goal) goalInput.value = ''; return }
  if(task.value?.durable&&!task.value.restorable&&isActiveTask(task.value)){await mutate(config=>inputComputerTask(task.value!.id,goal,config));if(!error.value)goalInput.value='';return}
  if ((task.value && isActiveTask(task.value)) || tasks.value.some(isActiveTask)) { error.value = '已有进行中的网页任务，请先补充、确认或停止当前任务，再开始新任务'; emit('reply', error.value); return }
  await mutate(config => createComputerTask(goal, config)); if (epoch === submissionEpoch && !error.value && goalInput.value.trim() === goal) goalInput.value = ''
}
const pauseTask = async () => { const current = task.value; if (current?.status === 'RUNNING') await mutate(config => pauseComputerTask(current.id, config)); else if (!online.value) requireWritable() }
const resumeTask = async () => { const current = task.value; if (current?.status === 'PAUSED') await mutate(config => current.restorable ? recoverComputerTask(current.id,config) : resumeComputerTask(current.id, config)); else if (!online.value) requireWritable() }
const stopTask = async () => { const current = task.value; if (current && isActiveTask(current)) await mutate(config => stopComputerTask(current.id, config)); else if (!online.value) requireWritable() }
const confirmTask = async () => {
  const current = task.value, preview = pendingPreview.value
  if (current?.status !== 'WAITING_CONFIRMATION' || !preview || current.pendingConfirmation?.id !== preview.id) { if (!online.value) requireWritable(); return }
  const id = preview.id
  await mutate(config => confirmComputerTask(current.id, id, config))
}
const sendInput = async () => {
  const current = task.value, message = supplement.value.trim(), submissionEpoch = epoch
  if (current?.status !== 'WAITING_INPUT' || !message || message.length > 2000) { if (!online.value) requireWritable(); return }
  await mutate(config => inputComputerTask(current.id, message, config)); if (epoch === submissionEpoch && !error.value && supplement.value.trim() === message) supplement.value = ''
}
const selectTask = async (id: string) => {
  if (!online.value || busy.value || !props.active || !available.value) return
  clearPoll(); const request = scope(), read = ++readEpoch, receipt = revision
  try { const result = await getComputerTask(id, request.config); if (request.current() && read === readEpoch && receipt === revision) { acceptTask(result.data); error.value = ''; supplement.value = '' } }
  catch (failure) { if (request.current() && read === readEpoch) error.value = errorText(failure) }
  finally { request.done(); if (request.current() && read === readEpoch) schedulePoll() }
}
const syncNetwork = () => { online.value = navigator.onLine !== false }
watch(online, value => { if (!value) { invalidate(); error.value = '当前离线，操作已停用；恢复网络后请手动继续或确认' } else if (mounted && props.active) void refresh() }, { flush: 'sync' })
watch(() => props.active, active => { invalidate(); if (active && mounted) void refresh() }, { flush: 'sync' })
watch(identityKey, () => {
  invalidate(); task.value = null; tasks.value = []; pendingPreview.value = null; service.value = null; goalInput.value = ''; supplement.value = ''; error.value = ''; announced.clear()
  if (mounted && props.active && online.value && auth.token) void refresh()
}, { flush: 'sync' })
onMounted(() => { mounted = true; window.addEventListener('online', syncNetwork); window.addEventListener('offline', syncNetwork); if (props.active) void refresh() })
onBeforeUnmount(() => { suspendPageTasks(); disposed = true; mounted = false; invalidate(); task.value = null; tasks.value = []; pendingPreview.value = null; window.removeEventListener('online', syncNetwork); window.removeEventListener('offline', syncNetwork) })
defineExpose({ submitGoal, refresh })
</script>

<style scoped>
.computer-panel{display:flex;flex:1;flex-direction:column;min-height:0;overflow:hidden;background:#f6f9fe;color:#294766}.computer-head{display:flex;flex:none;align-items:center;justify-content:space-between;gap:12px;padding:14px 16px;background:#edf5ff;border-bottom:1px solid #dce7f4}.computer-head strong{font-size:14px}.computer-head small,.computer-task>small{display:block;margin-top:5px;font-size:11px;color:#6d829c}.computer-head button,.computer-controls button{min-height:40px;padding:8px 12px;border:1px solid #cbdaee;border-radius:9px;background:#fff;color:#315c94;cursor:pointer}.computer-body{flex:1;min-height:0;overflow:auto;overscroll-behavior:contain;padding:14px 16px;overflow-wrap:anywhere}.computer-intro,.computer-muted{font-size:12px;line-height:1.7;color:#677d97}.computer-intro{margin:0 0 14px}.computer-warning,.computer-startup{padding:12px;border-radius:10px;background:#fff6df;color:#8a611e;font-size:12px;line-height:1.7;margin:0 0 12px}.computer-startup p{margin:6px 0 0}.computer-error{margin:8px 0;color:#b64141;font-size:12px;line-height:1.7;white-space:pre-wrap}.computer-suggestions{display:grid;gap:8px;margin:16px 0}.computer-suggestions>span{font-size:12px;color:#647c99}.computer-suggestions button{text-align:left;min-height:44px;padding:10px 12px;border:1px solid #d2e0f2;border-radius:11px;background:#fff;color:#315e9a;font-size:13px;line-height:1.6;cursor:pointer}.computer-history{display:grid;gap:7px;font-size:12px;color:#667f9e;margin:12px 0}.computer-history select{min-width:0;width:100%;min-height:42px;padding:9px;border:1px solid #cfdeef;border-radius:9px;background:#fff;color:#31567e;font-size:14px}.computer-task{padding:13px;background:#fff;border:1px solid #dbe5f1;border-radius:13px}.computer-task-title{display:flex;align-items:flex-start;justify-content:space-between;gap:10px}.computer-task-title h3{margin:0;font-size:14px;line-height:1.6;white-space:pre-wrap}.computer-task-title>span{flex:none;padding:4px 7px;border-radius:6px;background:#edf4ff;color:#346bb3;font-size:11px}.computer-task.succeeded{border-color:#b7ddc8}.succeeded .computer-task-title>span{background:#e9f7ef;color:#2f7d57}.failed .computer-task-title>span{background:#fff0ec;color:#b44835}.computer-success{font-size:12px;line-height:1.7;color:#27805b}.computer-controls{display:flex;flex-wrap:wrap;gap:8px;margin:12px 0}.computer-controls .computer-stop{color:#ad4c42;border-color:#e5c9c5}.computer-confirm,.computer-question{display:grid;gap:10px;padding:12px;border:1px solid #eed5a7;border-radius:10px;background:#fffbef;margin:14px 0;font-size:13px;line-height:1.6}.computer-confirm h4,.computer-artifacts h4{margin:0;font-size:13px}.computer-confirm p{margin:0;white-space:pre-wrap}.computer-confirm code{font-size:11px;white-space:pre-wrap;overflow-wrap:anywhere}.computer-confirm dl{display:grid;grid-template-columns:minmax(60px,1fr) minmax(0,2fr);gap:7px;margin:0;font-size:12px}.computer-confirm dt{color:#8a7046}.computer-confirm dd{margin:0;white-space:pre-wrap}.computer-confirm small{font-size:11px;color:#927344}.computer-primary{min-height:44px;padding:10px 13px;border:0;border-radius:9px;background:#2768d5;color:#fff;cursor:pointer;font-size:13px}.computer-question{background:#f0f6ff;border-color:#cdddF3}.computer-question textarea,.computer-goal textarea{width:100%;min-width:0;resize:vertical;padding:9px 10px;border:1px solid #c8d9ee;border-radius:9px;color:#274766;background:#fff;font:inherit;font-size:16px;line-height:1.5}.computer-steps{list-style:none;padding:0;margin:18px 0 0}.computer-steps li{display:flex;align-items:flex-start;gap:9px;padding:0 0 14px;font-size:12px}.computer-step-index{flex:none;display:grid;place-items:center;width:23px;height:23px;background:#eaf2ff;color:#3569ad;border-radius:50%;font-size:11px}.computer-steps strong{display:block;font-weight:500;line-height:1.7}.computer-steps small{font-size:10px;color:#7d8da1}.computer-steps p{margin:5px 0 0;line-height:1.6;color:#677f99;white-space:pre-wrap}.computer-screenshot{margin:12px 0 0}.computer-screenshot img{display:block;width:100%;border:1px solid #dce6f2;border-radius:8px}.computer-screenshot figcaption{margin-top:6px;color:#8394aa;font-size:10px;text-align:center}.computer-artifacts{border-top:1px solid #e1e9f4;margin-top:15px;padding-top:14px}.computer-artifacts article{margin-top:10px;padding:10px;background:#f5f8fd;border-radius:8px;font-size:12px;line-height:1.6}.computer-artifacts strong{display:block}.computer-artifacts small{color:#69829e}.computer-artifacts p{margin:5px 0;color:#607894}.computer-artifacts summary{cursor:pointer;color:#3c69a0}.computer-artifacts code{font-size:10px;overflow-wrap:anywhere}.computer-goal{display:flex;align-items:flex-end;flex:none;gap:8px;padding:12px;background:white;border-top:1px solid #dde7f4}.computer-goal textarea{flex:1;max-height:160px;resize:none}.computer-goal button{flex:none;min-width:62px}.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}button:disabled,select:disabled,textarea:disabled{opacity:.5;cursor:not-allowed}button:focus-visible,select:focus-visible,textarea:focus-visible,summary:focus-visible{outline:3px solid #84b6ff;outline-offset:2px}@media(max-width:760px){.computer-head{padding:12px}.computer-body{padding:12px}.computer-task{padding:12px}.computer-goal{padding:10px 12px}.computer-task-title{flex-wrap:wrap}}
</style>
