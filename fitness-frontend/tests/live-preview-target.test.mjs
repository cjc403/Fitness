import test from 'node:test'
import assert from 'node:assert/strict'
import { assertBookingPreview, assertBookingReceipt } from './helpers/isolated-browser.mjs'
const pending = { id: 'preview-1', status: 'PENDING', tool: 'book_course', parameters: { scheduleId: 42 } }
const reply = action => ({ code: 0, data: { mode: 'MODEL', actions: [action] } })
test('live booking QA accepts only the requested preview', () => {
  assert.equal(assertBookingPreview(reply(pending), 42), pending)
})
test('live booking QA rejects wrong, multiple, or differently targeted model actions', () => {
  for (const response of [reply({ ...pending, tool: 'cancel_booking' }), reply({ ...pending, parameters: { scheduleId: 43 } }), { code: 0, data: { mode: 'MODEL', actions: [pending, pending] } }]) assert.throws(() => assertBookingPreview(response, 42))
})
test('live booking QA requires a receipt bound to both preview id and schedule', () => {
  const receipt = { id: pending.id, tool: 'book_course', status: 'SUCCEEDED', result: { schedule: { id: 42 } } }
  assert.equal(assertBookingReceipt({ code: 0, data: receipt }, pending), receipt)
  for (const wrong of [{ ...receipt, id: 'other' }, { ...receipt, tool: 'cancel_booking' }, { ...receipt, result: { schedule: { id: 43 } } }]) assert.throws(() => assertBookingReceipt({ code: 0, data: wrong }, pending))
})
