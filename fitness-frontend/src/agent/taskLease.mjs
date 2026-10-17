// getRandomValues also works on HTTP H5, unlike the secure-context randomUUID API.
export function createDocumentLeaseId(cryptoApi=globalThis.crypto){
  if(cryptoApi?.getRandomValues){const bytes=cryptoApi.getRandomValues(new Uint8Array(24));return [...bytes].map(b=>b.toString(16).padStart(2,'0')).join('')}
  // The lease is bound to the authenticated session on the server, not a bearer secret.
  return `document_${Date.now().toString(36)}_${Math.random().toString(36).slice(2)}_${Math.random().toString(36).slice(2)}`
}
