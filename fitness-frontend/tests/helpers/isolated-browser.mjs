import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { fileURLToPath } from 'node:url'
export async function assertIsolatedBrowserTarget(fetcher = fetch) {
  const identity = await fetcher('http://127.0.0.1:4177/__fitchain/dev-health').then(r => r.json())
  const root = fileURLToPath(new URL('../../', import.meta.url)).replace(/[\\/]$/, '')
  const workspace = createHash('sha256').update(root.replace(/\\/g, '/').toLowerCase()).digest('hex')
  assert.equal(identity.app, 'fitchain-vite')
  assert.equal(identity.workspace, workspace, 'Test frontend must belong to this workspace')
  assert.equal(identity.port, 4177)
  assert.ok(['http://localhost:18080', 'http://127.0.0.1:18080'].includes(identity.apiOrigin), 'Refuse browser writes unless its actual proxy targets isolated port 18080')
  const marker = await fetcher('http://127.0.0.1:18091/actuator/info').then(r => r.json())
  assert.equal(marker.app?.qaDatabase, 'agent_browser', 'Only the isolated fixture database may receive writes')
}

export async function restrictIsolatedBrowserNetwork(context, blocked) {
  // An absolute VITE_API_BASE_URL can bypass /api entirely. Constrain all
  // browser traffic before the first page/login, including such alternate bases.
  await context.route('**/*', route => {
    if (new URL(route.request().url()).origin !== 'http://127.0.0.1:4177') {
      blocked.push(new URL(route.request().url()).origin)
      return route.abort('blockedbyclient')
    }
    return route.continue()
  })
}

export function assertBookingPreview(reply, scheduleId) {
  assert.equal(reply.code, 0)
  assert.equal(reply.data.mode, 'MODEL')
  assert.equal(reply.data.actions.length, 1, 'A single requested write needs exactly one preview')
  assert.equal(reply.data.actions[0]?.status, 'PENDING')
  assert.equal(reply.data.actions[0].tool, 'book_course')
  assert.equal(reply.data.actions[0].parameters.scheduleId, scheduleId)
  return reply.data.actions[0]
}

export function assertBookingReceipt(reply, pending) {
  assert.equal(reply.code, 0)
  assert.equal(reply.data.status, 'SUCCEEDED')
  assert.equal(reply.data.id, pending.id)
  assert.equal(reply.data.tool, 'book_course')
  assert.equal(reply.data.result.schedule.id, pending.parameters.scheduleId)
  return reply.data
}
