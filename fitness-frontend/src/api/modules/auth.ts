import request from '@/utils/request'

export interface LoginRequest {
  username: string
  password: string
}

export interface UserInfo {
  id: number
  username: string
  realName: string
  role: string
  roleName: string
  permissions: string[]
  organizationId: number
  organizationName: string
  mustChangePassword: boolean
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  userInfo: UserInfo
}

export const login = (data: LoginRequest) => {
  return request.post<LoginResponse>('/v1/auth/login', data)
}

export const logout = () => {
  return request.post('/v1/auth/logout')
}

export const refreshToken = (refreshToken: string) => {
  return request.post<LoginResponse>('/v1/auth/refresh', { refreshToken })
}

export const changePassword = (currentPassword: string, newPassword: string) =>
  request.post('/v1/auth/change-password', { currentPassword, newPassword })
