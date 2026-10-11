import request from '@/utils/request'

export interface AdvisorRef { userId: number; realName?: string; username?: string; venueId?: number }
export interface FollowUp {
  id: number; memberId: number; memberName?: string; staffUserId: number; staffName?: string
  followType: string; content: string; nextFollowAt?: string; createdAt: string; outcome?: FollowOutcome
}
export type FollowOutcome = 'NOT_RECORDED' | 'NO_CONTACT' | 'VISIT_BOOKED' | 'RENEWAL_PENDING' | 'DECLINED' | 'OTHER'
export interface FollowUpRequest { followType: string; content: string; nextFollowAt?: string; outcome?: FollowOutcome; idempotencyKey?: string }
export type RetentionFilter = 'ALL' | 'TODAY' | 'OVERDUE' | 'EXPIRING_7' | 'EXPIRING_30' | 'HIGH_RISK'
export interface RetentionMember {
  memberId: number; memberNo: string; realName: string; phone: string; status: string
  venueId: number; venueName: string; salesUserId: number | null; advisorName: string
  lastFollowAt: string | null; lastFollowContent: string | null; lastOutcome: FollowOutcome | null
  nextFollowAt: string | null; followStatus: 'TODAY' | 'OVERDUE' | 'UPCOMING' | 'UNSCHEDULED'
  riskScore: number; riskLevel: string; riskReason: string; expiresAt: string | null; expiryDays: number | null
  renewedThisMonth: boolean; lastRenewedAt: string | null; renewalNetAmount: number
}
export interface RetentionAdvisor {
  advisorId: number | null; advisorName: string; memberCount: number; todayFollowUps: number
  overdueFollowUps: number; followedThisMonth: number; renewedThisMonth: number
}
export interface RetentionCenter {
  asOf: string
  summary: { totalMembers: number; todayFollowUps: number; overdueFollowUps: number; expiring7: number; expiring30: number; highRisk: number; followedThisMonth: number; renewedThisMonth: number; renewalNetAmount: number }
  members: RetentionMember[]; advisors: RetentionAdvisor[]
}
export interface AdvisorSummary {
  myMembers: number; newMembersThisMonth: number; followUpsThisWeek: number
  dueFollowUps: number; salesOrders: number; salesAmount: number
}
export interface AdvisorMember {
  memberId: number; memberNo?: string; realName?: string; phone?: string; status?: string
  lastFollowAt?: string; lastFollowContent?: string; nextFollowAt?: string; due: boolean
  riskScore?: number; riskLevel?: 'HIGH' | 'MEDIUM' | 'LOW'
}
export interface AdvisorWorkbench { advisorName?: string; summary: AdvisorSummary; members: AdvisorMember[] }

/** 会员归属人下拉：role 传 SALES（会籍顾问）或 COACH（教练）。 */
export const listAdvisors = (role: 'SALES' | 'COACH' = 'SALES') =>
  request.get<AdvisorRef[]>('/v1/admin/advisors', { params: { role } })
export const getAdvisorWorkbench = () => request.get<AdvisorWorkbench>('/v1/admin/advisor/workbench')
export const getRetentionCenter = (filter: RetentionFilter = 'ALL', keyword?: string) =>
  request.get<RetentionCenter>('/v1/admin/advisor/retention', { params: { filter, ...(keyword?.trim() ? { keyword: keyword.trim() } : {}) } })
export const listMemberFollowUps = (memberId: number) => request.get<FollowUp[]>(`/v1/admin/members/${memberId}/follow-ups`)
export const addMemberFollowUp = (memberId: number, data: FollowUpRequest) =>
  request.post<FollowUp>(`/v1/admin/members/${memberId}/follow-ups`, data)
