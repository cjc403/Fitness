import test from 'node:test'
import assert from 'node:assert/strict'
import {
  ROLE_HOME, ROLE_NAMES, hasPermission, homeForRole, canAccessRoute, isMenuVisible
} from '../src/utils/accessControl.mjs'

test('every role has a home and a display name', () => {
  const roles = ['SUPER_ADMIN', 'MANAGER', 'COACH', 'RECEPTION', 'MEMBER', 'SALES']
  for (const role of roles) {
    assert.ok(ROLE_HOME[role], role + ' 缺少落地页')
    assert.ok(ROLE_NAMES[role], role + ' 缺少中文名')
  }
  assert.equal(ROLE_HOME.SALES, '/advisor-workbench')
})

test('homeForRole falls back for unknown or missing roles', () => {
  assert.equal(homeForRole('COACH'), '/coach-workbench')
  assert.equal(homeForRole('NOT_A_ROLE'), '/access-denied')
  assert.equal(homeForRole(undefined), '/login')
})

test('hasPermission is false for missing or malformed permission lists', () => {
  assert.equal(hasPermission(['MEMBER_READ'], 'MEMBER_READ'), true)
  assert.equal(hasPermission(['MEMBER_READ'], 'FINANCE_READ'), false)
  assert.equal(hasPermission(undefined, 'MEMBER_READ'), false)
  assert.equal(hasPermission('MEMBER_READ', 'MEMBER_READ'), false)
})

test('canAccessRoute requires one of the declared permissions', () => {
  const meta = { permissions: ['DASHBOARD_READ'] }
  assert.equal(canAccessRoute(meta, 'MANAGER', ['DASHBOARD_READ', 'MEMBER_READ']), true)
  assert.equal(canAccessRoute(meta, 'RECEPTION', ['MEMBER_READ']), false)
})

test('canAccessRoute also enforces declared roles, not just permissions', () => {
  // 会籍工作台：有 MEMBER_READ 但角色不是 SALES 时，URL 直接访问也必须被拦
  const meta = { permissions: ['MEMBER_READ'], roles: ['SALES'] }
  assert.equal(canAccessRoute(meta, 'SALES', ['MEMBER_READ']), true)
  assert.equal(canAccessRoute(meta, 'MANAGER', ['MEMBER_READ']), false)
  assert.equal(canAccessRoute(meta, 'RECEPTION', ['MEMBER_READ']), false)
  // 角色缺失（比如没登录信息）同样拒绝
  assert.equal(canAccessRoute(meta, undefined, ['MEMBER_READ']), false)
})

test('routes without declared requirements stay open', () => {
  assert.equal(canAccessRoute({}, 'MEMBER', []), true)
  assert.equal(canAccessRoute(undefined, undefined, undefined), true)
})

test('menu visibility follows the same rule as the route guard', () => {
  const advisorMenu = { permission: 'MEMBER_READ', roles: ['SALES'] }
  assert.equal(isMenuVisible(advisorMenu, 'SALES', ['MEMBER_READ']), true)
  assert.equal(isMenuVisible(advisorMenu, 'MANAGER', ['MEMBER_READ']), false)
  const courseMenu = { permission: 'COURSE_READ' }
  assert.equal(isMenuVisible(courseMenu, 'COACH', ['COURSE_READ', 'BOOKING_READ']), true)
  assert.equal(isMenuVisible(courseMenu, 'MEMBER', ['MEMBER_SELF_SERVICE']), false)
})
