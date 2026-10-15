import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { transformSync } from 'esbuild'
import axios from 'axios'
import * as vue from 'vue'
import { createPinia, setActivePinia, defineStore } from 'pinia'
import * as financeUi from '../src/utils/financeUi.mjs'

// Compile and execute production TS/SFC setup; substitute only transport, storage and lifecycle boundaries.
function execute(path, deps, names = []) {
  let source = readFileSync(new URL('../src/' + path, import.meta.url), 'utf8')
  if (path.endsWith('.vue')) source = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
  source += names.length ? `\nexport { ${names.join(',')} }` : ''
  const code = transformSync(source, { loader: 'ts', format: 'cjs', define: { 'import.meta.env': '{}' } }).code
  const module = { exports: {} }
  new Function('require', 'module', 'exports', 'defineProps', code)(name => {
    if (!(name in deps)) throw new Error('Uncontrolled import: ' + name)
    return deps[name]
  }, module, module.exports, () => ({}))
  return module.exports
}
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
const flush = async () => { for (let i = 0; i < 12; i++) await Promise.resolve() }
const session = id => ({ accessToken: id, refreshToken: 'refresh-' + id, userInfo: { id, role: 'MEMBER', permissions: [] } })
const ok = (config, data = {}) => ({ config, data: { code: 0, data }, status: 200, statusText: 'OK', headers: {} })
const unauthorized = config => Promise.reject({ config, response: { status: 401, data: {} } })
function browser() {
  const intervals = new Map(), timeouts = [], navigations = []
  let next = 0
  globalThis.window = { location: { pathname: '/member', search: '', hash: '', replace: url => navigations.push(url) }, setTimeout: fn => timeouts.push(fn), setInterval: fn => { intervals.set(++next, fn); return next }, clearInterval: id => intervals.delete(id) }
  globalThis.document = { hidden: false }
  return { intervals, timeouts, navigations }
}
function authRuntime(adapter = config => ok(config), refresh = async () => ok({}, session('A2'))) {
  const b = browser(), routes = [], notices = [], values = new Map()
  const storage = { getToken: () => values.get('token') || null, getUserInfo: () => values.get('user') || null, getRefreshToken: () => values.get('refresh') || null, setToken: v => values.set('token', v), setRefreshToken: v => values.set('refresh', v), setUserInfo: v => values.set('user', v), removeToken: () => values.clear() }
  let api
  setActivePinia(createPinia())
  const deps = { vue, pinia: { defineStore }, 'vue-router': { useRouter: () => ({ push: url => routes.push(url), replace: url => routes.push(url) }) }, '@/utils/storage': storage, '@/api/modules/auth': { login: (...args) => api.login(...args), logout: (...args) => api.logout(...args) } }
  return { b, routes, notices, values, storage, deps, init(defineStore) {
    deps.pinia = { defineStore }
    const { useAuthStore } = execute('store/modules/auth.ts', deps)
    const auth = useAuthStore(); auth.applySession(session('A'))
    const calls = []
    const client = axios.create({ adapter: async config => { calls.push(config); return adapter(config) } })
    Object.assign(deps, { axios: { ...axios, create: () => client, post: refresh }, 'element-plus': { ElMessage: { error: m => notices.push(m), warning: m => notices.push(m), success: m => notices.push(m), closeAll() {} } }, '@/store/modules/auth': { useAuthStore: () => auth }, '@/utils/financeUi.mjs': financeUi })
    const request = execute('utils/request.ts', deps).default
    // esbuild interop handles ESM-style default imports via the __esModule marker.
    deps['@/utils/request'] = { __esModule: true, default: request }
    api = execute('api/modules/auth.ts', deps)
    return { ...this, auth, request, calls, api }
  } }
}
const runtime = (...args) => authRuntime(...args).init(defineStore)

