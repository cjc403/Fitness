export function formatMoney(amount: number | string | null | undefined): string
export function refundIntent(newKey?: () => string): Readonly<{ idempotencyKey: string }>
export function parseBlobPayload(text: string): { code: number; message: string } | null
