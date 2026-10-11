import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { transformSync } from 'esbuild'
import * as vue from 'vue'
import * as financeUi from '../src/utils/financeUi.mjs'
import * as accessControl from '../src/utils/accessControl.mjs'

// Execute actual API and SFC setup; only HTTP, browser and lifecycle boundaries are substituted.
function execute(path, deps, names = []) {
  const url = new URL('../src/' + path, import.meta.url)
  assert.ok(existsSync(url), `${path} must exist for the approved retention flow`)
  let source = readFileSync(url, 'utf8')
  if (path.endsWith('.vue')) source = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
  const definedNames = names.filter(name => new RegExp(`\\b(?:const|let|function)\\s+${name}\\b|[,;]\\s*${name}\\s*=`).test(source))
  source += definedNames.length ? `\nexport { ${definedNames.join(',')} }` : ''
  const code = transformSync(source, { loader: 'ts', format: 'cjs' }).code
  const module = { exports: {} }
  new Function('require', 'module', 'exports', code)(name => {
    if (name.startsWith('@/components/')) return {}
    if (!(name in deps)) throw new Error('Uncontrolled import: ' + name)
    return deps[name]
  }, module, module.exports)
  return module.exports
}
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
const member = id => ({ memberId: id, memberNo: `M${id}`, realName: `会员${id}`, phone: '13800000000', status: 'ACTIVE', venueId: 1, venueName: '一号店', salesUserId: 10, advisorName: '顾问甲', lastFollowAt: null, lastFollowContent: null, lastOutcome: null, nextFollowAt: null, followStatus: 'UNSCHEDULED', riskScore: 80, riskLevel: 'HIGH', riskReason: '60天未到店', expiresAt: null, expiryDays: null, renewedThisMonth: false, lastRenewedAt: null, renewalNetAmount: 0 })
const overview = (id = 1) => ({ asOf: '2026-10-03T10:00:00', summary: { totalMembers: 3, todayFollowUps: 1, overdueFollowUps: 1, expiring7: 1, expiring30: 2, highRisk: 1, followedThisMonth: 2, renewedThisMonth: 1, renewalNetAmount: 200 }, members: [member(id)], advisors: [{ advisorId: 10, advisorName: '顾问甲', memberCount: 3, todayFollowUps: 1, overdueFollowUps: 1, followedThisMonth: 2, renewedThisMonth: 1 }] })
const follow = id => ({ id, memberId: id, memberName: `会员${id}`, staffUserId: 10, staffName: '顾问甲', followType: 'CALL', content: `内容${id}`, nextFollowAt: null, outcome: 'RENEWAL_PENDING', createdAt: '2026-10-03T10:00:00' })
function runtime(path, overrides = {}, role = 'MANAGER') {
  const calls = [], messages = [], unmounted = []
  const auth = { sessionGeneration: 1, userInfo: { role, realName: '顾问甲', permissions: ['MEMBER_READ'] } }
  const transport = {
    get: async (url, config) => {
      calls.push({ method: 'GET', url, params: config?.params })
      if (url.endsWith('/retention')) return { data: await (overrides.retention?.(config?.params) ?? overview()) }
      if (url.endsWith('/workbench')) return { data: { advisorName: '顾问甲', summary: { myMembers: 1, newMembersThisMonth: 0, followUpsThisWeek: 0, dueFollowUps: 0, salesOrders: 0, salesAmount: 0 }, members: [member(1)] } }
      return { data: await (overrides.history?.(Number(url.split('/')[4])) ?? [follow(Number(url.split('/')[4]))]) }
    },
    post: async (url, body) => {
      calls.push({ method: 'POST', url, body })
      return { data: await (overrides.save?.(url, body) ?? follow(1)) }
    }
  }
  const deps = {
    vue: { ...vue, onMounted() {}, onUnmounted: fn => unmounted.push(fn) },
    'element-plus': { ElMessage: { success: m => messages.push(m), warning: m => messages.push(m), error: m => messages.push(m) } },
    '@element-plus/icons-vue': {}, '@/utils/financeUi.mjs': financeUi,
    '@/store/modules/auth': { useAuthStore: () => auth },
    '@/utils/request': { __esModule: true, default: transport },
    'vue-router': { useRouter: () => ({ push() {} }) }
  }
  const api = execute('api/modules/advisor.ts', deps)
  deps['@/api/modules/advisor'] = api
  const names = ['load', 'data', 'loading', 'openFollow', 'submitFollow', 'followForm', 'saving', 'current', 'followDialog', 'openHistory', 'history', 'historyLoading', 'historyError', 'outcomeText']
  if (path.includes('Retention')) names.push('filter', 'keyword', 'loadError', 'showAdvisorOverview', 'expiryDateText')
  return { ...execute(path, deps, names), auth, api, calls, messages, unmount: () => unmounted.forEach(fn => fn()) }
}
const pages = ['views/advisor/AdvisorWorkbench.vue', 'views/admin/RetentionCenter.vue']

