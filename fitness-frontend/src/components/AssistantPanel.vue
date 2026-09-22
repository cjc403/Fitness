<template>
  <div class="assistant">
    <transition name="pop">
      <div v-if="open" class="assistant-panel">
        <div class="assistant-head">
          <div><span class="online-dot" /> FitChain 智能助手<small>{{ source }} · 在线</small></div>
          <el-button text circle @click="open = false"><el-icon><Close /></el-icon></el-button>
        </div>
        <div ref="bodyRef" class="assistant-body">
          <div v-for="(item, index) in messages" :key="index" :class="['bubble-row', item.role]">
            <div class="bubble">{{ item.text }}</div>
          </div>
          <div v-if="loading" class="bubble-row bot"><div class="bubble typing">正在思考…</div></div>
        </div>
        <div class="quick-list">
          <button v-for="item in suggestions" :key="item" @click="ask(item)">{{ item }}</button>
        </div>
        <div class="assistant-input">
          <el-input v-model="input" placeholder="输入业务问题…" maxlength="1000" @keyup.enter="send" />
          <el-button type="primary" :loading="loading" @click="send"><el-icon><Promotion /></el-icon></el-button>
        </div>
      </div>
    </transition>
    <el-button class="assistant-fab" type="primary" circle @click="open = !open">
      <el-icon size="24"><ChatDotRound /></el-icon>
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { chat, getAssistantStatus, type AssistantTurn } from '@/api/modules/assistant'
import { ChatDotRound, Close, Promotion } from '@element-plus/icons-vue'
const open = ref(false); const input = ref(''); const loading = ref(false); const bodyRef = ref<HTMLElement>()
const source = ref('正在检测服务')
const messages = ref([{ role: 'bot', text: '你好！我是你的健身房运营助手。会员、排课、预约和经营数据方面的问题都可以问我。' }])
const suggestions = ref(['如何新增会员？', '如何安排一节课程？', '如何为会员预约？'])
const scroll = () => nextTick(() => { if (bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight })
const ask = (text: string) => { input.value = text; send() }
const send = async () => {
  const text = input.value.trim(); if (!text || loading.value) return
  const history: AssistantTurn[] = messages.value.slice(1).slice(-6).map(item => ({
    role: item.role === 'bot' ? 'assistant' : 'user',
    content: item.text
  }))
  messages.value.push({ role: 'user', text }); input.value = ''; loading.value = true; scroll()
  try { const res = await chat(text, history); messages.value.push({ role: 'bot', text: res.data.answer }); suggestions.value = res.data.suggestions; source.value = res.data.source }
  catch { messages.value.push({ role: 'bot', text: '暂时无法处理这个问题，请稍后再试。' }) }
  finally { loading.value = false; scroll() }
}
onMounted(async () => {
  try {
    const res = await getAssistantStatus()
    source.value = res.data.configured ? `DeepSeek · ${res.data.model}` : '本地知识库'
  } catch { source.value = '本地知识库' }
})
</script>

<style scoped>
.assistant{position:fixed;right:28px;bottom:26px;z-index:2000}.assistant-fab{width:56px;height:56px;box-shadow:0 14px 30px rgba(20,104,255,.34)}
.assistant-panel{position:absolute;right:0;bottom:70px;width:380px;height:540px;background:#fff;border:1px solid #e9edf5;border-radius:20px;box-shadow:0 24px 70px rgba(24,39,75,.22);display:flex;flex-direction:column;overflow:hidden}
.assistant-head{height:72px;padding:0 18px;display:flex;align-items:center;justify-content:space-between;color:#14213d;font-weight:700;background:linear-gradient(135deg,#f1f6ff,#fff)}
.assistant-head small{display:block;margin:4px 0 0 15px;font-weight:400;color:#8791a5}.online-dot{display:inline-block;width:9px;height:9px;background:#2bc48a;border-radius:50%;margin-right:6px}
.assistant-body{flex:1;overflow:auto;padding:18px;background:#f7f9fc}.bubble-row{display:flex;margin-bottom:12px}.bubble-row.user{justify-content:flex-end}.bubble{max-width:84%;padding:11px 13px;border-radius:14px 14px 14px 4px;background:#fff;color:#3e4a61;font-size:14px;line-height:1.65;box-shadow:0 3px 12px rgba(31,45,70,.06)}.user .bubble{background:#2768ff;color:#fff;border-radius:14px 14px 4px 14px}.typing{color:#8791a5}
.quick-list{display:flex;gap:7px;overflow:auto;padding:9px 14px;border-top:1px solid #eef1f6}.quick-list button{white-space:nowrap;border:1px solid #dce5f5;background:#f8fbff;color:#3970d8;border-radius:14px;padding:6px 10px;font-size:12px;cursor:pointer}.assistant-input{display:flex;gap:8px;padding:12px 14px 16px}.pop-enter-active,.pop-leave-active{transition:.2s}.pop-enter-from,.pop-leave-to{opacity:0;transform:translateY(12px) scale(.97)}
@media(max-width:600px){.assistant{right:16px;bottom:16px}.assistant-panel{width:calc(100vw - 32px);height:70vh}}
</style>
