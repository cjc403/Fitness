<template>
  <!--
    进入界面：冰蓝「魔法 × 漫画分镜」视觉。

    ⚠️ 自包含约定（务必保留）：这里所有装饰都是**纯 CSS + 内联 SVG**，
    不加载任何外部图片 / 字体 / 远程资源，也不读取系统壁纸。
    所以即使关闭 Wallpaper Engine、换掉壁纸、甚至换一台机器，本页面都照常显示与使用。
  -->
  <div class="login-container">
    <!-- 左侧：品牌区（漫画分镜 + 魔法元素） -->
    <section class="login-brand">
      <div class="sparkles" aria-hidden="true"><i v-for="n in 16" :key="n"></i></div>
      <div class="magic-circle" aria-hidden="true"></div>
      <div class="panel panel-a" aria-hidden="true"><span class="panel-ink"></span></div>
      <div class="panel panel-b" aria-hidden="true"><span class="panel-ink"></span></div>
      <div class="panel panel-c" aria-hidden="true"><span class="panel-ink"></span><span class="panel-rune"></span></div>
      <div class="vertical-tag" aria-hidden="true">连锁运营</div>

      <header class="brand-head">
        <div class="logo">F</div>
        <span class="brand-name">FitChain</span>
      </header>

      <div class="brand-copy">
        <em>CHAIN FITNESS OPERATIONS</em>
        <h1>让每一家门店，<br/>都保持最佳状态。</h1>
        <p>会员、课程、预约与经营数据汇于一处，<br/>为连锁健身品牌打造清晰高效的运营体验。</p>
      </div>

      <div class="brand-stats">
        <div><strong>2</strong><span>运营场馆</span></div>
        <div><strong>20</strong><span>演示会员</span></div>
        <div><strong>24h</strong><span>数据洞察</span></div>
      </div>
    </section>

    <!-- 右侧：登录表单（保持原有功能与交互不变） -->
    <section class="login-panel">
      <div class="login-card">
        <div class="mobile-logo"><div class="logo">F</div><b>FitChain</b></div>
        <span class="welcome">WELCOME BACK</span>
        <h2>登录 FitChain</h2>
        <p>使用你的岗位账号进入专属工作台</p>

        <div v-if="isDemoMode" class="demo-roles">
          <span>快速体验角色</span>
          <div>
            <button v-for="account in demoAccounts" :key="account.username" type="button"
                    :class="{ active: loginForm.username === account.username }"
                    @click="selectDemo(account.username)">{{ account.label }}</button>
          </div>
        </div>

        <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" label-position="top">
          <el-form-item label="账号" prop="username">
            <el-input v-model="loginForm.username" placeholder="请输入用户名" :prefix-icon="User" size="large"/>
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" :prefix-icon="Lock"
                      size="large" show-password @keyup.enter="handleLogin"/>
          </el-form-item>

          <div class="login-options">
            <el-checkbox v-model="remember">记住账号</el-checkbox>
            <span>{{ isDemoMode ? '演示环境' : 'PostgreSQL 生产环境' }}</span>
          </div>

          <el-form-item>
            <el-button type="primary" :loading="loading" @click="handleLogin" style="width: 100%" size="large">
              登录
            </el-button>
          </el-form-item>
        </el-form>

        <div class="register-link">还没有会员账号？<router-link to="/register">立即注册</router-link></div>
        <div v-if="isDemoMode" class="demo-tip"><el-icon><InfoFilled /></el-icon><span>演示账号统一密码 <b>password</b></span></div>
        <footer>FitChain Management Suite · Account v1.3</footer>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/store/modules/auth'
import type { FormInstance, FormRules } from 'element-plus'
import { InfoFilled, Lock, User } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
// Production is the safe default. The disposable H2 demo must opt in explicitly.
const isDemoMode = import.meta.env.VITE_DEMO_MODE === 'true'

const loginFormRef = ref<FormInstance>()
const loading = ref(false)
const remember = ref(true)
const demoAccounts = [
  { label: '总部', username: 'admin' },
  { label: '店长', username: 'manager' },
  { label: '教练', username: 'coach' },
  { label: '前台', username: 'reception' },
  { label: '会员', username: 'member' }
]

const loginForm = reactive({
  username: typeof route.query.username === 'string' ? route.query.username : 'admin',
  password: isDemoMode ? 'password' : ''
})

const loginRules = reactive<FormRules>({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
})

onMounted(() => {
  if (route.query.reason === 'session-expired') {
    ElMessage.warning('登录状态已失效，请重新登录')
  }
})

const selectDemo = (username: string) => {
  loginForm.username = username
  loginForm.password = 'password'
}

