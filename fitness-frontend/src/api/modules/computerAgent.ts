import { useAuthStore } from '@/store/modules/auth'

export type ComputerTaskStatus = 'RUNNING' | 'PAUSED' | 'WAITING_CONFIRMATION' | 'WAITING_INPUT' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'
export interface ComputerTaskStep { index: number; action: string; label: string; status: string; detail?: string }
export interface ComputerArtifact { id: string; name: string; bytes: number; sha256: string; path?: string }
export interface ComputerConfirmation { id: string; method: string; path: string; summary: string; fields: { label: string; value: string }[] }
export interface ComputerTask {
  id: string; goal: string; status: ComputerTaskStatus; createdAt: string; updatedAt: string
  steps: ComputerTaskStep[]; artifacts: ComputerArtifact[]; pendingConfirmation?: ComputerConfirmation
  question?: string; error?: string; screenshot?: string
  pauseReason?: 'USER' | 'PAGE_HIDDEN' | 'ASSISTANT_CLOSED' | 'OFFLINE'
}
export interface ComputerAgentStatus { available: boolean; platform: string; scope: string; outputDirectory: string }
export interface ComputerRequestConfig { isRequestCurrent?: () => boolean; signal?: AbortSignal }
export interface ComputerResult<T> { code: number; message: string; data: T }

// Deliberately independent from the business Axios base and refresh interceptor:
// a computer write must never be redirected to an absolute API or replayed.
const base = '/local-agent/v1'
const identity = () => {
  const auth = useAuthStore()
  const user = auth.userInfo as (typeof auth.userInfo & { venueId?: number })
  return JSON.stringify([auth.sessionGeneration, auth.token, user?.id, user?.role, user?.organizationId, user?.venueId])
}
async function request<T>(method: 'GET' | 'POST', path: string, body?: unknown, config: ComputerRequestConfig = {}): Promise<ComputerResult<T>> {
  const auth = useAuthStore(), session = identity(), token = auth.token
  const current = () => identity() === session && !!useAuthStore().token && config.isRequestCurrent?.() !== false && !config.signal?.aborted
  const assertCurrent = () => { if (!current()) throw new Error('电脑任务请求已失效，请使用当前登录会话重新查看') }
  assertCurrent()
  if (!token) throw new Error('请先登录后使用电脑任务')
  if (method === 'POST' && typeof navigator !== 'undefined' && navigator.onLine === false) throw new Error('当前离线，无法提交电脑任务；恢复网络后请手动重试')
  try {
    const response = await fetch(`${base}${path}`, {
      method, headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      credentials: 'same-origin', cache: 'no-store', redirect: 'error', signal: config.signal,
      ...(body === undefined ? {} : { body: JSON.stringify(body) })
    })
    assertCurrent()
    let result: ComputerResult<T>
    try { result = await response.json() } catch { throw new Error('本机电脑执行器未返回有效结果，请检查启动窗口') }
    assertCurrent()
    if (!response.ok || result.code !== 0) throw new Error(result.message || `电脑执行器请求失败（${response.status}）`)
    if (!Object.prototype.hasOwnProperty.call(result, 'data')) throw new Error('电脑执行器结果不完整，请重新查看任务')
    return result
  } catch (error) {
    assertCurrent()
    throw error
  }
}
const taskPath = (id: string) => `/tasks/${encodeURIComponent(id)}`
export const getComputerAgentStatus = (config?: ComputerRequestConfig) => request<ComputerAgentStatus>('GET', '/status', undefined, config)
export const listComputerTasks = (config?: ComputerRequestConfig) => request<ComputerTask[]>('GET', '/tasks', undefined, config)
export const createComputerTask = (goal: string, config?: ComputerRequestConfig) => request<ComputerTask>('POST', '/tasks', { goal }, config)
export const getComputerTask = (id: string, config?: ComputerRequestConfig) => request<ComputerTask>('GET', taskPath(id), undefined, config)
export const pauseComputerTask = (id: string, config?: ComputerRequestConfig) => request<ComputerTask>('POST', `${taskPath(id)}/pause`, {}, config)
export const resumeComputerTask = (id: string, config?: ComputerRequestConfig) => request<ComputerTask>('POST', `${taskPath(id)}/resume`, {}, config)
export const stopComputerTask = (id: string, config?: ComputerRequestConfig) => request<ComputerTask>('POST', `${taskPath(id)}/stop`, {}, config)
export const confirmComputerTask = (id: string, confirmationId: string, config?: ComputerRequestConfig) => request<ComputerTask>('POST', `${taskPath(id)}/confirm`, { confirmationId }, config)
export const inputComputerTask = (id: string, message: string, config?: ComputerRequestConfig) => request<ComputerTask>('POST', `${taskPath(id)}/input`, { message }, config)
