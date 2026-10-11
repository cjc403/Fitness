import request, { type SessionRequestConfig } from '@/utils/request'

export type ShiftStatus = 'OPEN' | 'SUBMITTED' | 'RETURNED' | 'APPROVED'
export interface CashChannel {
  channel: string; systemReceipts: number; systemRefunds: number; systemNet: number
  actualReceipts: number | null; actualRefunds: number | null; actualNet: number | null
  receiptsDifference: number | null; refundsDifference: number | null; netDifference: number | null
}
export interface CashShift {
  id: number; shiftNo: string; venueId: number; venueName: string; businessDate: string; status: ShiftStatus; version: number
  openedBy: number; openedName: string; openedAt: string; submittedBy: number | null; submittedName: string | null; closedAt: string | null
  reviewedBy: number | null; reviewedName: string | null; reviewedAt: string | null; reviewReason: string | null
  note: string | null; differenceReason: string | null; handoverNote: string | null
  baselinePaymentId: number; openingPaymentId: number; cutoffPaymentId: number | null; ledgerFingerprint: string | null
  systemReceipts: number; systemRefunds: number; systemNet: number; actualReceipts: number | null; actualRefunds: number | null; actualNet: number | null; netDifference: number | null
}
export interface CashPayment { id: number; paymentTransactionId: number; transactionNo: string; orderId: number; transactionType: string; channel: string; amount: number; occurredAt: string | null; operatorId: number | null; carriedOver: boolean }
export interface CashAudit { id: number; action: 'OPEN' | 'SUBMIT' | 'RESUBMIT' | 'APPROVE' | 'RETURN'; actorName: string; content: string | null; changesJson: string | null; createdAt: string }
export interface CashDetail {
  shift: CashShift; preview: { ledgerFingerprint: string; paymentCount: number; carriedOverCount: number; historicalExcludedCount: number; channels: CashChannel[]; systemReceipts: number; systemRefunds: number; systemNet: number }
  channels: CashChannel[]; audit: CashAudit[]; payments: CashPayment[]; paymentTotal: number
}
export interface CashSummary { shiftCount: number; openCount: number; pendingReviewCount: number; returnedCount: number; approvedCount: number; systemReceipts: number; systemRefunds: number; systemNet: number; actualReceipts: number; actualRefunds: number; actualNet: number; netDifference: number; approvedNet: number }
export interface CashWorkbench { businessDate: string; venues: { id: number; name: string }[]; selectedVenueId: number | null; currentShift: CashShift | null; summary: CashSummary; channels: CashChannel[]; shifts: { records: CashShift[]; total: number; page: number; size: number } }
export interface CashPaymentPage { records: CashPayment[]; total: number; page: number; size: number }
export interface OpenCashRequest { venueId?: number; note?: string; idempotencyKey: string }
export interface SubmitCashRequest { expectedVersion: number; ledgerFingerprint: string; channels: { channel: string; actualReceipts: number; actualRefunds: number }[]; differenceReason?: string; handoverNote?: string; idempotencyKey: string }
export interface ReviewCashRequest { expectedVersion: number; decision: 'APPROVE' | 'RETURN'; reason?: string; idempotencyKey: string }
const base = '/v1/cash-shifts'
export const getCashWorkbench = (params: { venueId?: number; businessDate?: string; status: ShiftStatus | 'ALL'; page: number; size: number }, config?: SessionRequestConfig) => request.get<CashWorkbench>(`${base}/workbench`, { ...config, params })
export const getCashDetail = (id: number, config?: SessionRequestConfig) => request.get<CashDetail>(`${base}/${id}`, config)
export const getCashPayments = (id: number, page = 1, config?: SessionRequestConfig) => request.get<CashPaymentPage>(`${base}/${id}/payments`, { ...config, params: { page, size: 100 } })
export const openCashShift = (body: OpenCashRequest, config?: SessionRequestConfig) => request.post<CashDetail>(base, body, config)
export const submitCashShift = (id: number, body: SubmitCashRequest, config?: SessionRequestConfig) => request.post<CashDetail>(`${base}/${id}/submit`, body, config)
export const reviewCashShift = (id: number, body: ReviewCashRequest, config?: SessionRequestConfig) => request.post<CashDetail>(`${base}/${id}/review`, body, config)
