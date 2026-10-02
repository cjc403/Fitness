<template>
  <!--
    进入界面：冰蓝「魔法 × 漫画分镜」视觉，左侧为角色主视觉，右侧为登录表单。

    ⚠️ 自包含约定（务必保留）：所有装饰都是**纯 CSS + 内联元素**，
    不加载任何外部图片 / 字体 / 远程资源，也不读取系统壁纸。
    所以即使关闭 Wallpaper Engine、换掉壁纸、甚至换一台机器，本页面都照常显示与使用。

    动效约定（保持"克制"）：只用 transform / opacity（走 GPU，不触发重排）；
    不使用 blur 动画、不使用视频或逐帧 JS；开启系统"减少动态效果"时全部关闭。
  -->
  <div class="login-container">
    <!-- 左侧：角色主视觉 -->
    <section class="login-brand">
      <div class="bg-tone" aria-hidden="true"></div>
      <div class="dots dots-back" aria-hidden="true"></div>
      <div class="dots dots-front" aria-hidden="true"></div>
      <div class="magic-circle" aria-hidden="true"></div>
      <div class="sparkles" aria-hidden="true"><i v-for="n in 24" :key="n"></i></div>
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

      <!--
        角色主视觉（布局 A：占左侧面板右侧、全高展示；文字独占左边，互不重叠）。
        换图：把图片命名成 public/hero-character.jpg 覆盖即可（建议 900px 宽左右，JPEG 体积最小）。
        图片不存在或加载失败时整块自动隐藏，页面照常可用 —— 不依赖任何外部软件或壁纸。
      -->
      <div v-show="heroVisible" class="hero-wrap" aria-hidden="true">
        <div class="hero-frame">
          <div class="hero-media">
            <img :src="heroSrc" alt="" @error="heroVisible = false"/>
          </div>
          <span class="hero-ink"></span>
          <span class="hero-sheen"></span>
        </div>
      </div>
    </section>

    <!-- 右侧：登录表单（功能与交互保持原样） -->
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
// 角色主视觉：public/hero-character.jpg 存在就显示，不存在（或加载失败）自动隐藏。
// 用动态绑定而不是写死 src="..."，否则构建时 Vue 会把它当模块导入、文件不存在就直接构建失败。
const heroVisible = ref(true)
const heroSrc = '/hero-character.jpg'

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
   左侧角色主视觉 + 右侧登录表单
   —— 装饰全部由 CSS 绘制，无任何外部依赖（见模板顶部说明）
   ============================================================ */

.login-container {
  display: grid;
  grid-template-columns: minmax(0, 1.16fr) minmax(440px, .84fr);
  min-height: 100vh;
  background: #fff;
}
.login-container, .login-container *, .login-container *::before, .login-container *::after {
  box-sizing: border-box;
}

