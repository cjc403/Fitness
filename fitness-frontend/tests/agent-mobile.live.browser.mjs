// Actual isolated API + provider UI checks. Resizing simulates keyboard space;
// this is browser evidence, not physical-phone keyboard or installation evidence.
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { createServer } from 'node:http'
import { once } from 'node:events'
import { assertIsolatedBrowserTarget, assertBookingPreview, assertBookingReceipt, restrictIsolatedBrowserNetwork } from './helpers/isolated-browser.mjs'
const modulePath = process.env.FITCHAIN_PLAYWRIGHT_MODULE || 'playwright'
const { chromium } = await import(modulePath.includes(':') ? pathToFileURL(modulePath).href : modulePath)
const base = 'http://127.0.0.1:4177', api = 'http://127.0.0.1:18080/api/v1'
const out = fileURLToPath(new URL('../../docs/qa/mobile-agent-20261015/live-model/', import.meta.url))
await mkdir(out, { recursive: true })
await assertIsolatedBrowserTarget()
const roles = { SUPER_ADMIN: ['admin', '/dashboard'], MANAGER: ['manager', '/dashboard'], RECEPTION: ['reception', '/members'], SALES: ['advisor', '/advisor-workbench'], COACH: ['coach', '/coach-workbench'], MEMBER: ['member', '/member-home'] }
const report = { kind: 'Real isolated API and real browser login, keyboard-space simulation; not a physical phone', roles: [], errors: [], blockedOrigins: [], naturalLanguageUIFlow: null }
const browser = await chromium.launch({ channel: 'msedge', headless: true, args: ['--no-proxy-server'] })
try {
  // Exercise the guard against a real local listener before entering credentials.
  // No request may reach this stand-in for an alternate absolute API base.
  let trapRequests = 0
  const trap = createServer((req, res) => { trapRequests++; res.end('{}') })
  trap.listen(0, '127.0.0.1'); await once(trap, 'listening')
  const blocked = [], safetyContext = await browser.newContext()
  try {
    await restrictIsolatedBrowserNetwork(safetyContext, blocked)
    const safetyPage = await safetyContext.newPage(); await safetyPage.goto(base + '/login')
    const origin = `http://127.0.0.1:${trap.address().port}`
    const result = await safetyPage.evaluate(async url => { try { await fetch(url + '/alternate-api', { method: 'POST', body: '{}' }); return 'unexpected' } catch { return 'blocked' } }, origin)
    assert.equal(result, 'blocked'); assert.deepEqual(blocked, [origin]); assert.equal(trapRequests, 0)
    report.networkGuard = { alternateAbsoluteApiBlocked: true, trapRequests: 0 }
  } finally { await safetyContext.close(); trap.closeAllConnections(); await new Promise(resolve => trap.close(resolve)) }
  for (const [role, [username, home]] of Object.entries(roles)) {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })
    await restrictIsolatedBrowserNetwork(context, report.blockedOrigins)
    const page = await context.newPage()
    page.on('pageerror', e => report.errors.push(role + ': ' + e.message))
    await page.goto(base + '/login')
    await page.getByPlaceholder('请输入用户名').fill(username)
    await page.getByPlaceholder('请输入密码').fill('password')
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await page.waitForURL(base + home)
    await page.locator('.mobile-navigation').waitFor()
    for (const width of [390, 360, 320]) {
      await page.setViewportSize({ width, height: 740 })
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), role + ' overflow')
    }
    await page.setViewportSize({ width: 390, height: 844 })
    await page.getByRole('button', { name: '打开业务助手', exact: true }).click()
    const panel = page.getByRole('dialog', { name: 'FitChain 业务助手' })
    await panel.waitFor()
    await page.getByLabel('业务问题').focus()
    await page.setViewportSize({ width: 390, height: 420 })
    await page.waitForTimeout(180)
    const rect = await panel.boundingBox(), input = await page.getByLabel('业务问题').boundingBox()
    assert.ok(rect.y >= 0 && rect.y + rect.height <= 421, role + ' panel outside reduced viewport')
    assert.ok(input.y >= 0 && input.y + input.height <= 421, role + ' input obscured')
    await page.setViewportSize({ width: 390, height: 844 })
    if (role === 'MEMBER') {
      // These API reads only select a synthetic test target. The business action is
      // requested through the visible chat and confirmed through the visible card.
      const session = await fetch(api + '/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password: 'password' }) }).then(r => r.json())
      const query = await fetch(api + '/agent/query', { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + session.data.accessToken }, body: JSON.stringify({ tool: 'courses', arguments: {} }) }).then(r => r.json())
      const course = query.data.data.find(c => c.courseType === 'REGULAR' && !c.booked && !c.full && new Date(c.startTime).getTime() > Date.now() + 7200000)
      assert.ok(course, 'Need an available isolated regular course')
      let confirmRequests = 0
      page.on('request', r => { if (r.url().endsWith('/confirm')) confirmRequests++ })
      await page.getByLabel('业务问题').fill(`请准备预约排课编号${course.scheduleId}的待确认预览，只生成预览，不执行。`)
      const response = page.waitForResponse(r => r.url().endsWith('/agent/chat'), { timeout: 95000 })
      await page.getByRole('button', { name: '发送', exact: true }).click()
      const reply = await (await response).json()
      const pending = assertBookingPreview(reply, course.scheduleId)
      assert.equal(confirmRequests, 0)
      await page.getByText(/已生成.*项操作预览/).waitFor()
      await page.getByRole('button', { name: /执行记录/ }).click()
      const card = page.locator('.action-card').filter({ hasText: pending.summary }).filter({ has: page.getByRole('button', { name: '确认执行', exact: true }) })
      assert.equal(await card.count(), 1, 'Confirm only the visible card matching the validated preview')
      await card.waitFor(); await page.screenshot({ path: out + 'member-natural-preview.png' })
      const confirmed = page.waitForResponse(r => r.url().endsWith('/confirm'))
      await card.getByRole('button', { name: '确认执行', exact: true }).click()
      const receipt = await (await confirmed).json()
      assertBookingReceipt(receipt, pending); assert.equal(confirmRequests, 1)
      await page.getByText('业务操作已成功，以下为服务器回执。').waitFor()
      await page.screenshot({ path: out + 'member-natural-receipt.png' })
      report.naturalLanguageUIFlow = { role, beforeConfirm: 'PENDING', afterConfirm: 'SUCCEEDED', confirmRequests }
      // Return this test's synthetic booking to cancelled so the scenario can be rerun.
      const post = async (path, body) => {
        const result = await fetch(api + path, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + session.data.accessToken }, body: JSON.stringify(body) }).then(r => r.json())
        assert.equal(result.code, 0); return result.data
      }
      const cleanup = await post('/agent/actions/preview', { tool: 'cancel_booking', arguments: { bookingId: receipt.data.result.id } })
      const cancelled = await post(`/agent/actions/${cleanup.id}/confirm`, {})
      assert.equal(cancelled.status, 'SUCCEEDED'); assert.equal(cancelled.result.status, 'CANCELLED')
      report.naturalLanguageUIFlow.fixtureCleanup = 'CANCELLED'
    }
    await page.getByRole('button', { name: '关闭业务助手' }).click()
    if (role === 'RECEPTION') {
      await page.getByRole('button', { name: '新增会员', exact: true }).click()
      const dialog = page.getByRole('dialog', { name: '新增会员' }); await dialog.waitFor()
      await page.setViewportSize({ width: 320, height: 568 })
      await dialog.evaluate(async el => { await Promise.allSettled(el.getAnimations({ subtree: true }).map(a => a.finished)) })
      const box = await dialog.boundingBox()
      await page.screenshot({ path: out + 'reception-dialog-320.png' })
      assert.ok(box.x >= 0 && box.x + box.width <= 321 && box.height <= 568, JSON.stringify(box))
      await dialog.getByRole('button', { name: '取消', exact: true }).click()
    }
    await page.setViewportSize({ width: 1366, height: 900 })
    assert.equal(await page.locator('.aside').isVisible(), true)
    await page.getByRole('button', { name: '打开业务助手', exact: true }).click()
    await panel.waitFor()
    await page.screenshot({ path: out + role.toLowerCase() + '-desktop.png' })
    report.roles.push({ role, browserLogin: true, mobileWidths: [390, 360, 320], reducedViewportInputFits: true, desktop: true })
    console.log('PASS browser ' + role)
    await context.close()
  }
  assert.deepEqual(report.errors, [])
  assert.deepEqual(report.blockedOrigins, [])
} catch (error) { report.failure = error.message; throw error }
finally { await writeFile(out + 'browser-verification.json', JSON.stringify(report, null, 2)); await browser.close(); console.log(JSON.stringify(report, null, 2)) }