test('opt-in request scope rejects obsolete business success/errors silently', async () => {
  for (const mode of ['success', 'business', 'http', 'network']) {
    const wait = deferred(); let valid = true
    const r = runtime(async config => { await wait.promise; if (mode === 'http') throw { config, response: { status: 400, data: { code: 1001, message: '旧参数错误' } } }; if (mode === 'network') throw { config }; const response = ok(config, { financial: 123 }); if (mode === 'business') response.data = { code: 2600, message: '旧资金冲突' }; return response })
    const pending = r.request.get('/v1/cash-shifts/1', { isRequestCurrent: () => valid }).then(() => 'published', () => 'discarded')
    await flush(); valid = false; wait.resolve(); assert.equal(await pending, 'discarded'); assert.deepEqual(r.notices, [])
  }
})
test('opt-in request scope checks before transport and leaves ordinary requests compatible', async () => {
  const r = runtime(); await r.request.get('/v1/cash-shifts/1', { isRequestCurrent: () => false }).catch(() => {}); assert.equal(r.calls.length, 0)
  await r.request.get('/ordinary'); assert.equal(r.calls.length, 1)
})
test('opt-in scope rechecked after delayed success and error blob parsing', async () => {
  for (const error of [false,true]) {
    let valid = true; const text = deferred(), blob = new Blob(['{}'], { type: 'application/json' }); blob.text = () => text.promise
    const r = runtime(config => { if (error) throw { config, response: { status: 400, data: blob } }; return { ...ok(config), data: blob, headers: { 'content-type': 'application/json' } } })
    const pending = r.request.get('/blob', { responseType: 'blob', isRequestCurrent: () => valid }).catch(() => 'discarded'); await flush(); valid = false; text.resolve('{"code":1001,"message":"旧资金错误"}'); assert.equal(await pending, 'discarded'); assert.deepEqual(r.notices, [])
  }
})
test('opt-in scope never refreshes stale401 or replays after scope changes during refresh', async () => {
  let valid = true, refreshes = 0; const wait = deferred(), r = runtime(config => wait.promise.then(() => unauthorized(config)), async () => { refreshes++; return ok({}, session('A2')) })
  const old = r.request.get('/private', { isRequestCurrent: () => valid }).catch(() => {}); await flush(); valid = false; wait.resolve(); await old; assert.equal(refreshes, 0); assert.deepEqual(r.notices, [])
  valid = true; const refresh = deferred(), next = runtime(unauthorized, () => refresh.promise), pending = next.request.get('/private', { isRequestCurrent: () => valid }).catch(() => {}); await flush(); valid = false; refresh.resolve(ok({}, session('A2'))); await pending; assert.equal(next.calls.length, 1); assert.deepEqual(next.notices, [])
})
test('cancelled transport errors do not produce network messages', async () => { const r = runtime(config => { throw new axios.CanceledError('canceled', config) }); await r.request.get('/cancel').catch(() => {}); assert.deepEqual(r.notices, []) })

test('CORS rejection during login is not mislabeled as missing account permission', async () => {
  const r=runtime(config=>{throw {config,response:{status:403,data:'Invalid CORS request'}}})
  await r.request.post('/v1/auth/login',{}).catch(()=>{})
  assert.equal(r.notices.length,1);assert.match(r.notices[0],/来源/);assert.doesNotMatch(r.notices[0],/账号.*权限/)
  assert.equal(r.auth.token,'A');assert.deepEqual(r.routes,[])
})

test('offline transport refuses all business mutations without sending or queuing them', async () => {
  const descriptor = Object.getOwnPropertyDescriptor(globalThis, 'navigator')
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { onLine: false } })
  try {
    const r = runtime()
    for (const method of ['post', 'put', 'patch', 'delete']) await r.request[method]('/v1/business', {}).catch(() => {})
    assert.equal(r.calls.length, 0)
    assert.ok(r.notices.some(text => /离线/.test(text)))
  } finally { if (descriptor) Object.defineProperty(globalThis, 'navigator', descriptor); else delete globalThis.navigator }
})

