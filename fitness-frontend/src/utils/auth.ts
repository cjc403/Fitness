import { getToken as getTokenFromStorage, getUserInfo } from '@/utils/storage'
import {
  ROLE_HOME,
  ROLE_NAMES,
  hasPermission as hasPermissionIn,
  homeForRole as homeForRoleIn
} from '@/utils/accessControl.mjs'

export { ROLE_HOME, ROLE_NAMES }

/** 检查是否已登录 */
export function isAuthenticated(): boolean {
  return !!getTokenFromStorage()
}

/** 检查是否有指定权限 */
export function hasPermission(permission: string): boolean {
  return hasPermissionIn(getUserInfo()?.permissions, permission)
}

export function hasAnyPermission(permissions: string[]): boolean {
  return permissions.some(hasPermission)
}

/** 检查是否有指定角色 */
export function hasRole(role: string): boolean {
  return getUserInfo()?.role === role
}

export function homeForRole(role?: string): string {
  return homeForRoleIn(role)
}
