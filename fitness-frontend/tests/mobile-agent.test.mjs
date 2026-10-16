import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import { transformSync } from 'esbuild'
import * as vue from 'vue'
import * as access from '../src/utils/accessControl.mjs'

const root = new URL('../', import.meta.url)
function execute(path, deps = {}, names = []) {
  const file = new URL('src/' + path, root)
  assert.ok(existsSync(file), `${path} must exist`)
  let source = readFileSync(file, 'utf8')
  if (path.endsWith('.vue')) source = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
  source += names.length ? `\nexport { ${names.join(',')} }` : ''
  const module = { exports: {} }
  new Function('require', 'module', 'exports', 'defineProps', 'defineEmits', transformSync(source, { loader: 'ts', format: 'cjs' }).code)(name => {
    if (name.startsWith('@/components/')) return {}
    assert.ok(name in deps, `Controlled import ${name}`)
    return deps[name]
  }, module, module.exports, () => deps.__props || {}, () => deps.__emit || (() => {}))
  return module.exports
}
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
const preview = (id = 'preview-1') => ({ id, tool: 'book_course', title: '预约课程', summary: '课程 12，仅确认后预约', parameters: { scheduleId: 12 }, status: 'PENDING', createdAt: '2026-10-15T10:00:00', expiresAt: '2099-10-15T10:10:00' })
const tools = [{ name: 'courses', label: '可约课程', write: false, parameters: { type: 'object', properties: {} } }, { name: 'book_course', label: '预约课程', write: true, parameters: { type: 'object', required: ['scheduleId'], properties: { scheduleId: { type: 'integer', minimum: 1, description: '排课编号' } } } }]
function runtime(overrides = {}) {
  const mounted = [], unmounted = [], calls = [], notices = []
  const auth = vue.reactive({ sessionGeneration: 1, userInfo: { id: 8, role: 'MEMBER', permissions: ['ASSISTANT_USE'] } })
  globalThis.window = { addEventListener() {}, removeEventListener() {}, dispatchEvent() {} }
  const transport = {
    get: async (url, cfg) => { calls.push({ method: 'GET', url, cfg }); return { data: await (overrides.get?.(url) ?? (url.endsWith('profile') ? { role: 'MEMBER', roleName: '会员', greeting: '你好会员', suggestions: [], tools, model: { configured: false, toolCalling: false, state: 'LOCAL_TOOLS' } } : [])) } },
    post: async (url, body, cfg) => { calls.push({ method: 'POST', url, body, cfg }); return { data: await (overrides.post?.(url, body) ?? (url.endsWith('/preview') ? preview() : url.endsWith('/confirm') ? { ...preview(), status: 'SUCCEEDED', result: { id: 99 } } : url.endsWith('/cancel') ? { ...preview(), status: 'CANCELLED' } : { answer: '已查询', suggestions: [], source: '真实业务查询', results: [], actions: [] })) } }
  }
  const deps = { vue: { ...vue, onMounted: fn => mounted.push(fn), onBeforeUnmount: fn => unmounted.push(fn) }, '@/store/modules/auth': { useAuthStore: () => auth }, '@element-plus/icons-vue': {}, 'element-plus': { ElMessage: { warning: m => notices.push(m), error: m => notices.push(m) } }, '@/utils/request': { __esModule: true, default: transport } }
  const api = execute('api/modules/assistant.ts', deps)
  deps['@/api/modules/assistant'] = api
  const scope = vue.effectScope()
  const page = scope.run(() => execute('components/AssistantPanel.vue', deps, ['input', 'send', 'messages', 'profile', 'actions', 'selectedTool', 'toolForm', 'runTool', 'confirmAction', 'cancelAction', 'online', 'result', 'toolError', 'loading', 'loadProfile', 'loadActions', 'syncViewport', 'viewportHeight', 'viewportTop', 'serviceText', 'executionMode', 'computerRef', 'tab']))
  page.executionMode.value = 'business' // These tests exercise the separate business-answer protocol.
  return { ...page, api, auth, calls, notices, mount: () => Promise.all(mounted.map(fn => fn())), unmount() { unmounted.forEach(fn => fn()); scope.stop() } }
}

