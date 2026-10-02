<template>
  <el-container class="layout-container">
    <el-aside class="aside" :width="collapsed ? '76px' : '232px'">
      <div class="brand"><div class="brand-mark">F</div><div v-if="!collapsed"><strong>FitChain</strong><small>连锁健身管理</small></div></div>
      <div v-if="!collapsed" class="menu-label">运营中心</div>
      <el-menu :default-active="activeMenu" router class="menu" :collapse="collapsed">
        <el-menu-item v-for="item in visibleMenus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon><template #title>{{ item.label }}</template>
        </el-menu-item>
      </el-menu>
      <div class="aside-bottom" @click="collapsed=!collapsed"><el-icon><Fold v-if="!collapsed"/><Expand v-else/></el-icon><span v-if="!collapsed">收起导航</span></div>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-left"><div><span>{{ userInfo?.role === 'SUPER_ADMIN' ? '总部视角' : '当前场馆' }}</span><strong>{{ userInfo?.organizationName || 'FitChain 旗舰中心' }}</strong></div><el-tag size="small" type="success" effect="light">营业中</el-tag></div>
        <div class="header-right"><el-button text circle><el-icon><Bell /></el-icon></el-button>
        <el-dropdown @command="handleCommand">
          <span class="user-info"><span class="avatar">{{ (userInfo?.realName || '管').slice(0,1) }}</span><span class="user-name">{{ userInfo?.realName || userInfo?.username }}<small>{{ roleName }}</small></span><el-icon><ArrowDown /></el-icon></span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
    <AssistantPanel v-if="canUseAssistant" />
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/store/modules/auth'
import { ElMessage } from 'element-plus'
import { ArrowDown, Bell, ChatDotRound, Collection, CreditCard, Expand, Fold, Odometer, OfficeBuilding, Tickets, TrendCharts, User, UserFilled } from '@element-plus/icons-vue'
import AssistantPanel from '@/components/AssistantPanel.vue'
import { ROLE_NAMES } from '@/utils/auth'
import { isMenuVisible } from '@/utils/accessControl.mjs'

const route = useRoute()
const authStore = useAuthStore()
const collapsed = ref(window.innerWidth <= 900)
const syncCollapsed = () => { if (window.innerWidth <= 720) collapsed.value = true }
onMounted(() => window.addEventListener('resize', syncCollapsed))
onUnmounted(() => window.removeEventListener('resize', syncCollapsed))

const activeMenu = computed(() => route.path)
const userInfo = computed(() => authStore.userInfo)
const menuItems = [
  { path: '/dashboard', label: '经营看板', icon: Odometer, permission: 'DASHBOARD_READ' },
  { path: '/analytics', label: '经营分析', icon: TrendCharts, permission: 'DASHBOARD_READ' },
  { path: '/messages', label: '消息与渠道', icon: Bell, permission: 'MEMBER_READ' },
  // 站内消息对所有登录角色开放；会员用会员中心的「消息」页签，所以这里用 roles 只给员工看菜单
  { path: '/chat', label: '站内消息', icon: ChatDotRound, roles: ['SUPER_ADMIN', 'MANAGER', 'COACH', 'RECEPTION', 'SALES'] },
  { path: '/accounts', label: '员工账号', icon: UserFilled, permission: 'ACCOUNT_MANAGE' },
  { path: '/members', label: '会员管理', icon: User, permission: 'MEMBER_READ' },
  { path: '/commercial', label: '会籍与商业', icon: CreditCard, permission: 'PRODUCT_READ' },
  { path: '/courses', label: '课程与排课', icon: Collection, permission: 'COURSE_READ' },
  { path: '/schedule-week', label: '周课表', icon: Tickets, permission: 'COURSE_READ' },
  { path: '/bookings', label: '预约与签到', icon: Tickets, permission: 'BOOKING_READ' },
  { path: '/venues', label: '场馆管理', icon: OfficeBuilding, permission: 'VENUE_READ' },
  { path: '/coach-workbench', label: '教练工作台', icon: Odometer, permission: 'COACH_WORKBENCH' },
  { path: '/advisor-workbench', label: '会籍工作台', icon: User, permission: 'MEMBER_READ', roles: ['SALES'] },
  { path: '/member-home', label: '会员中心', icon: User, permission: 'MEMBER_SELF_SERVICE' }
]
const visibleMenus = computed(() => {
  const permissions = userInfo.value?.permissions || []
  // 与路由守卫同源（utils/accessControl.mjs），避免"菜单藏了但 URL 能进"
  const role = userInfo.value?.role
  return menuItems.filter(item => isMenuVisible(item, role, permissions))
})
const roleName = computed(() => userInfo.value?.roleName || ROLE_NAMES[userInfo.value?.role || ''] || '系统用户')
const canUseAssistant = computed(() => userInfo.value?.permissions?.includes('ASSISTANT_USE'))

