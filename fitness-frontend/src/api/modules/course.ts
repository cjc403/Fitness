import request from '@/utils/request'

export interface Course {
  id: number; name: string; type: string; description?: string; durationMinutes: number
  intensity: string; maxCapacity: number; fee: number; venueId: number; status: string
}
export interface Schedule {
  id: number; courseId: number; course: Course; coachId?: number | null; coachName: string; venueId: number
  scheduledStartTime: string; scheduledEndTime: string; maxStudents: number; enrolledCount: number; status: string
}
export type CourseRequest = Omit<Course, 'id'>
export interface ScheduleRequest {
  courseId: number; coachName: string; venueId?: number; scheduledStartTime: string; scheduledEndTime: string; maxStudents?: number
}
export interface ScheduleUpdateRequest {
  courseId: number; coachName: string; coachId?: number; scheduledStartTime: string; scheduledEndTime: string; maxStudents?: number
}
export const listCourses = () => request.get<Course[]>('/v1/admin/courses')
export interface CoachOption {
  id: number; name: string; venueId: number
}
export const listCoaches = () => request.get<CoachOption[]>('/v1/admin/coaches')
export const createCourse = (data: Partial<CourseRequest>) => request.post<Course>('/v1/admin/courses', data)
export const updateCourse = (id: number, data: Partial<CourseRequest>) => request.put<Course>(`/v1/admin/courses/${id}`, data)
export const deleteCourse = (id: number) => request.delete<void>(`/v1/admin/courses/${id}`)
export const listSchedules = (params?: { from?: string; to?: string }) => request.get<Schedule[]>('/v1/admin/schedules', { params })
export const createSchedule = (data: ScheduleRequest) => request.post<Schedule>('/v1/admin/schedules', data)
export interface ScheduleBatchRequest {
  courseId: number; coachId?: number; coachName: string; venueId?: number
  weekdays: number[]; startTime: string; endTime: string
  fromDate: string; toDate: string; maxStudents?: number; dryRun?: boolean
}
export interface BatchSlot { label: string; note: string }
export interface ScheduleBatchResult { created: BatchSlot[]; skipped: BatchSlot[]; dryRun: boolean }
export const createBatchSchedules = (data: ScheduleBatchRequest) =>
  request.post<ScheduleBatchResult>('/v1/admin/schedules/batch', data)
export const updateSchedule = (id: number, data: ScheduleUpdateRequest) => request.put<Schedule>(`/v1/admin/schedules/${id}`, data)
export const deleteSchedule = (id: number) => request.delete<void>(`/v1/admin/schedules/${id}`)
