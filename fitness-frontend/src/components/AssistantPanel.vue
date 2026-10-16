<template>
  <Teleport to="body">
  <div class="assistant" data-page-agent-exclude>
    <transition name="pop">
      <section v-if="open" class="assistant-panel" :style="{ '--agent-viewport-height': viewportHeight ? `${viewportHeight}px` : undefined, '--agent-viewport-top': `${viewportTop}px` }" role="dialog" aria-label="FitChain 业务助手" aria-modal="true" @keydown.esc="closePanel" @keydown="trapFocus">
        <header class="assistant-head"><div><strong>FitChain 业务助手</strong><small>{{ profile?.roleName || '当前账号' }} · {{ serviceText }}</small></div><button ref="closeButton" type="button" aria-label="关闭业务助手" @click="closePanel"><el-icon><Close /></el-icon></button></header>
        <nav class="assistant-tabs" aria-label="助手功能"><button v-for="item in tabs" :key="item.key" :data-page-agent-takeover="['tools', 'actions'].includes(item.key) ? '' : undefined" type="button" :class="{ active: tab === item.key }" :aria-pressed="tab === item.key" @click="selectTab(item.key)">{{ item.label }}<span v-if="item.key === 'actions' && pendingCount">（{{ pendingCount }}）</span></button></nav>
        <div v-if="tab === 'chat'" class="execution-mode" aria-label="会话执行方式"><button type="button" :class="{ active: executionMode === 'computer' }" :aria-pressed="executionMode === 'computer'" @click="executionMode = 'computer'">网页执行</button><button type="button" :class="{ active: executionMode === 'business' }" :aria-pressed="executionMode === 'business'" data-page-agent-takeover @click="executionMode = 'business'">业务问答</button><small>{{ executionMode === 'computer' ? '直接操作当前网页，过程可见' : '查询数据与准备业务确认卡' }}</small></div>
        <div v-if="!online" class="agent-warning" role="status">当前离线，无法查询或提交操作。恢复网络后可重试，系统不会自动补发。</div>
        <div v-if="profileError" class="agent-warning" role="alert">{{ profileError }} <button type="button" @click="loadProfile">重新连接</button></div>
        <div v-if="tab === 'chat'" ref="bodyRef" class="assistant-body" aria-live="polite">
          <div v-for="(item, index) in messages" :key="index" :class="['bubble-row', item.role]"><div class="bubble">{{ item.text }}</div></div>
          <div v-if="loading" class="bubble-row bot"><div class="bubble">正在处理，请稍候…</div></div>
          <p v-if="pendingCount" class="preview-hint">已生成 {{ pendingCount }} 项操作预览。请到「执行记录」核对并确认。</p>
          <article v-for="(item, index) in chatResults" :key="index" class="result-card"><h3>{{ item.label }}</h3><small>{{ item.source }} · {{ formatTime(item.asOf) }}</small><AgentData :value="item.data" /><p v-if="item.truncated">结果已截断，请缩小查询范围。</p></article>
        </div>
        <div v-if="tab === 'chat'" class="quick-list"><button v-for="item in suggestions" :key="item" type="button" :disabled="loading || !online" @click="ask(item)">{{ item }}</button></div>
        <form v-if="tab === 'chat'" class="assistant-input" @submit.prevent="send"><input v-model="input" aria-label="业务问题" placeholder="输入业务问题…" maxlength="1000" :disabled="!online" /><button type="submit" :disabled="loading || !online || !input.trim()">发送</button></form>
        <ComputerAgentPanel v-show="tab === 'computer'" ref="computerRef" :active="open && tab === 'computer'" @reply="appendComputerReply" />
        <div v-if="tab === 'tools'" class="assistant-body tools-body">
          <p class="agent-help">查询直接读取当前账号可见的业务数据；写入先预览，确认后才执行。</p>
          <form @submit.prevent="runTool">
            <label class="tool-field">业务工具<select v-model="selectedTool" :disabled="toolBusy" @change="resetToolForm"><option value="">请选择工具</option><option v-for="tool in profile?.tools || []" :key="tool.name" :value="tool.name">{{ tool.label }}{{ tool.write ? ' · 需确认' : '' }}</option></select></label>
            <p v-if="currentTool" class="agent-help">{{ currentTool.description }}</p>
            <div v-for="([name, field]) in toolFields" :key="name" class="tool-field"><label :for="`agent-field-${name}`">{{ field.description || name }}<b v-if="currentTool?.parameters.required?.includes(name)" aria-label="必填"> *</b></label>
              <AgentExercises v-if="name === 'exercises'" v-model="toolForm[name]" />
              <select v-else-if="field.enum" :id="`agent-field-${name}`" v-model="toolForm[name]"><option value="">请选择</option><option v-for="option in field.enum" :key="option" :value="option">{{ optionLabel(option) }}</option></select>
              <textarea v-else-if="['content', 'note'].includes(name)" :id="`agent-field-${name}`" v-model="toolForm[name]" rows="3" :maxlength="field.maxLength || 1000" />
              <input v-else :id="`agent-field-${name}`" v-model="toolForm[name]" :type="inputType(field)" :step="field.type === 'integer' ? 1 : field.type === 'number' ? 'any' : undefined" :min="field.minimum" :max="field.maximum" :maxlength="field.maxLength || 1000" :inputmode="field.type === 'integer' ? 'numeric' : undefined" />
            </div>
            <p v-if="toolError" class="agent-error" role="alert">{{ toolError }}</p><button class="agent-primary" type="submit" :disabled="!currentTool || toolBusy || !online">{{ toolBusy ? '正在处理…' : currentTool?.write ? '生成操作预览' : '查询业务数据' }}</button>
          </form>
          <article v-if="result" class="result-card"><h3>{{ result.label }}</h3><small>{{ result.source }} · {{ formatTime(result.asOf) }}</small><AgentData :value="result.data" /><p v-if="result.truncated">结果已截断，请补充筛选条件后查询。</p></article>
          <p v-if="!profile?.tools.length && !profileError" class="agent-help">正在获取当前角色可用工具…</p>
        </div>
        <div v-if="tab === 'actions'" class="assistant-body actions-body">
          <div class="history-heading"><p>当前登录会话最近 30 条操作</p><button type="button" :disabled="historyLoading || !online" @click="loadActions">刷新记录</button></div><p v-if="historyError" class="agent-error" role="alert">{{ historyError }}</p><p v-if="!actions.length" class="agent-help">{{ historyLoading ? '正在读取记录…' : '暂无操作记录。使用业务工具或对话生成预览后在此核对。' }}</p>
          <article v-for="action in actions" :key="action.id" class="action-card" :class="action.status.toLowerCase()"><div class="action-heading"><h3>{{ action.title }}</h3><span>{{ statusText(action) }}</span></div><p class="action-summary">{{ action.summary }}</p><small>创建于 {{ formatTime(action.createdAt) }}<template v-if="action.status === 'PENDING'"> · 有效至 {{ formatTime(action.expiresAt) }}</template></small><details><summary>核对参数快照</summary><AgentData :value="action.parameters" /></details>
            <div v-if="action.status === 'PENDING' && !isExpired(action)" class="action-buttons"><button class="agent-primary" type="button" :disabled="!online || busyIds.includes(action.id)" @click="confirmAction(action)">{{ busyIds.includes(action.id) ? '正在处理…' : '确认执行' }}</button><button type="button" :disabled="!online || busyIds.includes(action.id)" @click="cancelAction(action)">取消预览</button></div>
            <p v-if="action.status === 'SUCCEEDED'" class="receipt-success">业务操作已成功，以下为服务器回执。</p><AgentData v-if="action.result != null" :value="action.result" /><p v-if="action.error || actionErrors[action.id]" class="agent-error" role="alert">{{ action.error || actionErrors[action.id] }}</p>
          </article>
        </div>
      </section>
    </transition>
    <button ref="fab" class="assistant-fab" type="button" :aria-expanded="open" aria-label="打开业务助手" @click="togglePanel"><el-icon size="24"><ChatDotRound /></el-icon></button>
  </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useAuthStore } from '@/store/modules/auth'
