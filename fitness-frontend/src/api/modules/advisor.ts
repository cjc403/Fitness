import request from '@/utils/request'

export interface AdvisorRef { userId: number; realName?: string; username?: string; venueId?: number }
export interface FollowUp {
  id: number; memberId: number; memberName?: string; staffUserId: number; staffName?: string
  followType: string; content: string; nextFollowAt?: string; createdAt: string
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
export const listMemberFollowUps = (memberId: number) => request.get<FollowUp[]>(`/v1/admin/members/${memberId}/follow-ups`)
export const addMemberFollowUp = (memberId: number, data: { followType: string; content: string; nextFollowAt?: string }) =>
  request.post<FollowUp>(`/v1/admin/members/${memberId}/follow-ups`, data)
