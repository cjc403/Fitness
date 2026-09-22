// 本地存储工具函数

export const getToken = (): string | null => {
  return localStorage.getItem('token')
}

export const setToken = (token: string): void => {
  localStorage.setItem('token', token)
}

export const removeToken = (): void => {
  localStorage.removeItem('token')
  localStorage.removeItem('refresh_token')
  localStorage.removeItem('userInfo')
}

export const getUserInfo = (): any => {
  const userInfo = localStorage.getItem('userInfo')
  if (!userInfo) return null
  try {
    return JSON.parse(userInfo)
  } catch {
    removeToken()
    return null
  }
}

export const setUserInfo = (userInfo: any): void => {
  localStorage.setItem('userInfo', JSON.stringify(userInfo))
}

export const getRefreshToken = (): string | null => {
  return localStorage.getItem('refresh_token')
}

export const setRefreshToken = (token: string): void => {
  localStorage.setItem('refresh_token', token)
}
