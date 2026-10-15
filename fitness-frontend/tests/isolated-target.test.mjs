import test from 'node:test'
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import { assertIsolatedBrowserTarget } from './helpers/isolated-browser.mjs'
const root = fileURLToPath(new URL('../', import.meta.url)).replace(/[\\/]$/, '')
const workspace = createHash('sha256').update(root.replace(/\\/g, '/').toLowerCase()).digest('hex')
const fakeFetch = overrides => async url => ({ json: async () => url.endsWith('/actuator/info') ? { app: { qaDatabase: 'agent_browser' } } : { app: 'fitchain-vite', port: 4177, workspace, apiOrigin: 'http://localhost:18080', ...overrides } })
test('isolated browser guard accepts the bound test frontend', async () => {
  await assertIsolatedBrowserTarget(fakeFetch({}))
})
test('isolated browser guard rejects a normal backend proxy despite a healthy isolated marker', async () => {
  await assert.rejects(assertIsolatedBrowserTarget(fakeFetch({ apiOrigin: 'http://localhost:8080' })))
})
test('isolated browser guard rejects a different workspace or unverified listener', async () => {
  for (const data of [{ workspace: 'different' }, { port: 5174 }, { app: 'generic' }]) await assert.rejects(assertIsolatedBrowserTarget(fakeFetch(data)))
})