const handleCommand = (command: string) => {
  if (command === 'logout') {
    authStore.logout()
    ElMessage.success('已退出登录')
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh; background: #f5f7fb;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 72px; background: rgba(255,255,255,.94); border-bottom: 1px solid #e8edf5; padding: 0 30px;
}
.header-left{display:flex;align-items:center;gap:12px}.header-left div span,.header-left div strong{display:block}.header-left div span{color:#98a1b1;font-size:11px}.header-left div strong{margin-top:3px;color:#273248;font-size:14px}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 9px; color:#374258; outline:none;
}
.avatar{display:grid;place-items:center;width:36px;height:36px;border-radius:11px;background:linear-gradient(135deg,#2868ff,#72a1ff);color:#fff;font-weight:800}.user-name{font-size:13px;font-weight:700}.user-name small{display:block;margin-top:2px;color:#9aa3b2;font-size:10px;font-weight:400}.aside{position:relative;background:#111a2e;border-right:none;transition:width .2s;overflow:hidden}.brand{height:82px;display:flex;align-items:center;gap:12px;padding:0 18px;color:#fff}.brand-mark{flex:none;display:grid;place-items:center;width:40px;height:40px;border-radius:12px;background:linear-gradient(135deg,#3877ff,#75a4ff);font-size:21px;font-weight:900}.brand strong,.brand small{display:block;white-space:nowrap}.brand strong{font-size:18px;letter-spacing:.3px}.brand small{margin-top:3px;color:#7f8aa2;font-size:10px}.menu-label{padding:15px 23px 8px;color:#64718b;font-size:10px;letter-spacing:1.4px}.menu{border:0;background:transparent}.menu :deep(.el-menu-item){height:49px;margin:5px 11px;border-radius:11px;color:#929db2}.menu :deep(.el-menu-item:hover){background:#192641;color:#fff}.menu :deep(.el-menu-item.is-active){background:linear-gradient(90deg,#2768ff,#3b78f7);color:#fff;box-shadow:0 8px 20px rgba(39,104,255,.25)}.aside-bottom{position:absolute;left:14px;right:14px;bottom:16px;display:flex;align-items:center;gap:12px;padding:12px;color:#77839a;font-size:12px;cursor:pointer}.header-right{gap:8px}.main{background:#f5f7fb;padding:28px 32px;overflow:auto}@media(max-width:720px){.aside{width:76px!important}.brand>div:last-child,.menu-label,.aside-bottom span,.menu :deep(.el-menu-item span){display:none}.main{padding:20px 16px}.header{padding:0 16px}.user-name{display:none}}

/* ===== 侧边栏动漫风点缀（与进入界面同一套冰蓝语言；纯装饰，不影响功能）===== */
/* 选中项：品牌蓝渐变 + 左侧发光竖条 */
:deep(.el-menu-item.is-active){
  background:linear-gradient(90deg,rgba(64,132,224,.32),rgba(64,132,224,.06)) !important;
  box-shadow:inset 3px 0 0 #6aa8ff, 0 0 18px rgba(90,150,255,.22);
}
:deep(.el-menu-item.is-active)::after{
  content:"";position:absolute;left:0;top:18%;bottom:18%;width:3px;border-radius:0 3px 3px 0;
  background:linear-gradient(180deg,#8fc2ff,#3f80e0);
  box-shadow:0 0 10px rgba(120,180,255,.75);
}
:deep(.el-menu-item){position:relative}
/* 底部区域：极淡网点纹（只在留白处） */
.aside-bottom{position:relative;overflow:hidden}
.aside-bottom::before{
  content:"";position:absolute;inset:-20px;pointer-events:none;
  background-image:radial-gradient(rgba(190,222,255,.6) 1px,transparent 1.5px);
  background-size:6px 6px;opacity:.12;
}
.aside-bottom>*{position:relative}
</style>