import { chat, getAgentProfile, queryAgentTool, previewAgentAction, listAgentActions, confirmAgentAction, cancelAgentAction, type AssistantTurn, type AgentAction, type AgentProfile, type AgentSchema, type ToolResult } from '@/api/modules/assistant'
import { ChatDotRound, Close } from '@element-plus/icons-vue'
import AgentData from '@/components/AgentData.vue'
import AgentExercises from '@/components/AgentExercises.vue'
import ComputerAgentPanel from '@/components/ComputerAgentPanel.vue'

const open = ref(false), input = ref(''), loading = ref(false), bodyRef = ref<HTMLElement>(), closeButton = ref<HTMLButtonElement>(), fab = ref<HTMLButtonElement>()
const viewportHeight = ref<number>(), viewportTop = ref(0)
const syncViewport = () => { viewportHeight.value = window.visualViewport?.height; viewportTop.value = window.visualViewport?.offsetTop || 0 }
const trapFocus = (event: KeyboardEvent) => {
  if (event.key !== 'Tab') return
  const controls = Array.from((event.currentTarget as HTMLElement).querySelectorAll<HTMLElement>('button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), summary, [tabindex="0"]')).filter(item => item.offsetParent !== null)
  const first = controls[0], last = controls[controls.length - 1]
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}
const tabs = [{ key: 'chat', label: '会话' }, { key: 'computer', label: '网页任务' }, { key: 'tools', label: '业务工具' }, { key: 'actions', label: '执行记录' }]
const computerRef = ref<InstanceType<typeof ComputerAgentPanel>>()
const executionMode = ref<'business' | 'computer'>('computer')
const tab = ref('chat'), profile = ref<AgentProfile | null>(null), profileError = ref(''), historyError = ref(''), historyLoading = ref(false)
const online = ref(typeof navigator === 'undefined' || navigator.onLine !== false)
const authStore = useAuthStore(), sessionGeneration = authStore.sessionGeneration
let disposed = false, profileEpoch = 0, historyEpoch = 0, receiptEpoch = 0
const isCurrentView = () => !disposed && authStore.sessionGeneration === sessionGeneration
const config = () => ({ isRequestCurrent: isCurrentView })
const messages = ref([{ role: 'bot', text: '你好！我可以查询当前账号的业务数据，并为支持的操作生成预览。写入需要你单独确认。' }])
const suggestions = ref<string[]>([]), chatResults = ref<ToolResult[]>([]), actions = ref<AgentAction[]>([]), busyIds = ref<string[]>([])
const actionErrors = reactive<Record<string, string>>({}), selectedTool = ref(''), toolForm = reactive<Record<string, any>>({}), toolBusy = ref(false), toolError = ref(''), result = ref<ToolResult | null>(null)
const currentTool = computed(() => profile.value?.tools.find(tool => tool.name === selectedTool.value))
const toolFields = computed(() => Object.entries(currentTool.value?.parameters.properties || {}))
const pendingCount = computed(() => actions.value.filter(action => action.status === 'PENDING' && !isExpired(action)).length)
const modelFailed = ref(false)
const serviceText = computed(() => {
  if (!online.value) return '网络已断开'
  if (modelFailed.value || profile.value?.model.state?.toLowerCase() === 'degraded') return '模型服务异常 · 本地工具模式'
  if (!profile.value) return '正在连接服务'
  if (!profile.value.model.configured) return '本地工具模式'
  if (profile.value.model.state?.toLowerCase() === 'available') return profile.value.model.toolCalling ? `模型工具已验证 · ${profile.value.model.model || ''}` : '模型已响应 · 工具调用待验证'
  return '模型已配置，待验证 · 本地工具可用'
})
const scroll = () => nextTick(() => { if (isCurrentView() && bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight })
const formatTime = (value: string) => value ? value.replace('T', ' ').slice(0, 19) : '—'
const isExpired = (action: AgentAction) => new Date(action.expiresAt).getTime() <= Date.now()
const statusText = (action: AgentAction) => action.status === 'PENDING' && isExpired(action) ? '已过期' : ({ PENDING: '待确认', SUCCEEDED: '已成功', CANCELLED: '已取消', EXPIRED: '已过期', FAILED: '执行失败' }[action.status])
const inputType = (schema: AgentSchema) => schema.format === 'date-time' ? 'datetime-local' : schema.format === 'date' ? 'date' : ['integer', 'number'].includes(schema.type || '') ? 'number' : 'text'
const optionLabel = (value: string | number) => ({ FAT_LOSS: '减脂', MUSCLE_GAIN: '增肌', POSTURE: '体态改善', ENDURANCE: '耐力', GENERAL: '综合体能', ALL: '全部', DRAFT: '草稿', ACTIVE: '生效中', ARCHIVED: '已归档', TODAY: '今天', UPCOMING: '未来', CONFIRMED: '已确认', CANCELLED: '已取消' } as Record<string, string>)[String(value)] || String(value)
const errorText = (error: any) => error?.response?.data?.message || error?.message || '连接失败，请检查网络后重试。'
const mergeActions = (incoming: AgentAction[]) => { const map = new Map(actions.value.map(action => [action.id, action])); for (const action of incoming) map.set(action.id, action); actions.value = [...map.values()].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 30) }
const resetToolForm = () => {
  for (const key of Object.keys(toolForm)) delete toolForm[key]
  for (const [key, schema] of toolFields.value) toolForm[key] = schema.default ?? (schema.type === 'array' ? [] : '')
  result.value = null; toolError.value = ''
}
const loadProfile = async () => {
  if (!isCurrentView() || !online.value) return
  const epoch = ++profileEpoch; profileError.value = ''
  try { const response = await getAgentProfile(config()); if (!isCurrentView() || epoch !== profileEpoch) return
    profile.value = response.data; suggestions.value = response.data.suggestions || []
    if (messages.value.length === 1) messages.value[0].text = response.data.greeting
    if (!selectedTool.value) { selectedTool.value = response.data.tools[0]?.name || ''; resetToolForm() }
  } catch (error) { if (isCurrentView() && epoch === profileEpoch) profileError.value = errorText(error) }
}
const loadActions = async () => {
  if (!isCurrentView() || !online.value) return
  const epoch = ++historyEpoch, receipt = receiptEpoch; historyLoading.value = true; historyError.value = ''
  try { const response = await listAgentActions(config()); if (isCurrentView() && epoch === historyEpoch && receipt === receiptEpoch) actions.value = response.data }
  catch (error) { if (isCurrentView() && epoch === historyEpoch) historyError.value = errorText(error) }
  finally { if (isCurrentView() && epoch === historyEpoch) historyLoading.value = false }
}
const selectTab = (value: string) => { tab.value = value; if (value === 'actions') void loadActions() }
const togglePanel = () => { open.value = !open.value; if (open.value) { void loadProfile(); void loadActions(); nextTick(() => closeButton.value?.focus()) } }
const closePanel = () => { open.value = false; nextTick(() => fab.value?.focus()) }
const openFromNav = () => { if (!open.value) togglePanel() }
const ask = (text: string) => { input.value = text; void send() }
const appendComputerReply = (text: string) => { if (isCurrentView()) messages.value.push({ role: 'bot', text }) }
const send = async () => {
  const text = input.value.trim(); if (!isCurrentView() || !online.value || !text || loading.value) return
  if (executionMode.value === 'computer') {
    input.value = ''; messages.value.push({ role: 'user', text }); loading.value = true; tab.value = 'computer'
    try { await nextTick(); if (isCurrentView()) await computerRef.value?.submitGoal(text) }
    catch (error) { appendComputerReply(errorText(error)) }
    finally { if (isCurrentView()) loading.value = false }
    return
  }
  const history: AssistantTurn[] = messages.value.slice(1).slice(-6).map(item => ({ role: item.role === 'bot' ? 'assistant' : 'user', content: item.text.slice(0, 500) }))
  messages.value.push({ role: 'user', text }); input.value = ''; loading.value = true; scroll()
  try { const response = await chat(text, history, config()); if (!isCurrentView()) return
    messages.value.push({ role: 'bot', text: response.data.answer }); suggestions.value = response.data.suggestions || []
    chatResults.value = response.data.results || []; mergeActions(response.data.actions || []); receiptEpoch++
    modelFailed.value = response.data.mode?.toUpperCase().includes('LOCAL') || response.data.mode?.toUpperCase().includes('FALLBACK') || false
    await loadProfile()
  } catch (error) { if (isCurrentView()) messages.value.push({ role: 'bot', text: errorText(error) }) }
  finally { if (isCurrentView()) { loading.value = false; scroll() } }
}
const validateField = (name: string, schema: AgentSchema, value: any, required: boolean): unknown => {
  const label = schema.description || name
  if (value === '' || value == null) { if (required) throw Error(`请填写${label}`); return undefined }
  if (schema.type === 'integer' || schema.type === 'number') { const number = Number(value)
    if (!Number.isFinite(number) || (schema.type === 'integer' && !Number.isSafeInteger(number)) || (schema.minimum != null && number < schema.minimum) || (schema.maximum != null && number > schema.maximum)) throw Error(`${label}格式或范围不正确`)
    return number
  }
  if (schema.enum && !schema.enum.includes(value)) throw Error(`请选择${label}`)
  if (schema.type === 'array') {
    if (!Array.isArray(value) || (required && !value.length) || value.length < (schema.minItems || 0) || value.length > (schema.maxItems || 30)) throw Error(`请补充${label}`)
    if (name === 'exercises') return value.map(exercise => {
      const exerciseName = String(exercise.name || '').trim(), note = String(exercise.note || '').trim()
      if (!exerciseName || exerciseName.length > 100) throw Error('动作名称须为 1–100 字')
      if (note.length > 300) throw Error('动作备注最多 300 字')
      const output: Record<string, unknown> = { name: exerciseName, mode: exercise.mode }
      if (exercise.mode === 'STRENGTH') {
        if (!Number.isInteger(exercise.sets) || exercise.sets < 1 || exercise.sets > 20 || !Number.isInteger(exercise.reps) || exercise.reps < 1 || exercise.reps > 200 || (exercise.weightKg != null && (!Number.isFinite(exercise.weightKg) || exercise.weightKg < 0 || exercise.weightKg > 500 || Math.abs(exercise.weightKg * 100 - Math.round(exercise.weightKg * 100)) > 0.0001))) throw Error('请检查动作组数、次数或重量')
        output.sets = exercise.sets; output.reps = exercise.reps
        if (exercise.weightKg != null) output.weightKg = exercise.weightKg
      } else {
        if (exercise.mode !== 'DURATION' || !Number.isInteger(exercise.minutes) || exercise.minutes < 1 || exercise.minutes > 240) throw Error('请填写 1–240 分钟的训练时长')
        output.minutes = exercise.minutes
      }
      if (note) output.note = note
      return output
    })
    return JSON.parse(JSON.stringify(value))
  }
  const text = String(value).trim()
  if (required && !text) throw Error(`请填写${label}`)
  if (text.length > (schema.maxLength || 1000)) throw Error(`${label}内容过长`)
  if (schema.format === 'date' && !/^\d{4}-\d{2}-\d{2}$/.test(text)) throw Error(`请选择${label}`)
  if (schema.format === 'date-time' && !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(text)) throw Error(`请选择${label}`)
  return schema.format === 'date-time' && text.length === 16 ? `${text}:00` : text
}
const runTool = async () => {
  if (!isCurrentView() || !online.value || toolBusy.value || !currentTool.value) return
  toolError.value = ''; const tool = currentTool.value, args: Record<string, unknown> = {}
  try { for (const [name, schema] of toolFields.value) { const value = validateField(name, schema, toolForm[name], !!tool.parameters.required?.includes(name)); if (value !== undefined) args[name] = value } }
  catch (error) { toolError.value = errorText(error); return }
  toolBusy.value = true; result.value = null
  try {
    if (tool.write) { const response = await previewAgentAction(tool.name, args, config()); if (!isCurrentView()) return; receiptEpoch++; mergeActions([response.data]); tab.value = 'actions' }
    else { const response = await queryAgentTool(tool.name, args, config()); if (isCurrentView()) result.value = response.data }
  } catch (error) { if (isCurrentView()) toolError.value = errorText(error) }
  finally { if (isCurrentView()) toolBusy.value = false }
}
const performAction = async (action: AgentAction, cancel: boolean) => {
  if (!isCurrentView() || !online.value || action.status !== 'PENDING' || isExpired(action) || busyIds.value.includes(action.id)) return
  busyIds.value.push(action.id); delete actionErrors[action.id]; receiptEpoch++
  try { const response = await (cancel ? cancelAgentAction(action.id, config()) : confirmAgentAction(action.id, config())); if (!isCurrentView()) return; receiptEpoch++; mergeActions([response.data]) }
  catch (error) { if (isCurrentView()) { actionErrors[action.id] = errorText(error); await loadActions() } }
  finally { if (isCurrentView()) busyIds.value = busyIds.value.filter(id => id !== action.id) }
}
const confirmAction = (action: AgentAction) => performAction(action, false)
const cancelAction = (action: AgentAction) => performAction(action, true)
const syncNetwork = () => { online.value = navigator.onLine !== false }
watch(() => authStore.sessionGeneration, () => {
  open.value = false; profile.value = null; messages.value = []; actions.value = []; chatResults.value = []; suggestions.value = []; input.value = ''; result.value = null; selectedTool.value = ''; loading.value = false; toolBusy.value = false
  for (const key of Object.keys(toolForm)) delete toolForm[key]
  for (const key of Object.keys(actionErrors)) delete actionErrors[key]
}, { flush: 'sync' })
onMounted(() => { window.addEventListener?.('online', syncNetwork); window.addEventListener?.('offline', syncNetwork); window.addEventListener?.('fitchain:open-assistant', openFromNav); window.visualViewport?.addEventListener('resize', syncViewport); window.visualViewport?.addEventListener('scroll', syncViewport); syncViewport(); void loadProfile() })
onBeforeUnmount(() => { disposed = true; window.removeEventListener?.('online', syncNetwork); window.removeEventListener?.('offline', syncNetwork); window.removeEventListener?.('fitchain:open-assistant', openFromNav); window.visualViewport?.removeEventListener?.('resize', syncViewport); window.visualViewport?.removeEventListener?.('scroll', syncViewport) })
</script>