for (const page of pages) {
  test(`${page}: pending save ignores duplicate clicks and freezes the captured member/payload`, async () => {
    const waiting = deferred(), r = runtime(page, { save: () => waiting.promise })
    r.openFollow(member(1)); Object.assign(r.followForm, { content: ' 已约到店 ', outcome: 'VISIT_BOOKED', nextFollowAt: undefined })
    const pending = r.submitFollow(); const duplicate = r.submitFollow(); r.openFollow(member(2))
    r.followForm.content = '新的内容'
    const posts = r.calls.filter(c => c.method === 'POST')
    assert.equal(posts.length, 1); assert.equal(posts[0].url, '/v1/admin/members/1/follow-ups')
    assert.equal(posts[0].body.content, '已约到店'); assert.equal(posts[0].body.outcome, 'VISIT_BOOKED')
    assert.ok(Object.isFrozen(posts[0].body)); assert.equal(r.current.value.memberId, 1)
    assert.equal('nextFollowAt' in posts[0].body, false)
    waiting.resolve(follow(1)); await Promise.all([pending, duplicate]); assert.equal(r.saving.value, false)
  })
  test(`${page}: identical failed retries reuse a key; edited, reopened and successful intents use new keys`, async () => {
    let failing = true
    const r = runtime(page, { save: () => { if (failing) throw Error('网络超时'); return follow(1) } })
    r.openFollow(member(1)); Object.assign(r.followForm, { content: '考虑续费', outcome: 'RENEWAL_PENDING' })
    await r.submitFollow().catch(() => {}); await r.submitFollow().catch(() => {})
    r.followForm.content = '考虑半年卡'; await r.submitFollow().catch(() => {})
    r.openFollow(member(1)); r.followForm.content = '考虑半年卡'; await r.submitFollow().catch(() => {})
    failing = false; await r.submitFollow(); r.openFollow(member(1)); r.followForm.content = '考虑半年卡'; await r.submitFollow()
    const posts = r.calls.filter(c => c.method === 'POST')
    assert.ok(posts[0].body.idempotencyKey); assert.equal(posts[0].body.idempotencyKey, posts[1].body.idempotencyKey)
    assert.notEqual(posts[1].body.idempotencyKey, posts[2].body.idempotencyKey)
    assert.notEqual(posts[2].body.idempotencyKey, posts[3].body.idempotencyKey)
    assert.equal(posts[3].body.idempotencyKey, posts[4].body.idempotencyKey)
    assert.notEqual(posts[4].body.idempotencyKey, posts[5].body.idempotencyKey)
    assert.ok(r.messages.some(m => m.includes('重试')))
  })
  test(`${page}: invalid next date and manual renewal outcome never reach the write API`, async () => {
    const r = runtime(page); r.openFollow(member(1)); r.followForm.content = '下次联系'
    r.followForm.nextFollowAt = '2000-01-01T00:00:00'; await r.submitFollow()
    r.followForm.nextFollowAt = 'invalid'; await r.submitFollow()
    r.followForm.nextFollowAt = undefined; r.followForm.outcome = 'RENEWED'; await r.submitFollow()
    assert.equal(r.calls.filter(c => c.method === 'POST').length, 0)
    assert.equal(typeof r.outcomeText, 'function', 'history must explain recorded and legacy outcomes')
    assert.equal(r.outcomeText(undefined), '未记录结果'); assert.equal(r.outcomeText('RENEWAL_PENDING'), '考虑续费')
  })
  test(`${page}: unchanged failed intent replays after its future follow date passes, while edited/new past intents are blocked`, async t => {
    let now = new Date('2035-01-01T09:59:00').getTime()
    t.mock.method(Date, 'now', () => now)
    const r = runtime(page, { save: () => { throw Error('响应丢失') } })
    r.openFollow(member(1)); Object.assign(r.followForm, { content: '已约十点回访', outcome: 'VISIT_BOOKED', nextFollowAt: '2035-01-01T10:00:00' })
    await r.submitFollow(); now = new Date('2035-01-01T10:01:00').getTime(); await r.submitFollow()
    let posts = r.calls.filter(c => c.method === 'POST')
    assert.equal(posts.length, 2, 'original request must reach server replay even after its date passes')
    assert.equal(posts[0].body.idempotencyKey, posts[1].body.idempotencyKey)
    assert.equal(posts[1].body.nextFollowAt, '2035-01-01T10:00:00')
    r.followForm.content = '改约回访'; await r.submitFollow()
    assert.equal(r.calls.filter(c => c.method === 'POST').length, 2, 'edited past date is a new invalid intent')
    r.followForm.nextFollowAt = '2035-01-01T11:00:00'; await r.submitFollow()
    posts = r.calls.filter(c => c.method === 'POST')
    assert.equal(posts.length, 3); assert.notEqual(posts[2].body.idempotencyKey, posts[1].body.idempotencyKey)
    r.openFollow(member(1)); Object.assign(r.followForm, { content: '新回访', nextFollowAt: '2035-01-01T10:00:00' }); await r.submitFollow()
    assert.equal(r.calls.filter(c => c.method === 'POST').length, 3, 'a new past-date intent must remain blocked')
  })
  test(`${page}: switching histories clears old rows and ignores a late response/failure`, async () => {
    for (const failure of [false, true]) {
      const old = deferred(), selected = deferred(), r = runtime(page, { history: id => id === 1 ? old.promise : selected.promise })
      assert.ok(r.historyLoading, 'pending history must expose a loading state')
      const first = r.openHistory(member(1)), second = r.openHistory(member(2))
      assert.deepEqual(r.history.value, []); assert.equal(r.historyLoading.value, true)
      if (failure) old.reject(Error('旧会员失败')); else old.resolve([follow(1)])
      await first; assert.equal(r.historyLoading.value, true); assert.equal(r.historyError.value, '')
      selected.resolve([follow(2)]); await second
      assert.equal(r.current.value.memberId, 2); assert.equal(r.history.value[0].memberId, 2)
      assert.equal(r.historyLoading.value, false)
    }
  })
  test(`${page}: an old page cannot send a draft after account replacement`, async () => {
    const r = runtime(page); r.openFollow(member(1)); r.followForm.content = '旧账号草稿'
    r.auth.sessionGeneration++; await r.submitFollow(); await r.load(); await r.openHistory(member(2))
    assert.equal(r.calls.length, 0)
  })
  test(`${page}: late successful save after account replacement cannot close/publish/reload`, async () => {
    const waiting = deferred(), r = runtime(page, { save: () => waiting.promise })
    r.openFollow(member(1)); r.followForm.content = '旧账号记录'
    const pending = r.submitFollow(); r.auth.sessionGeneration++; waiting.resolve(follow(1)); await pending
    assert.equal(r.calls.length, 1); assert.equal(r.followDialog.value, true); assert.deepEqual(r.messages, [])
  })
}
test('retention filters are sent to the server with trimmed/omitted search, and obsolete results cannot replace the current selection', async () => {
  const old = deferred(), r = runtime('views/admin/RetentionCenter.vue', { retention: p => p.filter === 'ALL' ? old.promise : overview(2) })
  const first = r.load(); r.filter.value = 'OVERDUE'; r.keyword.value = '  张  '; await r.load()
  old.resolve(overview(1)); await first
  assert.equal(r.data.value.members[0].memberId, 2)
  assert.deepEqual(r.calls[0].params, { filter: 'ALL' }); assert.deepEqual(r.calls[1].params, { filter: 'OVERDUE', keyword: '张' })
  assert.equal(r.loading.value, false)
})
test('retention list errors preserve an explicit unavailable state, and an unmounted page rejects late data', async () => {
  const r = runtime('views/admin/RetentionCenter.vue', { retention: () => { throw Error('无权访问') } })
  await r.load(); assert.equal(r.loadError.value, '无权访问'); assert.equal(r.loading.value, false)
  const late = deferred(), p = runtime('views/admin/RetentionCenter.vue', { retention: () => late.promise })
  const pending = p.load(); p.unmount(); late.resolve(overview(9)); await pending
  assert.equal(p.data.value.members.length, 0)
})
test('staff overview is available to managers/headquarters and hidden from sales', () => {
  for (const role of ['SALES', 'MANAGER', 'SUPER_ADMIN']) {
    const r = runtime('views/admin/RetentionCenter.vue', {}, role)
    assert.equal(r.showAdvisorOverview.value, role !== 'SALES')
  }
})
test('date-only membership expiry preserves its calendar day across time zones without inventing a clock time', t => {
  const originalTimezone = process.env.TZ
  t.after(() => { if (originalTimezone === undefined) delete process.env.TZ; else process.env.TZ = originalTimezone })
  const r = runtime('views/admin/RetentionCenter.vue')
  assert.equal(typeof r.expiryDateText, 'function', 'expiry needs a date-only presentation boundary')
  for (const timezone of ['Asia/Shanghai', 'America/Los_Angeles']) {
    process.env.TZ = timezone
    assert.equal(r.expiryDateText('2026-10-10'), '2026/10/10')
  }
  assert.equal(r.expiryDateText(null), '—')
})
test('retention route and menu both reject non-owner staff roles even when MEMBER_READ is present', () => {
  let routerOptions
  const deps = { 'vue-router': { createRouter: options => { routerOptions = options; return { beforeEach() {} } }, createWebHistory: () => ({}) }, '@/utils/storage': { getToken: () => '', getUserInfo: () => ({}) }, '@/utils/auth': accessControl, '@/utils/accessControl.mjs': accessControl }
  execute('router/index.ts', deps)
  const route = routerOptions.routes.find(r => r.path === '/').children.find(r => r.path === 'retention')
  assert.ok(route, 'retention route must be registered')
  globalThis.window = { innerWidth: 1200, matchMedia: () => ({ matches: false }) }
  globalThis.document = { hidden: false }
  globalThis.localStorage = { getItem: () => null }
  const layout = execute('layouts/AdminLayout.vue', { ...deps, vue: { ...vue, provide() {}, onMounted() {}, onUnmounted() {}, watchEffect() {} }, 'vue-router': { useRoute: () => ({ path: '/retention' }) }, 'element-plus': { ElMessage: {} }, '@element-plus/icons-vue': {}, '@/store/modules/auth': { useAuthStore: () => ({ userInfo: { role: 'SALES', permissions: ['MEMBER_READ'] } }) }, '@/utils/workspaceAppearance.mjs': { normalizeMotionPreference: () => 'standard', resolveMotionState: () => 'full' }, '@/utils/workspaceMotion': { workspaceMotionKey: Symbol() } }, ['menuItems'])
  const menu = layout.menuItems.find(m => m.path === '/retention'); assert.ok(menu)
  for (const role of ['SALES', 'MANAGER', 'SUPER_ADMIN', 'COACH', 'RECEPTION', 'MEMBER']) {
    const permitted = ['SALES', 'MANAGER', 'SUPER_ADMIN'].includes(role)
    assert.equal(accessControl.canAccessRoute(route.meta, role, ['MEMBER_READ']), permitted, role)
    assert.equal(accessControl.isMenuVisible(menu, role, ['MEMBER_READ']), permitted, role)
  }
})
