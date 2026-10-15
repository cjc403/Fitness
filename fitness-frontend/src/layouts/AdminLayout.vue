<template>
  <el-container class="layout-container wb-workspace" :class="`wb-motion--${motionState}`" :data-role="userInfo?.role">
    <el-aside class="aside" :width="collapsed ? '76px' : '232px'">
      <div class="brand"><div class="brand-mark">F</div><div v-if="!collapsed"><strong>FitChain</strong><small>连锁健身管理</small></div></div>
      <div v-if="!collapsed" class="menu-label">运营中心</div>
      <div class="navigation-scroll"><el-menu :default-active="activeMenu" router class="menu" :collapse="collapsed">
        <el-menu-item v-for="item in visibleMenus" :key="item.path" :index="item.path" :aria-label="item.label">
          <el-icon><component :is="item.icon" /></el-icon><template #title>{{ item.label }}</template>
        </el-menu-item>
      </el-menu></div>
      <button class="aside-bottom" type="button" :aria-label="collapsed ? '展开导航' : '收起导航'" :aria-expanded="!collapsed" @click="collapsed=!collapsed"><el-icon><Fold v-if="!collapsed"/><Expand v-else/></el-icon><span v-if="!collapsed">收起导航</span></button>
    </el-aside>
    <el-container class="workspace-body">
      <el-header class="header">
        <div class="header-left"><div><span>{{ userInfo?.role === 'SUPER_ADMIN' ? '总部视角' : '当前场馆' }}</span><strong>{{ userInfo?.organizationName || 'FitChain 旗舰中心' }}</strong></div><el-tag size="small" type="success" effect="light">营业中</el-tag></div>
        <div class="header-right"><el-tooltip :content="systemReduced ? '系统已开启减少动态效果，工作台优先遵循系统设置' : '切换工作台动效，不影响任何业务操作'" placement="bottom"><button class="motion-toggle" type="button" :aria-label="`工作台动效：${motionPreference==='reduced' || systemReduced ? '减少' : '标准'}`" :aria-pressed="motionPreference==='reduced'" @click="toggleMotion"><el-icon><MagicStick /></el-icon><span>动效：{{ motionPreference==='reduced' || systemReduced ? '减少' : '标准' }}</span></button></el-tooltip><el-button text circle aria-label="通知"><el-icon><Bell /></el-icon></el-button>
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
        <div v-if="!online" class="mobile-offline" role="status">当前离线，无法提交业务操作。恢复网络后请手动重试。</div>
        <router-view />
      </el-main>
    </el-container>
    <nav class="mobile-navigation" aria-label="手机主导航">
      <router-link v-for="item in mobileMenus.primary" :key="item.path" :to="item.path" :class="{ active: activeMenu === item.path }" :aria-current="activeMenu === item.path ? 'page' : undefined"><el-icon><component :is="item.icon" /></el-icon><span>{{ item.label }}</span></router-link>
      <button v-if="canUseAssistant" type="button" aria-label="打开业务助手" @click="openAssistant"><el-icon><ChatDotRound /></el-icon><span>业务助手</span></button>
      <button type="button" :class="{ active: moreOpen }" :aria-expanded="moreOpen" aria-controls="mobile-more" @click="moreOpen = !moreOpen"><el-icon><Expand /></el-icon><span>更多</span></button>
    </nav>
    <div v-show="moreOpen" class="mobile-more-backdrop" @click.self="moreOpen = false" @keydown.esc="moreOpen = false">
      <section id="mobile-more" class="mobile-more" role="dialog" aria-label="全部功能" aria-modal="true"><header><div><strong>全部功能</strong><small>{{ roleName }} · {{ userInfo?.realName || userInfo?.username }}</small></div><button type="button" aria-label="关闭更多菜单" @click="moreOpen = false">关闭</button></header>
        <div class="mobile-menu-grid"><router-link v-for="item in visibleMenus" :key="item.path" :to="item.path" @click="moreOpen = false"><el-icon><component :is="item.icon" /></el-icon>{{ item.label }}</router-link></div>
        <PwaInstall />
        <button class="mobile-logout" type="button" @click="handleCommand('logout')">退出登录</button>
      </section>
    </div>
    <AssistantPanel v-if="canUseAssistant" :key="authStore.sessionGeneration" />
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, provide, ref, watch, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/store/modules/auth'
import { ElMessage } from 'element-plus'
import { ArrowDown, Bell, ChatDotRound, Collection, CreditCard, Expand, Fold, MagicStick, Odometer, OfficeBuilding, Tickets, TrendCharts, User, UserFilled } from '@element-plus/icons-vue'
import AssistantPanel from '@/components/AssistantPanel.vue'
import PwaInstall from '@/components/PwaInstall.vue'
import { ROLE_NAMES } from '@/utils/auth'
import { isMenuVisible } from '@/utils/accessControl.mjs'
import { mobileNavigation } from '@/utils/mobileNavigation.mjs'
import { normalizeMotionPreference, resolveMotionState } from '@/utils/workspaceAppearance.mjs'
import { workspaceMotionKey } from '@/utils/workspaceMotion'

