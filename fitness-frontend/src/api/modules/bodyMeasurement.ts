import request from '@/utils/request'

export interface BodyMeasurement {
  id: number; memberId: number; measuredOn: string
  weightKg?: number | null; bodyFatRate?: number | null; muscleKg?: number | null
  chestCm?: number | null; waistCm?: number | null; hipCm?: number | null
  restingHeartRate?: number | null; note?: string | null
  recordedBy?: number | null; recordedByName?: string | null
  createdAt: string; updatedAt?: string | null
}
export interface TrendPoint {
  date: string; weightKg?: number | null; bodyFatRate?: number | null
  muscleKg?: number | null; waistCm?: number | null
}
export interface TrendChange { metric: string; from: number; to: number; delta: number }
export interface BodyTrend {
  memberId: number; memberName?: string; trainingGoal?: string | null; trainingGoalNote?: string | null
  recordCount: number; points: TrendPoint[]; changes: TrendChange[]
  records: BodyMeasurement[]
}
export interface MeasurementPayload {
  measuredOn: string
  weightKg?: number | null; bodyFatRate?: number | null; muscleKg?: number | null
  chestCm?: number | null; waistCm?: number | null; hipCm?: number | null
  restingHeartRate?: number | null; note?: string | null
}

/* 会员自助 */
export const getMyBodyTrend = () => request.get<BodyTrend>('/v1/member/body-measurements')
export const recordMyBody = (data: MeasurementPayload) => request.post<BodyMeasurement>('/v1/member/body-measurements', data)
export const setMyTrainingGoal = (data: { trainingGoal: string; note?: string }) =>
  request.put<BodyTrend>('/v1/member/training-goal', data)

/* 员工 / 教练代录 */
export const getMemberBodyTrend = (memberId: number) =>
  request.get<BodyTrend>(`/v1/admin/members/${memberId}/body-measurements`)
export const recordMemberBody = (memberId: number, data: MeasurementPayload) =>
  request.post<BodyMeasurement>(`/v1/admin/members/${memberId}/body-measurements`, data)
export const setMemberTrainingGoal = (memberId: number, data: { trainingGoal: string; note?: string }) =>
  request.put<BodyTrend>(`/v1/admin/members/${memberId}/training-goal`, data)

export const deleteMyBody = (measurementId: number) =>
  request.delete<void>(`/v1/member/body-measurements/${measurementId}`)
export const deleteMemberBody = (memberId: number, measurementId: number) =>
  request.delete<void>(`/v1/admin/members/${memberId}/body-measurements/${measurementId}`)
