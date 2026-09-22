import request from '@/utils/request'

export interface MemberProfile {
  id: number
  memberNo: string
  realName: string
  phone: string
  email?: string
  venueId: number
  status: string
  blacklistFlag: boolean
}

export interface MemberSchedule {
  scheduleId: number
  courseId: number
  courseName: string
  courseType: string
  description?: string
  durationMinutes: number
  intensity: string
  fee: number
  coachName: string
  startTime: string
  endTime: string
  maxStudents: number
  enrolledCount: number
  booked: boolean
  bookingStatus?: string
  full: boolean
}

export interface MemberBooking {
  id: number
  scheduleId: number
  courseName: string
  coachName: string
  startTime: string
  endTime: string
  status: string
  bookedAt: string
  checkedInAt?: string
  cancelledAt?: string
  cancelReason?: string
  cancellationAllowed: boolean
  waitlistPosition?: number
  leaveRequestedAt?: string
  noShowPenaltyPoints?: number
}

export interface CourseRecommendation {
  scheduleId: number
  courseId: number
  courseName: string
  courseType: string
  intensity: string
  coachName: string
  startTime: string
  remainingPlaces: number
  fee: number
  score: number
  reasons: string[]
}

export interface RecommendationResult {
  summary: string
  source: string
  algorithmVersion: string
  recommendations: CourseRecommendation[]
}

export interface MemberProduct {
  id: number
  name: string
  productType: string
  description?: string
  validityDays: number
  totalUses?: number | null
  price: number
  venueId: number
  venueName?: string
  /** 由服务端判定：会员只能购买本人所属门店的商品，其他门店仅展示。 */
  purchasable: boolean
}

export interface MemberOrder {
  id: number
  orderNo: string
  orderType: string
  memberId: number
  productId: number
  productName: string
  paidAmount: number
  paymentMethod: string
  status: string
}

export const getMemberProfile = () => request.get<MemberProfile>('/v1/member/profile')
export const listMemberSchedules = () => request.get<MemberSchedule[]>('/v1/member/schedules')
export const listMemberBookings = () => request.get<MemberBooking[]>('/v1/member/bookings')
export const listMemberProducts = () => request.get<MemberProduct[]>('/v1/member/products')
export const purchaseMembership = (productId: number, idempotencyKey: string) => request.post<MemberOrder>('/v1/member/purchase', { productId, idempotencyKey })
export const createSelfBooking = (scheduleId: number, entitlementId?: number) => request.post('/v1/member/bookings', { scheduleId, entitlementId })
export const cancelSelfBooking = (id: number) => request.put(`/v1/member/bookings/${id}/cancel`)
export const joinScheduleWaitlist = (scheduleId: number) => request.post(`/v1/member/schedules/${scheduleId}/waitlist`)
export const requestBookingLeave = (id: number, reason = '会员临时请假') => request.post(`/v1/member/bookings/${id}/leave`, { reason })
export const getCourseRecommendations = (goal = '', preferredTime = 'ANY') =>
  request.post<RecommendationResult>('/v1/member/recommendations', { goal, preferredTime })