test('all six mobile roles prioritize their permitted home and never expose excluded routes', async () => {
  const file = new URL('src/utils/mobileNavigation.mjs', root)
  assert.ok(existsSync(file), 'mobile navigation module must exist')
  const { mobileNavigation } = await import(file.href)
  const menus = Object.entries(access.ROLE_HOME).map(([role, path]) => ({ path, label: role, permission: role, roles: [role] }))
  menus.push({ path: '/forbidden', permission: 'NO_ACCESS' })
  for (const role of Object.keys(access.ROLE_HOME)) {
    const nav = mobileNavigation(menus, role, [role])
    assert.equal(nav.primary[0].path, access.ROLE_HOME[role])
    assert.deepEqual([...nav.primary, ...nav.more].map(m => m.path), [access.ROLE_HOME[role]])
  }
  assert.deepEqual(mobileNavigation(menus, 'UNKNOWN', []).primary, [])
})

test('computer conversation delegates the goal to the actual task panel without issuing a business chat', async () => {
  const p = runtime(), goals = []
  assert.ok(p.executionMode && p.computerRef, 'Conversation must expose an actual computer task execution path')
  p.executionMode.value = 'computer'; p.computerRef.value = { submitGoal: async text => goals.push(text) }
  p.input.value = '整理我本周的课程并生成文件'; await p.send()
  assert.deepEqual(goals, ['整理我本周的课程并生成文件'])
  assert.equal(p.tab.value, 'computer'); assert.equal(p.calls.length, 0)
  assert.equal(p.loading.value, false); p.unmount()
})

test('agent real tool form reads directly and write form only creates a preview', async () => {
  const p = runtime(); await p.loadProfile()
  p.selectedTool.value = 'courses'; await p.runTool()
  assert.equal(p.calls.at(-1).url, '/v1/agent/query')
  p.selectedTool.value = 'book_course'; p.toolForm.scheduleId = 12; await p.runTool()
  assert.equal(p.calls.at(-1).url, '/v1/agent/actions/preview')
  assert.deepEqual(p.calls.at(-1).body, { tool: 'book_course', arguments: { scheduleId: 12 } })
  assert.equal(p.actions.value[0].status, 'PENDING')
  assert.equal(p.calls.some(c => c.url.endsWith('/confirm')), false)
  p.unmount()
})

test('invalid required integer never reaches preview transport', async () => {
  const p = runtime(); await p.loadProfile(); p.selectedTool.value = 'book_course'
  for (const value of [undefined, 0, 1.2, 'abc']) { p.toolForm.scheduleId = value; await p.runTool() }
  assert.equal(p.calls.filter(c => c.method === 'POST').length, 0)
  assert.match(p.toolError.value, /排课编号/); p.unmount()
})

test('confirmation freezes identity and sends only action id and empty object, once while pending', async () => {
  const pending = deferred(), p = runtime({ post: () => pending.promise })
  p.actions.value = [preview()]
  const first = p.confirmAction(p.actions.value[0]); const second = p.confirmAction(p.actions.value[0])
  assert.equal(p.calls.length, 1); assert.equal(p.calls[0].url, '/v1/agent/actions/preview-1/confirm')
  assert.deepEqual(p.calls[0].body, {})
  pending.resolve({ ...preview(), status: 'SUCCEEDED', result: { bookingId: 40 } }); await Promise.all([first, second])
  assert.equal(p.actions.value[0].status, 'SUCCEEDED'); await p.confirmAction(p.actions.value[0])
  assert.equal(p.calls.length, 1); p.unmount()
})

test('offline never submits chat, tools, confirmation or cancellation', async () => {
  const p = runtime(); await p.loadProfile(); p.calls.length = 0; p.online.value = false
  p.input.value = '预约'; p.selectedTool.value = 'book_course'; p.toolForm.scheduleId = 12; p.actions.value = [preview()]
  await p.send(); await p.runTool(); await p.confirmAction(p.actions.value[0]); await p.cancelAction(p.actions.value[0])
  assert.deepEqual(p.calls, []); p.unmount()
})

