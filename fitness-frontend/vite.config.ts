import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import { createHash } from 'node:crypto'

const apiTarget = process.env.FITCHAIN_DEV_API_TARGET || 'http://localhost:8080'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    {
      name: 'fitchain-dev-identity',
      apply: 'serve',
      configureServer(server) {
        // The Windows launcher may not be allowed to read an elevated process's
        // command line. Attest the actual workspace and listener instead of
        // guessing from an HTML title or stopping a process it cannot identify.
        server.middlewares.use('/__fitchain/dev-health', (request, response) => {
          response.setHeader('Cache-Control', 'no-store')
          response.setHeader('Content-Type', 'application/json; charset=utf-8')
          if (request.method !== 'GET') {
            response.statusCode = 405
            response.setHeader('Allow', 'GET')
            response.end('{}')
            return
          }
          const address = server.httpServer?.address()
          const workspace = createHash('sha256')
            .update(resolve(server.config.root).replace(/\\/g, '/').toLowerCase()).digest('hex')
          const proxy = server.config.server.proxy?.['/api']
          const target = typeof proxy === 'string' ? proxy : proxy?.target
          let apiOrigin: string | null = null
          try { if (typeof target === 'string') apiOrigin = new URL(target).origin } catch { /* Unverifiable configuration stays null. */ }
          response.end(JSON.stringify({ app: 'fitchain-vite', workspace, pid: process.pid,
            port: address && typeof address !== 'string' ? address.port : null,
            apiOrigin }))
        })
      }
    },
    {
      name: 'fitchain-separate-dev-caches',
      config(config, { command }) {
        if (config.cacheDir) return
        // CLI port overrides have already merged by this hook. Multiple live entries
        // must not replace each other's pre-bundled dependency versions.
        const entry = command === 'build' ? 'build' : `dev-${config.server?.port ?? 5173}`
        return { cacheDir: resolve(__dirname, '.vite', entry) }
      }
    }
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
        configure(proxy) {
          proxy.on('proxyReq', (outgoing, incoming) => {
            const origin = incoming.headers.origin
            const host = incoming.headers.host
            if (!origin || !host) return
            try {
              const protocol = (incoming.socket as { encrypted?: boolean }).encrypted ? 'https:' : 'http:'
              const browserOrigin = new URL(origin)
              // Only normalize a request originating from this same Vite address.
              // Preserve foreign/null Origins so the backend continues rejecting them.
              if (browserOrigin.origin === origin && new URL(`${protocol}//${host}`).origin === origin) {
                outgoing.setHeader('Origin', new URL(apiTarget).origin)
              }
            } catch { /* Malformed origins retain the backend's normal rejection. */ }
          })
        }
      }
    }
  }
})
