<template>
  <div class="chat-panel" :class="{ embedded }" v-loading="loading">
    <aside class="chat-aside">
      <div class="aside-head">
        <strong>会话</strong>
        <div>
          <el-button link type="primary" @click="openPicker">发起会话</el-button>
          <el-button link @click="reload"><el-icon><Refresh/></el-icon></el-button>
        </div>
      </div>
      <ul class="conv-list">
        <li v-for="c in conversations" :key="c.userId" :class="{ active: c.userId === activeUserId }">
          <button type="button" class="conv-contact" :aria-label="`打开会话：${c.name}（${c.roleName || '账号'}，编号${c.userId}）`" :aria-pressed="c.userId === activeUserId" @click="openThread(c.userId)">
          <span class="conv-top"><strong>{{ c.name }}</strong><small>{{ timeText(c.lastAt) }}</small></span>
          <span class="conv-bottom">
            <span class="conv-last">{{ c.lastContent }}</span>
            <span v-if="c.unread" class="unread">{{ c.unread > 99 ? '99+' : c.unread }}</span>
          </span>
          <small class="role">{{ c.roleName || '—' }}</small>
          </button>
        </li>
        <li v-if="!conversations.length" class="empty">还没有会话，点右上角「发起会话」给同事或会员发消息</li>
      </ul>
    </aside>

    <section class="chat-main">
      <template v-if="thread">
        <header class="main-head">
          <div><strong>{{ thread.name }}</strong><small>{{ thread.roleName || '—' }} · 账号编号 {{ thread.userId }}</small></div>
          <span class="hint">站内消息 · 对方登录后即可在自己的消息界面看到</span>
        </header>
        <div ref="scrollBox" class="bubbles">
          <div v-for="m in thread.messages" :key="m.id" class="bubble-row" :class="{ mine: m.mine }">
            <div class="bubble">
              <p>{{ m.content }}</p>
              <small>{{ m.senderName }} · {{ timeText(m.createdAt) }}</small>
            </div>
          </div>
          <p v-if="!thread.messages.length" class="empty-tip">还没有消息，打个招呼吧</p>
        </div>
        <footer class="composer">
          <el-input v-model="draft" type="textarea" :rows="2" maxlength="1000" show-word-limit
                    resize="none" placeholder="输入消息，Enter 发送 / Shift+Enter 换行" @keydown.enter.exact.prevent="send"/>
          <el-button type="primary" :loading="sending" :disabled="!draft.trim()" @click="send">发送</el-button>
        </footer>
      </template>
      <el-empty v-else description="从左侧选择一个会话，或发起新的会话" :image-size="80"/>
    </section>

    <el-dialog v-model="pickerVisible" title="选择联系人" width="480px">
      <el-input v-model="keyword" placeholder="搜索姓名" clearable style="margin-bottom:12px"/>
      <el-tabs v-model="pickerTab">
        <el-tab-pane :label="`员工（${filtered.staff.length}）`" name="staff">
          <ul class="picker-list">
            <li v-for="c in filtered.staff" :key="c.userId">
              <button type="button" class="picker-contact" :aria-label="`选择联系人：${c.name}（${c.roleName || '账号'}，编号${c.userId}）`" @click="startWith(c)">
              <strong>{{ c.name }}</strong><small>{{ c.roleName }}<template v-if="c.venueName"> · {{ c.venueName }}</template></small>
              <span v-if="c.unread" class="unread">{{ c.unread }}</span>
              </button>
            </li>
          </ul>
        </el-tab-pane>
        <el-tab-pane v-if="contacts.members.length" :label="`会员（${filtered.members.length}）`" name="members">
          <ul class="picker-list">
            <li v-for="c in filtered.members" :key="c.userId">
              <button type="button" class="picker-contact" :aria-label="`选择联系人：${c.name}（${c.roleName || '账号'}，编号${c.userId}）`" @click="startWith(c)">
              <strong>{{ c.name }}</strong><small>{{ c.roleName }}<template v-if="c.venueName"> · {{ c.venueName }}</template></small>
              <span v-if="c.unread" class="unread">{{ c.unread }}</span>
              </button>
            </li>
          </ul>
        </el-tab-pane>
      </el-tabs>
      <el-empty v-if="!filtered.staff.length && !filtered.members.length" description="没有可发消息的账号" :image-size="60"/>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/modules/auth'