test('account replacement erases private state and rejects late preview and old confirmation', async () => {
  const pending = deferred(), p = runtime({ post: () => pending.promise }); await p.loadProfile()
  p.selectedTool.value = 'book_course'; p.toolForm.scheduleId = 12; const old = p.runTool()
  p.auth.sessionGeneration++; await vue.nextTick(); pending.resolve(preview()); await old
  assert.deepEqual(p.actions.value, []); assert.equal(p.profile.value, null)
  const count = p.calls.length; await p.confirmAction(preview()); assert.equal(p.calls.length, count); p.unmount()
})

test('cancelled and expired previews cannot be confirmed; history refresh preserves authoritative receipts', async () => {
  const p = runtime(); p.actions.value = [preview()]; await p.cancelAction(p.actions.value[0])
  assert.equal(p.actions.value[0].status, 'CANCELLED'); const count = p.calls.length
  await p.confirmAction(p.actions.value[0]); await p.confirmAction({ ...preview(), expiresAt: '2000-01-01T00:00:00' })
  assert.equal(p.calls.length, count); p.unmount()
})

test('a delayed history read cannot replace a newer successful receipt', async () => {
  const history = deferred(), p = runtime({ get: url => url.endsWith('/actions') ? history.promise : undefined })
  p.actions.value = [preview()]; const read = p.loadActions(); await p.confirmAction(p.actions.value[0])
  history.resolve([preview()]); await read
  assert.equal(p.actions.value[0].status, 'SUCCEEDED'); p.unmount()
})

test('failed confirmation reloads authoritative FAILED state rather than claiming success', async () => {
  const p = runtime({ post: () => { throw Error('训练条件已改变') }, get: url => url.endsWith('/actions') ? [{ ...preview(), status: 'FAILED', error: '训练条件已改变' }] : undefined })
  p.actions.value = [preview()]; await p.confirmAction(p.actions.value[0])
  assert.equal(p.actions.value[0].status, 'FAILED'); assert.equal(p.actions.value[0].result, undefined)
  p.unmount()
})

test('all agent endpoints forward a request scope; confirmation has no mutable arguments', async () => {
  const p = runtime(), guard = () => true, cfg = { isRequestCurrent: guard }
  await p.api.getAgentProfile(cfg); await p.api.chat('你好', [], cfg); await p.api.queryAgentTool('courses', {}, cfg); await p.api.previewAgentAction('book_course', { scheduleId: 2 }, cfg); await p.api.listAgentActions(cfg); await p.api.confirmAgentAction('a/b', cfg); await p.api.cancelAgentAction('a/b', cfg)
  assert.equal(p.calls.length, 7); assert.ok(p.calls.every(call => call.cfg.isRequestCurrent === guard))
  assert.equal(p.calls.find(call => call.url.endsWith('/chat')).cfg.timeout, 90000)
  assert.equal(p.calls.at(-2).cfg.timeout, undefined)
  assert.deepEqual(p.calls.at(-2).body, {}); assert.equal(p.calls.at(-2).url, '/v1/agent/actions/a%2Fb/confirm'); p.unmount()
})

test('training draft duration rejects values over business maximum 240 minutes', async () => {
  const draft = {name:'training_draft',label:'训练草稿',write:true,parameters:{type:'object',properties:{exercises:{type:'array',description:'训练动作'}},required:['exercises']}}
  const p = runtime({get:url=>url.endsWith('/profile')?{role:'COACH',roleName:'教练',greeting:'你好',suggestions:[],tools:[draft],model:{configured:false,toolCalling:false,state:'LOCAL_TOOLS'}}:undefined})
  await p.loadProfile(); p.selectedTool.value='training_draft'; p.toolForm.exercises=[{name:'步行',mode:'DURATION',minutes:241}]; await p.runTool()
  assert.equal(p.calls.filter(call=>call.method==='POST').length,0); assert.match(p.toolError.value,/时长/)
  p.unmount()
})

