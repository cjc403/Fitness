import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { transformSync } from 'esbuild'
import * as vue from 'vue'

const sourceRoot = new URL('../src/', import.meta.url)
function execute(path, deps, globals, names = []) {
  const file = new URL(path, sourceRoot)
  assert.ok(existsSync(file), `${path} must exist`)
  let source = readFileSync(file, 'utf8')
  if (path.endsWith('.vue')) source = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
  source += names.length ? `\nexport { ${names.join(',')} }` : ''
  const module = { exports: {} }
  new Function('require', 'module', 'exports', ...Object.keys(globals), transformSync(source, { loader: 'ts', format: 'cjs' }).code)(name => {
    assert.ok(name in deps, `Controlled import ${name}`)
    return deps[name]
  }, module, module.exports, ...Object.values(globals))
  return module.exports
}
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
const flush = async () => { for (let i = 0; i < 16; i++) await Promise.resolve() }
const task = (status = 'RUNNING', extra = {}) => ({ id: 'task-1', goal: '打开我本周的课程', status, createdAt: '2026-10-15T08:00:00Z', updatedAt: '2026-10-15T08:00:01Z', steps: [{ index: 1, action: 'navigate', label: '打开课程', status: 'SUCCEEDED' }], artifacts: [], ...extra })
const confirmation = { id: 'confirm-1', method: 'POST', path: '/api/v1/bookings', summary: '预约课程 7', fields: [{ label: '课程编号', value: '7' }] }

function runtime(options = {}) {
  const calls = [], mounted = [], unmounted = [], timers = new Map(), listeners = new Map(), replies = []
  let nextTimer = 0
  const auth = vue.reactive({ token: 'session-A', sessionGeneration: 1, userInfo: { id: 12, role: 'MEMBER', roleName: '会员', organizationId: 3, permissions: [] } })
  const props = vue.reactive({ active: true })
  const navigator = { onLine: true }
  const globals = {
    navigator, AbortController, console,
    setTimeout: fn => { timers.set(++nextTimer, fn); return nextTimer }, clearTimeout: id => timers.delete(id),
    window: { addEventListener: (event, fn) => listeners.set(event, fn), removeEventListener: event => listeners.delete(event) },
    defineProps: () => props, withDefaults: value => value, defineEmits: () => (event, text) => replies.push({ event, text }), defineExpose: () => {},
    fetch: async (url, config) => {
      const call = { url, method: config.method, body: config.body ? JSON.parse(config.body) : undefined, headers: config.headers, signal: config.signal }
      calls.push(call)
      const payload = await options.fetch?.(call)
      if (payload instanceof Error) throw payload
      const data = payload ?? (url.endsWith('/status') ? { available: true, platform: 'win32', scope: 'browser', outputDirectory: 'C:\\Users\\test\\Desktop\\FitChain任务成果' } : url.endsWith('/tasks') && config.method === 'GET' ? [] : task())
      return { ok: true, status: 200, json: async () => ({ code: 0, message: 'ok', data }) }
    }
  }
  const deps = { vue: { ...vue, onMounted: fn => mounted.push(fn), onBeforeUnmount: fn => unmounted.push(fn) }, '@/store/modules/auth': { useAuthStore: () => auth } }
  const api = execute('api/modules/computerAgent.ts', deps, globals)
  deps['@/api/modules/computerAgent'] = api
  deps['@/agent/currentPageAgent'] = { ...api, suspendPageTasks() {} }
  const scope = vue.effectScope()
  const panel = scope.run(() => execute('components/ComputerAgentPanel.vue', deps, globals, ['submitGoal', 'refresh', 'task', 'tasks', 'online', 'error', 'busy', 'confirmTask', 'pauseTask', 'resumeTask', 'stopTask', 'sendInput', 'supplement', 'goalInput', 'selectTask', 'roleName', 'suggestions', 'statusLabel', 'screenshotSource', 'pendingPreview']))
  return { ...panel, api, auth, props, navigator, calls, timers, listeners, replies, mount: async () => { mounted.forEach(fn => fn()); await flush() }, unmount() { unmounted.forEach(fn => fn()); scope.stop() }, async tick() { const next = timers.entries().next().value; if (next) { timers.delete(next[0]); await next[1](); await flush() } } }
}

