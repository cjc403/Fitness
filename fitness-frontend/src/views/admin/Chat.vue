<template>
  <div class="chat-page">
    <WorkspaceHero compact>
      <template #heading><span>INTERNAL MESSAGES</span><h1>站内消息</h1><p>各角色账号之间真实互发：员工之间、员工与会员之间都可以。会员在自己的「消息」页签里就能看到。</p></template>
    </WorkspaceHero>
    <ChatPanel/>
  </div>
</template>

<script setup lang="ts">
import WorkspaceHero from '@/components/WorkspaceHero.vue'
import ChatPanel from '@/components/ChatPanel.vue'
</script>

<style scoped>
.chat-page{max-width:1280px;margin:0 auto}.page-title{margin-bottom:20px}.page-title>div>span{color:#3474ff;font-size:11px;font-weight:800;letter-spacing:1.8px}.page-title h1{margin:7px 0;color:#172033;font-size:29px}.page-title p{margin:0;color:#8792a5}
</style>
