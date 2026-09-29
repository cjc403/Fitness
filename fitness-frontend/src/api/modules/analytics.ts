import request from '@/utils/request'

export interface AnalyticsOverview {
  from: string; to: string; newMembers: number; totalMembers: number
  activeMembers: number; silentMembers: number; churnedMembers: number
  totalAttendances: number; revenue: number; highRiskMembers: number
}
export interface CoachRank {
  coachId: number; coachName?: string; privateLessons: number; privateRevenue: number
  groupAttendances: number; reviews: number; averageRating?: number | null
}
export interface MemberRisk {
  memberId: number; memberNo?: string; realName?: string; phone?: string
  advisorId?: number | null; advisorName?: string | null; lastVisit?: string | null; visits60: number
  spending: number; riskScore: number; riskLevel: 'HIGH' | 'MEDIUM' | 'LOW'; reason: string
}
export interface MemberAnalytics { members: MemberRisk[]; highRisk: number; mediumRisk: number }

export const getAnalyticsOverview = (params?: { from?: string; to?: string }) =>
  request.get<AnalyticsOverview>('/v1/admin/analytics/overview', { params })
export const getCoachRanking = (params?: { from?: string; to?: string }) =>
  request.get<CoachRank[]>('/v1/admin/analytics/coaches', { params })
export const getMemberAnalytics = (riskOnly = false) =>
  request.get<MemberAnalytics>('/v1/admin/analytics/members', { params: { riskOnly } })
