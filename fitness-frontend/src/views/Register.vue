<template>
  <div class="auth-page">
    <div class="auth-card">
      <router-link class="brand" to="/login"><span>F</span> FitChain</router-link>
      <h1>注册会员账号</h1>
      <p>注册后即可进入会员中心，浏览课程并自主预约。</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="grid">
          <el-form-item label="姓名" prop="realName"><el-input v-model="form.realName" /></el-form-item>
          <el-form-item label="手机号" prop="phone"><el-input v-model="form.phone" maxlength="11" /></el-form-item>
        </div>
        <el-form-item label="所属场馆" prop="venueCode">
          <el-select v-model="form.venueCode" placeholder="请选择常用场馆" style="width:100%">
            <el-option v-for="venue in venues" :key="venue.code" :label="venue.name" :value="venue.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="登录账号" prop="username"><el-input v-model="form.username" placeholder="4-50位，以字母开头" /></el-form-item>
        <el-form-item label="邮箱（选填）" prop="email"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="密码" prop="password"><el-input v-model="form.password" type="password" show-password placeholder="至少8位，含大小写字母、数字和特殊字符" /></el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword"><el-input v-model="form.confirmPassword" type="password" show-password /></el-form-item>
        <el-checkbox v-model="agreed">我已阅读并同意会员服务与隐私条款</el-checkbox>
        <el-button class="submit" type="primary" size="large" :loading="saving" @click="submit">创建会员账号</el-button>
      </el-form>
      <div class="back">已有账号？<router-link to="/login">返回登录</router-link></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { listPublicVenues, registerMember, type VenueOption } from '@/api/modules/account'

const router = useRouter()
const formRef = ref<FormInstance>()
const venues = ref<VenueOption[]>([])
const saving = ref(false)
const agreed = ref(false)
const form = reactive({ username: '', password: '', confirmPassword: '', realName: '', phone: '', email: '', venueCode: '' })
const strongPassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,72}$/.test(value)
    ? callback() : callback(new Error('密码至少8位，且需包含大小写字母、数字和特殊字符'))
}
const rules: FormRules = {
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{ required: true, pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }],
  venueCode: [{ required: true, message: '请选择所属场馆', trigger: 'change' }],
  username: [{ required: true, pattern: /^[A-Za-z][A-Za-z0-9_.-]{3,49}$/, message: '账号须以字母开头，至少4位', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  password: [{ required: true, validator: strongPassword, trigger: 'blur' }],
  confirmPassword: [{ required: true, validator: (_r, v, cb) => v === form.password ? cb() : cb(new Error('两次密码输入不一致')), trigger: 'blur' }]
}
const submit = async () => {
  if (!formRef.value || !(await formRef.value.validate().catch(() => false))) return
  if (!agreed.value) return ElMessage.warning('请先同意会员服务与隐私条款')
  saving.value = true
  try {
    await registerMember({ username: form.username, password: form.password, realName: form.realName, phone: form.phone, email: form.email || undefined, venueCode: form.venueCode })
    ElMessage.success('注册成功，请登录会员中心')
    router.push({ path: '/login', query: { username: form.username } })
  } finally { saving.value = false }
}
onMounted(async () => {
  const response = await listPublicVenues()
  venues.value = response.data
  if (venues.value.length === 1) form.venueCode = venues.value[0].code
})
</script>

<style scoped>
.auth-page{min-height:100vh;display:grid;place-items:center;padding:36px;background:radial-gradient(circle at 20% 10%,#dce9ff,transparent 34%),#f4f7fc}.auth-card{width:min(520px,calc(100vw - 40px));padding:36px 42px;border-radius:22px;background:#fff;box-shadow:0 24px 70px rgba(24,42,78,.13)}.brand{display:flex;align-items:center;gap:10px;color:#16233d;font-size:20px;font-weight:800;text-decoration:none}.brand span{display:grid;place-items:center;width:38px;height:38px;border-radius:12px;background:#3978ff;color:#fff}.auth-card h1{margin:28px 0 8px;color:#17233a}.auth-card>p{margin:0 0 24px;color:#8a95a8}.grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.submit{width:100%;margin-top:22px}.back{text-align:center;margin-top:20px;color:#8993a5;font-size:13px}.back a{margin-left:5px;color:#3474ff;text-decoration:none}@media(max-width:560px){.auth-card{padding:28px 24px}.grid{grid-template-columns:1fr}}
</style>