test('computer API stays same origin, carries live bearer, encodes ids and binds exact confirmation only', async () => {
  const p = runtime()
  await p.api.confirmComputerTask('task/a', 'confirm/b')
  assert.equal(p.calls[0].url, '/local-agent/v1/tasks/task%2Fa/confirm')
  assert.deepEqual(p.calls[0].body, { confirmationId: 'confirm/b' })
  assert.equal(p.calls[0].headers.Authorization, 'Bearer session-A')
  p.auth.token = 'session-B'; await p.api.stopComputerTask('task/a')
  assert.equal(p.calls[1].headers.Authorization, 'Bearer session-B')
  assert.deepEqual(p.calls[1].body, {}); p.unmount()
})

test('API rejects delayed data for logout, role, venue and caller scope replacement', async () => {
  for (const change of [p => { p.auth.token = null; p.auth.sessionGeneration++ }, p => { p.auth.userInfo.role = 'COACH' }, p => { p.auth.userInfo.organizationId = 9 }, p => { p.current = false }]) {
    const pending = deferred(), p = runtime({ fetch: () => pending.promise }); p.current = true
    const request = p.api.getComputerTask('task-1', { isRequestCurrent: () => p.current }).then(() => 'published', () => 'discarded')
    change(p); pending.resolve(task('SUCCEEDED'))
    assert.equal(await request, 'discarded'); p.unmount()
  }
})

test('offline API refuses every mutation without transport or replay after reconnect', async () => {
  const p = runtime(); p.navigator.onLine = false
  for (const call of [() => p.api.createComputerTask('打开课程'), () => p.api.pauseComputerTask('a'), () => p.api.resumeComputerTask('a'), () => p.api.stopComputerTask('a'), () => p.api.confirmComputerTask('a', 'c'), () => p.api.inputComputerTask('a', '明天')]) await assert.rejects(call, /离线/)
  assert.equal(p.calls.length, 0); p.navigator.onLine = true; await flush(); assert.equal(p.calls.length, 0); p.unmount()
})

test('all six roles get their own task suggestions and no role is treated as coach', () => {
  const p = runtime()
  for (const [role, name, word] of [['SUPER_ADMIN', '总部管理员', '总览'], ['MANAGER', '店长', '门店'], ['RECEPTION', '前台', '会员'], ['COACH', '教练', '学员'], ['SALES', '会籍顾问', '潜客'], ['MEMBER', '会员', '我的']]) {
    p.auth.userInfo.role = role; p.auth.userInfo.roleName = name
    assert.equal(p.roleName.value, name); assert.ok(p.suggestions.value.some(text => text.includes(word)))
  }
  p.unmount()
})

test('mount and reopen discover the existing running task; closing cancels polling without stopping it', async () => {
  const p = runtime({ fetch: call => call.url.endsWith('/tasks') && call.method === 'GET' ? [task()] : undefined })
  await p.mount(); assert.equal(p.task.value.id, 'task-1'); assert.equal(p.timers.size, 1)
  p.props.active = false; await vue.nextTick(); assert.equal(p.timers.size, 0)
  assert.equal(p.calls.some(c => c.method === 'POST'), false)
  p.props.active = true; await vue.nextTick(); await flush()
  assert.equal(p.calls.filter(c => c.url.endsWith('/tasks')).length, 2)
  assert.equal(p.timers.size, 1); p.unmount(); assert.equal(p.timers.size, 0); assert.equal(p.listeners.size, 0)
})