const motionStorageKey = 'fitchain.workspace.motion'
const motionMedia = window.matchMedia('(prefers-reduced-motion: reduce)')
const systemReduced = ref(motionMedia.matches)
const pageHidden = ref(document.hidden)
const motionPreference = ref(normalizeMotionPreference((() => { try { return localStorage.getItem(motionStorageKey) } catch { return null } })()))
const motionState = computed(() => resolveMotionState(motionPreference.value, systemReduced.value, pageHidden.value))
provide(workspaceMotionKey, computed(() => motionState.value === 'full'))
const syncSystemMotion = () => { systemReduced.value = motionMedia.matches }
const syncVisibility = () => { pageHidden.value = document.hidden }
const toggleMotion = () => {
  motionPreference.value = motionPreference.value === 'standard' ? 'reduced' : 'standard'
  try { localStorage.setItem(motionStorageKey, motionPreference.value) } catch { /* Optional appearance storage; the setting still works for this session. */ }
}
watchEffect(() => { document.body.dataset.wbMotion = motionState.value })
onMounted(() => {
  document.body.classList.add('wb-portal-active')
  motionMedia.addEventListener('change', syncSystemMotion)
  document.addEventListener('visibilitychange', syncVisibility)
})
onUnmounted(() => {
  motionMedia.removeEventListener('change', syncSystemMotion)
  document.removeEventListener('visibilitychange', syncVisibility)
  document.body.classList.remove('wb-portal-active')
  delete document.body.dataset.wbMotion
})

const route = useRoute()
const authStore = useAuthStore()
const moreOpen = ref(false), online = ref(navigator.onLine !== false)
const syncOnline = () => { online.value = navigator.onLine !== false }
const openAssistant = () => { moreOpen.value = false; window.dispatchEvent(new CustomEvent('fitchain:open-assistant')) }
watch(() => route.fullPath, () => { moreOpen.value = false })
watch(() => authStore.sessionGeneration, () => { moreOpen.value = false })
onMounted(() => { window.addEventListener('online', syncOnline); window.addEventListener('offline', syncOnline) })
onUnmounted(() => { window.removeEventListener('online', syncOnline); window.removeEventListener('offline', syncOnline) })
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
  { path: '/retention', label: '续费与回访', icon: ChatDotRound, permission: 'MEMBER_READ', roles: ['SALES', 'MANAGER', 'SUPER_ADMIN'] },
  { path: '/leads', label: '潜客与体验课', icon: UserFilled, permission: 'LEAD_READ', roles: ['SALES', 'MANAGER', 'SUPER_ADMIN'] },
  { path: '/cash-shifts', label: '交班与日结', icon: CreditCard, permission: 'CASH_SHIFT_READ', roles: ['RECEPTION', 'MANAGER', 'SUPER_ADMIN'] },
  { path: '/training-plans', label: '训练计划', icon: UserFilled, permission: 'TRAINING_PLAN_READ', roles: ['COACH', 'MANAGER', 'SUPER_ADMIN'] },
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
const mobileMenus = computed(() => mobileNavigation(menuItems, userInfo.value?.role, userInfo.value?.permissions || []))
const canUseAssistant = computed(() => userInfo.value?.permissions?.includes('ASSISTANT_USE'))

const handleCommand = (command: string) => {
  if (command === 'logout') {
    moreOpen.value = false
    authStore.logout()
    ElMessage.success('已退出登录')
  }
}
</script>

