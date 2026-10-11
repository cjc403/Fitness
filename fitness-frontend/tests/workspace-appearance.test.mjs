import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeMotionPreference, resolveMotionState, trendBarPercent } from '../src/utils/workspaceAppearance.mjs'

test('an unsupported saved preference falls back without enabling a reduced setting', () => {
  assert.equal(normalizeMotionPreference('reduced'), 'reduced')
  for (const value of [null, undefined, '', 'invalid', 'standard']) {
    assert.equal(normalizeMotionPreference(value), 'standard')
  }
})

test('system reduced motion takes precedence over the standard appearance setting', () => {
  assert.equal(resolveMotionState('standard', true, false), 'reduced')
  assert.equal(resolveMotionState('standard', false, false), 'full')
  assert.equal(resolveMotionState('reduced', false, false), 'reduced')
})

test('a hidden workspace pauses motion and restores the user preference when visible', () => {
  assert.equal(resolveMotionState('standard', false, true), 'paused')
  assert.equal(resolveMotionState('reduced', false, true), 'paused')
  assert.equal(resolveMotionState('reduced', false, false), 'reduced')
})

test('zero and invalid course counts never draw a positive trend bar', () => {
  for (const value of [0, -1, NaN, Infinity, null, undefined]) {
    assert.equal(trendBarPercent(value, 10), 0)
  }
  assert.equal(trendBarPercent(2, 0), 0)
})

test('trend bars retain relative scale at both small and large real counts', () => {
  assert.equal(trendBarPercent(1, 2), 50)
  assert.equal(trendBarPercent(2, 2), 100)
  assert.equal(trendBarPercent(100, 200), 50)
  assert.equal(trendBarPercent(500, 200), 100)
})