const handleLogin = async () => {
  if (!loginFormRef.value) return

  await loginFormRef.value.validate((valid) => {
    if (valid) {
      loading.value = true
      authStore.login(loginForm.username, loginForm.password)
        .then(() => {
          ElMessage.success('登录成功')
          router.push(authStore.userInfo?.mustChangePassword ? '/change-password' : '/')
        })
        // The shared response interceptor has already shown the error.
        .catch(() => undefined)
        .finally(() => {
          loading.value = false
        })
    }
  })
}
</script>

<style scoped>
/* ============================================================
   进入界面视觉：冰蓝魔法 × 漫画分镜
   —— 全部由 CSS / 内联元素绘制，无任何外部依赖（详见模板顶部说明）
   ============================================================ */

.login-container {
  display: grid;
  grid-template-columns: minmax(480px, 1.15fr) minmax(440px, .85fr);
  min-height: 100vh;
  background: #fff;
}

/* 本页统一盒模型：避免 width:100% 叠加内边距后撑破窄屏（scoped，只影响本页元素） */
.login-container, .login-container * , .login-container *::before, .login-container *::after {
  box-sizing: border-box;
}

/* ---------- 左：品牌区 ---------- */
.login-brand {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 48px 64px;
  color: #fff;
  overflow: hidden;
  background:
    radial-gradient(circle at 78% 16%, rgba(168, 219, 255, .55), transparent 44%),
    radial-gradient(circle at 14% 84%, rgba(88, 152, 255, .42), transparent 48%),
    linear-gradient(160deg, #0a1424 0%, #132a4c 46%, #1d4a86 100%);
}

/* 网点纸纹理（漫画感） */
.login-brand::before {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: .55;
  background-image: radial-gradient(rgba(196, 228, 255, .55) 1px, transparent 1.5px);
  background-size: 6px 6px;
  -webkit-mask-image: radial-gradient(circle at 68% 32%, #000 12%, transparent 74%);
  mask-image: radial-gradient(circle at 68% 32%, #000 12%, transparent 74%);
}

/* 斜向光带 */
.login-brand::after {
  content: "";
  position: absolute;
  inset: -20% -10%;
  pointer-events: none;
  background: linear-gradient(108deg, transparent 38%, rgba(190, 226, 255, .16) 48%, transparent 58%);
}

/* ---------- 漫画分镜框 ---------- */
.panel {
  position: absolute;
  border: 3px solid #050b16;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 0 0 1px rgba(186, 222, 255, .35), 0 22px 55px rgba(0, 0, 0, .45);
  pointer-events: none;
}

.panel-a { top: 6%; right: 5%; width: 210px; height: 150px; transform: rotate(2.5deg);
  background: radial-gradient(circle at 60% 40%, rgba(150, 210, 255, .5), rgba(12, 30, 56, .9) 62%); }
.panel-b { bottom: 9%; right: 12%; width: 150px; height: 118px; transform: rotate(-3deg);
  background: linear-gradient(150deg, #16325a, #0b1a30); }
/* 第三块分镜：把魔法阵框进分镜格里，避免看起来像孤立的黑色方块 */
.panel-c { top: 45%; right: 4%; width: 122px; height: 122px; transform: rotate(4deg);
  background: radial-gradient(circle at 50% 50%, rgba(120, 185, 255, .38), #0c1a30 62%); }

.panel-ink { position: absolute; inset: 0; opacity: .5;
  background-image: radial-gradient(rgba(210, 236, 255, .8) 1.1px, transparent 1.6px); background-size: 5px 5px; }
.panel-beam { position: absolute; inset: -30% -60%;
  background: linear-gradient(115deg, transparent 42%, rgba(205, 235, 255, .45) 50%, transparent 58%); }
/* 分镜里的魔法阵：同心圆 + 断续刻度 */
.panel-rune {
  position: absolute; inset: 16%; border-radius: 50%;
  border: 1px dashed rgba(190, 228, 255, .6);
  box-shadow: inset 0 0 18px rgba(140, 200, 255, .35);
}
.panel-rune::before {
  content: ""; position: absolute; inset: 20%; border-radius: 50%;
  border: 1px solid rgba(200, 234, 255, .45);
}
.panel-rune::after {
  content: ""; position: absolute; inset: 38%; border-radius: 50%;
  background: radial-gradient(circle, rgba(225, 245, 255, .85), rgba(150, 210, 255, .15) 70%, transparent);
  box-shadow: 0 0 16px 4px rgba(150, 210, 255, .4);
}

/* ---------- 魔法阵 ---------- */
.magic-circle {
  position: absolute; right: -120px; bottom: -140px; width: 460px; height: 460px;
  border-radius: 50%; pointer-events: none;
  border: 1px dashed rgba(158, 214, 255, .45);
  animation: wb-spin 52s linear infinite;
}
.magic-circle::before {
  content: ""; position: absolute; inset: 13%;
  border: 1px solid rgba(158, 214, 255, .26); border-radius: 50%;
}
.magic-circle::after {
  content: ""; position: absolute; inset: 27%;
  border: 2px solid rgba(190, 230, 255, .2); border-top-color: rgba(224, 246, 255, .62);
  border-radius: 50%; animation: wb-spin 19s linear infinite reverse;
}

/* ---------- 漂浮光点 ---------- */
.sparkles { position: absolute; inset: 0; pointer-events: none; }
.sparkles i {
  position: absolute; width: 4px; height: 4px; border-radius: 50%;
  background: #eaf8ff; box-shadow: 0 0 10px 3px rgba(165, 218, 255, .85);
  opacity: .15; animation: wb-float 9s ease-in-out infinite;
}
.sparkles i:nth-child(1) { left: 8%;  top: 18%; animation-delay: -0.4s; }
.sparkles i:nth-child(2) { left: 22%; top: 62%; animation-delay: -1.6s; }
.sparkles i:nth-child(3) { left: 34%; top: 12%; animation-delay: -2.9s; }
.sparkles i:nth-child(4) { left: 46%; top: 78%; animation-delay: -3.6s; }
.sparkles i:nth-child(5) { left: 55%; top: 34%; animation-delay: -4.3s; }
.sparkles i:nth-child(6) { left: 63%; top: 66%; animation-delay: -5.1s; }
.sparkles i:nth-child(7) { left: 71%; top: 24%; animation-delay: -6.2s; }
.sparkles i:nth-child(8) { left: 79%; top: 52%; animation-delay: -7.4s; }
.sparkles i:nth-child(9) { left: 15%; top: 86%; animation-delay: -0.9s; }
.sparkles i:nth-child(10){ left: 28%; top: 38%; animation-delay: -2.2s; }
.sparkles i:nth-child(11){ left: 41%; top: 55%; animation-delay: -3.3s; }
.sparkles i:nth-child(12){ left: 52%; top: 8%;  animation-delay: -4.8s; }
.sparkles i:nth-child(13){ left: 66%; top: 88%; animation-delay: -5.7s; }
.sparkles i:nth-child(14){ left: 74%; top: 12%; animation-delay: -6.8s; }
.sparkles i:nth-child(15){ left: 88%; top: 44%; animation-delay: -8.1s; }
.sparkles i:nth-child(16){ left: 6%;  top: 48%; animation-delay: -7.1s; }

/* ---------- 竖排小标签 ---------- */
.vertical-tag {
  position: absolute; left: 22px; top: 50%; transform: translateY(-50%);
  writing-mode: vertical-rl; letter-spacing: 6px;
  color: rgba(206, 233, 255, .5); font-size: 12px; font-weight: 700;
  border-left: 2px solid rgba(150, 210, 255, .3); padding-left: 10px;
}

/* ---------- 品牌文字 ---------- */
.brand-head { display: flex; align-items: center; gap: 14px; position: relative; z-index: 2; }
.logo {
  display: grid; place-items: center; width: 43px; height: 43px; border-radius: 13px;
  background: linear-gradient(135deg, #3d80ff, #8fc0ff); color: #fff;
  font-size: 22px; font-weight: 900;
  box-shadow: 0 10px 30px rgba(48, 113, 255, .45), 0 0 22px rgba(150, 210, 255, .5);
}
.brand-name { font-size: 21px; font-weight: 800; letter-spacing: .5px; }

.brand-copy { margin: auto 0; position: relative; z-index: 2; }
.brand-copy em, .welcome {
  color: #86bbff; font-size: 11px; font-style: normal; font-weight: 800; letter-spacing: 2px;
}
.brand-copy h1 {
  margin: 18px 0 22px; font-size: 48px; line-height: 1.22; letter-spacing: -2px;
  text-shadow: 0 0 28px rgba(140, 205, 255, .5), 0 0 60px rgba(90, 160, 255, .28);
}
.brand-copy p { color: #b6c6de; line-height: 1.9; }

.brand-stats { display: flex; gap: 52px; position: relative; z-index: 2; }
.brand-stats strong, .brand-stats span { display: block; }
.brand-stats strong { font-size: 23px; text-shadow: 0 0 18px rgba(150, 210, 255, .45); }
.brand-stats span { margin-top: 5px; color: #93a3bd; font-size: 11px; }

/* ---------- 右：登录表单 ---------- */
.login-panel {
  display: grid; place-items: center; padding: 40px;
  min-width: 0; /* 关键：防止网格项被内容的最小宽度撑破（窄屏溢出根因） */
  background:
    radial-gradient(circle at 82% 12%, rgba(186, 222, 255, .5), transparent 46%),
    radial-gradient(circle at 8% 92%, rgba(206, 232, 255, .55), transparent 42%),
    linear-gradient(165deg, #f6fbff, #eaf4ff 58%, #dfeeff);
}

.login-card {
  width: 380px; max-width: 100%; min-width: 0; position: relative;
  padding: 30px 28px 22px; border-radius: 18px;
  background: rgba(255, 255, 255, .82);
  border: 1px solid rgba(150, 198, 245, .55);
  box-shadow: 0 22px 60px rgba(52, 106, 178, .18), inset 0 1px 0 rgba(255, 255, 255, .9);
  backdrop-filter: blur(6px);
}
/* 卡片四角的「分镜」小括号，呼应左侧漫画分镜 */
.login-card::before, .login-card::after {
  content: ""; position: absolute; width: 22px; height: 22px; border: 3px solid #2f6bd8; opacity: .55;
}
.login-card::before { top: -3px; left: -3px; border-right: 0; border-bottom: 0; border-radius: 18px 0 0 0; }
.login-card::after { bottom: -3px; right: -3px; border-left: 0; border-top: 0; border-radius: 0 0 18px 0; }

.mobile-logo { display: none; }
.login-card h2 { margin: 10px 0 7px; color: #16233b; font-size: 29px; }
.login-card > p { margin-bottom: 20px; color: #7d8ca4; }

.demo-roles { margin-bottom: 20px; }
.demo-roles > span { display: block; margin-bottom: 8px; color: #7f8a9d; font-size: 11px; }
.demo-roles > div { display: grid; grid-template-columns: repeat(5, 1fr); gap: 6px; }
.demo-roles button {
  padding: 7px 3px; border: 1px solid #dbe6f5; border-radius: 8px; background: #fff;
  color: #6d7890; font-size: 11px; cursor: pointer; transition: all .18s;
}
.demo-roles button:hover, .demo-roles button.active {
  border-color: #4a84ff; background: #edf4ff; color: #2868ff;
  box-shadow: 0 0 0 3px rgba(74, 132, 255, .12);
}

.login-card :deep(.el-form-item__label) { font-weight: 600; color: #46536b; }
.login-card :deep(.el-input__wrapper) { border-radius: 10px; }
.login-card :deep(.el-button--primary) {
  border: none; border-radius: 10px; font-weight: 700; letter-spacing: 2px;
  background: linear-gradient(135deg, #2f6bd8, #5fa4ff);
  box-shadow: 0 12px 28px rgba(47, 107, 216, .32);
}
.login-card :deep(.el-button--primary:hover) { box-shadow: 0 14px 32px rgba(47, 107, 216, .45); }

.login-options { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 6px 12px; margin-bottom: 12px; color: #8b97ab; font-size: 12px; }
.register-link { margin-top: 14px; text-align: center; color: #8b97ab; font-size: 13px; }
.register-link a { color: #2f6bd8; font-weight: 600; text-decoration: none; }
.demo-tip { display: flex; align-items: center; gap: 6px; margin-top: 12px; padding: 8px 12px; border-radius: 8px; background: #eef5ff; color: #4a6fa8; font-size: 12px; }
.login-card footer { margin-top: 18px; text-align: center; color: #a9b4c6; font-size: 11px; }

/* ---------- 动画 ---------- */
@keyframes wb-spin { to { transform: rotate(360deg); } }
@keyframes wb-float {
  0%, 100% { opacity: .1; transform: translateY(0) scale(.9); }
  50%      { opacity: .85; transform: translateY(-16px) scale(1.25); }
}

/* 尊重系统「减少动态效果」设置 */
@media (prefers-reduced-motion: reduce) {
  .magic-circle, .magic-circle::after, .sparkles i { animation: none; }
  .sparkles i { opacity: .5; }
}

/* ---------- 窄屏 ---------- */
@media (max-width: 980px) {
  /* minmax(0,1fr)：普通 1fr 不会小于内容的最小宽度，会被表单撑破导致横向溢出 */
  .login-container { grid-template-columns: minmax(0, 1fr); }
  .login-brand { display: none; }
  .login-panel { padding: 20px 16px; min-width: 0; }
  .mobile-logo { display: flex; align-items: center; gap: 10px; margin-bottom: 18px; }
  .login-card { box-sizing: border-box; width: 100%; max-width: 420px; min-width: 0; padding: 24px 20px 18px; }
  .brand-copy h1 { font-size: 34px; }
}
</style>
