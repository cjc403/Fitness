import request, { type SessionRequestConfig } from '@/utils/request'
export interface AssistantReply { answer: string; suggestions: string[]; source: string; aiEnabled: boolean }
export interface AssistantStatus { configured: boolean; model: string; fallbackAvailable: boolean }
export interface AssistantTurn { role: 'user' | 'assistant'; content: string }
export const legacyChat = (message: string, history: AssistantTurn[] = []) =>
  request.post<AssistantReply>('/v1/admin/assistant/chat', { message, history })
export const getAssistantStatus = () => request.get<AssistantStatus>('/v1/admin/assistant/status')

export interface AgentSchema {
  type?: string; description?: string; format?: string; enum?: (string | number)[]
  minimum?: number; maximum?: number; maxLength?: number; minItems?: number; maxItems?: number
  required?: string[]; properties?: Record<string, AgentSchema>; items?: AgentSchema; default?: unknown
}
export interface AgentTool { name: string; label: string; description: string; write: boolean; parameters: AgentSchema }
export interface ToolResult { tool: string; label: string; data: unknown; source: string; asOf: string; truncated: boolean }
export interface AgentAction {
  id: string; tool: string; title: string; summary: string; parameters: Record<string, unknown>
  status: 'PENDING' | 'SUCCEEDED' | 'CANCELLED' | 'EXPIRED' | 'FAILED'
  createdAt: string; expiresAt: string; result?: unknown; error?: string
}
export interface AgentProfile {
  role: string; roleName: string; greeting: string; suggestions: string[]; tools: AgentTool[]
  model: { configured: boolean; model?: string; toolCalling: boolean; state: string }
}
export interface AgentReply { answer: string; suggestions: string[]; source: string; mode: string; results: ToolResult[]; actions: AgentAction[]; traceId: string }
const base = '/v1/agent'
export const getAgentProfile = (config?: SessionRequestConfig) => request.get<AgentProfile>(`${base}/profile`, config)
export const chat = (message: string, history: AssistantTurn[] = [], config?: SessionRequestConfig) => request.post<AgentReply>(`${base}/chat`, { message, history }, { timeout: 90000, ...config })
export const queryAgentTool = (tool: string, args: Record<string, unknown>, config?: SessionRequestConfig) => request.post<ToolResult>(`${base}/query`, { tool, arguments: args }, config)
export const previewAgentAction = (tool: string, args: Record<string, unknown>, config?: SessionRequestConfig) => request.post<AgentAction>(`${base}/actions/preview`, { tool, arguments: args }, config)
export const listAgentActions = (config?: SessionRequestConfig) => request.get<AgentAction[]>(`${base}/actions`, config)
export const confirmAgentAction = (id: string, config?: SessionRequestConfig) => request.post<AgentAction>(`${base}/actions/${encodeURIComponent(id)}/confirm`, {}, config)
export const cancelAgentAction = (id: string, config?: SessionRequestConfig) => request.post<AgentAction>(`${base}/actions/${encodeURIComponent(id)}/cancel`, {}, config)
