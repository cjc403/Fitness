<template>
  <div class="login-container">
    <section class="login-brand"><div class="logo">F</div><span>FitChain</span><div class="brand-copy"><em>CHAIN FITNESS OPERATIONS</em><h1>让每一家门店，<br/>都保持最佳状态。</h1><p>会员、课程、预约与经营数据汇于一处，<br/>为连锁健身品牌打造清晰高效的运营体验。</p></div><div class="brand-stats"><div><strong>2</strong><span>运营场馆</span></div><div><strong>18</strong><span>演示会员</span></div><div><strong>24h</strong><span>数据洞察</span></div></div></section>
    <section class="login-panel"><div class="login-card"><div class="mobile-logo"><div class="logo">F</div><b>FitChain</b></div><span class="welcome">WELCOME BACK</span><h2>登录 FitChain</h2><p>使用你的岗位账号进入专属工作台</p>
      <div v-if="isDemoMode" class="demo-roles"><span>快速体验角色</span><div><button v-for="account in demoAccounts" :key="account.username" type="button" :class="{active:loginForm.username===account.username}" @click="selectDemo(account.username)">{{ account.label }}</button></div></div>
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        label-position="top"
      >
        <el-form-item label="账号" prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            :prefix-icon="User"
            size="large"
          />
        </el-form-item>
        
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            size="large"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        
        <div class="login-options"><el-checkbox v-model="remember">记住账号</el-checkbox><span>{{ isDemoMode ? '演示环境' : 'PostgreSQL 生产环境' }}</span></div><el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            @click="handleLogin"
            style="width: 100%"
            size="large"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form><div class="register-link">还没有会员账号？<router-link to="/register">立即注册</router-link></div><div v-if="isDemoMode" class="demo-tip"><el-icon><InfoFilled /></el-icon><span>演示账号统一密码 <b>password</b></span></div><footer>FitChain Management Suite · Account v1.3</footer></div></section>
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
.login-container {
  display: grid; grid-template-columns: minmax(480px,1.15fr) minmax(440px,.85fr); min-height:100vh; background:#fff;
}
.login-brand{position:relative;display:flex;flex-direction:column;padding:48px 64px;color:#fff;background:radial-gradient(circle at 75% 25%,rgba(65,117,255,.35),transparent 31%),linear-gradient(145deg,#111a2f,#152646 70%,#15386b);overflow:hidden}.login-brand:after{content:"";position:absolute;width:420px;height:420px;right:-110px;bottom:-160px;border:1px solid rgba(255,255,255,.08);border-radius:50%;box-shadow:0 0 0 75px rgba(255,255,255,.025),0 0 0 150px rgba(255,255,255,.018)}.logo{display:grid;place-items:center;width:43px;height:43px;border-radius:13px;background:linear-gradient(135deg,#3979ff,#78a5ff);color:#fff;font-size:22px;font-weight:900;box-shadow:0 10px 30px rgba(48,113,255,.35)}.login-brand>span{position:absolute;top:57px;left:119px;font-size:21px;font-weight:800}.brand-copy{margin:auto 0}.brand-copy em,.welcome{color:#6f9eff;font-size:11px;font-style:normal;font-weight:800;letter-spacing:2px}.brand-copy h1{margin:18px 0 22px;font-size:48px;line-height:1.22;letter-spacing:-2px}.brand-copy p{color:#a7b2c8;line-height:1.9}.brand-stats{display:flex;gap:52px;position:relative;z-index:1}.brand-stats strong,.brand-stats span{display:block}.brand-stats strong{font-size:23px}.brand-stats span{margin-top:5px;color:#8794ac;font-size:11px}.login-panel{display:grid;place-items:center;padding:40px}.login-card{width:380px}.mobile-logo{display:none}.login-card h2{margin:10px 0 7px;color:#182238;font-size:29px}.login-card>p{margin-bottom:20px;color:#929bad}.demo-roles{margin-bottom:20px}.demo-roles>span{display:block;margin-bottom:8px;color:#7f8a9d;font-size:11px}.demo-roles>div{display:grid;grid-template-columns:repeat(5,1fr);gap:6px}.demo-roles button{padding:7px 3px;border:1px solid #e2e8f3;border-radius:8px;background:#fff;color:#6d7890;font-size:11px;cursor:pointer}.demo-roles button:hover,.demo-roles button.active{border-color:#4a84ff;background:#edf4ff;color:#2868ff}.login-card :deep(.el-form-item__label){font-weight:600;color:#48546a}.login-options{display:flex;justify-content:space-between;align-items:center;margin:-4px 0 19px;color:#9aa3b2;font-size:12px}.register-link{text-align:center;color:#8a94a7;font-size:13px}.register-link a{margin-left:5px;color:#3474ff;text-decoration:none;font-weight:600}.demo-tip{display:flex;align-items:center;gap:9px;margin-top:12px;padding:12px 14px;border-radius:10px;background:#f2f6ff;color:#6f7d96;font-size:12px}.demo-tip .el-icon{color:#3474ff}.demo-tip b{color:#30405c}.login-card footer{margin-top:32px;text-align:center;color:#b2b9c5;font-size:10px;letter-spacing:.5px}@media(max-width:900px){.login-container{grid-template-columns:1fr}.login-brand{display:none}.mobile-logo{display:flex;align-items:center;gap:10px;margin-bottom:40px;color:#172033;font-size:19px}.login-panel{background:#f5f7fb}.login-card{padding:28px;background:#fff;border-radius:18px;box-shadow:0 18px 55px rgba(25,40,70,.1)}}
</style>