/* ---------- 左：角色主视觉 ---------- */
.login-brand {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 44px clamp(30px, 4vw, 62px);
  color: #fff;
  overflow: hidden;
  background:
    radial-gradient(circle at 74% 20%, rgba(168, 219, 255, .5), transparent 46%),
    radial-gradient(circle at 12% 86%, rgba(88, 152, 255, .4), transparent 50%),
    linear-gradient(158deg, #0a1424 0%, #132a4c 48%, #1c4780 100%);
}

/* ① 底色明暗呼吸（最慢，幅度极小） */
.bg-tone {
  position: absolute; inset: 0; pointer-events: none; opacity: .25;
  background: radial-gradient(circle at 62% 38%, rgba(150, 205, 255, .3), transparent 58%);
  animation: tone-breathe 60s ease-in-out infinite;
}
@keyframes tone-breathe { 0%, 100% { opacity: .25; } 50% { opacity: .65; } }

/* ② 网点纸（两层不同速度 = 轻微视差） */
.dots {
  position: absolute; inset: -12%; pointer-events: none;
  background-image: radial-gradient(rgba(198, 230, 255, .55) 1px, transparent 1.5px);
  background-size: 6px 6px;
  -webkit-mask-image: radial-gradient(circle at 66% 34%, #000 10%, transparent 76%);
  mask-image: radial-gradient(circle at 66% 34%, #000 10%, transparent 76%);
}
.dots-back { opacity: .38; animation: drift 96s ease-in-out infinite alternate; }
.dots-front {
  opacity: .22; background-size: 9px 9px;
  animation: drift 68s ease-in-out infinite alternate-reverse;
}
@keyframes drift {
  from { transform: translate3d(-14px, 8px, 0); }
  to   { transform: translate3d(18px, -12px, 0); }
}

/* ③ 魔法阵：缓慢自转 */
.magic-circle {
  position: absolute; right: -130px; bottom: -150px; width: 480px; height: 480px;
  border-radius: 50%; pointer-events: none;
  border: 1px dashed rgba(158, 214, 255, .42);
  animation: spin 52s linear infinite;
}
.magic-circle::before {
  content: ""; position: absolute; inset: 13%;
  border: 1px solid rgba(158, 214, 255, .24); border-radius: 50%;
}
.magic-circle::after {
  content: ""; position: absolute; inset: 27%;
  border: 2px solid rgba(190, 230, 255, .18); border-top-color: rgba(224, 246, 255, .6);
  border-radius: 50%; animation: spin 19s linear infinite reverse;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* ④ 星尘：更亮更快；前后两层速度不同；部分星点带四角星闪烁 */
.sparkles { position: absolute; inset: 0; pointer-events: none; }
.sparkles i {
  position: absolute; width: 5px; height: 5px; border-radius: 50%;
  background: #ffffff;
  box-shadow: 0 0 14px 4px rgba(178, 226, 255, .95), 0 0 4px 1px rgba(255, 255, 255, .9);
  opacity: .22; animation: float 6.5s ease-in-out infinite;
}
.sparkles i:nth-child(n+13) { width: 3px; height: 3px; animation-duration: 9s; opacity: .16; }
/* 每三个星点里有一个带十字星芒，闪烁更明显 */
.sparkles i:nth-child(3n)::before,
.sparkles i:nth-child(3n)::after {
  content: ""; position: absolute; left: 50%; top: 50%;
  transform: translate(-50%, -50%); border-radius: 2px; background: #ffffff;
  box-shadow: 0 0 9px 2px rgba(196, 234, 255, .9);
}
.sparkles i:nth-child(3n)::before { width: 15px; height: 2px; }
.sparkles i:nth-child(3n)::after  { width: 2px; height: 15px; }
.sparkles i:nth-child(1) { left: 7%;  top: 16%; animation-delay: -0.2s; }
.sparkles i:nth-child(2) { left: 19%; top: 64%; animation-delay: -0.9s; }
.sparkles i:nth-child(3) { left: 27%; top: 11%; animation-delay: -1.6s; }
.sparkles i:nth-child(4) { left: 33%; top: 79%; animation-delay: -2.1s; }
.sparkles i:nth-child(5) { left: 41%; top: 33%; animation-delay: -2.6s; }
.sparkles i:nth-child(6) { left: 47%; top: 68%; animation-delay: -3.1s; }
.sparkles i:nth-child(7) { left: 52%; top: 23%; animation-delay: -3.7s; }
.sparkles i:nth-child(8) { left: 58%; top: 52%; animation-delay: -4.3s; }
.sparkles i:nth-child(9) { left: 13%; top: 87%; animation-delay: -0.6s; }
.sparkles i:nth-child(10){ left: 23%; top: 39%; animation-delay: -1.3s; }
.sparkles i:nth-child(11){ left: 37%; top: 56%; animation-delay: -1.9s; }
.sparkles i:nth-child(12){ left: 44%; top: 9%;  animation-delay: -2.8s; }
.sparkles i:nth-child(13){ left: 51%; top: 88%; animation-delay: -3.4s; }
.sparkles i:nth-child(14){ left: 56%; top: 13%; animation-delay: -4.0s; }
.sparkles i:nth-child(15){ left: 62%; top: 45%; animation-delay: -4.6s; }
.sparkles i:nth-child(16){ left: 6%;  top: 49%; animation-delay: -4.2s; }
.sparkles i:nth-child(17){ left: 30%; top: 26%; animation-delay: -5.4s; }
.sparkles i:nth-child(18){ left: 55%; top: 72%; animation-delay: -6.1s; }
.sparkles i:nth-child(19){ left: 16%; top: 30%; animation-delay: -5.0s; }
.sparkles i:nth-child(20){ left: 25%; top: 52%; animation-delay: -2.4s; }
.sparkles i:nth-child(21){ left: 39%; top: 18%; animation-delay: -3.9s; }
.sparkles i:nth-child(22){ left: 46%; top: 47%; animation-delay: -5.7s; }
.sparkles i:nth-child(23){ left: 60%; top: 35%; animation-delay: -1.1s; }
.sparkles i:nth-child(24){ left: 21%; top: 74%; animation-delay: -6.5s; }
@keyframes float {
  0%, 100% { opacity: .16; transform: translate3d(0, 0, 0) scale(.85); }
  50%      { opacity: 1;   transform: translate3d(0, -24px, 0) scale(1.35); }
}

.vertical-tag {
  position: absolute; left: 22px; top: 50%; transform: translateY(-50%);
  writing-mode: vertical-rl; letter-spacing: 6px;
  color: rgba(206, 233, 255, .48); font-size: 12px; font-weight: 700;
  border-left: 2px solid rgba(150, 210, 255, .28); padding-left: 10px;
}

/* ---------- 品牌文字（独占左侧，与角色互不重叠） ---------- */
.brand-head { display: flex; align-items: center; gap: 14px; position: relative; z-index: 3; }
.logo {
  display: grid; place-items: center; width: 43px; height: 43px; border-radius: 13px;
  background: linear-gradient(135deg, #3d80ff, #8fc0ff); color: #fff;
  font-size: 22px; font-weight: 900;
  box-shadow: 0 10px 30px rgba(48, 113, 255, .45), 0 0 22px rgba(150, 210, 255, .5);
}
.brand-name { font-size: 21px; font-weight: 800; letter-spacing: .5px; }

/* 文字区宽度设上限：即使文案变长也绝不会压到角色（角色约 53% 宽，这里留出安全间距） */
.brand-copy { margin: auto 0; position: relative; z-index: 3; max-width: min(34%, 380px); }
.brand-copy em, .welcome {
  color: #86bbff; font-size: 11px; font-style: normal; font-weight: 800; letter-spacing: 2px;
}
.brand-copy h1 {
  margin: 16px 0 20px; line-height: 1.26; letter-spacing: -1.2px;
  /* 字号上限 34px：8 字的最长一行（~272px）一定放得下，两行不会折成三行 */
  font-size: clamp(22px, 2.2vw, 34px);
  text-shadow: 0 0 28px rgba(140, 205, 255, .5), 0 0 60px rgba(90, 160, 255, .26);
}
.brand-copy p { color: #b6c6de; line-height: 1.85; font-size: clamp(13px, .95vw, 15px); }

.brand-stats { display: flex; gap: clamp(28px, 4vw, 52px); position: relative; z-index: 3; }
.brand-stats strong, .brand-stats span { display: block; }
.brand-stats strong { font-size: 23px; text-shadow: 0 0 18px rgba(150, 210, 255, .45); }
.brand-stats span { margin-top: 5px; color: #93a3bd; font-size: 11px; }

/* ---------- 角色主视觉（布局 A：右侧全高） ---------- */
.hero-wrap {
  position: absolute; top: 8%; right: clamp(20px, 3vw, 48px);
  /* 长宽比锁死成图片本身的 2:3 —— 这样 cover 不再裁剪，
     眼皮用"图片百分比"定位就能在任何窗口尺寸下都精准贴合 */
  height: 74%; aspect-ratio: 2 / 3; width: auto;
  z-index: 2; pointer-events: none;
  animation: hero-float 20s ease-in-out infinite;
}
/* ⑤ 角色轻微浮动（20 秒一个来回，上下约 8px） */
@keyframes hero-float {
  0%, 100% { transform: translate3d(0, 0, 0) rotate(.5deg); }
  50%      { transform: translate3d(0, -8px, 0) rotate(.5deg); }
}
.hero-frame {
  position: relative; width: 100%; height: 100%; overflow: hidden;
  border: 2px solid #050b16; border-radius: 16px;
  background: linear-gradient(170deg, #16325a, #0b1a30);
  box-shadow: 0 0 0 1px rgba(186, 222, 255, .35), 0 26px 60px rgba(0, 0, 0, .5);
}
.hero-media { position: absolute; inset: 0; }
.hero-media img {
  display: block; width: 100%; height: 100%;
  object-fit: cover; object-position: 50% 8%;
  /* 轻微放大 + 溢出裁掉：顺手把生成图右下角的模型水印切出画面 */
  transform: scale(1.1); transform-origin: 50% 8%;
}

.hero-ink {
  position: absolute; inset: 0; opacity: .16; pointer-events: none;
  background-image: radial-gradient(rgba(212, 238, 255, .85) 1.1px, transparent 1.6px);
  background-size: 5px 5px;
}
/* ⑥ 柔光带：约 12 秒掠过一次，很淡 */
.hero-sheen {
  position: absolute; inset: -25% -65%; pointer-events: none;
  background: linear-gradient(104deg, transparent 43%, rgba(232, 246, 255, .3) 50%, transparent 57%);
  animation: sheen 12s ease-in-out infinite;
}
@keyframes sheen {
  0%        { transform: translate3d(-32%, 0, 0); }
  14%, 100% { transform: translate3d(32%, 0, 0); }
}

/* ---------- 右：登录表单 ---------- */
.login-panel {
  display: grid; place-items: center; padding: 40px;
  min-width: 0;
  background:
    radial-gradient(circle at 82% 12%, rgba(186, 222, 255, .5), transparent 46%),
    radial-gradient(circle at 8% 92%, rgba(206, 232, 255, .55), transparent 42%),
    linear-gradient(165deg, #f6fbff, #eaf4ff 58%, #dfeeff);
}
.login-card {
  width: 380px; max-width: 100%; min-width: 0; position: relative;
  padding: 30px 28px 22px; border-radius: 18px;
  background: rgba(255, 255, 255, .84);
  border: 1px solid rgba(150, 198, 245, .55);
  box-shadow: 0 22px 60px rgba(52, 106, 178, .18), inset 0 1px 0 rgba(255, 255, 255, .9);
  backdrop-filter: blur(6px);
}
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

/* ---------- 适配常见屏幕 ---------- */
/* 偏矮的笔记本：收紧纵向节奏，避免文字与角色挤在一起 */
@media (max-height: 780px) {
  .brand-copy h1 { font-size: clamp(20px, 2vw, 30px); margin: 12px 0 14px; }
  .brand-copy p { line-height: 1.7; }
  .login-card { padding: 24px 24px 18px; }
  .login-card h2 { font-size: 26px; }
}

@media (max-width: 1440px) {
  /* 左栏变窄时，竖排装饰标签会贴到标题，直接隐藏更干净 */
  .vertical-tag { display: none; }
}

@media (max-width: 1180px) {
  .brand-copy { max-width: 54%; }
  .hero-wrap { width: clamp(220px, 36%, 320px); }
}

/* 窄屏：只保留登录卡片（角色与装饰整体隐藏，保证表单可用） */
@media (max-width: 980px) {
  .login-container { grid-template-columns: minmax(0, 1fr); }
  .login-brand { display: none; }
  .login-panel { padding: 20px 16px; }
  .mobile-logo { display: flex; align-items: center; gap: 10px; margin-bottom: 18px; }
  .login-card { box-sizing: border-box; width: 100%; max-width: 420px; padding: 24px 20px 18px; }
}

/* ---------- 尊重系统「减少动态效果」 ---------- */
@media (prefers-reduced-motion: reduce) {
  .bg-tone, .dots, .magic-circle, .magic-circle::after,
  .sparkles i, .hero-wrap, .hero-sheen { animation: none !important; }
  .bg-tone { opacity: .4; }
  .sparkles i { opacity: .5; }
  .hero-sheen { display: none; }
}
</style>
