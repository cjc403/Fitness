import request from '@/utils/request'
export interface AssistantReply { answer: string; suggestions: string[]; source: string; aiEnabled: boolean }
export interface AssistantStatus { configured: boolean; model: string; fallbackAvailable: boolean }
export interface AssistantTurn { role: 'user' | 'assistant'; content: string }
export const chat = (message: string, history: AssistantTurn[] = []) =>
  request.post<AssistantReply>('/v1/admin/assistant/chat', { message, history })
export const getAssistantStatus = () => request.get<AssistantStatus>('/v1/admin/assistant/status')
