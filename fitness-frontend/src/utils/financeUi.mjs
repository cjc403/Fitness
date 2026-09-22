/** Display an amount supplied by the API.  It never derives entitlement value. */
export function formatMoney(amount) {
  return amount === null || amount === undefined ? '待核对' : Number(amount).toFixed(2)
}

/** A retry carries the same object; a new application asks the browser for another key. */
export function secureUuid() {
  const crypto = globalThis.crypto
  if (typeof crypto?.randomUUID === 'function') return crypto.randomUUID()
  if (typeof crypto?.getRandomValues !== 'function') throw new Error('无法生成安全随机申请编号，请检查浏览器后重试')
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`
}
export function refundIntent(newKey = secureUuid) {
  return Object.freeze({ idempotencyKey: newKey() })
}

/** Returns a Result-shaped business failure, never treats arbitrary text as one. */
export function parseBlobPayload(text) {
  try {
    const parsed = JSON.parse(text)
    return typeof parsed?.code === 'number' && parsed.code !== 0 && typeof parsed?.message === 'string'
      ? { code: parsed.code, message: parsed.message }
      : null
  } catch {
    return null
  }
}

const twoDigits = value => String(value).padStart(2, '0')

/** Local calendar value for LocalDate API fields; deliberately does not serialize UTC. */
export function localCalendarDate(date = new Date()) {
  return `${date.getFullYear()}-${twoDigits(date.getMonth() + 1)}-${twoDigits(date.getDate())}`
}

/** Local wall-clock value for LocalDateTime API fields; deliberately has no Z suffix. */
export function localCalendarDateTime(date = new Date()) {
  return `${localCalendarDate(date)}T${twoDigits(date.getHours())}:${twoDigits(date.getMinutes())}:${twoDigits(date.getSeconds())}`
}