test('a polled terminal receipt stops polling and only actual success emits a success reply', async () => {
  for (const status of ['SUCCEEDED', 'FAILED', 'CANCELLED']) {
    const p = runtime({ fetch: call => call.url.endsWith('/tasks') && call.method === 'GET' ? [task()] : call.url.endsWith('/task-1') ? task(status, { error: status === 'FAILED' ? '页面无法访问' : undefined }) : undefined })
    await p.mount(); await p.tick(); assert.equal(p.task.value.status, status); assert.equal(p.timers.size, 0)
    assert.equal(p.replies.some(r => /已完成/.test(r.text)), status === 'SUCCEEDED')
    assert.match(p.statusLabel(status), status === 'FAILED' ? /失败/ : status === 'CANCELLED' ? /停止/ : /完成/)
    p.unmount()
  }
})

test('identity replacement clears screenshot, goal and task, and rejects a late create response', async () => {
  const pending = deferred(), p = runtime({ fetch: call => call.method === 'POST' ? pending.promise : undefined })
  await p.refresh(); const submitted = p.submitGoal('整理我的本周课表')
  p.auth.sessionGeneration++; p.auth.userInfo.organizationId = 8; await vue.nextTick()
  pending.resolve(task('SUCCEEDED', { screenshot: 'data:image/jpeg;base64,YQ==' })); await submitted; await flush()
  assert.equal(p.task.value, null); assert.deepEqual(p.tasks.value, []); assert.equal(p.screenshotSource.value, undefined)
  assert.equal(p.replies.some(r => /已完成/.test(r.text)), false); p.unmount()
})

test('confirmation displays frozen fields and sends only displayed confirmation id once', async () => {
  const wait = deferred(), remote = task('WAITING_CONFIRMATION', { pendingConfirmation: structuredClone(confirmation) })
  const p = runtime({ fetch: call => call.url.endsWith('/tasks') && call.method === 'GET' ? [remote] : call.url.endsWith('/confirm') ? wait.promise : undefined })
  await p.mount(); remote.pendingConfirmation.fields[0].value = '99'
  assert.equal(p.pendingPreview.value.fields[0].value, '7')
  const first = p.confirmTask(), second = p.confirmTask()
  assert.equal(p.calls.filter(c => c.url.endsWith('/confirm')).length, 1)
  assert.deepEqual(p.calls.at(-1).body, { confirmationId: 'confirm-1' })
  wait.resolve(task('RUNNING')); await Promise.all([first, second]); assert.equal(p.task.value.status, 'RUNNING')
  await p.confirmTask(); assert.equal(p.calls.filter(c => c.url.endsWith('/confirm')).length, 1); p.unmount()
})

test('pause, explicit resume, stop and user input preserve authoritative states and payload', async () => {
  const p = runtime({ fetch: call => call.url.endsWith('/pause') ? task('PAUSED') : call.url.endsWith('/resume') ? task('RUNNING') : call.url.endsWith('/stop') ? task('CANCELLED') : call.url.endsWith('/input') ? task('RUNNING') : call.url.endsWith('/tasks') && call.method === 'GET' ? [task()] : undefined })
  await p.mount(); await p.pauseTask(); assert.equal(p.task.value.status, 'PAUSED')
  p.listeners.get('online')(); await flush(); assert.equal(p.calls.some(c => c.url.endsWith('/resume')), false)
  await p.resumeTask(); assert.equal(p.task.value.status, 'RUNNING')
  p.task.value = task('WAITING_INPUT', { question: '哪一天？' }); p.supplement.value = ' 明天 '
  await p.sendInput(); assert.deepEqual(p.calls.at(-1).body, { message: '明天' }); assert.equal(p.task.value.status, 'RUNNING')
  await p.stopTask(); assert.equal(p.task.value.status, 'CANCELLED'); assert.equal(p.timers.size, 0); p.unmount()
})

