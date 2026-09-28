import request from '@/utils/request'
import type { Schedule } from './course'
import type { Member } from './member'

export interface Booking {
  id: number; scheduleId: number; memberId: number; schedule: Schedule; member: Member
  status: string; bookedAt: string; cancelledAt?: string; cancelReason?: string; checkedInAt?: string
  waitlistPosition?: number; leaveRequestedAt?: string; noShowPenaltyPoints?: number
}
export const listBookings = () => request.get<Booking[]>('/v1/admin/bookings')
export const createBooking = (data: { scheduleId: number; memberId: number; entitlementId?:number }) => request.post<Booking>('/v1/admin/bookings', data)
export const cancelBooking = (id: number, reason = '后台取消') => request.put<Booking>(`/v1/admin/bookings/${id}/cancel`, null, { params: { reason } })
export const checkIn = (id: number) => request.post<Booking>(`/v1/admin/bookings/${id}/check-in`)
/** 已结束但仍未核销的预约（会一直挡住会员退款）。 */
export interface OverdueBooking {
  id: number; memberName?: string; memberNo?: string; courseName?: string
  coachName?: string; startTime?: string; status: 'BOOKED' | 'WAITLISTED'
}
export interface OverdueResultItem { label: string; detail: string }
export interface OverdueResult {
  noShow: OverdueResultItem[]; cancelled: OverdueResultItem[]; skipped: OverdueResultItem[]
}
export const listOverdueBookings = () => request.get<OverdueBooking[]>('/v1/admin/bookings/overdue')
export const resolveOverdueBookings = () => request.post<OverdueResult>('/v1/admin/bookings/overdue/resolve')
export const markNoShow = (id: number) => request.post<Booking>(`/v1/admin/bookings/${id}/no-show`)
