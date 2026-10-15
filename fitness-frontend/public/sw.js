/* Public offline shell only. No app HTML, authenticated responses or request queue. */
const CACHE = 'fitchain-public-shell-v1'
const PUBLIC = ['/offline.html', '/manifest.webmanifest', '/icons/fitchain.svg', '/icons/fitchain-192.png', '/icons/fitchain-512.png']
self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(PUBLIC)).then(() => self.skipWaiting()))
})
self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key.startsWith('fitchain-public-shell-') && key !== CACHE).map(key => caches.delete(key)))).then(() => self.clients.claim()))
})
self.addEventListener('fetch', event => {
  const request = event.request, url = new URL(request.url)
  if (request.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/api/') || request.headers.has('Authorization')) return
  if (request.mode === 'navigate') {
    // Never persist login or business HTML. Only a generic offline notice is cached.
    event.respondWith(fetch(request).catch(() => caches.match('/offline.html')))
  } else if (PUBLIC.includes(url.pathname) && !url.search) {
    event.respondWith(caches.match(url.pathname).then(cached => cached || fetch(request)))
  }
})
