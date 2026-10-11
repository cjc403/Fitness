<template>
  <div class="msg-page">
    <WorkspaceHero compact>
      <template #heading><span>MESSAGE CHANNELS</span><h1>消息与渠道</h1><p>站内通知之外，短信与邮件通道的发送与投递记录。当前为模拟通道：<strong>只记录投递，不会真的发出去</strong>。</p></template>
      <template #actions><el-button @click="load"><el-icon><Refresh/></el-icon> 刷新</el-button></template>
    </WorkspaceHero>

    <div class="channel-grid">
      <article v-for="c in channels" :key="c.code" :class="['channel-card', c.enabled ? '' : 'off']">
        <div class="channel-head"><strong>{{ c.name }}</strong><el-tag :type="c.enabled ? (c.mock ? 'warning' : 'success') : 'info'" size="small">{{ c.enabled ? (c.mock ? '模拟' : '真实') : '已关闭' }}</el-tag></div>
        <span class="code">{{ c.code }} · {{ c.provider || '—' }}</span>
        <small>{{ c.note }}</small>
      </article>
    </div>

    <div class="section-head"><div><h2>发一条消息</h2><p>用于演示或临时通知；发送结果会逐通道记录在下方</p></div></div>
    <el-card shadow="never" class="form-card">
      <el-form label-width="96px">
        <div class="form-grid">
          <el-form-item label="会员">
            <el-select v-model="form.memberId" filterable clearable placeholder="搜索姓名或会员编号" style="width:100%">
              <el-option v-for="m in members" :key="m.id" :label="`${m.realName} · ${m.memberNo}`" :value="m.id"/>
            </el-select>
          </el-form-item>
          <el-form-item label="通道">
            <el-select v-model="form.channels" multiple collapse-tags placeholder="默认全部已启用通道" style="width:100%">
              <el-option v-for="c in channels" :key="c.code" :label="c.name" :value="c.code" :disabled="!c.enabled"/>
            </el-select>
          </el-form-item>
          <el-form-item label="分类"><el-input v-model="form.category" placeholder="如 MEMBERSHIP / BOOKING"/></el-form-item>
          <el-form-item label="标题"><el-input v-model="form.subject" maxlength="120"/></el-form-item>
        </div>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" :rows="3" maxlength="500" show-word-limit/></el-form-item>
        <div class="form-actions"><el-button type="primary" :loading="sending" @click="send">发送</el-button></div>
      </el-form>
      <ul v-if="lastResults.length" class="result-list">
        <li v-for="r in lastResults" :key="r.id">
          <el-tag :type="r.status==='SENT'?'success':r.status==='SKIPPED'?'info':'danger'" size="small">{{ statusText(r.status) }}</el-tag>
          <strong>{{ channelName(r.channel) }}</strong><span class="muted">{{ r.recipientMasked }}</span>
          <small v-if="r.error" class="err">{{ r.error }}</small><small v-else-if="r.providerMessageId" class="muted">{{ r.providerMessageId }}</small>
        </li>
      </ul>
    </el-card>

    <div class="section-head"><div><h2>投递记录</h2><p>最近 100 条；收件人一律脱敏存储，库里没有完整手机号与邮箱</p></div><el-tag effect="plain">{{ deliveries.length }} 条</el-tag></div>
    <el-card shadow="never" class="table-card">
      <el-table :data="deliveries" stripe max-height="520">
        <el-table-column label="时间" width="150"><template #default="{row}">{{ dateText(row.createdAt) }}</template></el-table-column>
        <el-table-column label="通道" width="90"><template #default="{row}">{{ channelName(row.channel) }}</template></el-table-column>
        <el-table-column label="收件人" width="150" prop="recipientMasked"/>
        <el-table-column label="分类" width="110" prop="category"/>
        <el-table-column label="标题" min-width="180" prop="subject"/>
        <el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="row.status==='SENT'?'success':row.status==='SKIPPED'?'info':'danger'" size="small">{{ statusText(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="供应商" width="110"><template #default="{row}">{{ row.provider || '—' }}</template></el-table-column>
        <el-table-column label="说明" min-width="180"><template #default="{row}"><small :class="row.error ? 'err' : 'muted'">{{ row.error || row.providerMessageId || '—' }}</small></template></el-table-column>
      </el-table>
      <el-empty v-if="!deliveries.length" description="还没有投递记录，发一条试试" :image-size="60"/>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import WorkspaceHero from '@/components/WorkspaceHero.vue'
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { listMembers, type Member } from '@/api/modules/member'
import { getMessageChannels, listDeliveries, sendMessage, type ChannelView, type Delivery } from '@/api/modules/messages'

const channels = ref<ChannelView[]>([])
const deliveries = ref<Delivery[]>([])
const members = ref<Member[]>([])
const lastResults = ref<Delivery[]>([])
const sending = ref(false)
const form = reactive({ memberId: undefined as number | undefined, channels: [] as string[], category: 'MEMBERSHIP', subject: '', content: '' })

const channelName = (code: string) => channels.value.find(c => c.code === code)?.name || code
const statusText = (s: string) => ({ SENT: '已发送', FAILED: '失败', SKIPPED: '已跳过' }[s] || s)
const dateText = (v?: string) => v ? new Date(v).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '—'

const load = async () => {
  const [c, d] = await Promise.all([getMessageChannels(), listDeliveries()])
  channels.value = c.data
  deliveries.value = d.data
}
const send = async () => {
  if (!form.memberId) return ElMessage.warning('请选择会员')
  if (!form.subject.trim() || !form.content.trim()) return ElMessage.warning('请填写标题与内容')
  sending.value = true
  try {
    const r = await sendMessage({
      memberId: form.memberId, channels: form.channels.length ? form.channels : undefined,
      category: form.category.trim() || 'MEMBERSHIP', subject: form.subject.trim(), content: form.content.trim(),
    })
    lastResults.value = r.data
    const sent = r.data.filter(x => x.status === 'SENT').length
    ElMessage[sent ? 'success' : 'info'](`已投递 ${r.data.length} 条（成功 ${sent} 条）`)
    form.subject = ''; form.content = ''
    await load()
  } finally { sending.value = false }
}
onMounted(async () => {
  await load()
  members.value = (await listMembers({ page: 1, size: 100 })).data.items
})
</script>

<style scoped>
.msg-page{max-width:1200px;margin:0 auto}.page-title{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin-bottom:20px}.page-title>div>span{color:#3474ff;font-size:11px;font-weight:800;letter-spacing:1.8px}.page-title h1{margin:7px 0;color:#172033;font-size:29px}.page-title p{margin:0;color:#8792a5;max-width:760px}.page-title strong{color:#d8871a}.channel-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:14px}.channel-card{padding:18px;border:1px solid #e7ecf4;border-radius:16px;background:#fff}.channel-card.off{opacity:.65}.channel-head{display:flex;align-items:center;justify-content:space-between}.channel-head strong{color:#1b263b}.channel-card .code{display:block;margin:8px 0 6px;color:#3474ff;font-size:12px;font-weight:700}.channel-card small{color:#9aa5b8;font-size:12px;line-height:1.6}.section-head{display:flex;align-items:center;justify-content:space-between;margin:26px 0 12px;gap:12px}.section-head h2{margin:0 0 5px;color:#1b263b}.section-head p{margin:0;color:#8792a5;font-size:12px}.form-card,.table-card{border-radius:16px}.form-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:0 18px}.form-actions{display:flex;justify-content:flex-end}.result-list{list-style:none;margin:14px 0 0;padding:12px 0 0;border-top:1px dashed #eef1f6}.result-list li{display:flex;align-items:center;gap:10px;padding:6px 0;font-size:13px;color:#31405a}.muted{color:#9aa5b8}.err{color:#e85d6a}.el-table small{display:block}@media(max-width:900px){.channel-grid,.form-grid{grid-template-columns:1fr}}
</style>