<style scoped>
.execution-mode{display:flex;align-items:center;gap:6px;flex:none;padding:9px 12px;border-bottom:1px solid #e3ebf6;background:#f7faff;flex-wrap:wrap}.execution-mode button{border:1px solid #d4e2f5;background:#fff;color:#57789d;border-radius:8px;padding:7px 10px;font-size:12px;cursor:pointer}.execution-mode button.active{background:#e8f0ff;border-color:#8eb5f0;color:#215fb7}.execution-mode small{flex:1;min-width:110px;font-size:10px;line-height:1.5;color:#6d85a1}
.assistant{position:fixed;right:28px;bottom:26px;z-index:2147483000}.assistant-fab{display:flex;align-items:center;justify-content:center;min-width:58px;height:56px;border:0;border-radius:18px;background:#2365d6;color:#fff;box-shadow:0 10px 30px #1468ff50;cursor:pointer}.assistant-panel{position:absolute;right:0;bottom:70px;width:460px;height:min(740px,calc(100dvh - 120px));background:#fff;border:1px solid #dce7f4;border-radius:20px;box-shadow:0 24px 70px #18274b38;display:flex;flex-direction:column;overflow:hidden}.assistant-head{flex:none;padding:15px 18px;display:flex;align-items:center;justify-content:space-between;color:#193c64;background:linear-gradient(135deg,#edf5ff,#fff)}.assistant-head small{display:block;margin-top:5px;color:#6c819a;font-size:11px}.assistant-head button{display:grid;place-items:center;min-height:42px;min-width:42px;border:0;border-radius:10px;background:#edf4fd;color:#35577f;cursor:pointer}.assistant-tabs{flex:none;display:flex;padding:0 12px;border-bottom:1px solid #dde7f3}.assistant-tabs button{flex:1;min-height:46px;border:0;background:white;color:#6a7f99;border-bottom:3px solid transparent;cursor:pointer}.assistant-tabs button.active{border-bottom-color:#2a6cd5;color:#215eb4;font-weight:700}.assistant-body{flex:1;min-height:0;overflow:auto;overscroll-behavior:contain;padding:16px;background:#f6f9fe}.bubble-row{display:flex;margin-bottom:12px}.bubble-row.user{justify-content:flex-end}.bubble{max-width:90%;padding:12px;border-radius:14px;background:#fff;color:#374e6c;font-size:14px;line-height:1.7;white-space:pre-wrap;overflow-wrap:anywhere}.user .bubble{background:#2768d5;color:#fff}.quick-list{display:flex;flex:none;gap:7px;overflow:auto;padding:8px 12px;border-top:1px solid #eef1f6}.quick-list button{white-space:nowrap;border:1px solid #dce5f5;background:#f8fbff;color:#3970bd;border-radius:14px;padding:7px 10px;font-size:12px;cursor:pointer}.assistant-input{display:flex;flex:none;gap:8px;padding:12px}.assistant-input input{min-width:0;flex:1;border:1px solid #cbdbee;border-radius:9px;padding:10px;font-size:16px}.assistant-input button,.agent-primary{min-height:44px;border:0;border-radius:9px;padding:10px 15px;background:#2768d5;color:#fff;cursor:pointer}.agent-warning{flex:none;padding:10px 14px;background:#fff6df;color:#8c5f17;font-size:12px;line-height:1.6}.agent-warning button{background:transparent;border:0;color:#2768d5;text-decoration:underline;cursor:pointer}.agent-help{margin-bottom:14px;color:#637996;font-size:12px;line-height:1.7}.tool-field{display:grid;gap:7px;margin-bottom:15px;color:#385570;font-size:13px}.tool-field b{color:#c24b48}.tool-field>input,.tool-field>select,.tool-field>textarea{width:100%;min-width:0;min-height:44px;padding:10px;border:1px solid #cddbed;border-radius:9px;background:#fff;color:#274466;font-size:16px}.tools-body form>.agent-primary{width:100%}.result-card,.action-card{background:#fff;border:1px solid #dce6f2;border-radius:13px;padding:14px;margin:12px 0}.result-card h3,.action-card h3{font-size:14px;color:#254568;margin-bottom:8px}.result-card small,.action-card small{display:block;color:#7f8fa4;font-size:11px;margin-bottom:10px}.result-card>p,.preview-hint{font-size:12px;color:#9c6c20;line-height:1.6}.agent-error{font-size:12px;color:#bf4141;line-height:1.6;margin:8px 0}.history-heading,.action-heading{display:flex;justify-content:space-between;align-items:center;gap:10px}.history-heading p{font-size:12px;color:#6f8299}.history-heading button,.action-buttons>button:not(.agent-primary){min-height:40px;padding:8px 10px;border:1px solid #cdddf0;border-radius:9px;background:#fff;color:#3562a3;cursor:pointer}.action-heading span{font-size:11px;white-space:nowrap;color:#a67322}.action-summary{font-size:13px;line-height:1.8;color:#38536f;white-space:pre-wrap;overflow-wrap:anywhere}.action-card details{font-size:12px;margin:10px 0}.action-card summary{cursor:pointer;padding:8px 0;color:#3463a0}.action-buttons{display:flex;gap:10px;margin-top:12px}.action-buttons>button{flex:1}.succeeded{border-color:#b9dfcf}.succeeded .action-heading span,.receipt-success{color:#21825d}.receipt-success{font-size:12px;line-height:1.7;margin:10px 0}button:disabled{opacity:.5;cursor:not-allowed}button:focus-visible,input:focus-visible,select:focus-visible,textarea:focus-visible{outline:3px solid #84b6ff;outline-offset:2px}.pop-enter-active,.pop-leave-active{transition:.18s}.pop-enter-from,.pop-leave-to{opacity:0;transform:translateY(10px)}
@media(max-width:760px){.assistant{right:14px;bottom:calc(80px + env(safe-area-inset-bottom,0px))}.assistant-fab{display:none}.assistant-panel{position:fixed;inset:0;width:100%;top:var(--agent-viewport-top,0px);bottom:auto;height:var(--agent-viewport-height,100dvh);max-height:var(--agent-viewport-height,100dvh);border:0;border-radius:0;padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}.assistant-head{padding:10px 14px}.assistant-body{padding:12px}.assistant-input{padding-bottom:12px}.assistant-tabs button{min-height:48px}}
@media(prefers-reduced-motion:reduce){.pop-enter-active,.pop-leave-active{transition:none}}
</style>
