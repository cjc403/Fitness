import { homeForRole, isMenuVisible } from './accessControl.mjs'

const priorities = {
  SUPER_ADMIN: ['/dashboard', '/analytics', '/venues'],
  MANAGER: ['/dashboard', '/members', '/cash-shifts'],
  RECEPTION: ['/members', '/bookings', '/cash-shifts'],
  COACH: ['/coach-workbench', '/training-plans', '/schedule-week'],
  SALES: ['/advisor-workbench', '/leads', '/retention'],
  MEMBER: ['/member-home']
}

export function mobileNavigation(items, role, permissions) {
  const visible = items.filter(item => isMenuVisible(item, role, permissions))
  const paths = priorities[role] || [homeForRole(role)]
  const primary = paths.map(path => visible.find(item => item.path === path)).filter(Boolean)
  return { primary, more: visible.filter(item => !primary.includes(item)) }
}
