import request from '@/utils/request'

export interface CourseReview {
  id: number
  bookingId: number
  memberId?: number
  memberName?: string
  courseId: number
  courseName?: string
  scheduleId: number
  coachId?: number
  coachName?: string
  startTime?: string
  rating: number
  tags?: string
  content?: string
  createdAt: string
}

export interface PendingReview {
  bookingId: number
  scheduleId: number
  courseId: number
  courseName?: string
  coachId?: number
  coachName?: string
  startTime?: string
  checkedInAt?: string
}

export interface RatingSummary {
  targetId?: number
  targetName?: string
  reviewCount: number
  averageRating?: number
  distribution: Record<string, number>
  recent: CourseReview[]
}

export interface CoachRating { coachId: number; coachName?: string; reviewCount: number; averageRating?: number }
export interface VenueReviewSummary {
  reviewCount: number
  averageRating?: number
  distribution: Record<string, number>
  coaches: CoachRating[]
  lowRated: CourseReview[]
}

export const listPendingReviews = () => request.get<PendingReview[]>('/v1/member/reviews/pending')
export const listMyReviews = () => request.get<CourseReview[]>('/v1/member/reviews')
export const submitReview = (data: { bookingId: number; rating: number; tags?: string; content?: string }) =>
  request.post<CourseReview>('/v1/member/reviews', data)
export const getCoachReviewSummary = () => request.get<RatingSummary>('/v1/coach/reviews/summary')
export const getVenueReviewSummary = () => request.get<VenueReviewSummary>('/v1/admin/reviews/summary')
