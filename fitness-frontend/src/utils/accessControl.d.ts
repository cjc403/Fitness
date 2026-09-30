export const ROLE_HOME: Record<string, string>
export const ROLE_NAMES: Record<string, string>
export function hasPermission(userPermissions: string[] | undefined, code: string): boolean
export function homeForRole(role?: string): string
export function canAccessRoute(
  meta: { permissions?: string[]; roles?: string[] } | undefined,
  role: string | undefined,
  userPermissions: string[] | undefined
): boolean
export function isMenuVisible(
  item: { permission?: string; permissions?: string[]; roles?: string[] },
  role: string | undefined,
  userPermissions: string[] | undefined
): boolean
