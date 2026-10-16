import { readFileSync } from 'node:fs'
import { request as httpRequest } from 'node:http'

export function createLocalAgentBridge({ port, projectFingerprint, configPath, readConfig = () => JSON.parse(readFileSync(configPath, 'utf8').replace(/^\uFEFF/, '')) } = {}) {
  return (request, response) => {
    const fail = (status, message) => {
      response.statusCode = status
      response.setHeader('Content-Type', 'application/json; charset=utf-8')
      response.setHeader('Cache-Control', 'no-store')
      response.end(JSON.stringify({ code: status, message, data: null }))
    }
    const ip = request.socket?.remoteAddress || ''
    const host = request.headers.host
    const allowedHosts = [`localhost:${port}`, `127.0.0.1:${port}`, `[::1]:${port}`]
    if (!(ip === '::1' || ip.startsWith('127.') || ip.startsWith('::ffff:127.')) || !allowedHosts.includes(host)) {
      return fail(403, '电脑执行器仅接受本机访问，请在启动它的电脑上使用。')
    }
    const protocol = request.socket?.encrypted ? 'https:' : 'http:'
    if (request.headers.origin && request.headers.origin !== `${protocol}//${host}`) return fail(403, '电脑执行器拒绝了非同源请求。')
    if (!/^\/v1\/[a-zA-Z0-9/_-]+(?:\?[^#]*)?$/.test(request.url || '')) return fail(400, '电脑任务地址无效。')
    let config
    try {
      config = readConfig()
      if (config.projectFingerprint !== projectFingerprint || !/^[A-Za-z0-9_-]{32,128}$/.test(config.token || '') || !Number.isInteger(config.port) || config.port < 1024 || config.port > 65535) throw Error('Unverified configuration')
    } catch { return fail(503, '本机电脑执行器未启动，请重新运行 start.bat。') }
    const headers = { ...request.headers, host: `127.0.0.1:${config.port}`, 'x-fitchain-bridge-token': config.token }
    if (request.headers.origin && config.frontendOrigin) headers.origin = config.frontendOrigin
    delete headers.connection
    const upstream = httpRequest({ hostname: '127.0.0.1', port: config.port, path: request.url, method: request.method, headers, timeout: 15000 }, reply => {
      response.statusCode = reply.statusCode || 502
      for (const [key, value] of Object.entries(reply.headers)) if (value !== undefined && !['connection', 'set-cookie', 'access-control-allow-origin'].includes(key)) response.setHeader(key, value)
      response.setHeader('Cache-Control', 'no-store')
      reply.pipe(response)
    })
    upstream.on('timeout', () => upstream.destroy())
    upstream.on('error', () => { if (!response.headersSent) fail(503, '本机电脑执行器连接失败，请重新运行 start.bat。'); else response.destroy() })
    request.on('aborted', () => upstream.destroy())
    request.pipe(upstream)
  }
}