const trainingSchema = {
  type: 'object', required: ['exercises'], properties: { exercises: { type: 'array', description: '训练动作', minItems: 1, maxItems: 20, items: { type: 'object', additionalProperties: false, required: ['name','mode'], properties: { name: {type:'string',maxLength:100},mode:{type:'string',enum:['STRENGTH','DURATION']},sets:{type:'integer',minimum:1,maximum:20},reps:{type:'integer',minimum:1,maximum:200},weightKg:{type:'number',minimum:0,maximum:500},minutes:{type:'integer',minimum:1,maximum:240},note:{type:'string',maxLength:300} } } } }
}
async function trainingForm() {
  const draft = {name:'training_draft',label:'训练草稿',write:true,parameters:trainingSchema}
  const p = runtime({get:url=>url.endsWith('/profile')?{role:'COACH',roleName:'教练',greeting:'你好',suggestions:[],tools:[draft],model:{configured:false,toolCalling:false,state:'unknown'}}:undefined})
  await p.loadProfile(); p.selectedTool.value='training_draft'; return p
}
test('training draft outbound nested exercises omit nullable, blank and mode-inapplicable fields', async () => {
  const p=await trainingForm()
  const original=[
    {name:' 深蹲 ',mode:'STRENGTH',sets:3,reps:12,weightKg:null,minutes:null,note:''},
    {name:'自重蹲',mode:'STRENGTH',sets:2,reps:10,weightKg:0,minutes:30,note:' 放慢速度 '},
    {name:'步行',mode:'DURATION',minutes:20,sets:3,reps:12,weightKg:null,note:'   '}
  ]
  p.toolForm.exercises=structuredClone(original); await p.runTool()
  const request=p.calls.find(call=>call.method==='POST')
  assert.equal(request.url,'/v1/agent/actions/preview')
  assert.deepEqual(request.body,{tool:'training_draft',arguments:{exercises:[
    {name:'深蹲',mode:'STRENGTH',sets:3,reps:12},
    {name:'自重蹲',mode:'STRENGTH',sets:2,reps:10,weightKg:0,note:'放慢速度'},
    {name:'步行',mode:'DURATION',minutes:20}
  ]}})
  assert.deepEqual(JSON.parse(JSON.stringify(p.toolForm.exercises)),original,'serializing does not mutate editable form state')
  p.unmount()
})
test('training nested schema rejects overlong notes, excessive exercises and missing mode requirements before transport', async () => {
  const p=await trainingForm(), valid={name:'深蹲',mode:'STRENGTH',sets:3,reps:12,weightKg:null,minutes:null,note:''}
  for(const exercises of [[{...valid,note:'字'.repeat(301)}],Array.from({length:21},()=>({...valid})),[{...valid,sets:null}],[{...valid,reps:null}],[{...valid,mode:'DURATION',minutes:null}],[{...valid,name:'字'.repeat(101)}]]) {
    p.toolForm.exercises=exercises;await p.runTool();assert.equal(p.calls.filter(call=>call.method==='POST').length,0)
    assert.ok(p.toolError.value)
  }
  p.unmount()
})
test('training action editor does not add a 21st exercise', () => {
  const changes=[],props={modelValue:Array.from({length:20},()=>({name:'深蹲',mode:'STRENGTH',sets:3,reps:12,weightKg:null,minutes:null,note:''}))}
  const editor=execute('components/AgentExercises.vue',{__props:props,__emit:(event,value)=>changes.push({event,value})},['add'])
  editor.add();assert.deepEqual(changes,[])
  props.modelValue.pop();editor.add();assert.equal(changes[0].value.length,20)
})

test('mobile keyboard viewport shrink keeps the assistant within the visual viewport', () => {
  const p=runtime(); window.visualViewport={height:430,offsetTop:12}; p.syncViewport()
  assert.equal(p.viewportHeight.value,430); assert.equal(p.viewportTop.value,12); p.unmount()
})