test('delayed refresh success cannot restore a logged-out session or replace a new account', async () => {
  for (const next of [null, 'B']) {
    let attempts = 0
    const refresh = deferred(), r = runtime(config => attempts++ === 0 ? unauthorized(config) : ok(config), () => refresh.promise)
    const pending = r.request.get('/private').catch(e => e)
    await flush(); r.auth.clearSession(); if (next) r.auth.applySession(session(next))
    refresh.resolve(ok({}, session('A2'))); await pending
    assert.equal(r.auth.token, next)
    assert.equal(r.calls.length, 1)
  }
})
test('delayed refresh failure cannot clear a new account or redirect it', async () => {
  const refresh = deferred(), r = runtime(unauthorized, () => refresh.promise)
  const pending = r.request.get('/private').catch(e => e)
  await flush(); r.auth.clearSession(); r.auth.applySession(session('B'))
  refresh.reject(new Error('expired')); await pending
  r.b.timeouts.forEach(fn => fn())
  assert.equal(r.auth.token, 'B'); assert.deepEqual(r.b.navigations, [])
})
test('an old request 401 never borrows the new account refresh token', async () => {
  const response = deferred(); let refreshes = 0
  const r = runtime(config => response.promise.then(() => unauthorized(config)), async () => { refreshes++; return ok({}, session('B2')) })
  const pending = r.request.get('/private').catch(e => e)
  await flush(); r.auth.clearSession(); r.auth.applySession(session('B')); response.resolve(); await pending
  assert.equal(refreshes, 0); assert.equal(r.auth.token, 'B'); assert.equal(r.calls.length, 1)
})
test('expiry redirect scheduled before a new login cannot interrupt that login', async t => {
  t.mock.method(console, 'warn', () => {})
  const r = runtime(unauthorized, async () => { throw new Error('expired') })
  await r.request.get('/private').catch(() => {})
  r.auth.applySession(session('B')); r.b.timeouts.forEach(fn => fn())
  assert.deepEqual(r.b.navigations, [])
})
test('logout carries the captured token and late completion cannot navigate a new account', async () => {
  const response = deferred(), r = runtime(config => response.promise.then(() => ok(config)))
  const pending = r.auth.logout(); await flush()
  r.auth.applySession(session('B')); response.resolve(); await pending
  assert.equal(r.calls[0].headers.Authorization, 'Bearer A')
  assert.deepEqual(JSON.parse(r.calls[0].data), { refreshToken: 'refresh-A' })
  assert.equal(r.auth.token, 'B'); assert.deepEqual(r.routes, [])
})
test('logout 401 is never refreshed or replayed', async t => {
  t.mock.method(console, 'error', () => {})
  let refreshes = 0
  const r = runtime(unauthorized, async () => { refreshes++; return ok({}, session('B2')) })
  await r.auth.logout()
  assert.equal(refreshes, 0); assert.equal(r.calls.length, 1); assert.equal(r.auth.token, null)
})
function componentRuntime(path, api, names, extra = {}) {
  const b = browser(), mounted = [], unmounted = []
  const auth = { sessionGeneration: 0, userInfo: { permissions: [] } }
  const deps = { vue: { ...vue, onMounted: fn => mounted.push(fn), onBeforeUnmount: fn => unmounted.push(fn), onUnmounted: fn => unmounted.push(fn) }, 'element-plus': { ElMessage: { error() {}, success() {} } }, '@element-plus/icons-vue': {}, '@/components/AgentData.vue': {}, '@/components/AgentExercises.vue': {}, '@/api/modules/chat': api, '@/api/modules/assistant': api, '@/store/modules/auth': { useAuthStore: () => auth }, ...extra }
  return { ...execute(path, deps, names), auth, b, mounted, unmount: () => unmounted.forEach(fn => fn()) }
}
const threadData = id => ({ data: { userId: id, name: String(id), messages: [] } })
const chatApi = overrides => ({ getChatThread: async id => threadData(id), getChatConversations: async () => ({ data: [] }), getChatContacts: async () => ({ data: { staff: [], members: [] } }), sendChatMessage: async () => ({}), ...overrides })
const chatPanel = api => componentRuntime('components/ChatPanel.vue', chatApi(api), ['openThread', 'thread', 'activeUserId', 'draft', 'send', 'sending', 'reload', 'loading', 'contacts', 'scrollBox'])
test('out-of-order chat openings display the selected contact', async () => {
  const a = deferred(), p = chatPanel({ getChatThread: id => id === 1 ? a.promise : Promise.resolve(threadData(id)) })
  const pending = p.openThread(1); await p.openThread(2); a.resolve(threadData(1)); await pending
  assert.equal(p.activeUserId.value, 2); assert.equal(p.thread.value.userId, 2)
})
test('switching contact never sends the previous contact draft', async () => {
  const sent = [], p = chatPanel({ sendChatMessage: async (...args) => sent.push(args) })
  await p.openThread(1); p.draft.value = 'private draft for A'; await p.openThread(2); await p.send()
  assert.deepEqual(sent, [])
})
test('late send completion cannot clear or reload the newly selected contact', async () => {
  const response = deferred(), loaded = [], p = chatPanel({ sendChatMessage: () => response.promise, getChatThread: async id => { loaded.push(id); return threadData(id) } })
  await p.openThread(1); p.draft.value = 'A'; const pending = p.send(); await p.openThread(2); p.draft.value = 'B'
  response.resolve(); await pending
  assert.equal(p.draft.value, 'B'); assert.deepEqual(loaded, [1, 2]); assert.equal(p.thread.value.userId, 2)
})
test('chat initial load finishing after unmount cannot create polling', async () => {
  const response = deferred(), p = chatPanel({ getChatContacts: () => response.promise })
  const pending = p.mounted[0](); p.unmount(); response.resolve({ data: { staff: [], members: [] } }); await pending
  assert.equal(p.b.intervals.size, 0)
})
test('chat poll response finishing after a contact switch cannot overwrite its thread', async () => {
  const response = deferred(); let poll = false
  const p = chatPanel({ getChatThread: id => poll && id === 1 ? response.promise : Promise.resolve(threadData(id)) })
  await p.mounted[0](); await p.openThread(1); poll = true
  const pending = [...p.b.intervals.values()][0](); await flush(); await p.openThread(2)
  response.resolve({ data: { ...threadData(1).data, messages: [{ id: 1 }] } }); await pending
  assert.equal(p.thread.value.userId, 2)
})
test('assistant history keeps six turns with at most 500 UTF-16 characters per turn', async () => {
  const calls = [], p = componentRuntime('components/AssistantPanel.vue', { chat: async (text, history) => { calls.push({ text, history }); return { data: { answer: '答'.repeat(700), suggestions: [], source: 'test' } } } }, ['input', 'send', 'messages'])
  for (let i = 0; i < 5; i++) { p.input.value = '问'.repeat(501); await p.send() }
  assert.equal(calls.at(-1).history.length, 6)
  assert.ok(calls.at(-1).history.every(turn => turn.content.length <= 500))
  assert.equal(p.messages.value[1].text.length, 501)
})
function memberPanel(qr) {
  const empty = async () => ({ data: [] }), code = async () => ({ data: { qrPayload: 'qr', ttlSeconds: 1 } })
  return componentRuntime('views/member/MemberHome.vue', {}, ['load', 'refreshAccessCode', 'qrImage'], {
    '@/components/WorkspaceHero.vue': {}, '@/components/ChatPanel.vue': {}, '@/components/TrainingPanel.vue': {}, '@/components/BodyMeasurePanel.vue': {},
    qrcode: { __esModule: true, default: { toDataURL: qr } },
    '@/api/modules/memberPortal': { getMemberProfile: empty, listMemberSchedules: empty, listMemberBookings: empty }, '@/api/modules/commercial': { getMyEntitlements: empty, getMyAccessCode: code },
    '@/api/modules/finance': {}, '@/api/modules/notification': {}, '@/api/modules/coupon': {}, '@/api/modules/review': {}, '@/utils/financeUi.mjs': financeUi
  })
}
for (const method of ['load', 'refreshAccessCode']) test(`member ${method}: QR completion after unmount cannot publish or start auto-renewal`, async () => {
  const qr = deferred(), p = memberPanel(() => qr.promise), pending = p[method]()
  await flush(); p.unmount(); qr.resolve('old qr'); await pending
  assert.equal(p.b.intervals.size, 0); assert.equal(p.qrImage.value, '')
})
test('password change applies the new server session before entering the workbench', async () => {
  const r = runtime(config => ok(config, session('A-new')))
  const p = execute('views/ChangePassword.vue', { ...r.deps, '@/api/modules/auth': r.api, '@/utils/auth': { homeForRole: () => '/member' } }, ['submit', 'formRef'])
  p.formRef.value = { validate: async () => true }; await p.submit()
  assert.equal(r.auth.token, 'A-new'); assert.deepEqual(r.routes, ['/member'])
})
test('old mounted chat cannot send a saved draft or poll using a replacement session', async () => {
  const calls = [], p = chatPanel({ getChatConversations: async () => { calls.push('list'); return { data: [] } }, sendChatMessage: async () => calls.push('send') })
  await p.mounted[0](); await p.openThread(1); p.draft.value = 'A draft'; calls.length = 0
  p.auth.sessionGeneration++; await p.send(); await [...p.b.intervals.values()][0]()
  assert.deepEqual(calls, [])
})
test('old mounted member cannot renew an access token using a replacement session', async () => {
  const p = memberPanel(async () => 'qr')
  await p.refreshAccessCode(); p.auth.sessionGeneration++
  await [...p.b.intervals.values()][0](); await flush()
  assert.equal(p.b.intervals.size, 0)
})
test('assistant response after account replacement does not publish private content', async () => {
  const response = deferred(), p = componentRuntime('components/AssistantPanel.vue', { chat: () => response.promise }, ['input', 'send', 'messages'])
  p.input.value = 'A question'; const pending = p.send(); p.auth.sessionGeneration++
  response.resolve({ data: { answer: 'private A answer', suggestions: [], source: 'test' } }); await pending
  assert.equal(p.messages.value.some(item => item.text === 'private A answer'), false)
})
test('parallel 401 requests share one refresh and replay under the same account', async () => {
  const refresh = deferred(); let refreshes = 0
  const r = runtime(config => config.headers.Authorization === 'Bearer A' ? unauthorized(config) : ok(config, 'business result'), () => { refreshes++; return refresh.promise })
  const generation = r.auth.sessionGeneration
  const first = r.request.get('/private/1'), second = r.request.get('/private/2')
  await flush(); refresh.resolve(ok({}, session('A2')))
  assert.deepEqual((await Promise.all([first, second])).map(result => result.data), ['business result', 'business result'])
  assert.equal(refreshes, 1); assert.equal(r.auth.token, 'A2'); assert.equal(r.auth.sessionGeneration, generation)
})
test('delayed login success cannot replace a later account or restore a logged-out account', async () => {
  for (const next of [null, 'B']) {
    const response = deferred(), r = runtime(config => response.promise.then(() => ok(config, session('A2'))))
    const pending = r.auth.login('a', 'password').catch(e => e)
    await flush(); r.auth.clearSession(); if (next) r.auth.applySession(session(next))
    response.resolve(); await pending
    assert.equal(r.auth.token, next)
  }
})
test('delayed successful business data is rejected after account replacement', async () => {
  const response = deferred(), r = runtime(config => response.promise.then(() => ok(config, 'private A data')))
  const pending = r.request.get('/private')
  await flush(); r.auth.applySession(session('B')); response.resolve()
  await assert.rejects(pending); assert.equal(r.auth.token, 'B')
})
test('logout token remains captured even if another login happens before request dispatch', async () => {
  const r = runtime(), pending = r.auth.logout()
  r.auth.applySession(session('B')); await pending
  assert.equal(r.calls[0].headers.Authorization, 'Bearer A')
  assert.deepEqual(JSON.parse(r.calls[0].data), { refreshToken: 'refresh-A' }); assert.deepEqual(r.routes, [])
})
test('chat preserves drafts per contact and preserves newly typed text during sending', async () => {
  const response = deferred(), p = chatPanel({ sendChatMessage: () => response.promise })
  await p.openThread(1); p.draft.value = 'A'; await p.openThread(2); p.draft.value = 'B'; await p.openThread(1)
  assert.equal(p.draft.value, 'A')
  const pending = p.send(); p.draft.value = 'next A'; response.resolve(); await pending
  assert.equal(p.draft.value, 'next A')
})
test('only the latest QR generation can publish or start its countdown', async () => {
  const old = deferred(); let attempt = 0
  const p = memberPanel(() => ++attempt === 1 ? old.promise : Promise.resolve('new qr'))
  const first = p.refreshAccessCode(); await flush(); await p.refreshAccessCode(); old.resolve('old qr'); await first
  assert.equal(p.qrImage.value, 'new qr'); assert.equal(p.b.intervals.size, 1)
})
test('a password response arriving after account replacement cannot overwrite or navigate it', async () => {
  const response = deferred(), r = runtime(config => response.promise.then(() => ok(config, session('A-new'))))
  const p = execute('views/ChangePassword.vue', { ...r.deps, '@/api/modules/auth': r.api, '@/utils/auth': { homeForRole: () => '/member' } }, ['submit', 'formRef'])
  p.formRef.value = { validate: async () => true }; const pending = p.submit().catch(e => e)
  await flush(); r.auth.applySession(session('B')); response.resolve(); await pending
  assert.equal(r.auth.token, 'B'); assert.deepEqual(r.routes, [])
})
test('chat opening during contact reload never leaves the loading overlay stuck', async () => {
  const contacts = deferred(), p = chatPanel({ getChatContacts: () => contacts.promise })
  const pending = p.reload(); await p.openThread(1)
  contacts.resolve({ data: { staff: [{ userId: 1 }], members: [] } }); await pending
  assert.equal(p.loading.value, false); assert.equal(p.contacts.value.staff.length, 1)
})
test('chat polling without new messages preserves the reader scroll position', async () => {
  const p = chatPanel({}); await p.mounted[0](); await p.openThread(1)
  p.scrollBox.value = { scrollTop: 7, scrollHeight: 100 }
  await [...p.b.intervals.values()][0]()
  assert.equal(p.scrollBox.value.scrollTop, 7)
})
