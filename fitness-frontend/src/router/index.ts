import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { getToken, getUserInfo } from '@/utils/storage'
import { homeForRole } from '@/utils/auth'
import { canAccessRoute } from '@/utils/accessControl.mjs'

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
        path: 'analytics',
        name: 'Analytics',
        component: () => import('@/views/admin/Analytics.vue'),
        meta: { title: '经营分析', requiresAuth: true, permissions: ['DASHBOARD_READ'] }
      },
      {
        path: 'messages',
        name: 'Messages',
        component: () => import('@/views/admin/Messages.vue'),
        meta: { title: '消息与渠道', requiresAuth: true, permissions: ['MEMBER_READ'] }
      },
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/admin/Chat.vue'),
        // 所有登录角色都能用；会员走会员中心的「消息」页签，这里用 roles 只把菜单给员工
        meta: { title: '站内消息', requiresAuth: true }
      },
      {
        path: 'members',
        name: 'Members',
        component: () => import('@/views/admin/MemberList.vue'),
        meta: { title: '会员管理', requiresAuth: true, permissions: ['MEMBER_READ'] }
      },
      {
        path: 'training-plans',
        name: 'TrainingPlanCenter',
        component: () => import('@/views/admin/TrainingPlanCenter.vue'),
        meta: { title: '训练计划', requiresAuth: true, permissions: ['TRAINING_PLAN_READ'], roles: ['COACH', 'MANAGER', 'SUPER_ADMIN'] }
      },
      {
        path: 'cash-shifts',
        name: 'CashShiftCenter',
        component: () => import('@/views/admin/CashShiftCenter.vue'),
        meta: { title: '交班与日结', requiresAuth: true, permissions: ['CASH_SHIFT_READ'], roles: ['RECEPTION', 'MANAGER', 'SUPER_ADMIN'] }
      },
      {
        path: 'leads',
        name: 'LeadCenter',
        component: () => import('@/views/admin/LeadCenter.vue'),
        meta: { title: '潜客与体验课', requiresAuth: true, permissions: ['LEAD_READ'], roles: ['SALES', 'MANAGER', 'SUPER_ADMIN'] }
      },
      {
        path: 'retention',
        name: 'RetentionCenter',
        component: () => import('@/views/admin/RetentionCenter.vue'),
        meta: { title: '续费与回访', requiresAuth: true, permissions: ['MEMBER_READ'], roles: ['SALES', 'MANAGER', 'SUPER_ADMIN'] }
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
        path: 'schedule-week',
        name: 'ScheduleWeek',
        component: () => import('@/views/admin/ScheduleWeek.vue'),
        meta: { title: '周课表', requiresAuth: true, permissions: ['COURSE_READ'] }
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
      // 准入判定与菜单、权限常量同源（见 utils/accessControl.mjs），
      // 避免再出现"菜单藏了但 URL 能进"的口径不一致
      const user = getUserInfo()
      if (!canAccessRoute(to.meta, user?.role, user?.permissions)) {
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