import {
  getChatContacts, getChatConversations, getChatThread, sendChatMessage,
  type ChatContact, type ChatContactGroups, type ChatConversation, type ChatThread,
} from '@/api/modules/chat'

defineProps<{ embedded?: boolean }>()

const loading = ref(false)
const sending = ref(false)
const conversations = ref<ChatConversation[]>([])
const contacts = ref<ChatContactGroups>({ staff: [], members: [] })
const thread = ref<ChatThread | null>(null)
const activeUserId = ref<number | null>(null)
const draft = ref('')
const pickerVisible = ref(false)
const pickerTab = ref<'staff' | 'members'>('staff')
const keyword = ref('')
const scrollBox = ref<HTMLElement>()
let timer: number | undefined
let disposed = false
let selectionVersion = 0
let threadRequestVersion = 0
let listRequestVersion = 0
let reloadVersion = 0
let polling = false
const drafts = new Map<number, string>()
const authStore = useAuthStore()
const sessionGeneration = authStore.sessionGeneration
const isCurrentView = () => !disposed && authStore.sessionGeneration === sessionGeneration
const isSelected = (userId: number, version: number) => isCurrentView() && activeUserId.value === userId && selectionVersion === version

const filtered = computed(() => {
  const kw = keyword.value.trim()
  const match = (c: ChatContact) => !kw || (c.name || '').includes(kw)
  return { staff: contacts.value.staff.filter(match), members: contacts.value.members.filter(match) }
})
const timeText = (v?: string) => {
  if (!v) return ''
  const d = new Date(v)
  const today = new Date()
  const sameDay = d.toDateString() === today.toDateString()
  const hm = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  return sameDay ? hm : `${d.getMonth() + 1}/${d.getDate()} ${hm}`
}

const reload = async () => {
  if (!isCurrentView()) return
  const version = ++reloadVersion
  const listVersion = ++listRequestVersion
  loading.value = true
  try {
    const [c, ct] = await Promise.all([getChatConversations(), getChatContacts()])
    if (!isCurrentView() || version !== reloadVersion) return
    if (listVersion === listRequestVersion) conversations.value = c.data
    contacts.value = ct.data
  } finally { if (isCurrentView() && version === reloadVersion) loading.value = false }
}
const reloadConversations = async () => {
  if (!isCurrentView()) return
  const version = ++listRequestVersion
  const result = await getChatConversations()
  if (isCurrentView() && version === listRequestVersion) conversations.value = result.data
}
const loadThread = async (userId: number, version: number, scroll = true) => {
  const requestVersion = ++threadRequestVersion
  const result = await getChatThread(userId)
  if (!isSelected(userId, version) || requestVersion !== threadRequestVersion) return
  const hasNewMessages = result.data.messages.length !== (thread.value?.messages.length ?? 0)
  thread.value = result.data
  if (scroll || hasNewMessages) await scrollToBottom()
}
const openThread = async (userId: number) => {
  if (!isCurrentView()) return
  if (activeUserId.value !== userId) {
    if (activeUserId.value !== null) drafts.set(activeUserId.value, draft.value)
    draft.value = drafts.get(userId) || ''
  }
  const version = ++selectionVersion
  activeUserId.value = userId
  thread.value = null
  await loadThread(userId, version)
  if (!isSelected(userId, version)) return
  // 打开即已读，刷新列表里的未读角标
  await reloadConversations()
}
const startWith = async (c: ChatContact) => {
  pickerVisible.value = false
  if (!conversations.value.some(x => x.userId === c.userId)) {
    conversations.value = [{ userId: c.userId, name: c.name, roleName: c.roleName, roleCode: c.roleCode, lastContent: '', lastAt: new Date().toISOString(), unread: 0 }, ...conversations.value]
  }
  await openThread(c.userId)
}
const openPicker = () => { keyword.value = ''; pickerTab.value = 'staff'; pickerVisible.value = true }
const send = async () => {
  const content = draft.value.trim()
  const userId = activeUserId.value
  const version = selectionVersion
  if (!isCurrentView() || sending.value || !content || !userId || thread.value?.userId !== userId) return
  sending.value = true
  try {
    await sendChatMessage(userId, content)
    if (!isSelected(userId, version)) return
    if (draft.value.trim() === content) draft.value = ''
    await loadThread(userId, version)
    if (!isSelected(userId, version)) return
    await reloadConversations()
  } catch (e: any) {
    if (isSelected(userId, version)) ElMessage.error(e?.message || '发送失败')
  } finally { if (isCurrentView()) sending.value = false }
}
const scrollToBottom = async () => {
  await nextTick()
  if (isCurrentView() && scrollBox.value) scrollBox.value.scrollTop = scrollBox.value.scrollHeight
}