<style scoped>
.layout-container { height: 100dvh; min-height: 0; background: #eff5fc; }
.workspace-body { min-width: 0; min-height: 0; }
.aside { position: relative; display: flex; flex-direction: column; flex: none; min-height: 0; border-right: 1px solid #283c57; background: radial-gradient(ellipse at 20% 5%,#244f78 0%,transparent 45%),linear-gradient(165deg,#152b46,#101b31 65%); transition: width .2s; overflow: hidden; }
.aside::before { content: ''; position: absolute; right: -55px; bottom: 20px; width: 200px; height: 200px; border: 1px solid #80bbfa21; border-radius: 50%; box-shadow: 0 0 0 22px #70b0f408,0 0 0 45px #70b0f406; pointer-events: none; }
.brand { position: relative; flex: none; min-height: 94px; display: flex; align-items: center; gap: 12px; padding: 0 20px; color: #f4faff; }
.brand-mark { flex: none; display: grid; place-items: center; width: 42px; height: 42px; border: 1px solid #a6d2ff80; border-radius: 14px 6px 14px 6px; background: linear-gradient(135deg,#4b8de6,#80baff); box-shadow: 0 0 24px #559be629; font-size: 24px; font-weight: 800; }
.brand strong,.brand small { display: block; white-space: nowrap; }
.brand strong { font-size: 19px; letter-spacing: .4px; }.brand small { margin-top: 5px; font-size: 10px; color: #9ab1ce; letter-spacing: 1px; }
.menu-label { flex: none; padding: 10px 24px 12px; color: #9cb7d4; font-size: 10px; letter-spacing: 2px; }
.navigation-scroll { position: relative; flex: 1; min-height: 0; overflow-y: auto; overflow-x: hidden; scrollbar-width: thin; scrollbar-color: #46617c transparent; }
.menu { border: 0; background: transparent; }
.menu :deep(.el-menu-item) { position: relative; height: 48px; margin: 5px 12px; border: 1px solid transparent; border-radius: 11px; color: #a7bad2; font-size: 13px; transition: background .18s,color .18s; }
.menu :deep(.el-menu-item:hover) { background: #37618f30; color: #f2f8ff; }
.menu :deep(.el-menu-item.is-active) { color: #fff; background: linear-gradient(90deg,#376baf80,#487cac21); border-color: #89bafa33; box-shadow: inset 3px 0 0 #95c8ff,0 4px 16px #0a142327; }
.menu :deep(.el-menu-item.is-active::after) { content: '✧'; position: absolute; right: 12px; color: #afd7ff; font-size: 17px; pointer-events: none; }
.menu.el-menu--collapse { width: 76px; }
.menu.el-menu--collapse :deep(.el-menu-item) { width: 52px; padding: 0 !important; }
.menu.el-menu--collapse :deep(.el-menu-tooltip__trigger) { padding: 0 !important; display: flex; align-items: center; justify-content: center; }
.menu.el-menu--collapse :deep(.el-menu-item::after) { display: none; }
.aside-bottom { position: relative; flex: none; display: flex; align-items: center; justify-content: flex-start; gap: 10px; margin: 14px 12px; padding: 13px 16px; border: 1px solid #8abaf526; border-radius: 10px; background: #2139577a; color: #afc5dd; font: inherit; font-size: 11px; cursor: pointer; }
.aside-bottom:hover { color: #fff; background: #30547a7a; }
.header { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex: none; height: 70px; padding: 0 30px; border-bottom: 1px solid #dce7f4; background: linear-gradient(90deg,#ffffffed,#f5faffed); }
.header-left { display: flex; align-items: center; gap: 12px; min-width: 0; }
.header-left div span,.header-left div strong { display: block; }.header-left div span { font-size: 10px; color: #6e8bab; letter-spacing: 1px; }.header-left div strong { margin-top: 4px; font-size: 14px; color: #234565; }
.header-right { display: flex; align-items: center; gap: 12px; }
.motion-toggle { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 34px; padding: 0 10px; border: 1px solid #d6e4f5; border-radius: 20px; background: #f4f9ff; color: #4e729a; font: inherit; font-size: 11px; cursor: pointer; }
.motion-toggle:hover { background: #e6f1ff; }.motion-toggle[aria-pressed="true"] { background: #edf1f7; color: #61758d; }
.user-info { cursor: pointer; display: flex; align-items: center; gap: 10px; color: #365574; }
.avatar { display: grid; place-items: center; width: 37px; height: 37px; border: 2px solid #fff; border-radius: 12px; background: linear-gradient(130deg,#387ce7,#90bffb); box-shadow: 0 0 0 1px #cfe0f4; color: #fff; font-weight: 800; }
.user-name { font-size: 13px; font-weight: 700; }.user-name small { display: block; margin-top: 4px; color: #7890aa; font-size: 10px; font-weight: 400; }
.main { position: relative; min-width: 0; min-height: 0; padding: 26px 30px 36px; overflow: auto; background: radial-gradient(ellipse at 100% 0,#d7e9ff6b,transparent 42%),radial-gradient(ellipse at 0 100%,#e6e2ff40,transparent 45%),#eff5fc; }
.mobile-navigation,.mobile-more-backdrop { display:none; }.mobile-offline{padding:12px;margin-bottom:14px;border-radius:9px;background:#fff2d4;color:#815719;font-size:12px;line-height:1.7}
@media (max-width: 1100px) { .main { padding: 22px; }.header { padding: 0 22px; }.header-right { gap: 8px; } }
@media (max-width: 720px) { .aside { width: 76px !important; }.brand { padding: 0 17px; min-height: 78px; }.brand>div:not(.brand-mark),.menu-label,.aside-bottom span,.menu :deep(.el-menu-item span) { display: none; }.aside-bottom { justify-content: center; padding: 13px; }.main { padding: 16px 12px 25px; }.header { padding: 0 12px; height: 64px; }.user-name { display: none; }.header-right { gap: 4px; }.header-left .el-tag { display: none; }.motion-toggle { padding: 7px; }.motion-toggle span { display: none; }.header-left div strong { font-size: 12px; }.header-left div span { font-size: 9px; } }
@media(max-width:760px){.aside{display:none}.header{height:calc(62px + env(safe-area-inset-top,0px));padding:env(safe-area-inset-top,0px) 14px 0}.main{padding:16px 12px calc(90px + env(safe-area-inset-bottom,0px))}.user-name{display:none}.header-left .el-tag,.motion-toggle{display:none}.header-right{gap:4px}.mobile-navigation{position:fixed;bottom:0;left:0;right:0;z-index:1800;display:flex;justify-content:space-around;min-height:calc(66px + env(safe-area-inset-bottom,0px));padding:4px 4px env(safe-area-inset-bottom,0px);border-top:1px solid #d5e2f2;background:#fffffffa;box-shadow:0 -5px 22px #294d6d0b}.mobile-navigation>a,.mobile-navigation>button{flex:1;display:flex;flex-direction:column;gap:5px;align-items:center;justify-content:center;min-width:0;min-height:58px;border:0;border-radius:10px;background:transparent;color:#73869d;text-decoration:none;font-size:10px;cursor:pointer}.mobile-navigation .el-icon{font-size:21px}.mobile-navigation .active{color:#235fbb;background:#eef5ff}.mobile-navigation span{overflow:hidden;max-width:100%;text-overflow:ellipsis;white-space:nowrap}.mobile-more-backdrop{position:fixed;inset:0;z-index:1900;display:flex;align-items:flex-end;background:#10223e65}.mobile-more{width:100%;max-height:calc(100dvh - 32px);overflow:auto;overscroll-behavior:contain;padding:18px 16px calc(24px + env(safe-area-inset-bottom,0px));border-radius:22px 22px 0 0;background:#fff}.mobile-more header{display:flex;justify-content:space-between;align-items:center;gap:15px;margin-bottom:18px;color:#25476b}.mobile-more header small{display:block;font-size:11px;color:#7c91a9;margin-top:5px}.mobile-more header button,.mobile-logout{border:1px solid #d6e2f1;border-radius:9px;min-height:44px;padding:10px 15px;color:#395e8d;background:#f8fbff}.mobile-menu-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:9px;margin-bottom:18px}.mobile-menu-grid>a{display:flex;gap:7px;flex-direction:column;justify-content:center;align-items:center;min-height:72px;border:1px solid #e1eaf6;border-radius:11px;color:#37608a;background:#f6faff;text-decoration:none;font-size:12px}.mobile-menu-grid .el-icon{font-size:22px}.mobile-logout{width:100%;margin-top:16px;color:#aa4141}.header-left div strong{max-width:50vw;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.header-left div span{font-size:10px}}
</style>
