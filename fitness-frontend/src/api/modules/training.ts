import request from '@/utils/request'

export interface PrivateLesson {
  id:number; bookingId:number; entitlementId:number; memberId:number; memberName:string;
  coachId:number; coachName:string; venueId:number; courseName:string; startTime:string; endTime:string;
  status:string; unitPrice:number; commissionRate?:number; commissionAmount?:number; settlementId?:number;
  submittedAt?:string; confirmedAt?:string; note?:string
}
export interface CommissionRule { coachId:number; coachName:string; venueId:number; rate:number; defaultRule:boolean; updatedAt?:string }
export interface CoachSettlement { id:number; coachId:number; coachName:string; venueId:number; month:string; lessonCount:number; serviceAmount:number; commissionAmount:number; status:string; createdAt:string; approvedAt?:string }
export interface PrivateReadiness {
  privateCourses: number
  scheduledPrivateLessons: number
  lessonsWithCoach: number
  usablePackages: number
}
export const listPrivateLessons=()=>request.get<PrivateLesson[]>('/v1/training/lessons')
export const getPrivateReadiness=()=>request.get<PrivateReadiness>('/v1/training/readiness')
export const submitPrivateLesson=(bookingId:number)=>request.post<PrivateLesson>(`/v1/training/lessons/${bookingId}/submit`)
export const confirmPrivateLesson=(bookingId:number)=>request.post<PrivateLesson>(`/v1/training/lessons/${bookingId}/confirm`)
export const reversePrivateLesson=(bookingId:number,reason:string)=>request.post<PrivateLesson>(`/v1/training/lessons/${bookingId}/reverse`,{reason})
export const listCommissionRules=()=>request.get<CommissionRule[]>('/v1/training/commission-rules')
export const setCommissionRule=(coachId:number,rate:number)=>request.put<CommissionRule>('/v1/training/commission-rules',{coachId,rate})
export const listCoachSettlements=()=>request.get<CoachSettlement[]>('/v1/training/settlements')
export const generateCoachSettlement=(coachId:number,month:string)=>request.post<CoachSettlement>('/v1/training/settlements',{coachId,month})
export const approveCoachSettlement=(id:number)=>request.post<CoachSettlement>(`/v1/training/settlements/${id}/approve`)
