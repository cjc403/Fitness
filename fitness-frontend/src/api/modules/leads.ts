import request from '@/utils/request'

export type LeadStage = 'CONSULTING' | 'VISITED' | 'TRIAL_BOOKED' | 'TRIAL_COMPLETED' | 'LOST' | 'CONVERTED'
export type LeadSource = 'WALK_IN' | 'REFERRAL' | 'DOUYIN' | 'WECHAT' | 'OTHER'
export type LeadIntent = 'HIGH' | 'MEDIUM' | 'LOW'
export type LeadFilter = 'ALL' | 'TODAY' | 'OVERDUE' | 'TRIAL_TODAY'
export type FollowOutcome = 'CONTACTED' | 'NO_CONTACT' | 'VISITED' | 'INTERESTED' | 'DECLINED' | 'REOPEN'
export type FollowMethod = 'CALL' | 'WECHAT' | 'VISIT' | 'OTHER'
export type TrialStatus = 'BOOKED' | 'ATTENDED' | 'NO_SHOW' | 'CANCELLED'
export type PaymentMethod = 'CARD' | 'WECHAT' | 'ALIPAY' | 'CASH'
export interface TrialView {
  id: number; leadId: number; hostId: number; hostName: string; startsAt: string; endsAt: string
  content: string; status: TrialStatus; resultNote: string | null; recordedAt: string | null; createdAt: string
}
export interface LeadView {
  id: number; version: number; name: string; phone: string; source: LeadSource; intent: LeadIntent
  ownerId: number; ownerName: string; venueId: number; venueName: string; stage: LeadStage; note: string | null
  nextFollowAt: string | null; visitedAt: string | null; trialCompletedAt: string | null
  convertedMemberId: number | null; convertedOrderId: number | null; convertedAt: string | null
  conversionNetAmount: number | null; createdAt: string; updatedAt: string; nextTrial: TrialView | null
}
export interface LeadEvent { id: number; type: string; actorName: string; content: string; outcome: string | null; nextFollowAt: string | null; createdAt: string }
export interface LeadDetail { lead: LeadView; events: LeadEvent[]; trials: TrialView[] }
export interface LeadWorkbench {
  summary: { totalLeads: number; newThisMonth: number; todayFollowUps: number; overdueFollowUps: number; todayTrials: number; convertedLeads: number; conversionRate: number }
  channels: { source: LeadSource; total: number; visited: number; trialCompleted: number; converted: number; conversionRate: number }[]
  leads: { records: LeadView[]; total: number; page: number; size: number }
}
export interface LeadOptions {
  venues: { id: number; name: string }[]
  advisors: { id: number; name: string; venueId: number }[]
  products: { id: number; name: string; price: number; venueId: number; productType: string }[]
}
export interface ConversionOrder { orderId: number; orderNo: string; memberId: number; memberNo: string; memberName: string; productName: string; netAmount: number; paidAt: string }
export interface CreateLeadRequest { name: string; phone: string; source: LeadSource; intent: LeadIntent; ownerId: number; venueId: number; note?: string; nextFollowAt?: string; idempotencyKey: string }
export interface ProfileLeadRequest { expectedVersion: number; name: string; intent: LeadIntent; ownerId: number; note?: string; idempotencyKey: string }
export interface FollowLeadRequest { expectedVersion: number; method: FollowMethod; content: string; outcome: FollowOutcome; nextFollowAt?: string; idempotencyKey: string }
export interface BookTrialRequest { expectedVersion: number; hostId: number; startsAt: string; endsAt: string; content: string; idempotencyKey: string }
export interface TrialResultRequest { expectedVersion: number; status: Exclude<TrialStatus, 'BOOKED'>; note: string; idempotencyKey: string }
export type ConvertLeadRequest = { expectedVersion: number; idempotencyKey: string } & ({ mode: 'NEW'; productId: number; paymentMethod: PaymentMethod; paidAmount: number } | { mode: 'LINK'; orderId: number })

const base = '/v1/leads'
export const getLeadWorkbench = (params: { venueId?: number; stage: LeadStage | 'ALL'; filter: LeadFilter; keyword?: string; page: number; size: number }) =>
  request.get<LeadWorkbench>(`${base}/workbench`, { params: { ...params, ...(params.keyword?.trim() ? { keyword: params.keyword.trim() } : {}) } })
export const getLeadDetail = (id: number) => request.get<LeadDetail>(`${base}/${id}`)
export const getLeadOptions = (venueId?: number) => request.get<LeadOptions>(`${base}/options`, { params: venueId ? { venueId } : {} })
export const getConversionOptions = (id: number) => request.get<ConversionOrder[]>(`${base}/${id}/conversion-options`)
export const createLead = (body: CreateLeadRequest) => request.post<LeadDetail>(base, body)
export const updateLeadProfile = (id: number, body: ProfileLeadRequest) => request.post<LeadDetail>(`${base}/${id}/profile`, body)
export const addLeadFollowUp = (id: number, body: FollowLeadRequest) => request.post<LeadDetail>(`${base}/${id}/follow-ups`, body)
export const bookLeadTrial = (id: number, body: BookTrialRequest) => request.post<LeadDetail>(`${base}/${id}/trials`, body)
export const recordLeadTrialResult = (id: number, trialId: number, body: TrialResultRequest) => request.post<LeadDetail>(`${base}/${id}/trials/${trialId}/result`, body)
export const convertLead = (id: number, body: ConvertLeadRequest) => request.post<LeadDetail>(`${base}/${id}/convert`, body)
