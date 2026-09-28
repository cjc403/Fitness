import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { getToken, getUserInfo } from '@/utils/storage'
import { homeForRole } from '@/utils/auth'

const routes: Array<RouteRecordRaw> = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/change-password',
    name: 'ChangePassword',
    component: () => import('@/views/ChangePassword.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: () => homeForRole(getUserInfo()?.role),
    meta: { requiresAuth: true },
    children: [
      {
        path: 'accounts',
        name: 'Accounts',
        component: () => import('@/views/admin/EmployeeAccounts.vue'),
        meta: { title: '员工账号', requiresAuth: true, permissions: ['ACCOUNT_MANAGE'] }
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/admin/Dashboard.vue'),
        meta: { title: '经营看板', requiresAuth: true, permissions: ['DASHBOARD_READ'] }
      },
      {
        path: 'members',
        name: 'Members',
        component: () => import('@/views/admin/MemberList.vue'),
        meta: { title: '会员管理', requiresAuth: true, permissions: ['MEMBER_READ'] }
      },
      {
        path: 'commercial',
        name: 'CommercialCenter',
        component: () => import('@/views/admin/CommercialCenter.vue'),
        meta: { title: '会籍与商业', requiresAuth: true, permissions: ['PRODUCT_READ'] }
      },
      {
        path: 'venues',
        name: 'Venues',
        component: () => import('@/views/admin/VenueList.vue'),
        meta: { title: '场馆管理', requiresAuth: true, permissions: ['VENUE_READ'] }
      },
      {
        path: 'courses',
        name: 'Courses',
        component: () => import('@/views/admin/CourseManagement.vue'),
        meta: { title: '课程与排课', requiresAuth: true, permissions: ['COURSE_READ'] }
      },
      {
        path: 'bookings',
        name: 'Bookings',
        component: () => import('@/views/admin/BookingList.vue'),
        meta: { title: '预约与签到', requiresAuth: true, permissions: ['BOOKING_READ'] }
      },
      {
        path: 'coach-workbench',
        name: 'CoachWorkbench',
        component: () => import('@/views/coach/CoachWorkbench.vue'),
        meta: { title: '教练工作台', requiresAuth: true, permissions: ['COACH_WORKBENCH'] }
      },
      {
        path: 'member-home',
        name: 'MemberHome',
        component: () => import('@/views/member/MemberHome.vue'),
        meta: { title: '会员中心', requiresAuth: true, permissions: ['MEMBER_SELF_SERVICE'] }
      },
      {
        path: 'advisor-workbench',
        name: 'AdvisorWorkbench',
        component: () => import('@/views/advisor/AdvisorWorkbench.vue'),
        meta: { title: '会籍工作台', requiresAuth: true, permissions: ['MEMBER_READ'], roles: ['SALES'] }
      },
      {
        path: 'access-denied',
        name: 'AccessDenied',
        component: () => import('@/views/AccessDenied.vue'),
        meta: { title: '无权访问', requiresAuth: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, _from, next) => {
  const token = getToken()
  
  if (to.meta.requiresAuth) {
    if (!token) {
      next({ path: '/login', query: { redirect: to.fullPath } })
    } else if (getUserInfo()?.mustChangePassword && to.path !== '/change-password') {
      next({ path: '/change-password', replace: true })
    } else if (!getUserInfo()?.mustChangePassword && to.path === '/change-password') {
      next({ path: homeForRole(getUserInfo()?.role), replace: true })
    } else {
      const required = to.meta.permissions as string[] | undefined
      const permissions = getUserInfo()?.permissions || []
      if (required?.length && !required.some(permission => permissions.includes(permission))) {
        next({ path: '/access-denied', replace: true })
      } else {
        next()
      }
    }
  } else {
    // 如果已登录，访问登录页时重定向到首页
    if ((to.path === '/login' || to.path === '/register') && token) {
      next({ path: homeForRole(getUserInfo()?.role) })
    } else {
      next()
    }
  }
})

export default router
