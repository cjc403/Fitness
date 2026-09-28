import { getToken as getTokenFromStorage, getUserInfo } from '@/utils/storage'

export const ROLE_HOME: Record<string, string> = {
  SUPER_ADMIN: '/dashboard',
  MANAGER: '/dashboard',
  RECEPTION: '/members',
  COACH: '/coach-workbench',
  MEMBER: '/member-home',
  SALES: '/advisor-workbench'
}

export const ROLE_NAMES: Record<string, string> = {
  SUPER_ADMIN: '总部管理员',
  MANAGER: '店长',
  COACH: '教练',
  RECEPTION: '前台',
  MEMBER: '会员',
  SALES: '会籍顾问'
}

/**
 * 检查是否已登录
 */
export function isAuthenticated(): boolean {
  return !!getTokenFromStorage()
}

/**
 * 检查是否有指定权限
 */
export function hasPermission(permission: string): boolean {
  const permissions = getUserInfo()?.permissions
  return Array.isArray(permissions) && permissions.includes(permission)
}

export function hasAnyPermission(permissions: string[]): boolean {
  return permissions.some(hasPermission)
}

/**
 * 检查是否有指定角色
 */
export function hasRole(role: string): boolean {
  return getUserInfo()?.role === role
}

export function homeForRole(role?: string): string {
  return role ? ROLE_HOME[role] || '/access-denied' : '/login'
}
