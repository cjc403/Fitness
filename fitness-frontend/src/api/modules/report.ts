import request from '@/utils/request'
export interface DashboardData {
  totalMembers: number; activeMembers: number; pausedMembers: number; blacklistedMembers: number
  venues: number; courses: number; todaySchedules: number; booked: number; attended: number; cancelled: number
  attendanceRate: number; weeklyTrend: { date: string; schedules: number }[]; generatedAt: string
}
export const getDashboard = () => request.get<DashboardData>('/v1/admin/reports/dashboard')
