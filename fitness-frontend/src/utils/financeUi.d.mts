export function formatMoney(amount: number | string | null | undefined): string
export function secureUuid(): string
export function refundIntent(newKey?: () => string): Readonly<{ idempotencyKey: string }>
export function parseBlobPayload(text: string): { code: number; message: string } | null
export function localCalendarDate(date?: Date): string
export function localCalendarDateTime(date?: Date): string
