/**
 * 角色与权限的纯逻辑（不依赖 store、不依赖浏览器），便于用 node --test 直接测。
 *
 * 这里是"谁能进哪个页面"的**唯一判定处**：路由守卫、菜单过滤都调它，
 * 避免出现"菜单藏了但 URL 能进"这种口径不一致。
 */

export const ROLE_HOME = {
  SUPER_ADMIN: '/dashboard',
  MANAGER: '/dashboard',
  RECEPTION: '/members',
  COACH: '/coach-workbench',
  MEMBER: '/member-home',
  SALES: '/advisor-workbench'
}

export const ROLE_NAMES = {
  SUPER_ADMIN: '总部管理员',
  MANAGER: '店长',
  COACH: '教练',
  RECEPTION: '前台',
  MEMBER: '会员',
  SALES: '会籍顾问'
}

/** 是否持有某个权限。 */
export function hasPermission(userPermissions, code) {
  return Array.isArray(userPermissions) && userPermissions.includes(code)
}

/** 角色落地页；未知角色去无权限页。 */
export function homeForRole(role) {
  return role ? ROLE_HOME[role] || '/access-denied' : '/login'
}

/**
 * 路由准入判定。
 *
 * 规则：meta.permissions 满足其一**并且**（若声明了 meta.roles）当前角色在其中。
 * roles 这一条曾经只在菜单里生效、守卫没校验，导致声明了 roles:['SALES'] 的页面
 * 仍可被店长/前台用 URL 直接打开。
 */
export function canAccessRoute(meta, role, userPermissions) {
  const required = meta && meta.permissions
  if (Array.isArray(required) && required.length && !required.some(code => hasPermission(userPermissions, code))) {
    return false
  }
  const requiredRoles = meta && meta.roles
  if (Array.isArray(requiredRoles) && requiredRoles.length && (!role || !requiredRoles.includes(role))) {
    return false
  }
  return true
}

/**
 * 菜单项是否可见，与路由守卫同一套规则。
 * 菜单项用的是单数 permission 字段，这里统一成数组再判定。
 */
export function isMenuVisible(item, role, userPermissions) {
  const required = item && item.permissions
    ? item.permissions
    : (item && item.permission ? [item.permission] : undefined)
  return canAccessRoute({ permissions: required, roles: item && item.roles }, role, userPermissions)
}