onMounted(async () => {
  await reload()
  if (!isCurrentView()) return
  // 轻量轮询：对方（比如会员）发来的消息能自动出现
  timer = window.setInterval(async () => {
    if (!isCurrentView()) { window.clearInterval(timer); return }
    if (!document.hidden && !polling) {
      const userId = activeUserId.value
      const version = selectionVersion
      polling = true
      try {
        await reloadConversations()
        if (userId && isSelected(userId, version) && !sending.value) await loadThread(userId, version, false)
      } catch { /* 轮询失败静默，不打断使用 */ }
      finally { polling = false }
    }
  }, 20000)
})
onBeforeUnmount(() => { disposed = true; if (timer !== undefined) window.clearInterval(timer) })
</script>

<style scoped>
.chat-panel{display:grid;grid-template-columns:280px 1fr;gap:16px;min-height:520px}
.chat-panel.embedded{min-height:420px;grid-template-columns:240px 1fr}
.chat-aside,.chat-main{background:#fff;border:1px solid #e7ecf4;border-radius:16px;display:flex;flex-direction:column;overflow:hidden}
.aside-head{display:flex;align-items:center;justify-content:space-between;padding:12px 14px;border-bottom:1px solid #eef1f6}
.aside-head strong{color:#1b263b;font-size:14px}
.conv-list{list-style:none;margin:0;padding:0;overflow:auto;flex:1}
.conv-list li{border-bottom:1px dashed #f2f5fa}
.conv-contact,.picker-contact{width:100%;border:0;background:transparent;text-align:left;font:inherit;cursor:pointer;transition:background .15s}
.conv-contact{display:block;padding:11px 14px}
.conv-contact:hover{background:#f7faff}
.conv-contact:focus-visible,.picker-contact:focus-visible{outline:2px solid #4080ff;outline-offset:-2px}
.conv-list li.active{background:#eef4ff}
.conv-list li.empty{padding:11px 14px;color:#9aa5b8;font-size:12px;line-height:1.7}
.conv-top,.conv-bottom{display:flex;align-items:center;justify-content:space-between;gap:8px}
.conv-top strong{color:#1b263b;font-size:13px}
.conv-top small{color:#9aa5b8;font-size:11px}
.conv-last{color:#7c8aa5;font-size:12px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.conv-list .role{display:block;margin-top:3px;color:#a6b0c2;font-size:11px}
.unread{min-width:18px;height:18px;padding:0 5px;border-radius:9px;background:#f56c6c;color:#fff;font-size:11px;line-height:18px;text-align:center}
.main-head{display:flex;align-items:center;justify-content:space-between;padding:12px 16px;border-bottom:1px solid #eef1f6}
.main-head strong{color:#1b263b}
.main-head small{margin-left:8px;color:#9aa5b8;font-size:12px}
.main-head .hint{color:#a6b0c2;font-size:12px}
.bubbles{flex:1;overflow:auto;padding:16px;background:#fafcff}
.bubble-row{display:flex;margin-bottom:12px}
.bubble-row.mine{justify-content:flex-end}
.bubble{max-width:76%;padding:9px 12px;border-radius:12px;background:#fff;border:1px solid #e7ecf4}
.bubble-row.mine .bubble{background:#eaf1ff;border-color:#cddffd}
.bubble p{margin:0 0 4px;color:#243249;font-size:13px;line-height:1.6;white-space:pre-wrap;word-break:break-word}
.bubble small{color:#9aa5b8;font-size:11px}
.empty-tip{text-align:center;color:#9aa5b8;font-size:12px}
.composer{display:flex;gap:10px;align-items:flex-end;padding:12px 16px;border-top:1px solid #eef1f6}
.composer .el-button{margin-bottom:2px}
.picker-list{list-style:none;margin:0;padding:0;max-height:280px;overflow:auto}
.picker-contact{display:flex;align-items:center;gap:10px;padding:9px 10px;border-radius:8px}
.picker-contact:hover{background:#f2f7ff}
.picker-list strong{color:#1b263b;font-size:13px}
.picker-list small{color:#9aa5b8;font-size:12px}
@media(max-width:900px){.chat-panel,.chat-panel.embedded{grid-template-columns:1fr}.chat-aside{max-height:240px}}
</style>
