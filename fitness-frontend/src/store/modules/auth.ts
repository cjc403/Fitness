import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, logout as logoutApi } from '@/api/modules/auth'
import type { LoginResponse, UserInfo } from '@/api/modules/auth'
import { getToken, setToken, removeToken, setUserInfo, getUserInfo, setRefreshToken } from '@/utils/storage'
import { useRouter } from 'vue-router'

export const useAuthStore = defineStore('auth', () => {
  const router = useRouter()
  const token = ref<string | null>(getToken())
  const userInfo = ref<UserInfo | null>(getUserInfo())

  const applySession = (session: LoginResponse) => {
    token.value = session.accessToken
    userInfo.value = session.userInfo
    setToken(session.accessToken)
    setRefreshToken(session.refreshToken)
    setUserInfo(session.userInfo)
  }

  const clearSession = () => {
    token.value = null
    userInfo.value = null
    removeToken()
  }

  // 登录
  const login = async (username: string, password: string) => {
    try {
      const response = await loginApi({ username, password })
      applySession(response.data)
      return response
    } catch (error) {
      throw error
    }
  }

  // 登出
  const logout = async () => {
    const wasLoggedIn = !!token.value
    clearSession()
    try {
      if (wasLoggedIn) await logoutApi()
    } catch (error) {
      console.error('登出失败:', error)
    } finally {
      router.push('/login')
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
    login,
    logout,
    isAuthenticated,
    markPasswordChanged,
    applySession,
    clearSession
  }
})
