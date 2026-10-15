import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, existsSync } from 'node:fs'
import { transformSync } from 'esbuild'
import * as vue from 'vue'
import * as financeUi from '../src/utils/financeUi.mjs'
import * as accessControl from '../src/utils/accessControl.mjs'
import * as mobileNavigation from '../src/utils/mobileNavigation.mjs'

function execute(path, deps, names = []) {
  const url = new URL('../src/' + path, import.meta.url)
  assert.ok(existsSync(url), `${path} must exist`)
  let source = readFileSync(url, 'utf8')
  if (path.endsWith('.vue')) source = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
  source += names.length ? `\nexport { ${names.join(',')} }` : ''
  const code = transformSync(source, { loader: 'ts', format: 'cjs' }).code, module = { exports: {} }
  new Function('require', 'module', 'exports', code)(name => {
    if (name.startsWith('@/components/')) return {}
    if (!(name in deps)) throw Error('Uncontrolled import: ' + name)
    return deps[name]
  }, module, module.exports)
  return module.exports
}
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
const settle = () => new Promise(resolve => setImmediate(resolve))
const lead = (id = 1, stage = 'CONSULTING') => ({ id, version: 3, name: `潜客${id}`, phone: '13800000000', source: 'WECHAT', intent: 'HIGH', ownerId: 10, ownerName: '顾问甲', venueId: 1, venueName: '一号店', stage, note: '', nextFollowAt: null, nextTrial: null, createdAt: '2026-10-09T10:00:00' })
const detail = (id = 1) => ({ lead: lead(id), events: [], trials: [] })
const workbench = (id = 1) => ({ summary: { totalLeads: 1, newThisMonth: 1, todayFollowUps: 0, overdueFollowUps: 0, todayTrials: 0, convertedLeads: 0, conversionRate: 0 }, channels: [], leads: { records: [lead(id)], total: 1, page: 1, size: 20 } })
const options = { venues: [{ id: 1, name: '一号店' }, { id: 2, name: '二号店' }], advisors: [{ id: 10, name: '顾问甲', venueId: 1 }, { id: 11, name: '顾问乙', venueId: 1 }], products: [{ id: 20, name: '月卡', price: 100, venueId: 1, productType: 'MEMBERSHIP' }] }
function runtime(overrides = {}, role = 'MANAGER') {
  const calls = [], messages = [], unmounted = [], mounted = []
  const auth = vue.reactive({ sessionGeneration: 1, userInfo: { id: 10, userId: 10, role, venueId: 1, organizationId: 1, permissions: ['LEAD_READ'] } })
  const transport = {
    get: async (url, config) => {
      calls.push({ method: 'GET', url, params: config?.params })
      if (url.endsWith('/workbench')) return { data: await (overrides.list?.(config.params) ?? workbench()) }
      if (url.endsWith('/options')) return { data: await (overrides.options?.(config?.params) ?? options) }
      if (url.endsWith('/conversion-options')) return { data: await (overrides.candidates?.() ?? []) }
      return { data: await (overrides.detail?.(Number(url.split('/').at(-1))) ?? detail(Number(url.split('/').at(-1)))) }
    },
    post: async (url, body) => { calls.push({ method: 'POST', url, body }); return { data: await (overrides.save?.(url, body) ?? detail(Number(url.split('/')[3]) || 1)) } }
  }
  const deps = { vue: { ...vue, onMounted: fn => mounted.push(fn), onUnmounted: fn => unmounted.push(fn) }, 'element-plus': { ElMessage: { warning: m => messages.push(m), error: m => messages.push(m), success: m => messages.push(m) } }, '@element-plus/icons-vue': {}, '@/utils/financeUi.mjs': financeUi, '@/store/modules/auth': { useAuthStore: () => auth }, '@/utils/request': { __esModule: true, default: transport } }
  const api = execute('api/modules/leads.ts', deps); deps['@/api/modules/leads'] = api
  const scope = vue.effectScope()
  const names = ['load', 'data', 'loading', 'loadError', 'filter', 'stage', 'keyword', 'page', 'venueId', 'openDetail', 'detailData', 'detailLoading', 'detailError', 'detailDialog', 'openAction', 'submitOperation', 'operationForm', 'operationDialog', 'saving', 'current', 'operationError', 'reloadConflict', 'operationOptions', 'candidateOrders', 'dateText', 'trialHasStarted', 'changeCreateVenue', 'createVenueOptions']
  const page = scope.run(() => execute('views/admin/LeadCenter.vue', deps, names))
  return { ...page, auth, calls, messages, api, mount: () => Promise.all(mounted.map(fn => fn())), unmount: () => { unmounted.forEach(fn => fn()); scope.stop() } }
}
const posts = r => r.calls.filter(c => c.method === 'POST')
const startFollow = async r => { await r.openAction('follow', lead()); Object.assign(r.operationForm, { content: ' 已联系 ', outcome: 'CONTACTED', method: 'CALL' }) }

