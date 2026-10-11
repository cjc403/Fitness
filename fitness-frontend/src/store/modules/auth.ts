import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, logout as logoutApi } from '@/api/modules/auth'
import type { LoginResponse, UserInfo } from '@/api/modules/auth'
import { getToken, setToken, removeToken, setUserInfo, getUserInfo, setRefreshToken, getRefreshToken } from '@/utils/storage'
import { useRouter } from 'vue-router'

export const useAuthStore = defineStore('auth', () => {
  const router = useRouter()
  const token = ref<string | null>(getToken())
  const userInfo = ref<UserInfo | null>(getUserInfo())
  const sessionGeneration = ref(0)

  const applySession = (session: LoginResponse, expectedGeneration?: number) => {
    if (expectedGeneration !== undefined && expectedGeneration !== sessionGeneration.value) return false
    if (expectedGeneration === undefined) sessionGeneration.value++
    token.value = session.accessToken
    userInfo.value = session.userInfo
    setToken(session.accessToken)
    setRefreshToken(session.refreshToken)
    setUserInfo(session.userInfo)
    return true
  }

  const clearSession = () => {
    sessionGeneration.value++
    token.value = null
    userInfo.value = null
    removeToken()
  }

  // 登录
  const login = async (username: string, password: string) => {
    const generation = ++sessionGeneration.value
    try {
      const response = await loginApi({ username, password })
      if (!applySession(response.data, generation)) throw new Error('登录请求已失效')
      return response
    } catch (error) {
      throw error
    }
  }

  // 登出
  const logout = async () => {
    const logoutToken = token.value
    const logoutRefreshToken = getRefreshToken()
    clearSession()
    const generation = sessionGeneration.value
    try {
      if (logoutToken || logoutRefreshToken) await logoutApi(logoutToken, logoutRefreshToken)
    } catch (error) {
      console.error('登出请求未完成')
    } finally {
      if (sessionGeneration.value === generation && !token.value) router.push('/login')
    }
  }

  // 检查是否已登录
  const isAuthenticated = (): boolean => {
    return !!token.value
  }

  const markPasswordChanged = () => {
    if (!userInfo.value) return
    userInfo.value = { ...userInfo.value, mustChangePassword: false }
    setUserInfo(userInfo.value)
  }

  return {
    token,
    userInfo,
    sessionGeneration,
    login,
    logout,
    isAuthenticated,
    markPasswordChanged,
    applySession,
    clearSession
  }
})
