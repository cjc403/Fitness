import request from '@/utils/request'

export interface CoachStudentBooking {
  bookingId: number
  memberId: number
  memberNo: string
  realName: string
  phone: string
  status: string
  checkedInAt?: string
}

export interface CoachSchedule {
  id: number
  courseName: string
  startTime: string
  endTime: string
  status: string
  maxStudents: number
  enrolledCount: number
  students: CoachStudentBooking[]
}

export interface CoachWorkbench {
  summary: {
    todaySchedules: number
    upcomingSchedules: number
    bookedStudents: number
    attendedStudents: number
    completedSessions: number
    deliveredHours: number
    serviceRevenue: number
    estimatedCommission: number
  }
  schedules: CoachSchedule[]
}

export const getCoachWorkbench = () => request.get<CoachWorkbench>('/v1/coach/workbench')