test('leads: single-flight saves freeze payload, target and expected version', async () => {
  const wait = deferred(), r = runtime({ save: () => wait.promise }); await startFollow(r)
  const first = r.submitOperation(), second = r.submitOperation(); await r.openAction('follow', lead(2)); r.operationForm.content = '其他内容'
  assert.equal(posts(r).length, 1); assert.equal(posts(r)[0].url, '/v1/leads/1/follow-ups')
  assert.equal(posts(r)[0].body.content, '已联系'); assert.equal(posts(r)[0].body.expectedVersion, 3); assert.ok(Object.isFrozen(posts(r)[0].body)); assert.equal(r.current.value.id, 1)
  wait.resolve(detail()); await Promise.all([first, second]); assert.equal(r.saving.value, false)
})
test('leads: failed same intent retains key; edits and reopened intents get new keys', async () => {
  let fail = true; const r = runtime({ save: () => { if (fail) throw Error('响应丢失'); return detail() } }); await startFollow(r)
  await r.submitOperation(); await r.submitOperation(); r.operationForm.content = '修改'; await r.submitOperation()
  assert.equal(posts(r)[0].body.idempotencyKey, posts(r)[1].body.idempotencyKey); assert.notEqual(posts(r)[1].body.idempotencyKey, posts(r)[2].body.idempotencyKey)
  await startFollow(r); await r.submitOperation(); assert.notEqual(posts(r)[2].body.idempotencyKey, posts(r)[3].body.idempotencyKey)
  fail = false; await r.submitOperation(); assert.equal(posts(r)[3].body.idempotencyKey, posts(r)[4].body.idempotencyKey)
  await startFollow(r); await r.submitOperation(); assert.notEqual(posts(r)[4].body.idempotencyKey, posts(r)[5].body.idempotencyKey)
})
test('leads: identical failed future trial may replay after time passes, edited past trial cannot', async t => {
  let now = Date.parse('2035-01-01T09:59:00+08:00'); t.mock.method(Date, 'now', () => now)
  const r = runtime({ save: () => { throw Error('响应丢失') } }); await r.openAction('trial', lead())
  Object.assign(r.operationForm, { hostId: 10, startsAt: '2035-01-01T10:00:00', endsAt: '2035-01-01T10:30:00', content: '器械体验' })
  await r.submitOperation(); now = Date.parse('2035-01-01T10:01:00+08:00'); await r.submitOperation()
  assert.equal(posts(r).length, 2); assert.equal(posts(r)[0].body.idempotencyKey, posts(r)[1].body.idempotencyKey)
  r.operationForm.content = '修改体验'; await r.submitOperation(); assert.equal(posts(r).length, 2)
  await r.openAction('trial', lead()); Object.assign(r.operationForm, { hostId: 10, startsAt: '2035-01-01T10:00:00', endsAt: '2035-01-01T10:30:00', content: '新体验' }); await r.submitOperation(); assert.equal(posts(r).length, 2)
})
test('leads: list filters are captured; obsolete success/failure cannot replace current list', async () => {
  for (const failure of [false, true]) {
    const wait = deferred(), r = runtime({ list: p => p.filter === 'ALL' ? wait.promise : workbench(2) })
    const pending = r.load(); r.filter.value = 'OVERDUE'; r.stage.value = 'VISITED'; r.keyword.value = ' 张 '; r.page.value = 2; r.venueId.value = 1; await r.load()
    if (failure) wait.reject(Error('旧失败')); else wait.resolve(workbench(1)); await pending
    assert.equal(r.data.value.leads.records[0].id, 2); assert.equal(r.loadError.value, '')
    assert.deepEqual(r.calls[1].params, { venueId: 1, stage: 'VISITED', filter: 'OVERDUE', keyword: '张', page: 2, size: 20 })
  }
})
test('leads: switching detail clears data and suppresses old detail errors/results', async () => {
  for (const failure of [false, true]) {
    const a = deferred(), b = deferred(), r = runtime({ detail: id => id === 1 ? a.promise : b.promise })
    const first = r.openDetail(lead(1)), second = r.openDetail(lead(2)); assert.equal(r.detailData.value, null); assert.equal(r.detailLoading.value, true)
    if (failure) a.reject(Error('旧详情')); else a.resolve(detail(1)); await first; assert.equal(r.detailError.value, ''); assert.equal(r.detailLoading.value, true)
    b.resolve(detail(2)); await second; assert.equal(r.detailData.value.lead.id, 2)
  }
})
test('leads: account replacement and unmount suppress new reads/writes and late save effects', async () => {
  for (const stop of [r => r.auth.sessionGeneration++, r => r.unmount()]) {
    const r = runtime(); await startFollow(r); const before = r.calls.length; stop(r); await r.submitOperation(); await r.load(); await r.openDetail(lead(2)); await r.openAction('create'); assert.equal(r.calls.length, before)
    const wait = deferred(), late = runtime({ save: () => wait.promise }); await startFollow(late); const pending = late.submitOperation(), count = late.calls.length; stop(late); wait.resolve(detail()); await pending
    assert.equal(late.calls.length, count); assert.equal(late.operationDialog.value, false); assert.deepEqual(late.messages, [])
  }
})
test('leads: reactive logout/account or scope replacement immediately clears loaded private data, draft and modal layers', async t => {
  let cleared = 0
  t.mock.method(globalThis, 'setInterval', () => 456); t.mock.method(globalThis, 'clearInterval', id => { if (id === 456) cleared++ })
  const mutations = [
    r => { r.auth.sessionGeneration++; r.auth.userInfo = null },
    r => { r.auth.sessionGeneration++; r.auth.userInfo = { ...r.auth.userInfo, id: 12 } },
    r => { r.auth.userInfo = { ...r.auth.userInfo, organizationId: 2 } },
    r => { r.auth.userInfo = { ...r.auth.userInfo, role: 'RECEPTION', permissions: ['MEMBER_READ'] } }
  ]
  for (const mutate of mutations) {
    const wait = deferred(), r = runtime({ save: () => wait.promise, candidates: () => [{ orderId: 99, memberName: '私密姓名', memberNo: 'M99' }] })
    await r.mount(); await r.openDetail(lead()); await r.openAction('convert', lead()); assert.equal(r.candidateOrders.value.length, 1)
    await r.openAction('profile', lead()); Object.assign(r.operationForm, { name: '私密姓名', phone: '13800000000', note: '私密备注' }); r.keyword.value = '私密检索'
    assert.equal(r.data.value.leads.records[0].phone, '13800000000'); assert.equal(r.detailData.value.lead.phone, '13800000000'); assert.equal(r.operationDialog.value, true)
    const pending = r.submitOperation(), count = r.calls.length; mutate(r)
    // flush:sync must remove records even before nextTick and before logout's HTTP request completes.
    assert.equal(r.data.value.leads.records.length, 0); await vue.nextTick()
    assert.equal(r.detailData.value, null); assert.equal(r.current.value, null); assert.equal(r.detailDialog.value, false); assert.equal(r.operationDialog.value, false)
    assert.equal(r.operationForm.name, ''); assert.equal(r.operationForm.phone, ''); assert.equal(r.operationForm.note, ''); assert.equal(r.keyword.value, '')
    assert.equal(r.operationOptions.value.advisors.length, 0); assert.deepEqual(r.candidateOrders.value, []); assert.deepEqual(r.createVenueOptions.value, []); assert.equal(r.saving.value, false)
    wait.resolve(detail(9)); await pending; await r.load(); await r.openAction('create'); await r.openDetail(lead(9)); assert.equal(r.calls.length, count)
    assert.equal(r.detailData.value, null); assert.deepEqual(r.messages, []); r.unmount()
  }
  assert.equal(cleared, mutations.length)
})
test('leads: headquarters create retains all venues after scoped reload and can switch with correct owner/payload', async () => {
  const scoped = id => ({ venues: options.venues.filter(v => v.id === id), advisors: [{ id: id * 10, name: `顾问${id}`, venueId: id }], products: [] })
  for (const initialVenue of [undefined, 1]) {
    const r = runtime({ options: p => p?.venueId ? scoped(p.venueId) : { ...options, advisors: [], products: [] } }, 'SUPER_ADMIN')
    r.venueId.value = initialVenue; await r.openAction('create'); assert.deepEqual(r.createVenueOptions.value.map(v => v.id), [1, 2])
    Object.assign(r.operationForm, { venueId: 1, ownerId: 10, name: '张三', phone: '13800000000', note: '保留此备注' }); await r.changeCreateVenue(); await settle()
    assert.deepEqual(r.operationOptions.value.venues.map(v => v.id), [1]); assert.deepEqual(r.createVenueOptions.value.map(v => v.id), [1, 2])
    r.operationForm.venueId = 2; const changed = r.changeCreateVenue(); assert.equal(r.operationForm.ownerId, undefined); await changed; await settle()
    assert.deepEqual(r.createVenueOptions.value.map(v => v.id), [1, 2]); assert.equal(r.operationForm.ownerId, 20); assert.equal(r.operationForm.name, '张三'); assert.equal(r.operationForm.note, '保留此备注')
    await r.submitOperation(); assert.equal(posts(r)[0].body.venueId, 2); assert.equal(posts(r)[0].body.ownerId, 20); r.unmount()
  }
})
test('leads: racing headquarters venue switches retain global selector and discard obsolete scoped advisor response', async () => {
  for (const failure of [false, true]) {
    const old = deferred(), r = runtime({ options: p => !p?.venueId ? { ...options, advisors: [] } : p.venueId === 1 ? old.promise : { venues: [options.venues[1]], advisors: [{ id: 20, name: '二店顾问', venueId: 2 }], products: [] } }, 'SUPER_ADMIN')
    await r.openAction('create'); r.operationForm.venueId = 1; const first = r.changeCreateVenue(); r.operationForm.venueId = 2; await r.changeCreateVenue(); await settle()
    if (failure) old.reject(Error('旧门店失败')); else old.resolve({ venues: [options.venues[0]], advisors: [{ id: 10, name: '一店顾问', venueId: 1 }], products: [] }); await first; await settle()
    assert.deepEqual(r.createVenueOptions.value.map(v => v.id), [1, 2]); assert.equal(r.operationForm.ownerId, 20); assert.equal(r.operationOptions.value.advisors[0].venueId, 2); assert.equal(r.operationError.value, '')
    r.unmount()
  }
})
test('leads: late lists/options/candidates cannot publish after account replacement or unmount', async () => {
  for (const stop of [r => r.auth.sessionGeneration++, r => r.unmount()]) {
    const wait = deferred(), r = runtime({ list: () => wait.promise }); const pending = r.load(); stop(r); wait.resolve(workbench(9)); await pending; assert.equal(r.data.value.leads.records.length, 0)
    const opts = deferred(), p = runtime({ options: () => opts.promise }); const open = p.openAction('create'); stop(p); opts.resolve(options); await open; assert.equal(p.operationOptions.value.advisors.length, 0)
    const orders = deferred(), c = runtime({ candidates: () => orders.promise }); const conversion = c.openAction('convert', lead()); stop(c); orders.resolve([{ orderId: 99 }]); await conversion; assert.deepEqual(c.candidateOrders.value, [])
  }
})
test('leads: invalid follow enums/content/dates and trial duration/host are blocked', async () => {
  const r = runtime(); await startFollow(r)
  for (const patch of [{ content: '  ' }, { content: '内容', outcome: 'CONVERTED' }, { outcome: 'CONTACTED', method: 'INVALID' }, { method: 'CALL', nextFollowAt: 'bad' }, { nextFollowAt: '2000-01-01T00:00:00' }]) { Object.assign(r.operationForm, patch); await r.submitOperation() }
  assert.equal(posts(r).length, 0)
  await r.openAction('trial', lead()); Object.assign(r.operationForm, { hostId: 10, startsAt: '2035-01-01T10:00:00', endsAt: '2035-01-01T10:10:00', content: '体验' }); await r.submitOperation()
  r.operationForm.endsAt = '2035-01-01T13:01:00'; await r.submitOperation(); r.operationForm.endsAt = '2035-01-01T10:30:00'; r.operationForm.hostId = 999; await r.submitOperation(); assert.equal(posts(r).length, 0)
})
test('leads: create/profile validate scope and preserve fixed phone/source/venue', async () => {
  const r = runtime(); await r.openAction('create'); Object.assign(r.operationForm, { name: ' ', phone: '13800000000', venueId: 1, ownerId: 10 }); await r.submitOperation()
  Object.assign(r.operationForm, { name: ' 张三 ', source: 'INVALID' }); await r.submitOperation(); r.operationForm.source = 'WECHAT'; r.operationForm.intent = 'INVALID'; await r.submitOperation(); assert.equal(posts(r).length, 0)
  r.operationForm.intent = 'HIGH'; await r.submitOperation(); assert.equal(posts(r)[0].body.name, '张三'); assert.equal('expectedVersion' in posts(r)[0].body, false)
  await r.openAction('profile', lead()); Object.assign(r.operationForm, { name: '李四', phone: '13900000000', source: 'OTHER', venueId: 2 }); await r.submitOperation()
  const body = posts(r)[1].body; assert.equal(body.expectedVersion, 3); assert.equal('phone' in body, false); assert.equal('source' in body, false); assert.equal('venueId' in body, false)
})
test('leads: creation validates exact member-compatible mainland phone and 50-character name', async () => {
  const r = runtime(); await r.openAction('create'); Object.assign(r.operationForm, { name: '张'.repeat(51), phone: '13800000000', venueId: 1, ownerId: 10 }); await r.submitOperation()
  r.operationForm.name = '张三'; for (const phone of ['123', '12800000000', '138000000000', 'abc', '138 0000 0000']) { r.operationForm.phone = phone; await r.submitOperation() }
  assert.equal(posts(r).length, 0)
  r.operationForm.phone = ' 13800000000 '; await r.submitOperation(); assert.equal(posts(r)[0].body.phone, '13800000000')
})
test('leads: SALES cannot transfer owner but may choose another local trial host', async () => {
  const r = runtime({}, 'SALES'); await r.openAction('profile', lead()); r.operationForm.ownerId = 11; await r.submitOperation(); assert.equal(posts(r).length, 0)
  await r.openAction('trial', lead()); Object.assign(r.operationForm, { hostId: 11, content: '体验', startsAt: '2035-01-01T10:00:00', endsAt: '2035-01-01T10:30:00' }); await r.submitOperation(); assert.equal(posts(r)[0].body.hostId, 11)
})
test('leads: conversion rejects pending trial, bad amount/method/order and captures valid real payment', async () => {
  const r = runtime(); const pendingLead = { ...lead(), nextTrial: { id: 4, status: 'BOOKED' } }; await r.openAction('convert', pendingLead); assert.equal(r.operationDialog.value, false)
  await r.openAction('convert', lead()); Object.assign(r.operationForm, { mode: 'NEW', productId: 20, paymentMethod: 'CASH', paidAmount: '100' })
  for (const value of ['0', 'NaN', '0.001', '-1']) { r.operationForm.paidAmount = value; await r.submitOperation() }; r.operationForm.paidAmount = '100'; r.operationForm.paymentMethod = 'FAKE'; await r.submitOperation(); assert.equal(posts(r).length, 0)
  r.operationForm.paymentMethod = 'WECHAT'; await r.submitOperation(); assert.equal(posts(r)[0].url, '/v1/leads/1/convert'); assert.equal(posts(r)[0].body.paidAmount, 100)
  await r.openAction('convert', lead()); r.operationForm.mode = 'LINK'; r.operationForm.orderId = 999; await r.submitOperation(); assert.equal(posts(r).length, 1)
})
test('leads: payment channels reuse existing commercial CARD, WECHAT, ALIPAY and CASH vocabulary', async () => {
  const r = runtime(); await r.openAction('convert', lead()); Object.assign(r.operationForm, { mode: 'NEW', productId: 20, paidAmount: '100', paymentMethod: 'BANK_TRANSFER' }); await r.submitOperation()
  r.operationForm.paymentMethod = 'POS'; await r.submitOperation(); assert.equal(posts(r).length, 0)
  for (const paymentMethod of ['CARD', 'WECHAT', 'ALIPAY', 'CASH']) { await r.openAction('convert', lead()); Object.assign(r.operationForm, { mode: 'NEW', productId: 20, paidAmount: '100', paymentMethod }); await r.submitOperation() }
  assert.deepEqual(posts(r).map(c => c.body.paymentMethod), ['CARD', 'WECHAT', 'ALIPAY', 'CASH'])
})
test('leads: trial results target captured booked trial and cannot record attendance before start', async t => {
  t.mock.method(Date, 'now', () => Date.parse('2035-01-01T09:00:00+08:00')); const r = runtime(), trial = { id: 7, status: 'BOOKED', startsAt: '2035-01-01T10:00:00' }
  await r.openAction('result', lead(), trial); Object.assign(r.operationForm, { status: 'ATTENDED', note: '体验完毕' }); await r.submitOperation(); r.operationForm.status = 'INVALID'; await r.submitOperation(); assert.equal(posts(r).length, 0)
  r.operationForm.status = 'CANCELLED'; await r.submitOperation(); assert.equal(posts(r)[0].url, '/v1/leads/1/trials/7/result'); assert.equal(posts(r)[0].body.status, 'CANCELLED')
})
test('leads: waiting in result dialog enables started outcomes when Shanghai time crosses the appointment start', async t => {
  let now = Date.parse('2035-01-01T09:59:59+08:00'), tick, cleared = false
  t.mock.method(Date, 'now', () => now); t.mock.method(globalThis, 'setInterval', fn => { tick = fn; return 123 }); t.mock.method(globalThis, 'clearInterval', id => { if (id === 123) cleared = true })
  const r = runtime(); await r.mount(); await r.openAction('result', lead(), { id: 7, status: 'BOOKED', startsAt: '2035-01-01T10:00:00' })
  assert.equal(r.trialHasStarted.value, false); assert.equal(typeof tick, 'function')
  now += 2000; tick(); assert.equal(r.trialHasStarted.value, true); r.unmount(); assert.equal(cleared, true)
})
test('leads: LINK captures only validated order and retains member/payment fields untouched', async () => {
  const order = { orderId: 99, orderNo: 'S99', memberId: 7, memberNo: 'M7', memberName: '张三', productName: '月卡', netAmount: 100, paidAt: '2026-10-09T11:00:00' }
  const r = runtime({ candidates: () => [order] }); await r.openAction('convert', lead()); Object.assign(r.operationForm, { mode: 'LINK', orderId: 99, paidAmount: '999', paymentMethod: 'FAKE', name: '覆盖会员' }); await r.submitOperation()
  assert.deepEqual(Object.keys(posts(r)[0].body).sort(), ['expectedVersion', 'idempotencyKey', 'mode', 'orderId']); assert.equal(posts(r)[0].body.orderId, 99)
})
test('leads: started trial accepts attendance/no-show with immutable recorded notes', async t => {
  t.mock.method(Date, 'now', () => Date.parse('2035-01-01T10:01:00+08:00'))
  const r = runtime(); for (const status of ['ATTENDED', 'NO_SHOW']) { await r.openAction('result', lead(), { id: 7, status: 'BOOKED', startsAt: '2035-01-01T10:00:00' }); Object.assign(r.operationForm, { status, note: ' 已确认结果 ' }); await r.submitOperation() }
  assert.deepEqual(posts(r).map(c => [c.body.status, c.body.note]), [['ATTENDED', '已确认结果'], ['NO_SHOW', '已确认结果']])
})
test('leads: obsolete option and conversion candidates do not replace the newly selected action', async () => {
  for (const failure of [false, true]) {
    const wait = deferred(); let count = 0
    const r = runtime({ options: () => ++count === 1 ? wait.promise : { ...options, advisors: [{ id: 11, name: '顾问乙', venueId: 1 }] }, candidates: () => [{ orderId: 99 }] })
    const first = r.openAction('convert', lead(1)); await r.openAction('trial', lead(2))
    if (failure) wait.reject(Error('旧选项失败')); else wait.resolve(options); await first
    assert.equal(r.current.value.id, 2); assert.equal(r.operationOptions.value.advisors[0].id, 11); assert.deepEqual(r.candidateOrders.value, []); assert.equal(r.operationError.value, '')
  }
})
test('leads: write errors remain safe, version conflict requires reload then fresh intent', async () => {
  const r = runtime({ save: () => { const e = Error('SQL SELECT password from users'); e.response = { status: 409 }; throw e } }); await startFollow(r); await r.submitOperation()
  assert.match(r.operationError.value, /已更新|重新加载/); assert.doesNotMatch(r.operationError.value, /SQL|password/); await r.submitOperation(); assert.equal(posts(r).length, 1)
  await r.reloadConflict(); assert.equal(r.operationDialog.value, false); assert.ok(r.calls.some(c => c.method === 'GET' && c.url === '/v1/leads/1'))
})
test('leads: route and menu require both LEAD_READ and permitted owner roles', () => {
  let routerOptions
  const deps = { 'vue-router': { createRouter: value => { routerOptions = value; return { beforeEach() {} } }, createWebHistory: () => ({}) }, '@/utils/storage': { getToken: () => '', getUserInfo: () => ({}) }, '@/utils/auth': accessControl, '@/utils/accessControl.mjs': accessControl, '@/utils/mobileNavigation.mjs': mobileNavigation }
  execute('router/index.ts', deps); const route = routerOptions.routes.find(r => r.path === '/').children.find(r => r.path === 'leads'); assert.ok(route)
  globalThis.window = { innerWidth: 1200, matchMedia: () => ({ matches: false }) }; globalThis.document = { hidden: false }; globalThis.localStorage = { getItem: () => null }
  const layout = execute('layouts/AdminLayout.vue', { ...deps, vue: { ...vue, provide() {}, onMounted() {}, onUnmounted() {}, watchEffect() {} }, 'vue-router': { useRoute: () => ({ path: '/leads' }) }, 'element-plus': { ElMessage: {} }, '@element-plus/icons-vue': {}, '@/store/modules/auth': { useAuthStore: () => ({ userInfo: { role: 'SALES', permissions: ['LEAD_READ'] } }) }, '@/utils/workspaceAppearance.mjs': { normalizeMotionPreference: () => 'standard', resolveMotionState: () => 'full' }, '@/utils/workspaceMotion': { workspaceMotionKey: Symbol() } }, ['menuItems']); const menu = layout.menuItems.find(m => m.path === '/leads'); assert.ok(menu)
  for (const role of ['SALES', 'MANAGER', 'SUPER_ADMIN', 'COACH', 'RECEPTION', 'MEMBER']) {
    const permit = ['SALES', 'MANAGER', 'SUPER_ADMIN'].includes(role); assert.equal(accessControl.canAccessRoute(route.meta, role, ['LEAD_READ']), permit); assert.equal(accessControl.isMenuVisible(menu, role, ['LEAD_READ']), permit)
    assert.equal(accessControl.canAccessRoute(route.meta, role, []), false); assert.equal(accessControl.isMenuVisible(menu, role, []), false)
  }
})