test('offline UI disables controls and invalid screenshot schemes are never rendered', async () => {
  const p = runtime({ fetch: call => call.url.endsWith('/tasks') && call.method === 'GET' ? [task('WAITING_CONFIRMATION', { pendingConfirmation: confirmation, screenshot: 'https://untrusted/image.jpg' })] : undefined })
  await p.mount(); assert.equal(p.screenshotSource.value, undefined)
  p.online.value = false; const count = p.calls.length
  await p.submitGoal('新的课程'); await p.confirmTask(); await p.pauseTask(); await p.resumeTask(); await p.stopTask(); await p.sendInput()
  assert.equal(p.calls.length, count); assert.match(p.error.value, /离线/); p.unmount()
})

test('unmount ignores delayed polling receipts and does not publish private replies', async () => {
  const pending = deferred(), p = runtime({ fetch: call => call.url.endsWith('/tasks') && call.method === 'GET' ? [task()] : call.url.endsWith('/task-1') ? pending.promise : undefined })
  await p.mount(); const read = p.tick(); await flush(); p.unmount(); pending.resolve(task('SUCCEEDED')); await read
  assert.equal(p.replies.length, 0); assert.equal(p.timers.size, 0)
})

test('the first conversational goal waits for initial executor discovery instead of being lost', async () => {
  const pending = deferred(), p = runtime({ fetch: call => call.url.endsWith('/status') ? pending.promise : undefined })
  const opening = p.mount(); await flush(); const submit = p.submitGoal('打开我的本周课程')
  assert.equal(p.calls.filter(call => call.url.endsWith('/status')).length, 1)
  pending.resolve({ available: true, platform: 'win32', scope: 'browser', outputDirectory: 'C:\\成果' })
  await Promise.all([opening, submit]); assert.equal(p.calls.filter(call => call.method === 'POST').length, 1)
  assert.equal(p.task.value.goal, '打开我本周的课程'); p.unmount()
})

test('conversation replies supplement WAITING_INPUT and selecting history cannot start a second active task', async () => {
  const p = runtime({ fetch: call => call.url.endsWith('/tasks') && call.method === 'GET' ? [task('WAITING_INPUT', { question: '哪一天？' })] : call.url.endsWith('/task-old') ? task('SUCCEEDED', { id: 'task-old' }) : undefined })
  await p.mount(); await p.submitGoal('明天')
  assert.equal(p.calls.at(-1).url, '/local-agent/v1/tasks/task-1/input'); assert.deepEqual(p.calls.at(-1).body, { message: '明天' })
  await p.selectTask('task-old'); const count = p.calls.length; await p.submitGoal('另一个新任务')
  assert.equal(p.calls.length, count); assert.match(p.error.value, /进行中/); p.unmount()
})

test('a delayed poll cannot overwrite the newer pause receipt', async () => {
  const pending = deferred(), p = runtime({ fetch: call => call.url.endsWith('/tasks') && call.method === 'GET' ? [task()] : call.url.endsWith('/task-1') ? pending.promise : call.url.endsWith('/pause') ? task('PAUSED') : undefined })
  await p.mount(); const read = p.tick(); await flush(); await p.pauseTask(); pending.resolve(task('RUNNING')); await read
  assert.equal(p.task.value.status, 'PAUSED'); assert.equal(p.timers.size, 1); p.unmount()
})

test('late create and input completion cannot clear the replacement session drafts', async () => {
  for (const needsInput of [false, true]) {
    const pending = deferred(), p = runtime({ fetch: call => call.method === 'POST' ? pending.promise : call.url.endsWith('/tasks') && call.method === 'GET' && needsInput ? [task('WAITING_INPUT')] : undefined })
    await p.refresh(); p.goalInput.value = '明天'; p.supplement.value = '明天'
    const write = needsInput ? p.sendInput() : p.submitGoal('明天')
    p.auth.sessionGeneration++; p.goalInput.value = '新会话任务'; p.supplement.value = '新会话补充'
    pending.resolve(task('RUNNING')); await write
    assert.equal(p.goalInput.value, '新会话任务'); assert.equal(p.supplement.value, '新会话补充'); p.unmount()
  }
})
