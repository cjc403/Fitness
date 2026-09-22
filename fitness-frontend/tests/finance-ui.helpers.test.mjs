import test from 'node:test'
import assert from 'node:assert/strict'
import { formatMoney, refundIntent, parseBlobPayload, localCalendarDate, localCalendarDateTime } from '../src/utils/financeUi.mjs'

test('formatMoney displays returned amount without deriving a refundable amount', () => {
  assert.equal(formatMoney(null), '待核对')
  assert.equal(formatMoney(12.3), '12.30')
  assert.equal(formatMoney('100.005'), '100.00')
})

test('refund intent keeps one idempotency key for retries and creates a new key only for a new application', () => {
  const first = refundIntent(() => 'key-one')
  assert.equal(first.idempotencyKey, 'key-one')
  assert.equal(first.idempotencyKey, 'key-one')
  assert.equal(refundIntent(() => 'key-two').idempotencyKey, 'key-two')
})

test('parseBlobPayload distinguishes Result business errors from CSV downloads', () => {
  assert.deepEqual(parseBlobPayload('{"code":4001,"message":"报表范围无效"}'), { code: 4001, message: '报表范围无效' })
  assert.equal(parseBlobPayload('transactionNo,amount\nTX-1,10.00'), null)
})

test('local calendar formatter keeps a Shanghai month boundary out of UTC serialization', () => {
  const localMidnight = new Date('2026-08-31T16:00:00.000Z')
  assert.equal(localCalendarDate(localMidnight), '2026-09-01')
  assert.equal(localCalendarDateTime(localMidnight), '2026-09-01T00:00:00')
})
