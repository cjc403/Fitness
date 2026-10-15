// Opt-in integration: requires the explicitly marked, synthetic agent_browser database.
// Uses the backend's protected provider configuration. Never embeds an API key.
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
const api = 'http://127.0.0.1:18080/api/v1'
const out = fileURLToPath(new URL('../../docs/qa/mobile-agent-20261015/live-model/', import.meta.url))
await mkdir(out, { recursive: true })
const marker = await fetch('http://127.0.0.1:18091/actuator/info').then(r => r.json())
assert.equal(marker.app?.qaDatabase, 'agent_browser', 'Refuse all writes without the isolated database marker')
const report = { kind: 'Actual provider and actual isolated PostgreSQL; synthetic fixtures only', date: new Date().toISOString(), roles: [], flows: [], model: null }
const call = async (session, path, body) => {
  const response = await fetch(api + path, { method: body === undefined ? 'GET' : 'POST', headers: { 'Content-Type': 'application/json', ...(session ? { Authorization: 'Bearer ' + session.accessToken } : {}) }, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(95000) })
  const result = await response.json()
  assert.equal(result.code, 0, path + ': ' + result.message)
  return result.data
}
const accounts = { SUPER_ADMIN: 'admin', MANAGER: 'manager', RECEPTION: 'reception', SALES: 'advisor', COACH: 'coach', MEMBER: 'member' }
const query = { SUPER_ADMIN: ['analytics', '请调用经营概况工具，查询当前权限范围的经营概况，只查询。'], MANAGER: ['analytics', '请调用经营概况工具，查询本店经营概况，只查询。'], RECEPTION: ['cash_summary', '请查询今天的交班与日结概况，只查询。'], SALES: ['leads', '请查询我的潜客，只查询，不安排体验。'], COACH: ['students', '请查询我的学员，只查询，不创建计划。'], MEMBER: ['courses', '请查询未来七天可以预约的课程，只查询，不预约。'] }
const sessions = {}
const localDate = d => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(d)
const today = localDate(new Date()), end = localDate(new Date(Date.now() + 14 * 86400000))
async function prepare(role, tool, message) {
  const reply = await call(sessions[role], '/agent/chat', { message })
  const pending = reply.actions.find(a => a.tool === tool)
  assert.ok(pending, role + ' did not produce the expected preview: ' + JSON.stringify({ mode: reply.mode, answer: reply.answer, results: reply.results.map(r => r.tool), actions: reply.actions.map(a => a.tool) }))
  assert.equal(pending.status, 'PENDING')
  assert.ok(reply.actions.every(a => a.status === 'PENDING'), 'The model must never confirm')
  assert.equal(reply.mode, 'MODEL')
  return pending
}
async function confirm(role, pending, check) {
  const receipt = await call(sessions[role], `/agent/actions/${pending.id}/confirm`, {})
  assert.equal(receipt.status, 'SUCCEEDED', receipt.error)
  check?.(receipt)
  const replay = await call(sessions[role], `/agent/actions/${pending.id}/confirm`, {})
  assert.deepEqual(replay.result, receipt.result, 'Repeated confirmation must replay the original result')
  report.flows.push({ role, tool: pending.tool, naturalLanguagePreview: true, beforeConfirm: pending.status, afterConfirm: receipt.status, replaySameReceipt: true })
  console.log('PASS flow ' + role + ' ' + pending.tool)
  return receipt
}
try {
  for (const [role, username] of Object.entries(accounts)) {
    const session = sessions[role] = await call(null, '/auth/login', { username, password: 'password' })
    const reply = await call(session, '/agent/chat', { message: query[role][1] })
    assert.equal(reply.mode, 'MODEL', role + ': ' + reply.answer)
    assert.ok(reply.results.some(r => r.tool === query[role][0]), role + ' did not use expected scoped query')
    assert.equal(reply.actions.length, 0)
    report.roles.push({ role, query: query[role][0], actualModelToolCall: true, writes: 0 })
    console.log('PASS query ' + role)
  }
  const member = sessions.MEMBER
  const courses = (await call(member, '/agent/query', { tool: 'courses', arguments: {} })).data
  let course = courses.find(c => c.courseType === 'REGULAR' && !c.booked && !c.full && new Date(c.startTime).getTime() > Date.now() + 7200000)
  if (!course) {
    const templates = await call(sessions.SUPER_ADMIN, '/admin/courses')
    const template = templates.find(c => c.type === 'REGULAR' && c.venueId === member.userInfo.organizationId)
    assert.ok(template, 'Need an isolated regular course template')
    const day = localDate(new Date(Date.now() + 4 * 86400000))
    const schedules = await call(sessions.SUPER_ADMIN, `/admin/schedules?from=${day}&to=${day}`)
    const slot = [8,9,11,12,13,14,15,16,17,18].find(h => !schedules.some(s => s.coachId === sessions.COACH.userInfo.id && s.scheduledStartTime < `${day}T${String(h).padStart(2,'0')}:40:00` && s.scheduledEndTime > `${day}T${String(h).padStart(2,'0')}:10:00`))
    assert.ok(slot, 'Need a free coach slot in isolated fixtures')
    const schedule = await call(sessions.SUPER_ADMIN, '/admin/schedules', { courseId: template.id, venueId: template.venueId, coachId: sessions.COACH.userInfo.id, coachName: '隔离测试教练', scheduledStartTime: `${day}T${String(slot).padStart(2,'0')}:10:00`, scheduledEndTime: `${day}T${String(slot).padStart(2,'0')}:40:00`, maxStudents: 20 })
    course = (await call(member, '/agent/query', { tool: 'courses', arguments: {} })).data.find(c => c.scheduleId === schedule.id)
    console.log('Prepared isolated regular schedule')
  }
  assert.ok(course, 'Need a future unbooked regular course in isolated fixtures')
  const beforeBookings = (await call(member, '/agent/query', { tool: 'bookings', arguments: {} })).data
  const booking = await prepare('MEMBER', 'book_course', `请为我准备预约排课编号 ${course.scheduleId} 的课程。只生成待确认预览，不要执行；不要更换排课编号。`)
  assert.equal(booking.parameters.scheduleId, course.scheduleId)
  assert.deepEqual((await call(member, '/agent/query', { tool: 'bookings', arguments: {} })).data, beforeBookings, 'Preview must not change bookings')
  const bookingReceipt = await confirm('MEMBER', booking)
  const bookingId = bookingReceipt.result.id
  assert.ok(bookingId)
  const cancellation = await prepare('MEMBER', 'cancel_booking', `请为我准备取消预约编号 ${bookingId} 的待确认预览，只生成预览，不执行。`)
  assert.equal(cancellation.parameters.bookingId, bookingId)
  await confirm('MEMBER', cancellation, r => assert.equal(r.result.status, 'CANCELLED'))
  const sales = sessions.SALES
  const lead = await call(sales, '/leads', { name: '模型联调隔离潜客', phone: '139' + String(Date.now()).slice(-8), source: 'WALK_IN', intent: 'HIGH', ownerId: sales.userInfo.id, venueId: sales.userInfo.organizationId, idempotencyKey: 'model_' + Date.now() })
  const trialDate = localDate(new Date(Date.now() + 6 * 86400000))
  const hour = 9 + (new Date().getMinutes() % 8), start = `${trialDate}T${String(hour).padStart(2, '0')}:10:00`, finish = `${trialDate}T${String(hour).padStart(2, '0')}:40:00`
  const trial = await prepare('SALES', 'book_trial', `请准备独立体验预约预览：潜客编号${lead.lead.id}，当前版本${lead.lead.version}，接待顾问编号${sales.userInfo.id}，开始时间${start}，结束时间${finish}，体验内容为“器械入门体验”。只生成待确认预览，不执行。`)
  assert.equal(trial.parameters.leadId, lead.lead.id)
  const leadBefore = await call(sales, '/leads/' + lead.lead.id)
  assert.equal(leadBefore.trials.length, 0)
  await confirm('SALES', trial)
  assert.equal((await call(sales, '/leads/' + lead.lead.id)).trials.length, 1)
  const students = (await call(sessions.COACH, '/agent/query', { tool: 'students', arguments: {} })).data.records
  assert.ok(students.length)
  const training = await prepare('COACH', 'training_draft', `为学员编号${students[0].id}准备训练草稿预览：标题“模型联调隔离训练”，目标GENERAL，开始${today}，结束${end}，一个力量动作深蹲，mode为STRENGTH，3组，每组12次，不填写重量和分钟。只生成待确认预览，不执行、不发布。`)
  assert.equal(training.parameters.memberId, students[0].id)
  await confirm('COACH', training, r => assert.equal(r.result.plan.status, 'DRAFT'))
  report.model = (await call(member, '/agent/profile')).model
  assert.equal(report.model.state, 'available'); assert.equal(report.model.toolCalling, true)
} catch (error) {
  report.failure = error.message
  throw error
} finally {
  await writeFile(out + 'verification.json', JSON.stringify(report, null, 2))
  console.log(JSON.stringify(report, null, 2))
}
