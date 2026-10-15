import test from 'node:test'
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'

test('development launcher can identify the exact workspace without process command-line access', async t => {
  const root = fileURLToPath(new URL('../', import.meta.url)).replace(/[\\/]$/, '')
  const cacheDir = await mkdtemp(join(tmpdir(), 'fitchain-identity-test-'))
  const target = 'http://127.0.0.1:18080'
  const server = await createServer({ root, cacheDir, logLevel: 'silent', server: { host: '127.0.0.1', port: 0, proxy: { '/api': { target } } } })
  t.after(async () => {
    await server.close()
    assert.ok(cacheDir.startsWith(join(tmpdir(), 'fitchain-identity-test-')))
    await rm(cacheDir, { recursive: true, force: true })
  })
  await server.listen()
  const base = `http://127.0.0.1:${server.httpServer.address().port}`
  const response = await fetch(base + '/__fitchain/dev-health')
  assert.match(response.headers.get('content-type') || '', /application\/json/)
  assert.equal(response.headers.get('cache-control'), 'no-store')
  const body = await response.json()
  assert.equal(body.app, 'fitchain-vite')
  assert.equal(body.pid, process.pid)
  assert.equal(body.workspace, createHash('sha256').update(root.replace(/\\/g, '/').toLowerCase()).digest('hex'))
  assert.equal(JSON.stringify(body).includes(root), false, 'do not expose absolute project paths')
  assert.equal(body.port, server.httpServer.address().port)
  assert.equal(body.apiOrigin, target, 'Attest the resolved proxy after inline/CLI overrides, not an earlier config variable')
  const post = await fetch(base + '/__fitchain/dev-health', { method: 'POST' })
  assert.equal(post.status, 405)
})
