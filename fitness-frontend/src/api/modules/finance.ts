import request from '@/utils/request'

export type VerificationStatus = 'VERIFIED' | 'UNVERIFIED' | string
export type RefundStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'PAID' | string

export interface FundingQuote { orderId: number; orderNo: string; unusedAmount: number }
export interface EntitlementQuote {
  entitlementId: number; verificationStatus: VerificationStatus; productType?: string
  refundableAmount: number | null; remainingUses: number | null; remainingServiceDays: number | null
  version: number; blockedReasons: string[]; activeRefundId?: number | null; funding: FundingQuote[]
}
export interface ReconcileSource { orderId: number; unusedAmount: number; productType?: string; totalUnits?: number; startDate?: string }
export interface RefundAllocation { orderId: number; orderNo: string; amount: number }
export interface RefundAudit { action: string; reason?: string; occurredAt: string }
export interface Refund {
  id: number; refundNo: string; entitlementId: number; entitlementNo: string; memberId: number; memberName: string; venueId: number
  status: RefundStatus; amount: number | null; reason: string; requestedBy: number; requestedAt: string
  approvedBy?: number; approvedAt?: string; paidAt?: string; decisionReason?: string
  allocations: RefundAllocation[]; audit: RefundAudit[]
}
export interface MemberRefund { id: number; refundNo: string; entitlementNo: string; status: RefundStatus; amount: number | null; requestedAt: string; approvedAt?: string; paidAt?: string }
export interface FinanceChannel { channel: string; receipts: number; refunds: number; netCash: number }
export interface FinanceDifference { type: string; subjectType: string; subjectId: number; entitlementId?: number; orderId?: number; paymentTransactionId?: number; refundId?: number; accountBalance?: number; eventBalance?: number; message: string }
export interface FinanceReport {
  from: string; to: string; valuationDate: string; receipts: number; refunds: number; netCash: number; unusedVerifiedValue: number
  unverifiedEntitlements: number; serviceConsumed: number; channels: FinanceChannel[]; differences: FinanceDifference[]
}

export const getEntitlementQuote = (id: number) => request.get<EntitlementQuote>(`/v1/finance/entitlements/${id}/quote`)
export const reconcileEntitlement = (id: number, data: { version: number; remainingUses: number | null; reason: string; sources: ReconcileSource[]; transferEvidence?: string }) => request.post<EntitlementQuote>(`/v1/finance/entitlements/${id}/reconcile`, data)
export const verifyTransfer = (id: number, data: { version: number; reason: string; transferEvidence: string }) => request.post<EntitlementQuote>(`/v1/finance/entitlements/${id}/verify-transfer`, data)
export const createRefund = (data: { entitlementId: number; version: number; reason: string; idempotencyKey: string }) => request.post<Refund>('/v1/finance/refunds', data)
export const listRefunds = () => request.get<Refund[]>('/v1/finance/refunds')
export const listMyRefunds = () => request.get<MemberRefund[]>('/v1/finance/me/refunds')
export const getMyEntitlementQuote = (id: number) => request.get<EntitlementQuote>(`/v1/finance/me/entitlements/${id}/quote`)
/** 挡住退款的一条未完成预约（或待签私教课）。cancellable=true 表示可以一键取消。 */
export interface RefundBlocker {
  type: 'BOOKING' | 'LESSON'
  bookingId?: number | null
  lessonId?: number | null
  label: string
  detail: string
  cancellable: boolean
}
export interface ClearBlockersItem { label: string; detail: string }
/** 一键取消的结果：已取消 / 需人工处理 / 处理完仍存在的阻断原因。 */
export interface ClearBlockersResult {
  cancelled: ClearBlockersItem[]
  skipped: ClearBlockersItem[]
  remainingBlocks: string[]
}
export const getMyRefundBlockers = (entitlementId: number) =>
  request.get<RefundBlocker[]>('/v1/finance/me/refunds/blockers', { params: { entitlementId } })
export const clearMyRefundBlockers = (entitlementId: number) =>
  request.post<ClearBlockersResult>('/v1/finance/me/refunds/clear-blockers', null, { params: { entitlementId } })
export const createMyRefund = (data: { entitlementId: number; version: number; reason: string; idempotencyKey: string }) => request.post<Refund>('/v1/finance/me/refunds', data)
export const cancelMyRefund = (id: number, reason: string) => request.post<Refund>(`/v1/finance/me/refunds/${id}/cancel`, { reason })
export const approveRefund = (id: number, reason: string) => request.post<Refund>(`/v1/finance/refunds/${id}/approve`, { reason })
export const rejectRefund = (id: number, reason: string) => request.post<Refund>(`/v1/finance/refunds/${id}/reject`, { reason })
export const cancelRefund = (id: number, reason: string) => request.post<Refund>(`/v1/finance/refunds/${id}/cancel`, { reason })
export const registerRefundPayment = (id: number, data: { amount: number; channel: string; externalReference: string; occurredAt: string; note: string }) => request.post<Refund>(`/v1/finance/refunds/${id}/pay`, data)
export const getFinanceReport = (params: { from: string; to: string }) => request.get<FinanceReport>('/v1/finance/report', { params })
export const exportFinanceReport = (params: { from: string; to: string }) : Promise<Blob> => request.get<Blob, Blob>('/v1/finance/report/export', { params, responseType: 'blob' })
