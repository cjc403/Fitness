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
export const markNoShow = (id: number) => request.post<Booking>(`/v1/admin/bookings/${id}/no-show`)
