<template>
  <div class="password-page">
    <el-card class="password-card" shadow="never">
      <div class="shield">✓</div>
      <h1>首次登录，请修改密码</h1>
      <p>临时密码仅用于账号激活。新密码设置成功后，才能进入工作台。</p>
      <el-alert title="密码至少8位，须包含大小写字母、数字和特殊字符" type="info" :closable="false" />
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="当前临时密码" prop="currentPassword"><el-input v-model="form.currentPassword" type="password" show-password /></el-form-item>
        <el-form-item label="新密码" prop="newPassword"><el-input v-model="form.newPassword" type="password" show-password /></el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword"><el-input v-model="form.confirmPassword" type="password" show-password @keyup.enter="submit" /></el-form-item>
        <el-button type="primary" size="large" :loading="saving" style="width:100%" @click="submit">保存并进入工作台</el-button>
      </el-form>
      <el-button class="logout" link @click="authStore.logout()">退出当前账号</el-button>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { changePassword } from '@/api/modules/auth'
import { useAuthStore } from '@/store/modules/auth'
import { homeForRole } from '@/utils/auth'

const router = useRouter(); const authStore = useAuthStore(); const formRef = ref<FormInstance>(); const saving = ref(false)
const form = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const strong = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,72}$/
const rules: FormRules = {
  currentPassword: [{ required: true, message: '请输入当前临时密码', trigger: 'blur' }],
  newPassword: [{ required: true, validator: (_r, v, cb) => strong.test(v) ? cb() : cb(new Error('新密码强度不足')), trigger: 'blur' }],
  confirmPassword: [{ required: true, validator: (_r, v, cb) => v === form.newPassword ? cb() : cb(new Error('两次密码输入不一致')), trigger: 'blur' }]
}
const submit = async () => {
  if (!formRef.value || !(await formRef.value.validate().catch(() => false))) return
  saving.value = true
  try {
    await changePassword(form.currentPassword, form.newPassword)
    authStore.markPasswordChanged()
    ElMessage.success('密码修改成功')
    router.replace(homeForRole(authStore.userInfo?.role))
  } finally { saving.value = false }
}
</script>

<style scoped>
.password-page{min-height:100vh;display:grid;place-items:center;background:#f3f6fb;padding:24px}.password-card{width:min(440px,calc(100vw - 36px));border:0;border-radius:20px;padding:18px}.shield{display:grid;place-items:center;width:54px;height:54px;margin:0 auto;border-radius:18px;background:#eaf2ff;color:#2f70ff;font-size:27px;font-weight:900}.password-card h1{text-align:center;margin:20px 0 8px;color:#17233a;font-size:24px}.password-card p{text-align:center;color:#8490a5;line-height:1.7;margin:0 0 18px}.password-card .el-alert{margin-bottom:20px}.logout{display:block;margin:15px auto 0}
</style>
