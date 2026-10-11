import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, existsSync } from 'node:fs'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import { transformSync } from 'esbuild'
import * as vue from 'vue'
import * as access from '../src/utils/accessControl.mjs'

function execute(path, deps) {
  const url = new URL('../src/' + path, import.meta.url)
  assert.ok(existsSync(url), `${path} must exist`)
  let source = readFileSync(url, 'utf8')
  if (path.endsWith('.vue')) source = compileScript(parse(source).descriptor, { id: 'cash-test' }).content
  const module = { exports: {} }, code = transformSync(source, { loader: 'ts', format: 'cjs' }).code
  new Function('require', 'module', 'exports', code)(name => {
    if (name.startsWith('@/components/')) return {}
    assert.ok(name in deps, `Uncontrolled import ${name}`)
    return deps[name]
  }, module, module.exports)
  return module.exports
}
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
const shift = (status = 'OPEN', extra = {}) => ({ id: 1, shiftNo: 'CS1', venueId: 1, venueName: '一店', businessDate: '2026-10-10', status, version: 2, openedBy: 10, openedName: '前台甲', openedAt: '2026-10-10T08:00:00.123456', submittedBy: status === 'OPEN' ? null : 11, submittedName: null, closedAt: status === 'OPEN' ? null : '2026-10-11T00:01:00', reviewedBy: null, reviewedName: null, reviewedAt: null, reviewReason: null, note: null, differenceReason: null, handoverNote: null, baselinePaymentId: 8, openingPaymentId: 9, cutoffPaymentId: status === 'OPEN' ? null : 12, ledgerFingerprint: 'a'.repeat(64), systemReceipts: 10, systemRefunds: 2, systemNet: 8, actualReceipts: null, actualRefunds: null, actualNet: null, netDifference: null, ...extra })
const channel = (channel, receipts = 0, refunds = 0) => ({ channel, systemReceipts: receipts, systemRefunds: refunds, systemNet: receipts - refunds, actualReceipts: null, actualRefunds: null, actualNet: null, receiptsDifference: null, refundsDifference: null, netDifference: null })
const channels = () => [channel('CASH', 10, 2), channel('WECHAT'), channel('ALIPAY'), channel('CARD'), channel('SELF_SERVICE')]
const detail = (status = 'OPEN', extra = {}) => ({ shift: shift(status, extra), preview: { ledgerFingerprint: 'a'.repeat(64), paymentCount: 4, carriedOverCount: 1, historicalExcludedCount: 8, channels: channels(), systemReceipts: 10, systemRefunds: 2, systemNet: 8 }, channels: channels(), audit: [], payments: [], paymentTotal: 4 })
const workbench = () => ({ businessDate: '2026-10-10', venues: [{ id: 1, name: '一店' }, { id: 2, name: '二店' }], selectedVenueId: 1, currentShift: shift(), summary: { shiftCount: 3, openCount: 1, pendingReviewCount: 1, returnedCount: 1, approvedCount: 1, systemReceipts: 40, systemRefunds: 50, systemNet: -10, actualReceipts: 41, actualRefunds: 50, actualNet: -9, netDifference: 1, approvedNet: -20 }, channels: channels(), shifts: { records: [shift('RETURNED')], total: 30, page: 1, size: 20 } })
function runtime(overrides = {}, role = 'MANAGER', permissions = ['CASH_SHIFT_READ', 'CASH_SHIFT_WRITE', 'CASH_SHIFT_REVIEW']) {
  const calls = [], messages = [], mounted = [], unmounted = []
  const auth = vue.reactive({ token: 'test', sessionGeneration: 1, userInfo: { id: 10, role, organizationId: 1, permissions } })
  const request = {
    get: async (url, config) => { calls.push({ method: 'GET', url, params: config?.params, guard: config?.isRequestCurrent }); return { data: await (url.endsWith('/workbench') ? overrides.list?.(config.params) ?? workbench() : url.endsWith('/payments') ? overrides.payments?.(config.params) ?? { records: [], total: 4, page: config.params.page, size: 100 } : overrides.detail?.(Number(url.split('/').at(-1))) ?? detail()) } },
    post: async (url, body, config) => { calls.push({ method: 'POST', url, body, guard: config?.isRequestCurrent }); return { data: await (overrides.save?.(url, body) ?? detail('SUBMITTED')) } }
  }
  const deps = { vue: { ...vue, onMounted: fn => mounted.push(fn), onUnmounted: fn => unmounted.push(fn) }, 'element-plus': { ElMessage: { warning: m => messages.push(m), error: m => messages.push(m), success: m => messages.push(m) } }, '@/store/modules/auth': { useAuthStore: () => auth }, '@/utils/request': { __esModule: true, default: request } }
  const api = execute('api/modules/cashShifts.ts', deps); deps['@/api/modules/cashShifts'] = api
  const scope = vue.effectScope(), page = scope.run(() => execute('views/admin/CashShiftCenter.vue', deps).default.setup({}, { expose() {} }))
  return { ...page, auth, calls, messages, api, mount: () => Promise.all(mounted.map(fn => fn())), unmount: () => { unmounted.forEach(fn => fn()); scope.stop() } }
}
const posts = r => r.calls.filter(c => c.method === 'POST')
async function start(r, status = 'OPEN', extra = {}) { await r.openAction('submit', shift(status, extra)); for (const c of r.form.channels) { c.actualReceipts = c.channel === 'CASH' ? '10' : '0'; c.actualRefunds = c.channel === 'CASH' ? '2' : '0' } }

