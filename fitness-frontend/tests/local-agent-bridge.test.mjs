import test from 'node:test'
import assert from 'node:assert/strict'
import { createServer, request } from 'node:http'
import { once } from 'node:events'
import { createLocalAgentBridge } from '../dev/local-agent-bridge.mjs'

test('computer bridge forwards only same-origin local requests using its private capability', async t => {
  const calls = []
  const runner = createServer((req, res) => { calls.push(req.headers); res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify({ code: 0, data: { available: true } })) })
  runner.listen(0, '127.0.0.1'); await once(runner, 'listening')
  const config = { token: 'a'.repeat(64), port: runner.address().port, projectFingerprint: 'b'.repeat(64) }
  const bridge = createLocalAgentBridge({ readConfig: () => config, projectFingerprint: config.projectFingerprint, port: 5173 })
  const frontend = createServer((req, res) => bridge(req, res, () => { res.statusCode = 404; res.end('no bridge') }))
  frontend.listen(0, '127.0.0.1'); await once(frontend, 'listening')
  t.after(() => { frontend.closeAllConnections(); frontend.close(); runner.closeAllConnections(); runner.close() })
  const get = (host, origin, extra = {}) => new Promise((resolve, reject) => {
    const req = request({ hostname: '127.0.0.1', port: frontend.address().port, path: '/v1/status', headers: { host, ...(origin ? { origin } : {}), Authorization: 'Bearer fixture-token', ...extra } }, res => { let body = ''; res.on('data', b => body += b); res.on('end', () => resolve({ status: res.statusCode, body, headers: res.headers })) })
    req.on('error', reject); req.end()
  })
  const ok = await get('localhost:5173', 'http://localhost:5173', { 'x-fitchain-bridge-token': 'untrusted' })
  assert.equal(ok.status, 200); assert.equal(JSON.parse(ok.body).data.available, true)
  assert.equal(calls[0]['x-fitchain-bridge-token'], config.token)
  assert.equal(calls[0].authorization, 'Bearer fixture-token')
  assert.equal(ok.body.includes(config.token), false)
  assert.equal(ok.headers['cache-control'], 'no-store')
  for (const [host, origin] of [['192.168.1.71:5173', 'http://192.168.1.71:5173'], ['untrusted.example:5173', 'http://untrusted.example:5173'], ['localhost:5173', 'https://untrusted.example'], ['localhost:5173', 'null'], ['localhost:5173', 'http://localhost:5174']]) {
    assert.equal((await get(host, origin)).status, 403)
  }
  assert.equal(calls.length, 1, 'Blocked sources must not touch the companion')
})

test('computer bridge rejects remote sockets and unavailable/mismatched private configuration', async () => {
  const run = (options, remoteAddress) => new Promise(resolve => {
    const headers = {}, response = { setHeader: (k,v) => headers[k]=v, end: body => resolve({ status: response.statusCode, body, headers }) }
    createLocalAgentBridge(options)({ method: 'GET', url: '/v1/status', headers: { host: 'localhost:5173' }, socket: { remoteAddress } }, response, () => resolve({ status: 404 }))
  })
  assert.equal((await run({ port: 5173 }, '192.168.1.20')).status, 403)
  assert.equal((await run({ port: 5173, readConfig: () => { throw Error('private path and secret') } }, '127.0.0.1')).status, 503)
  const wrong = await run({ port: 5173, projectFingerprint: 'a'.repeat(64), readConfig: () => ({ token: 's'.repeat(64), port: 5188, projectFingerprint: 'wrong' }) }, '127.0.0.1')
  assert.equal(wrong.status, 503); assert.equal(wrong.body.includes('ssss'), false)
})
