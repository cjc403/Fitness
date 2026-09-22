<template>
  <div class="state-page">
    <div class="state-icon"><el-icon><Lock /></el-icon></div>
    <span>ROLE BASED ACCESS</span>
    <h1>当前账号无权访问此页面</h1>
    <p>系统已按岗位分配功能权限。如工作职责发生变化，请联系总部管理员调整账号角色。</p>
    <el-button type="primary" size="large" @click="goHome">返回我的工作台</el-button>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Lock } from '@element-plus/icons-vue'
import { getUserInfo } from '@/utils/storage'
import { homeForRole } from '@/utils/auth'

const router = useRouter()
const goHome = () => router.replace(homeForRole(getUserInfo()?.role))
</script>

<style scoped>
.state-page{min-height:calc(100vh - 150px);display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center}.state-icon{display:grid;place-items:center;width:72px;height:72px;border-radius:22px;background:#eaf1ff;color:#2f70ff;font-size:30px}.state-page>span{margin-top:22px;color:#3474ff;font-size:11px;font-weight:800;letter-spacing:1.8px}.state-page h1{margin:10px 0;color:#172033;font-size:28px}.state-page p{max-width:480px;margin:0 0 26px;color:#8792a5;line-height:1.8}
</style>