test('cash: API and route/menu role intersection register the real page', () => {
  const routes = execute('router/index.ts', { 'vue-router': { createRouter: config => ({ ...config, beforeEach() {} }), createWebHistory: () => ({}) }, '@/utils/storage': { getToken() {}, getUserInfo() {} }, '@/utils/accessControl.mjs': access, '@/utils/auth': { homeForRole: () => '/' } }).default.routes
  const route = routes.find(r => r.path === '/').children.find(r => r.path === 'cash-shifts')
  assert.ok(route, 'cash-shifts route must exist')
  assert.equal(access.canAccessRoute(route.meta, 'RECEPTION', ['CASH_SHIFT_READ']), true)
  assert.equal(access.canAccessRoute(route.meta, 'SALES', ['CASH_SHIFT_READ']), false)
  assert.equal(access.canAccessRoute(route.meta, 'MANAGER', []), false)
  const menu = readFileSync(new URL('../src/layouts/AdminLayout.vue', import.meta.url), 'utf8').match(/\{ path: '\/cash-shifts'[^\n]+/)
  assert.ok(menu, 'cash shift navigation must exist')
})
test('cash: real SFC compiles and submits exact DTO with explicitly entered zeroes and dynamic channels', async () => {
  const r = runtime(); await start(r); await r.submitOperation()
  assert.equal(posts(r).length, 1, 'real SFC must produce one submit request')
  assert.equal(posts(r)[0].url, '/v1/cash-shifts/1/submit'); assert.equal(posts(r)[0].body.expectedVersion, 2); assert.equal(posts(r)[0].body.ledgerFingerprint, 'a'.repeat(64))
  assert.deepEqual(posts(r)[0].body.channels, [{ channel: 'CASH', actualReceipts: 10, actualRefunds: 2 }, ...['WECHAT','ALIPAY','CARD','SELF_SERVICE'].map(channel => ({ channel, actualReceipts: 0, actualRefunds: 0 }))])
  const descriptor = parse(readFileSync(new URL('../src/views/admin/CashShiftCenter.vue', import.meta.url), 'utf8')).descriptor
  assert.deepEqual(compileTemplate({ source: descriptor.template.content, id: 'cash' }).errors, [])
})
test('cash: actuals begin blank and invalid or implicit zero never creates a write', async () => {
  const r = runtime(); await r.openAction('submit', shift()); assert.ok(r.form.channels.every(c => c.actualReceipts === '' && c.actualRefunds === '')); await r.submitOperation(); assert.equal(posts(r).length, 0)
  await start(r); for (const value of ['', '-1', '1.001', '1e2', 'NaN', '10000000000', '  ']) { r.form.channels[0].actualReceipts = value; await r.submitOperation() }; assert.equal(posts(r).length, 0)
})
test('cash: equal receipts/refunds differences still require reason when net cancels', async () => {
  const r = runtime(); await start(r); r.form.channels[0].actualReceipts = '11'; r.form.channels[0].actualRefunds = '3'; assert.equal(r.formTotals.value.netDifference, 0); await r.submitOperation(); assert.equal(posts(r).length, 0)
  r.form.differenceReason = '人工核对差异'; await r.submitOperation(); assert.equal(posts(r).length, 1)
})
test('cash: cross-channel cancelled net still requires difference reason', async () => {
  const r = runtime(); await start(r); r.form.channels[0].actualReceipts = '9'; r.form.channels[1].actualReceipts = '1'; await r.submitOperation(); assert.equal(posts(r).length, 0); assert.equal(r.formTotals.value.netDifference, 0)
})
test('cash: integer cents retain precise difference and negative actual net', async () => {
  const r = runtime(); await start(r); r.form.channels[0].actualReceipts = '0.10'; r.form.channels[0].actualRefunds = '0.30'; r.form.differenceReason = '实际退款较多'; assert.equal(r.formTotals.value.actualNet, -20); assert.equal(r.formTotals.value.netDifference, -820); await r.submitOperation(); assert.equal(posts(r)[0].body.channels[0].actualRefunds, .3)
})
test('cash: duplicate writes freeze target, nested payload and version', async () => {
  const wait = deferred(), r = runtime({ save: () => wait.promise }); await start(r); const a = r.submitOperation(), b = r.submitOperation(); await r.openAction('open'); r.form.channels[0].actualReceipts = '99'
  assert.equal(posts(r).length, 1); assert.ok(Object.isFrozen(posts(r)[0].body)); assert.ok(Object.isFrozen(posts(r)[0].body.channels)); assert.ok(Object.isFrozen(posts(r)[0].body.channels[0])); assert.equal(posts(r)[0].body.channels[0].actualReceipts, 10); wait.resolve(detail('SUBMITTED')); await Promise.all([a,b])
})
test('cash: failed unchanged retry preserves key; edited and reopened action uses new key', async () => {
  const r = runtime({ save: () => { throw Error('连接中断') } }); await start(r); await r.submitOperation(); await r.submitOperation(); r.form.handoverNote = '修改'; await r.submitOperation(); assert.equal(posts(r)[0].body.idempotencyKey, posts(r)[1].body.idempotencyKey); assert.notEqual(posts(r)[1].body.idempotencyKey, posts(r)[2].body.idempotencyKey); await start(r); await r.submitOperation(); assert.notEqual(posts(r)[2].body.idempotencyKey, posts(r)[3].body.idempotencyKey)
})
test('cash: 2600 version or ledger conflict blocks writes until explicit fresh detail reload', async () => {
  let fail = true; const r = runtime({ save: () => { if (fail) throw { code: 2600, message: '流水已变化' }; return detail('SUBMITTED') }, detail: () => detail('OPEN', { version: 5 }) }); await start(r); await r.submitOperation(); assert.equal(r.conflict.value, true); await r.submitOperation(); assert.equal(posts(r).length, 1); fail = false; await r.reloadConflict(); assert.equal(r.form.channels[0].actualReceipts, ''); for (const c of r.form.channels) { c.actualReceipts = c.channel === 'CASH' ? '10' : '0'; c.actualRefunds = c.channel === 'CASH' ? '2' : '0' }; await r.submitOperation(); assert.equal(posts(r)[1].body.expectedVersion, 5)
})
test('cash: HTTP400/code1001 stays editable and is not classified as conflict', async () => {
  const r = runtime({ save: () => { throw { response: { status: 400, data: { code: 1001, message: '参数校验失败' } } } } }); await start(r); await r.submitOperation(); assert.equal(r.conflict.value, false); assert.match(r.operationError.value, /参数/); await r.submitOperation(); assert.equal(posts(r).length, 2)
})
test('cash: reception cannot review and manager cannot review own last submission', async () => {
  for (const [role, submitter] of [['RECEPTION',11], ['MANAGER',10], ['SALES',11]]) { const r = runtime({}, role); assert.equal(r.canReview(shift('SUBMITTED', { submittedBy: submitter })), false); await r.openAction('review', shift('SUBMITTED', { submittedBy: submitter })); await r.submitOperation(); assert.equal(posts(r).length, 0) }
})
test('cash: review requires role plus permission and return reason', async () => {
  const denied = runtime({}, 'MANAGER', ['CASH_SHIFT_READ','CASH_SHIFT_WRITE']); assert.equal(denied.canReview(shift('SUBMITTED')), false)
  const r = runtime({ detail: () => detail('SUBMITTED') }); await r.openAction('review', shift('SUBMITTED')); r.form.decision = 'RETURN'; await r.submitOperation(); assert.equal(posts(r).length, 0); r.form.reason = '重新点验现金'; await r.submitOperation(); assert.equal(posts(r)[0].url, '/v1/cash-shifts/1/review'); assert.equal(posts(r)[0].body.decision, 'RETURN')
})
test('cash: reception submits own open or returned shift only; management may substitute', async () => {
  const r = runtime({}, 'RECEPTION'); assert.equal(r.canSubmit(shift('OPEN', { openedBy: 11 })), false); assert.equal(r.canSubmit(shift('RETURNED', { openedBy: 11, submittedBy: 12 })), false); assert.equal(r.canSubmit(shift('RETURNED', { openedBy: 11, submittedBy: 10 })), true); assert.equal(runtime().canSubmit(shift('OPEN', { openedBy: 11 })), true)
})
test('cash: returned correction uses frozen original ledger and leaves actuals blank', async () => {
  const fixed = detail('RETURNED'); fixed.channels[0].actualReceipts = 11; fixed.preview.ledgerFingerprint = 'f'.repeat(64); const r = runtime({ detail: () => fixed }); await r.openAction('submit', fixed.shift); assert.equal(r.form.channels[0].actualReceipts, ''); for (const c of r.form.channels) { c.actualReceipts = c.channel === 'CASH' ? '10' : '0'; c.actualRefunds = c.channel === 'CASH' ? '2' : '0' }; await r.submitOperation(); assert.equal(posts(r)[0].body.ledgerFingerprint, 'f'.repeat(64)); assert.equal(posts(r)[0].body.expectedVersion, 2)
})
test('cash: complete headquarters venue options persist and selected venue clears all detail/drafts synchronously', async () => {
  const r = runtime({}, 'SUPER_ADMIN'); await r.load(); await start(r); r.venueId.value = 2; assert.equal(r.detailData.value, null); assert.equal(r.operationDialog.value, false); assert.deepEqual(r.form.channels, []); await r.load(); assert.deepEqual(r.venues.value.map(v => v.id), [1,2]); assert.equal(r.calls.filter(c => c.url.endsWith('/workbench')).at(-1).params.venueId, 2)
})
test('cash: summary uses server daily denominator despite status filter and pagination', async () => {
  const r = runtime(); r.status.value = 'APPROVED'; r.page.value = 2; await r.load(); assert.equal(r.data.value.summary.shiftCount, 3); assert.equal(r.data.value.summary.approvedNet, -20); assert.equal(r.data.value.shifts.total, 30); assert.equal(r.calls[0].params.status, 'APPROVED'); assert.equal(r.calls[0].params.page, 2)
})
test('cash: daily shift label includes OPEN without describing all shifts as submitted', async () => { const r = runtime(); await r.load(); assert.equal(r.dailyShiftLabel?.value, '当日共3班') })
test('cash: current open shift is independent of selected historical date', async () => { const r = runtime(); r.businessDate.value = '2026-09-01'; await r.load(); assert.equal(r.calls[0].params.businessDate, '2026-09-01'); assert.equal(r.data.value.currentShift.businessDate, '2026-10-10') })
test('cash: OPEN and closed payments page independently without changing preview or daily totals', async () => { const r = runtime(); await r.load(); await r.openDetail(shift()); await r.loadPayments(2); assert.equal(r.calls.at(-1).url, '/v1/cash-shifts/1/payments'); assert.deepEqual(r.calls.at(-1).params, { page: 2, size: 100 }); assert.equal(r.detailData.value.preview.paymentCount, 4); assert.equal(r.data.value.summary.shiftCount, 3) })
test('cash: Shanghai wall time does not shift with viewer timezone and opening never backdates', async () => { const r = runtime({ list: () => ({ ...workbench(), currentShift: null }) }); assert.equal(r.dateText('2026-10-11T00:01:00.123456'), '2026-10-11 00:01:00'); await r.load(); await r.openAction('open'); r.form.note = '开班'; await r.submitOperation(); assert.equal(posts(r).length, 1); assert.equal(posts(r)[0].url, '/v1/cash-shifts'); assert.equal('businessDate' in posts(r)[0].body, false) })
test('cash: audit safely shows before/after actuals and prior reasons rather than raw JSON', () => { const r = runtime(); const lines = r.auditChanges(JSON.stringify({ before: [{ channel: 'CASH', actualReceipts: 10, actualRefunds: 2 }], after: [{ channel: 'CASH', actualReceipts: 11, actualRefunds: 3 }], previousDifferenceReason: '<img src=x>', previousHandoverNote: '原交班备注' })); assert.match(lines.join(' '), /10.00.*11.00/); assert.match(lines.join(' '), /2.00.*3.00/); assert.match(lines.join(' '), /原交班备注/); assert.match(lines.join(' '), /<img src=x>/) })
for (const [label, mutate] of [['logout', r => { r.auth.userInfo = null }], ['identity', r => { r.auth.userInfo.id = 12 }], ['role', r => { r.auth.userInfo.role = 'SALES' }], ['venue', r => { r.auth.userInfo.organizationId = 2 }], ['permission', r => { r.auth.userInfo.permissions.splice(0) }], ['generation', r => { r.auth.sessionGeneration++ }], ['unmount', r => r.unmount()]]) {
  test(`cash: ${label} immediately clears financial data, every dialog/draft and suppresses late write effects`, async () => { for (const failure of [false,true]) { const wait = deferred(), r = runtime({ save: () => wait.promise }); await r.load(); await start(r); r.form.handoverNote = '财务备注'; const pending = r.submitOperation(), count = r.calls.length; mutate(r); assert.equal(r.data.value, null); assert.equal(r.detailData.value, null); assert.equal(r.detailDialog.value, false); assert.equal(r.operationDialog.value, false); assert.equal(r.form.handoverNote, ''); assert.deepEqual(r.form.channels, []); assert.equal(r.saving.value, false); if (failure) wait.reject(Error('旧错误')); else wait.resolve(detail('SUBMITTED')); await pending; await r.load(); await r.openDetail(shift()); await r.submitOperation(); assert.equal(r.calls.length, count); assert.deepEqual(r.messages, []) } })
}
test('cash: old list/detail/payment success or failure cannot overwrite new selection', async () => {
  for (const failure of [false,true]) { const wait = deferred(), r = runtime({ detail: id => id === 1 ? wait.promise : detail('OPEN', { id: 2 }) }); const old = r.openDetail(shift()); await r.openDetail(shift('OPEN', { id: 2 })); if (failure) wait.reject(Error('旧详情')); else wait.resolve(detail()); await old; assert.equal(r.detailData.value.shift.id, 2); assert.equal(r.detailError.value, '') }
  const wait = deferred(), r = runtime({ list: p => p.page === 1 ? wait.promise : workbench() }); const old = r.load(); r.page.value = 2; await r.load(); wait.reject(Error('旧列表')); await old; assert.equal(r.loadError.value, ''); assert.equal(r.data.value.summary.shiftCount, 3)
})
test('cash: API forwards per-request lifecycle guards for list, detail, payments and writes', async () => {
  const wait = deferred(), r = runtime({ save: () => wait.promise }); await r.load(); await start(r); await r.loadPayments(2); const pending = r.submitOperation(); assert.ok(r.calls.every(c => typeof c.guard === 'function')); assert.equal(r.calls.at(-1).guard(), true); r.unmount(); assert.ok(r.calls.every(c => c.guard() === false)); wait.resolve(detail()); await pending
})
test('cash: late payment page success/failure cannot contaminate another detail or leave loading active', async () => {
  for (const failure of [false,true]) { const wait = deferred(), r = runtime({ payments: () => wait.promise, detail: id => detail('APPROVED', { id }) }); await r.openDetail(shift('APPROVED')); const pending = r.loadPayments(2); await r.openDetail(shift('APPROVED', { id: 2 })); if (failure) wait.reject(Error('旧流水错误')); else wait.resolve({ records: [{ id: 900 }], total: 1000, page: 2, size: 100 }); await pending; assert.equal(r.detailData.value.shift.id, 2); assert.deepEqual(r.paymentData.value.records, []); assert.equal(r.paymentError.value, ''); assert.equal(r.paymentLoading.value, false) }
})
test('cash: late read success/failure after scope invalidation stays empty with no errors', async () => {
  for (const failure of [false,true]) { const wait = deferred(), r = runtime({ list: () => wait.promise }); const pending = r.load(); r.auth.userInfo.permissions = []; if (failure) wait.reject(Error('旧读取错误')); else wait.resolve(workbench()); await pending; assert.equal(r.data.value, null); assert.deepEqual(r.venues.value, []); assert.equal(r.loadError.value, ''); assert.equal(r.loading.value, false) }
})
test('cash: obsolete conflict reload continuation cannot unlock another action conflict or write again', async () => {
  for (const failure of [false, true]) {
  const old = deferred(); let firstDetails = 0
  const r = runtime({ detail: id => id === 1 && ++firstDetails === 2 ? old.promise : detail('OPEN', { id }), save: () => { throw { code: 2600, message: '流水已变化' } } })
  await start(r); await r.submitOperation(); assert.equal(r.conflict.value, true)
  const reload = r.reloadConflict(); r.closeOperation(); await r.openAction('submit', shift('OPEN', { id: 2 }))
  for (const c of r.form.channels) { c.actualReceipts = c.channel === 'CASH' ? '10' : '0'; c.actualRefunds = c.channel === 'CASH' ? '2' : '0' }
  r.form.handoverNote = 'B班次草稿'; await r.submitOperation(); assert.equal(r.conflict.value, true); assert.equal(posts(r).length, 2)
  if (failure) old.reject(Error('A旧重载失败')); else old.resolve(detail('OPEN', { version: 8 })); await reload
  assert.equal(r.conflict.value, true, 'A reload must leave B conflict locked'); assert.equal(r.current.value.id, 2); assert.equal(r.form.handoverNote, 'B班次草稿'); assert.equal(r.form.channels[0].actualReceipts, '10'); await r.submitOperation(); assert.equal(posts(r).length, 2); assert.deepEqual(r.messages, [])
  }
})
test('cash: obsolete opening workbench reload cannot unlock a newer opening conflict', async () => {
  for (const failure of [false, true]) {
  const old = deferred(); let lists = 0
  const r = runtime({ list: () => ++lists === 2 ? old.promise : { ...workbench(), currentShift: null }, save: () => { throw { code: 2600, message: '班次已变化' } } })
  await r.load(); await r.openAction('open'); await r.submitOperation(); const reload = r.reloadConflict(); r.closeOperation(); await r.load(); await r.openAction('open'); r.form.note = '新开班说明'; await r.submitOperation(); assert.equal(r.conflict.value, true)
  if (failure) old.reject(Error('A旧工作台失败')); else old.resolve({ ...workbench(), currentShift: null }); await reload
  assert.equal(r.conflict.value, true); assert.equal(r.form.note, '新开班说明'); await r.submitOperation(); assert.equal(posts(r).length, 2); assert.deepEqual(r.messages, [])
  }
})
