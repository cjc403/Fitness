import request, { type SessionRequestConfig } from '@/utils/request'
export interface PageView<T> { records:T[]; total:number; page:number; size:number }
export type Goal = 'FAT_LOSS'|'MUSCLE_GAIN'|'POSTURE'|'ENDURANCE'|'GENERAL'
export interface ExerciseInput { name:string; mode:'STRENGTH'|'DURATION'; sets:number|null; reps:number|null; weightKg:number|null; minutes:number|null; note:string|null }
export interface ExerciseView extends ExerciseInput { id:number; position:number }
export interface PlanSummary { id:number; memberId:number; memberName:string; memberNo:string; coachId:number; coachName:string; venueId:number; venueName:string; title:string; goal:Goal; startDate:string; endDate:string; note:string|null; status:'DRAFT'|'ACTIVE'|'ARCHIVED'; version:number; exerciseCount:number; sessionCount:number; lastSessionOn:string|null; canEdit:boolean; canPublish:boolean; canArchive:boolean; canRecord:boolean; canViewBody:boolean }
export interface SessionExercise extends ExerciseInput { exerciseId:number; position:number; status:'DONE'|'SKIPPED'; targetSets:number|null; targetReps:number|null; targetWeightKg:number|null; targetMinutes:number|null }
export interface SessionView { id:number; planId:number; version:number; performedOn:string; durationMinutes:number; effort:number|null; note:string|null; bookingId:number|null; courseName:string|null; status:'COMPLETED'|'VOIDED'; voidReason:string|null; coachName:string; createdAt:string; feedbackRating:number|null; feedbackContent:string|null; feedbackAt:string|null; canVoid:boolean; canFeedback:boolean; exercises:SessionExercise[] }
export interface EventView { id:number; type:string; actorName:string; createdAt:string; reason:string|null; sessionId:number|null }
export interface Detail { plan:PlanSummary; exercises:ExerciseView[]; sessions:PageView<SessionView>; events:EventView[]; eventTotal:number }
export interface Student { id:number; realName:string; memberNo:string; venueId:number; trainingGoal:string|null; trainingGoalNote:string|null }
export interface Workbench { summary:{draftPlans:number;activePlans:number;archivedPlans:number;sessionsThisMonth:number}; plans:PageView<PlanSummary>; venues:{id:number;name:string}[] }
export interface BookingOption { id:number; courseName:string; startTime:string }
export interface CreateRequest { memberId:number; title:string; goal:Goal; startDate:string; endDate:string; note:string|null; exercises:ExerciseInput[]; idempotencyKey:string }
export type DraftRequest = Omit<CreateRequest,'memberId'> & {expectedVersion:number}
export interface TransitionRequest { expectedVersion:number; idempotencyKey:string; reason?:string }
export interface RecordRequest extends TransitionRequest { performedOn:string; durationMinutes:number; effort:number|null; note:string|null; bookingId:number|null; exercises:{exerciseId:number;status:'DONE'|'SKIPPED';sets:number|null;reps:number|null;weightKg:number|null;minutes:number|null;note:string|null}[] }
export interface FeedbackRequest extends TransitionRequest { rating:number; content:string|null }
const base='/v1/training-plans', self='/v1/member/training-plans'
export const getTrainingStudents=(params:{keyword?:string;page:number;size:number},config?:SessionRequestConfig)=>request.get<PageView<Student>>(`${base}/students`,{...config,params})
export const getTrainingWorkbench=(params:{venueId?:number;status:string;keyword?:string;page:number;size:number},config?:SessionRequestConfig)=>request.get<Workbench>(`${base}/workbench`,{...config,params})
export const getMemberTrainingPlans=(params:{status:string;page:number;size:number},config?:SessionRequestConfig)=>request.get<Workbench>(self,{...config,params})
export const getTrainingDetail=(id:number,params:{sessionPage:number;sessionSize:number},memberMode:boolean,config?:SessionRequestConfig)=>request.get<Detail>(`${memberMode?self:base}/${id}`,{...config,params})
export const getTrainingBookings=(id:number,performedOn:string,config?:SessionRequestConfig)=>request.get<BookingOption[]>(`${base}/${id}/booking-options`,{...config,params:{performedOn}})
export const createTrainingPlan=(body:CreateRequest,config?:SessionRequestConfig)=>request.post<Detail>(base,body,config)
export const updateTrainingDraft=(id:number,body:DraftRequest,config?:SessionRequestConfig)=>request.post<Detail>(`${base}/${id}/draft`,body,config)
export const publishTrainingPlan=(id:number,body:TransitionRequest,config?:SessionRequestConfig)=>request.post<Detail>(`${base}/${id}/publish`,body,config)
export const archiveTrainingPlan=(id:number,body:TransitionRequest,config?:SessionRequestConfig)=>request.post<Detail>(`${base}/${id}/archive`,body,config)
export const recordTrainingSession=(id:number,body:RecordRequest,config?:SessionRequestConfig)=>request.post<Detail>(`${base}/${id}/sessions`,body,config)
export const voidTrainingSession=(id:number,sessionId:number,body:TransitionRequest,config?:SessionRequestConfig)=>request.post<Detail>(`${base}/${id}/sessions/${sessionId}/void`,body,config)
export const feedbackTrainingSession=(id:number,sessionId:number,body:FeedbackRequest,config?:SessionRequestConfig)=>request.post<Detail>(`${self}/${id}/sessions/${sessionId}/feedback`,body,config)