test('agent status distinguishes configured unknown, real provider response and degraded service', () => {
  const p=runtime()
  p.profile.value={tools:[],model:{configured:true,toolCalling:false,state:'unknown',model:'test'}}
  assert.match(p.serviceText.value,/待验证/);assert.doesNotMatch(p.serviceText.value,/在线|已验证/)
  p.profile.value.model.state='available';assert.match(p.serviceText.value,/已响应/)
  p.profile.value.model.toolCalling=true;assert.match(p.serviceText.value,/工具已验证/)
  p.profile.value.model.state='degraded';assert.match(p.serviceText.value,/异常.*本地/)
  p.unmount()
})

test('a completed chat refreshes provider status so the first real tool call does not stay unverified', async () => {
  let reads = 0
  const p = runtime({
    get: url => url.endsWith('/profile') ? { tools, suggestions: [], model: { configured: true, model: 'test', toolCalling: reads++ > 0, state: reads > 1 ? 'available' : 'unknown' } } : [],
    post: () => ({ answer: '已查询', suggestions: [], results: [], actions: [], mode: 'MODEL' })
  })
  await p.loadProfile(); assert.match(p.serviceText.value, /待验证/)
  p.input.value = '查询课程'; await p.send()
  assert.match(p.serviceText.value, /工具已验证/)
  p.unmount()
})

test('six role mobile menus retain every allowed function in primary or more', async () => {
  const { mobileNavigation } = await import(new URL('src/utils/mobileNavigation.mjs', root))
  const menus = ['/dashboard','/analytics','/venues','/members','/bookings','/cash-shifts','/coach-workbench','/training-plans','/schedule-week','/advisor-workbench','/leads','/retention','/member-home'].map(path => ({ path, label: path, permission: path }))
  for (const role of Object.keys(access.ROLE_HOME)) {
    const nav = mobileNavigation(menus, role, menus.map(m => m.path)), all = [...nav.primary, ...nav.more]
    assert.equal(nav.primary[0].path, access.ROLE_HOME[role]); assert.equal(new Set(all.map(m => m.path)).size, menus.length); assert.ok(nav.primary.length <= 3)
  }
})

test('accepting installation does not pretend the current browser tab is a standalone window', async () => {
  globalThis.window = { isSecureContext: true, matchMedia: () => ({ matches: false }) }
  const p = execute('components/PwaInstall.vue', { vue: { ...vue, onMounted() {}, onUnmounted() {} } }, ['install', 'installEvent', 'standalone', 'installText'])
  p.installEvent.value = { prompt: async () => {}, userChoice: Promise.resolve({ outcome: 'accepted' }) }; await p.install()
  assert.equal(p.standalone.value, false); assert.match(p.installText.value, /已添加|已安装/)
})

test('public service worker never caches private html, API, token requests or arbitrary resources', async () => {
  const file = new URL('public/sw.js', root); assert.ok(existsSync(file), 'service worker must exist')
  const events = {}, cached = [], puts = []
  const cache = { addAll: async urls => cached.push(...urls), match: async () => ({ offline: true }), put: async (...a) => puts.push(a) }
  const self = { location: { origin: 'https://fitness.example' }, addEventListener: (type, fn) => { events[type] = fn }, skipWaiting: async () => {}, clients: { claim: async () => {} } }
  runInNewContext(readFileSync(file, 'utf8'), { self, URL, caches: { open: async () => cache, keys: async () => [], delete: async () => {}, match: async () => ({ offline: true }) }, fetch: async () => { throw Error('offline') } })
  const tasks = []; events.install({ waitUntil: p => tasks.push(p) }); await Promise.all(tasks)
  assert.ok(cached.includes('/offline.html')); assert.equal(cached.some(p => p === '/' || p === '/login' || p.includes('/api')), false)
  for (const path of ['/api/v1/agent/profile', '/assets/private.json', '/login', '/member-home']) {
    let response; events.fetch({ request: { url: 'https://fitness.example' + path, method: 'GET', mode: path.includes('api') ? 'cors' : 'navigate', headers: { has: () => false } }, respondWith: p => { response = p } })
    if (response) await response
  }
  assert.deepEqual(puts, [])
})
