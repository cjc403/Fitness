import axios, { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { LoginResponse } from '@/api/modules/auth'
import { useAuthStore } from '@/store/modules/auth'
import { getRefreshToken } from '@/utils/storage'
import { parseBlobPayload } from '@/utils/financeUi.mjs'

interface Result<T = any> {
  code: number
  message: string
  data: T
  timestamp?: string
}

export interface SessionRequestConfig extends AxiosRequestConfig {
  skipAuthRefresh?: boolean
  // Opt-in page/venue guard. Invalidated reads and writes must not publish data or global notices.
  isRequestCurrent?: () => boolean
}

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
  _sessionGeneration?: number
  skipAuthRefresh?: boolean
  isRequestCurrent?: () => boolean
}

export class BusinessError extends Error {
  readonly code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'BusinessError'
    this.code = code
  }
}

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')

const service: AxiosInstance = axios.create({
  baseURL: apiBaseUrl,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

let refreshPending: { generation: number; promise: Promise<LoginResponse> } | null = null
let lastForbiddenNoticeAt = 0

const isAuthenticationRequest = (url?: string) =>
  !!url && ['/v1/auth/login', '/v1/auth/refresh', '/v1/auth/register/member'].some(path => url.includes(path))

/**
 * 后端对字段级校验失败会返回 { message: '参数校验失败', data: { 字段: 原因 } }。
 * 把明细拼到提示里，避免用户只看到"参数校验失败"却不知道是哪个字段。
 */
const describeFieldErrors = (payload: any): string => {
  const details = payload?.data
  if (!details || typeof details !== 'object' || details instanceof Blob) return ''
  const entries = Object.entries(details as Record<string, unknown>)
    .filter(([, value]) => typeof value === 'string')
    .slice(0, 3)
    .map(([field, reason]) => `${field}：${reason}`)
  return entries.length ? `（${entries.join('；')}）` : ''
}

const isCurrentSession = (generation?: number) => useAuthStore().sessionGeneration === generation
const isRequestCurrent = (config?: { isRequestCurrent?: () => boolean }) => !config?.isRequestCurrent || config.isRequestCurrent()

const refreshSession = (generation: number): Promise<LoginResponse> => {
  if (!isCurrentSession(generation)) return Promise.reject(new Error('登录会话已变更'))
  if (refreshPending && refreshPending.generation === generation) return refreshPending.promise

  const refreshToken = getRefreshToken()
  if (!refreshToken) return Promise.reject(new Error('没有可用的刷新令牌'))

  // Use the base axios client here so a failed refresh never re-enters this interceptor.
  const pending = { generation, promise: null as unknown as Promise<LoginResponse> }
  pending.promise = axios
    .post<Result<LoginResponse>>(`${apiBaseUrl}/v1/auth/refresh`, { refreshToken }, {
      timeout: 10000,
      headers: { 'Content-Type': 'application/json' }
    })
    .then(response => {
      const result = response.data
      if (result.code !== 0 || !result.data?.accessToken || !result.data?.refreshToken) {
        throw new Error(result.message || '刷新登录状态失败')
      }
      const authStore = useAuthStore()
      if (!isCurrentSession(generation)) throw new Error('登录会话已变更')
      authStore.applySession(result.data, generation)
      return result.data
    })
    .finally(() => {
      if (refreshPending === pending) refreshPending = null
    })

  refreshPending = pending
  return pending.promise
}

const endExpiredSession = (generation?: number) => {
  if (!isCurrentSession(generation)) return
  const authStore = useAuthStore()
  authStore.clearSession()
  const endedGeneration = authStore.sessionGeneration
  ElMessage.closeAll()
  ElMessage.warning('登录状态已过期，请重新登录')

  if (window.location.pathname !== '/login') {
    const redirect = `${window.location.pathname}${window.location.search}${window.location.hash}`
    const loginUrl = `/login?reason=session-expired&redirect=${encodeURIComponent(redirect)}`
    window.setTimeout(() => {
      if (isCurrentSession(endedGeneration) && !authStore.token) window.location.replace(loginUrl)
    }, 80)
  }
}

service.interceptors.request.use(
  (config: RetryableRequestConfig) => {
    if (!isRequestCurrent(config)) return Promise.reject(new axios.CanceledError('请求范围已变更', config))
    const authStore = useAuthStore()
    if (config._retry && !isCurrentSession(config._sessionGeneration)) {
      return Promise.reject(new Error('登录会话已变更'))
    }
    config._sessionGeneration = authStore.sessionGeneration
    if (!config.skipAuthRefresh && authStore.token) {
      config.headers.Authorization = `Bearer ${authStore.token}`
    }
    return config
  },
  error => Promise.reject(error)
)

service.interceptors.response.use(
  (response: AxiosResponse<Result>): any => {
    const config = response.config as RetryableRequestConfig
    if (!isRequestCurrent(config)) return Promise.reject(new axios.CanceledError('请求范围已变更', config))
    if (!config.skipAuthRefresh && !isAuthenticationRequest(config.url) && !isCurrentSession(config._sessionGeneration)) {
      return Promise.reject(new Error('登录会话已变更'))
    }
    if (response.config.responseType === 'blob') {
      const blob = response.data as unknown
      if (blob instanceof Blob) {
        const contentType = String(response.headers['content-type'] || '')
        // 约定：导出接口成功时返回文件（CSV / Excel / PDF…），失败时返回 application/json 的业务错误。
        // 所以这里用"是不是 JSON"来判断，而不是枚举文件类型白名单——
        // 之前只写了 text/csv 与 octet-stream，接口换成 Excel（spreadsheetml）后就被误判成"没有返回文件"。
        const isBusinessError = contentType.includes('json')
        if (!isBusinessError) return blob
        return blob.text().then(text => {
          if (!isRequestCurrent(config) || (!config.skipAuthRefresh && !isCurrentSession(config._sessionGeneration))) return Promise.reject(new axios.CanceledError('请求范围已变更', config))
          const failure = parseBlobPayload(text)
          if (failure) {
            ElMessage.error(failure.message)
            return Promise.reject(new BusinessError(failure.code, failure.message))
          }
          ElMessage.error('导出失败，服务器返回的不是文件')
          return Promise.reject(new BusinessError(-1, '导出失败，服务器返回的不是文件'))
        })
      }
    }
    const result = response.data
    if (result.code === 0) return result

    ElMessage.error(result.message || '请求失败')
    return Promise.reject(new BusinessError(result.code, result.message || '请求失败'))
  },
  async error => {
    const status = error.response?.status
    const data = error.response?.data
    const originalRequest = error.config as RetryableRequestConfig | undefined
    if (axios.isCancel(error) || !isRequestCurrent(originalRequest)) return Promise.reject(error)
    const blobFailure = data instanceof Blob ? parseBlobPayload(await data.text()) : null
    if (!isRequestCurrent(originalRequest)) return Promise.reject(error)
    const errorMessage = blobFailure?.message || data?.message
    const fieldDetail = status === 400 && !blobFailure ? describeFieldErrors(data) : ''

    if (originalRequest?.skipAuthRefresh) return Promise.reject(error)
    if (originalRequest && !isAuthenticationRequest(originalRequest.url) && !isCurrentSession(originalRequest._sessionGeneration)) {
      return Promise.reject(error)
    }

    if (status === 401 && originalRequest && !isAuthenticationRequest(originalRequest.url)) {
      if (!originalRequest._retry && getRefreshToken()) {
        originalRequest._retry = true
        try {
          const session = await refreshSession(originalRequest._sessionGeneration!)
          if (!isCurrentSession(originalRequest._sessionGeneration) || !isRequestCurrent(originalRequest)) return Promise.reject(error)
          originalRequest.headers.Authorization = `Bearer ${session.accessToken}`
          return service(originalRequest)
        } catch (refreshError) {
          if (!isCurrentSession(originalRequest._sessionGeneration) || !isRequestCurrent(originalRequest)) return Promise.reject(error)
          console.warn('自动刷新登录状态失败')
        }
      }
      endExpiredSession(originalRequest._sessionGeneration)
      return Promise.reject(error)
    }

    if (status === 401) {
      ElMessage.error(errorMessage || '认证失败，请重新登录')
    } else if (status === 403) {
      // A page can issue many requests in parallel. Show one useful notice, not a toast storm.
      const now = Date.now()
      if (now - lastForbiddenNoticeAt > 2000) {
        lastForbiddenNoticeAt = now
        ElMessage.error(errorMessage || '当前账号没有执行此操作的权限')
      }
    } else if (status === 404) {
      ElMessage.error('请求的资源不存在')
    } else if (status === 500) {
      ElMessage.error('服务器错误')
    } else if (error.response) {
      ElMessage.error((errorMessage || '请求失败') + fieldDetail)
    } else {
      ElMessage.error('网络错误，请检查网络连接')
    }

    return Promise.reject(blobFailure ? new BusinessError(blobFailure.code, blobFailure.message) : error)
  }
)

export default service
