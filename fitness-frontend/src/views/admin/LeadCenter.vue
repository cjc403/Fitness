<template>
  <div v-if="!sessionExpired" class="leads-page">
    <WorkspaceHero compact>
      <template #heading><span>LEADS &amp; TRIALS</span><h1>潜客与体验课</h1><p>{{ scopeText }}。从第一次咨询到体验、开卡，每次联系都有下一步。</p></template>
      <template #actions><el-button type="primary" :disabled="saving" @click="openAction('create')">新建潜客</el-button><el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon> 刷新</el-button></template>
      <template #footer><span class="hero-note">体验预约独立管理，由接待顾问协调活动；开卡以实际收款记录或已核验订单为依据。</span></template>
    </WorkspaceHero>

    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" show-icon class="page-error"/>
    <div class="metrics" :aria-busy="loading">
      <article><span>范围内潜客</span><strong>{{ metric('totalLeads') }}</strong><small>本月新增 {{ metric('newThisMonth') }} 人</small></article>
      <button type="button" class="metric attention" @click="selectFilter('TODAY')"><span>今日待联系</span><strong>{{ metric('todayFollowUps') }}</strong><small>查看今天的联系约定 →</small></button>
      <button type="button" class="metric overdue" @click="selectFilter('OVERDUE')"><span>逾期联系</span><strong>{{ metric('overdueFollowUps') }}</strong><small>优先处理错过的约定 →</small></button>
      <button type="button" class="metric" @click="selectFilter('TRIAL_TODAY')"><span>今日体验预约</span><strong>{{ metric('todayTrials') }}</strong><small>查看待接待潜客 →</small></button>
      <article class="converted"><span>有效开卡</span><strong>{{ metric('convertedLeads') }}<em> 人</em></strong><small>有效转化率 {{ metric('conversionRate') }}%</small></article>
    </div>

    <section class="workbench-section">
      <div class="section-heading"><div><h2>潜客工作台</h2><p>查看联系约定和体验安排，打开档案记录进展。</p></div><span class="count">{{ data.leads.total }} 条筛选结果</span></div>
      <el-card shadow="never" class="workbench-card">
        <div class="filter-row">
          <div class="filter-tabs" role="group" aria-label="潜客任务筛选"><button v-for="item in filterOptions" :key="item.value" type="button" :class="{ selected: filter === item.value }" :aria-pressed="filter === item.value" @click="selectFilter(item.value)">{{ item.label }}</button></div>
          <div class="search"><el-input v-model="keyword" placeholder="姓名或联系电话" aria-label="搜索潜客" clearable maxlength="80" @keyup.enter="search" @clear="search"/><el-button type="primary" plain @click="search">查询</el-button></div>
        </div>
        <div class="select-row"><el-select v-if="isHeadquarters" v-model="venueId" clearable placeholder="全部门店" aria-label="筛选门店" @change="search"><el-option v-for="item in venues" :key="item.id" :label="item.name" :value="item.id"/></el-select><el-select v-model="stage" aria-label="筛选潜客阶段" @change="search"><el-option label="全部阶段" value="ALL"/><el-option v-for="item in stageOptions" :key="item.value" :label="item.label" :value="item.value"/></el-select><span>统计按可见范围计算，筛选与分页只影响列表。</span></div>
        <div v-loading="loading" class="lead-list">
          <article v-for="row in data.leads.records" :key="row.id" class="lead-card">
            <div class="lead-heading"><div><strong>{{ row.name }}</strong><el-tag size="small" effect="plain">{{ intentText(row.intent) }}</el-tag><small>{{ row.phone }} · {{ row.venueName }} · {{ row.ownerName }}</small></div><el-tag :type="stageType(row.stage)">{{ stageText(row.stage) }}</el-tag></div>
            <div class="lead-facts"><div><span>下次联系</span><strong :class="{ overdueText: isOverdue(row.nextFollowAt) }">{{ row.nextFollowAt ? dateText(row.nextFollowAt) : '尚未约定' }}</strong></div><div><span>体验安排</span><strong>{{ row.nextTrial ? dateText(row.nextTrial.startsAt) : '暂无待体验预约' }}</strong><small v-if="row.nextTrial">{{ row.nextTrial.hostName }} · {{ row.nextTrial.content }}</small></div><div><span>咨询来源</span><strong>{{ sourceText(row.source) }}</strong><small>建档 {{ dateText(row.createdAt) }}</small></div></div>
            <div class="lead-footer"><span v-if="row.stage === 'CONVERTED'">开卡净实收 ¥{{ money(row.conversionNetAmount) }}</span><span v-else>{{ row.note || '打开档案查看完整跟进记录' }}</span><el-button type="primary" plain :disabled="saving" @click="openDetail(row)" data-agent-type="lead" :data-agent-id="row.id" :data-agent-label="row.name" data-agent-action="open">查看档案</el-button></div>
          </article>
          <el-empty v-if="!loading && !loadError && !data.leads.records.length" description="当前范围和筛选下没有潜客" :image-size="70"/>
          <div v-if="loadError && !loading" class="unavailable"><p>暂时无法加载潜客，请稍后重试。</p><el-button @click="load">重新加载</el-button></div>
        </div>
        <el-pagination v-if="data.leads.total" v-model:current-page="page" :page-size="20" :total="data.leads.total" layout="prev, pager, next" :pager-count="5" class="pagination" @current-change="load"/>
      </el-card>
    </section>

    <section class="channel-section"><div class="section-heading"><div><h2>咨询渠道表现</h2><p>到店和体验按人去重；已开卡订单全额退款后，有效转化数会减少，开卡历史保留。</p></div></div><el-card shadow="never" class="channel-card"><el-table :data="loadError ? [] : data.channels" stripe><el-table-column label="咨询来源" min-width="130"><template #default="{ row }">{{ sourceText(row.source) }}</template></el-table-column><el-table-column prop="total" label="潜客" width="85"/><el-table-column prop="visited" label="已到店" width="100"/><el-table-column prop="trialCompleted" label="已体验" width="100"/><el-table-column prop="converted" label="有效开卡" width="110"/><el-table-column label="有效转化率" min-width="130"><template #default="{ row }">{{ Number(row.conversionRate).toFixed(1) }}%</template></el-table-column></el-table><el-empty v-if="!loading && !loadError && !data.channels.length" description="暂无渠道数据" :image-size="60"/></el-card></section>

    <el-drawer v-model="detailDialog" title="潜客档案" size="min(720px, 100vw)" :before-close="closeDetail">
      <div v-loading="detailLoading" class="detail-body">
        <el-alert v-if="detailError" :title="detailError" type="error" show-icon :closable="false"/><el-button v-if="detailError && current" class="retry-detail" @click="openDetail(current)">重试加载</el-button>
        <template v-if="detailData && !detailError">
          <div class="profile-heading"><div><h2>{{ detailData.lead.name }}</h2><p>{{ detailData.lead.phone }} · {{ detailData.lead.venueName }}</p></div><el-tag :type="stageType(detailData.lead.stage)">{{ stageText(detailData.lead.stage) }}</el-tag></div>
          <div class="profile-facts"><div><span>负责顾问</span><strong>{{ detailData.lead.ownerName }}</strong></div><div><span>咨询来源 / 意向</span><strong>{{ sourceText(detailData.lead.source) }} / {{ intentText(detailData.lead.intent) }}</strong></div><div><span>下次联系</span><strong>{{ dateText(detailData.lead.nextFollowAt) }}</strong></div><div><span>建档时间</span><strong>{{ dateText(detailData.lead.createdAt) }}</strong></div></div>
          <p v-if="detailData.lead.note" class="profile-note">{{ detailData.lead.note }}</p>
          <div class="detail-actions"><el-button :disabled="saving" @click="openAction('profile', detailData.lead)">编辑资料</el-button><el-button v-if="detailData.lead.stage !== 'CONVERTED'" :disabled="saving" type="primary" @click="openAction('follow', detailData.lead)">{{ detailData.lead.stage === 'LOST' ? '重新跟进' : '记录跟进' }}</el-button><el-button v-if="canBook(detailData.lead)" :disabled="saving" @click="openAction('trial', detailData.lead)">预约体验</el-button><el-button v-if="canConvert(detailData.lead)" :disabled="saving" type="success" plain @click="openAction('convert', detailData.lead)">办理开卡</el-button></div>
          <p v-if="detailData.lead.nextTrial" class="detail-tip">请先登记待体验预约的结果，再办理开卡或标记流失。</p>
          <div v-if="detailData.lead.stage === 'CONVERTED'" class="conversion-record"><strong>已关联真实开卡订单 #{{ detailData.lead.convertedOrderId }}</strong><p>会员 #{{ detailData.lead.convertedMemberId }} · 当前净实收 ¥{{ money(detailData.lead.conversionNetAmount) }}</p><small>开卡时间 {{ dateText(detailData.lead.convertedAt) }}。新建会员不自动开通登录账号。</small></div>
          <h3>体验预约</h3><p class="detail-tip">独立体验安排，不预占团课名额、教练或教室资源。</p>
          <div v-for="trial in detailData.trials" :key="trial.id" class="trial-card"><div><strong>{{ trial.content }}</strong><el-tag size="small" :type="trial.status === 'BOOKED' ? 'warning' : 'info'">{{ trialText(trial.status) }}</el-tag></div><p>{{ dateText(trial.startsAt) }} — {{ dateText(trial.endsAt) }}</p><small>接待：{{ trial.hostName }}</small><p v-if="trial.resultNote" class="record-text">{{ trial.resultNote }}</p><el-button v-if="trial.status === 'BOOKED'" size="small" :disabled="saving" @click="openAction('result', detailData.lead, trial)">登记体验结果</el-button></div>
          <el-empty v-if="!detailData.trials.length" description="尚无体验预约" :image-size="45"/>
          <h3>完整记录</h3><el-timeline><el-timeline-item v-for="event in detailData.events" :key="event.id" :timestamp="dateText(event.createdAt)" placement="top"><div class="event-heading"><strong>{{ eventText(event.type) }}</strong><span>{{ event.actorName }}</span><el-tag v-if="event.outcome" size="small" effect="plain">{{ outcomeText(event.outcome) }}</el-tag></div><p class="record-text">{{ event.content }}</p><small v-if="event.nextFollowAt">下次联系 {{ dateText(event.nextFollowAt) }}</small></el-timeline-item></el-timeline><el-empty v-if="!detailData.events.length" description="暂无记录" :image-size="45"/>
        </template>
      </div>
    </el-drawer>

    <el-dialog v-model="operationDialog" :title="actionTitle" width="min(590px, calc(100vw - 24px))" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" :before-close="closeOperation">
      <div v-if="current" class="dialog-context"><strong>{{ current.name }} · {{ current.phone }}</strong><span>{{ current.venueName }} · {{ current.ownerName }}</span></div>
      <el-alert v-if="operationError" :title="operationError" type="error" show-icon :closable="false" class="operation-error"/>
      <el-button v-if="conflict" @click="reloadConflict" :disabled="saving">重新加载最新档案</el-button><el-button v-if="optionsError && !conflict" :loading="optionsLoading" @click="reloadActionOptions">重试加载选项</el-button>
      <el-form label-position="top" :disabled="saving || optionsLoading || !!optionsError || conflict" class="operation-form">
        <template v-if="operation === 'create' || operation === 'profile'">
          <div class="form-grid"><el-form-item label="姓名"><el-input v-model="operationForm.name" maxlength="50" placeholder="填写姓名"/></el-form-item><el-form-item label="意向程度"><el-select v-model="operationForm.intent"><el-option v-for="item in intentOptions" :key="item.value" :value="item.value" :label="item.label"/></el-select></el-form-item></div>
          <template v-if="operation === 'create'"><div class="form-grid"><el-form-item label="联系电话"><el-input v-model="operationForm.phone" maxlength="11" inputmode="tel" placeholder="大陆手机号，创建后固定"/></el-form-item><el-form-item label="咨询来源"><el-select v-model="operationForm.source"><el-option v-for="item in sourceOptions" :key="item.value" :label="item.label" :value="item.value"/></el-select></el-form-item></div><el-form-item label="门店"><el-select v-model="operationForm.venueId" :disabled="!isHeadquarters" @change="changeCreateVenue"><el-option v-for="item in createVenueOptions" :key="item.id" :label="item.name" :value="item.id"/></el-select></el-form-item></template>
          <p v-else class="dialog-note">联系电话、咨询来源和门店保持建档时的记录。</p>
          <el-form-item label="负责顾问"><el-select v-model="operationForm.ownerId" :disabled="!canAssign"><el-option v-for="item in ownerOptions" :key="item.id" :label="item.name" :value="item.id"/></el-select></el-form-item><el-form-item label="备注"><el-input v-model="operationForm.note" type="textarea" :rows="3" maxlength="1000" show-word-limit/></el-form-item><el-form-item v-if="operation === 'create'" label="下次联系（上海时间，可留空）"><el-date-picker v-model="operationForm.nextFollowAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="填写未来时间"/></el-form-item><p v-if="operation === 'create'" class="dialog-note">只建立潜客档案，不生成会员、订单或登录账号。同一门店的联系电话只能建档一次。</p>
        </template>
        <template v-if="operation === 'follow'"><div class="form-grid"><el-form-item label="联系方式"><el-select v-model="operationForm.method"><el-option v-for="item in methodOptions" :key="item.value" :label="item.label" :value="item.value"/></el-select></el-form-item><el-form-item label="跟进结果" data-agent-field="outcome"><el-select v-model="operationForm.outcome"><el-option v-for="item in allowedOutcomes" :key="item.value" :label="item.label" :value="item.value"/></el-select></el-form-item></div><el-form-item label="跟进内容"><el-input v-model="operationForm.content" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="记录实际沟通和下一步约定"/></el-form-item><el-form-item label="下次联系（上海时间，可留空）"><el-date-picker v-model="operationForm.nextFollowAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="填写未来时间"/></el-form-item><p class="dialog-note">沟通结果不会生成开卡订单；开卡请使用“办理开卡”。每次记录追加保留。</p></template>
        <template v-if="operation === 'trial'"><el-form-item label="接待顾问"><el-select v-model="operationForm.hostId"><el-option v-for="item in localAdvisors" :key="item.id" :label="item.name" :value="item.id"/></el-select></el-form-item><div class="form-grid"><el-form-item label="开始时间（上海时间）"><el-date-picker v-model="operationForm.startsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"/></el-form-item><el-form-item label="结束时间（上海时间）"><el-date-picker v-model="operationForm.endsAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"/></el-form-item></div><el-form-item label="体验内容"><el-input v-model="operationForm.content" type="textarea" :rows="3" maxlength="1000" placeholder="例如：器械入门与场馆参观"/></el-form-item><p class="dialog-note">预约 15—180 分钟，同一接待顾问的待体验时间不能重叠。顾问负责协调实际活动。</p></template>
        <template v-if="operation === 'result'"><div class="dialog-context"><span>{{ selectedTrial?.content }} · {{ dateText(selectedTrial?.startsAt) }}</span></div><el-form-item label="体验结果"><el-select v-model="operationForm.status"><el-option label="已体验" value="ATTENDED" :disabled="!trialHasStarted"/><el-option label="未到店" value="NO_SHOW" :disabled="!trialHasStarted"/><el-option label="已取消" value="CANCELLED"/></el-select></el-form-item><el-form-item label="结果说明"><el-input v-model="operationForm.note" type="textarea" :rows="3" maxlength="1000" show-word-limit/></el-form-item><p class="dialog-note">已体验或未到店只可在预约开始后登记；取消后可以重新预约。</p></template>
        <template v-if="operation === 'convert'">
          <el-form-item label="开卡方式"><el-radio-group v-model="operationForm.mode"><el-radio-button value="NEW">创建会员并记账开卡</el-radio-button><el-radio-button value="LINK">关联已办理订单</el-radio-button></el-radio-group></el-form-item>
          <template v-if="operationForm.mode === 'NEW'"><el-alert title="请先确认已实际收到款项。此处登记收款和开卡，不发起线上支付。" type="warning" :closable="false" show-icon/><el-form-item label="开卡产品"><el-select v-model="operationForm.productId" placeholder="本门店在售产品"><el-option v-for="item in localProducts" :key="item.id" :value="item.id" :label="`${item.name} · ¥${money(item.price)}`"/></el-select></el-form-item><div class="form-grid"><el-form-item label="实际收款渠道"><el-select v-model="operationForm.paymentMethod"><el-option v-for="item in paymentOptions" :key="item.value" :value="item.value" :label="item.label"/></el-select></el-form-item><el-form-item label="实际收款金额（元）"><el-input v-model="operationForm.paidAmount" inputmode="decimal" placeholder="至少 0.01，最多两位小数"/></el-form-item></div><p class="dialog-note">将创建会员编号 LEAD-{{ current?.id }}，按真实收款开卡并生成权益。新会员不自动开通登录账号；若联系电话已有会员，请使用合格订单关联或已有会员流程。</p></template>
          <template v-else><p class="dialog-note">只展示同门店、同联系电话及建档后办理的合格新开卡订单。选择后保留原会员资料与账号。</p><el-form-item label="已核验的开卡订单"><el-select v-model="operationForm.orderId" placeholder="选择合格订单"><el-option v-for="item in candidateOrders" :key="item.orderId" :value="item.orderId" :label="`${item.orderNo} · ${item.memberName} · ${item.productName} · 净实收 ¥${money(item.netAmount)}`"/></el-select></el-form-item><div v-if="selectedOrder" class="order-preview"><strong>{{ selectedOrder.memberNo }} · {{ selectedOrder.memberName }}</strong><p>{{ selectedOrder.productName }} · 净实收 ¥{{ money(selectedOrder.netAmount) }}</p><small>支付时间 {{ dateText(selectedOrder.paidAt) }}</small></div><el-empty v-if="!optionsLoading && !candidateOrders.length" description="暂无合格订单，请在已有会员流程办理后重新加载" :image-size="50"/></template>
        </template>
      </el-form>
      <template #footer><el-button :disabled="saving" @click="closeOperation()">取消</el-button><el-button type="primary" :loading="saving" :disabled="optionsLoading || !!optionsError || conflict" @click="submitOperation">{{ operation === 'convert' ? operationForm.mode === 'NEW' ? '确认已收款并开卡' : '确认关联订单' : '保存记录' }}</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import WorkspaceHero from '@/components/WorkspaceHero.vue'
import { useAuthStore } from '@/store/modules/auth'
import { secureUuid } from '@/utils/financeUi.mjs'
import { getLeadWorkbench, getLeadDetail, getLeadOptions, getConversionOptions, createLead, updateLeadProfile, addLeadFollowUp, bookLeadTrial, recordLeadTrialResult, convertLead, type LeadView, type LeadDetail, type TrialView, type LeadOptions, type LeadWorkbench, type ConversionOrder, type LeadStage, type LeadFilter, type CreateLeadRequest, type ProfileLeadRequest, type FollowLeadRequest, type BookTrialRequest, type TrialResultRequest, type ConvertLeadRequest } from '@/api/modules/leads'

type Action = 'create' | 'profile' | 'follow' | 'trial' | 'result' | 'convert'
const auth = useAuthStore(), pageGeneration = auth.sessionGeneration
const authScope = () => JSON.stringify([auth.userInfo?.id, auth.userInfo?.role, auth.userInfo?.organizationId, [...(auth.userInfo?.permissions || [])].sort()])
const pageScope = authScope(), sessionExpired = ref(false)
const isHeadquarters = computed(() => auth.userInfo?.role === 'SUPER_ADMIN')
const canAssign = computed(() => ['MANAGER', 'SUPER_ADMIN'].includes(auth.userInfo?.role || ''))
const scopeText = computed(() => auth.userInfo?.role === 'SALES' ? '显示本门店归属我的潜客' : isHeadquarters.value ? '显示总部权限范围内的潜客' : '显示本门店潜客')
const emptyWorkbench = (): LeadWorkbench => ({ summary: { totalLeads: 0, newThisMonth: 0, todayFollowUps: 0, overdueFollowUps: 0, todayTrials: 0, convertedLeads: 0, conversionRate: 0 }, channels: [], leads: { records: [], total: 0, page: 1, size: 20 } })
const emptyOptions = (): LeadOptions => ({ venues: [], advisors: [], products: [] })
const data = ref<LeadWorkbench>(emptyWorkbench()), loaded = ref(false), loading = ref(false), loadError = ref('')
const filter = ref<LeadFilter>('ALL'), stage = ref<LeadStage | 'ALL'>('ALL'), keyword = ref(''), page = ref(1), venueId = ref<number>(), venues = ref<LeadOptions['venues']>([])
const detailDialog = ref(false), detailData = ref<LeadDetail | null>(null), detailLoading = ref(false), detailError = ref(''), current = ref<LeadView | null>(null)
const operation = ref<Action>('create'), operationDialog = ref(false), saving = ref(false), operationError = ref(''), conflict = ref(false)
const operationOptions = ref<LeadOptions>(emptyOptions()), candidateOrders = ref<ConversionOrder[]>([]), optionsLoading = ref(false), optionsError = ref(''), selectedTrial = ref<TrialView | null>(null)
const createVenueOptions = ref<LeadOptions['venues']>([])
const defaults = () => ({ name: '', phone: '', source: 'WALK_IN', intent: 'MEDIUM', ownerId: undefined as number | undefined, venueId: undefined as number | undefined, note: '', nextFollowAt: '' as string | null, method: 'CALL', content: '', outcome: 'CONTACTED', hostId: undefined as number | undefined, startsAt: '', endsAt: '', status: 'CANCELLED', mode: 'NEW', productId: undefined as number | undefined, paymentMethod: 'WECHAT', paidAmount: '', orderId: undefined as number | undefined })
const operationForm = reactive(defaults())
const currentTime = ref(Date.now())
let clockTimer: ReturnType<typeof setInterval> | undefined
let active = true, loadSequence = 0, detailSequence = 0, optionsSequence = 0, actionSequence = 0
let retryIntent: { fingerprint: string; key: string } | null = null
const sessionIsCurrent = (generation = pageGeneration) => active && generation === auth.sessionGeneration && pageGeneration === auth.sessionGeneration
const invalidateSession = () => {
  active = false; sessionExpired.value = true; loadSequence++; detailSequence++; optionsSequence++; actionSequence++
  if (clockTimer !== undefined) { clearInterval(clockTimer); clockTimer = undefined }
  retryIntent = null; data.value = emptyWorkbench(); loaded.value = false; loading.value = false; loadError.value = ''
  detailDialog.value = false; detailData.value = null; detailLoading.value = false; detailError.value = ''; current.value = null; selectedTrial.value = null
  operationDialog.value = false; operationOptions.value = emptyOptions(); createVenueOptions.value = []; candidateOrders.value = []; optionsLoading.value = false; optionsError.value = ''; operationError.value = ''; saving.value = false; conflict.value = false
  Object.assign(operationForm, defaults()); keyword.value = ''; filter.value = 'ALL'; stage.value = 'ALL'; page.value = 1; venueId.value = undefined; venues.value = []
}
// clearSession happens before the logout HTTP request and navigation; invalidate synchronously, including already-rendered records.
watch(() => [auth.sessionGeneration, authScope()], ([generation, scope]) => { if (generation !== pageGeneration || scope !== pageScope) invalidateSession() }, { flush: 'sync' })
onUnmounted(invalidateSession)

const sourceOptions = [{ value: 'WALK_IN', label: '自然到店' }, { value: 'REFERRAL', label: '转介绍' }, { value: 'DOUYIN', label: '抖音' }, { value: 'WECHAT', label: '微信' }, { value: 'OTHER', label: '其他' }]
const intentOptions = [{ value: 'HIGH', label: '高意向' }, { value: 'MEDIUM', label: '中意向' }, { value: 'LOW', label: '低意向' }]
const stageOptions = [{ value: 'CONSULTING', label: '咨询中' }, { value: 'VISITED', label: '已到店' }, { value: 'TRIAL_BOOKED', label: '待体验' }, { value: 'TRIAL_COMPLETED', label: '已体验' }, { value: 'LOST', label: '已流失' }, { value: 'CONVERTED', label: '已开卡' }]
const filterOptions: { value: LeadFilter; label: string }[] = [{ value: 'ALL', label: '全部潜客' }, { value: 'TODAY', label: '今日联系' }, { value: 'OVERDUE', label: '逾期联系' }, { value: 'TRIAL_TODAY', label: '今日体验' }]
const methodOptions = [{ value: 'CALL', label: '电话' }, { value: 'WECHAT', label: '微信' }, { value: 'VISIT', label: '到店' }, { value: 'OTHER', label: '其他' }]
const outcomeOptions = [{ value: 'CONTACTED', label: '已联系' }, { value: 'NO_CONTACT', label: '未联系上' }, { value: 'VISITED', label: '已到店' }, { value: 'INTERESTED', label: '有意向' }, { value: 'DECLINED', label: '已流失' }, { value: 'REOPEN', label: '重新跟进' }]
const paymentOptions = [{ value: 'CARD', label: '银行卡' }, { value: 'WECHAT', label: '微信' }, { value: 'ALIPAY', label: '支付宝' }, { value: 'CASH', label: '现金' }]
const textOf = (options: { value: string; label: string }[], value?: string | null) => options.find(item => item.value === value)?.label || '未记录'
const sourceText = (value: string) => textOf(sourceOptions, value), stageText = (value: string) => textOf(stageOptions, value), intentText = (value: string) => textOf(intentOptions, value)
const trialText = (value: string) => ({ BOOKED: '待体验', ATTENDED: '已体验', NO_SHOW: '未到店', CANCELLED: '已取消' }[value] || '未记录')
const outcomeText = (value: string) => outcomeOptions.some(item => item.value === value) ? textOf(outcomeOptions, value) : trialText(value)
const eventText = (value: string) => ({ CREATED: '潜客建档', CREATE: '潜客建档', PROFILE_UPDATED: '资料更新', PROFILE: '资料更新', FOLLOW_UP: '跟进记录', TRIAL_BOOKED: '预约体验', TRIAL: '预约体验', TRIAL_RESULT: '体验结果', CONVERTED: '办理开卡', CONVERT: '办理开卡' }[value] || '业务记录')
const stageType = (value: string): 'success' | 'warning' | 'info' | 'primary' => value === 'CONVERTED' ? 'success' : value === 'TRIAL_BOOKED' ? 'warning' : value === 'LOST' ? 'info' : 'primary'
const metric = (key: keyof LeadWorkbench['summary']) => loaded.value && !loadError.value ? data.value.summary[key] : '—'
const money = (value?: number | null) => Number(value || 0).toFixed(2)
// API dates are Shanghai wall-clock values; do not reinterpret them in the viewer's timezone.
const dateText = (value?: string | null) => value ? value.slice(0, 16).replace('T', ' ').replace(/-/g, '/') : '—'
const shanghaiTime = (value?: string | null) => {
  if (!value || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(value)) return NaN
  const time = Date.parse(value + '+08:00')
  return Number.isFinite(time) && new Date(time + 8 * 3600000).toISOString().slice(0, 19) === value ? time : NaN
}
const isOverdue = (value?: string | null) => !!value && shanghaiTime(value) < currentTime.value
const booked = (row: LeadView) => row.nextTrial?.status === 'BOOKED' || row.stage === 'TRIAL_BOOKED'
const canBook = (row: LeadView) => !booked(row) && !['LOST', 'CONVERTED'].includes(row.stage)
const canConvert = (row: LeadView) => !booked(row) && !['LOST', 'CONVERTED'].includes(row.stage)
const actionTitle = computed(() => ({ create: '新建潜客', profile: '编辑潜客资料', follow: '记录跟进', trial: '预约独立体验', result: '登记体验结果', convert: '办理开卡' }[operation.value]))
const localAdvisors = computed(() => operationOptions.value.advisors.filter(item => item.venueId === (current.value?.venueId || operationForm.venueId)))
const ownerOptions = computed(() => canAssign.value ? localAdvisors.value : localAdvisors.value.filter(item => item.id === auth.userInfo?.id))
const localProducts = computed(() => operationOptions.value.products.filter(item => item.venueId === current.value?.venueId))
const allowedOutcomes = computed(() => outcomeOptions.filter(item => current.value?.stage === 'LOST' ? item.value === 'REOPEN' : item.value !== 'REOPEN' && !(current.value && booked(current.value) && item.value === 'DECLINED')))
const trialHasStarted = computed(() => !!selectedTrial.value && shanghaiTime(selectedTrial.value.startsAt) <= currentTime.value)
const selectedOrder = computed(() => candidateOrders.value.find(item => item.orderId === operationForm.orderId))
const isConflict = (error: any) => error?.response?.status === 409 || error?.code === 409 || /版本|已更新|version|conflict/i.test(String(error?.response?.data?.message || error?.message || ''))
const safeError = (error: any, fallback: string) => {
  const message = error?.response?.status === 500 ? '' : String(error?.response?.data?.message || error?.message || '')
  // Do not expose exception/SQL/transport internals in an operator's workflow.
  return message.length <= 160 && /[\u4e00-\u9fff]/.test(message) && !/SQL|select\s|insert\s|update\s|Exception|constraint|Jdbc|Hibernate|password|\bat\s|[<>]/i.test(message) ? message : fallback
}

const load = async () => {
  if (!sessionIsCurrent()) return
  const sequence = ++loadSequence, generation = auth.sessionGeneration
  const params = { ...(venueId.value ? { venueId: venueId.value } : {}), stage: stage.value, filter: filter.value, ...(keyword.value.trim() ? { keyword: keyword.value.trim() } : {}), page: page.value, size: 20 }
  loading.value = true; loadError.value = ''; data.value.leads.records = []
  try { const response = await getLeadWorkbench(params); if (sequence === loadSequence && sessionIsCurrent(generation)) { data.value = response.data; loaded.value = true } }
  catch (error: any) { if (sequence === loadSequence && sessionIsCurrent(generation)) { data.value = emptyWorkbench(); loadError.value = safeError(error, '潜客数据加载失败，请稍后重试') } }
  finally { if (sequence === loadSequence && sessionIsCurrent(generation)) loading.value = false }
}
const search = () => { page.value = 1; load() }
const selectFilter = (value: LeadFilter) => { filter.value = value; search() }
const openDetail = async (row: LeadView) => {
  if (saving.value || !sessionIsCurrent()) return
  const sequence = ++detailSequence, generation = auth.sessionGeneration
  current.value = Object.freeze({ ...row }); detailData.value = null; detailError.value = ''; detailLoading.value = true; detailDialog.value = true
  const selected = () => sequence === detailSequence && detailDialog.value && current.value?.id === row.id && sessionIsCurrent(generation)
  try { const response = await getLeadDetail(row.id); if (selected()) { detailData.value = response.data; current.value = Object.freeze({ ...response.data.lead }) } }
  catch (error: any) { if (selected()) detailError.value = safeError(error, '档案加载失败，请重试') }
  finally { if (selected()) detailLoading.value = false }
}
const closeDetail = (done: () => void) => { if (saving.value) return; detailSequence++; detailData.value = null; done() }
const closeOperation = (done?: () => void) => { if (saving.value) return; actionSequence++; optionsSequence++; retryIntent = null; operationDialog.value = false; done?.() }
const reloadActionOptions = async () => {
  if (!sessionIsCurrent() || saving.value || !operationDialog.value) return
  const sequence = ++optionsSequence, action = actionSequence, generation = auth.sessionGeneration, type = operation.value, id = current.value?.id
  const scopeVenue = type === 'create' ? operationForm.venueId : current.value?.venueId
  optionsLoading.value = true; optionsError.value = ''; operationError.value = ''; operationOptions.value = emptyOptions(); candidateOrders.value = []
  const selected = () => sequence === optionsSequence && action === actionSequence && operationDialog.value && sessionIsCurrent(generation)
  try {
    // A scoped options response contains only one venue; retain a separate complete selector for this create form.
    const [optionsResult, ordersResult, allVenuesResult] = await Promise.all([getLeadOptions(scopeVenue), type === 'convert' && id ? getConversionOptions(id) : Promise.resolve(null), type === 'create' && isHeadquarters.value && scopeVenue && !createVenueOptions.value.length ? getLeadOptions() : Promise.resolve(null)])
    if (!selected()) return
    operationOptions.value = optionsResult.data; if (ordersResult) candidateOrders.value = ordersResult.data
    if (type === 'create' && (allVenuesResult || !scopeVenue || !isHeadquarters.value)) createVenueOptions.value = [...(allVenuesResult?.data.venues || optionsResult.data.venues)]
    if (type === 'create' && !operationForm.venueId && optionsResult.data.venues.length === 1) operationForm.venueId = optionsResult.data.venues[0].id
    if (type === 'create' && !operationForm.ownerId) operationForm.ownerId = ownerOptions.value[0]?.id
    if (type === 'trial' && !operationForm.hostId) operationForm.hostId = localAdvisors.value.find(item => item.id === current.value?.ownerId)?.id || localAdvisors.value[0]?.id
  } catch (error: any) { if (selected()) { optionsError.value = safeError(error, '选项加载失败，请重试'); operationError.value = optionsError.value } }
  finally { if (selected()) optionsLoading.value = false }
}
const changeCreateVenue = () => { retryIntent = null; operationForm.ownerId = undefined; return reloadActionOptions() }
const openAction = async (type: Action, row?: LeadView, trial?: TrialView) => {
  if (saving.value || !sessionIsCurrent()) return
  if (type !== 'create' && !row) return
  if (type === 'convert' && !canConvert(row!)) return ElMessage.warning('请先处理待体验预约，已流失潜客需要重新跟进')
  if (type === 'trial' && !canBook(row!)) return ElMessage.warning('当前潜客不能新建体验预约')
  if (type === 'follow' && row?.stage === 'CONVERTED') return ElMessage.warning('已开卡潜客的历史记录已保留')
  if (type === 'result' && (!trial || trial.status !== 'BOOKED')) return ElMessage.warning('请选择待体验预约')
  actionSequence++; detailSequence++; current.value = row ? Object.freeze({ ...row }) : null; selectedTrial.value = trial ? Object.freeze({ ...trial }) : null
  operation.value = type; retryIntent = null; operationError.value = ''; optionsError.value = ''; conflict.value = false; createVenueOptions.value = []
  Object.assign(operationForm, defaults(), row && ['profile', 'follow', 'trial', 'convert'].includes(type) ? { name: row.name, phone: row.phone, source: row.source, intent: row.intent, ownerId: row.ownerId, venueId: row.venueId, note: type === 'profile' ? row.note || '' : '' } : {})
  if (type === 'create') operationForm.venueId = venueId.value || (!isHeadquarters.value ? venues.value[0]?.id : undefined)
  if (type === 'follow' && row?.stage === 'LOST') operationForm.outcome = 'REOPEN'
  operationDialog.value = true; await reloadActionOptions()
}
const reloadConflict = async () => {
  if (saving.value || !sessionIsCurrent() || !current.value) return
  const row = current.value; closeOperation(); await openDetail(row); await load()
}
const submitOperation = async () => {
  if (saving.value || !sessionIsCurrent() || !operationDialog.value || optionsLoading.value || optionsError.value || conflict.value) return
  const type = operation.value, row = current.value, trial = selectedTrial.value, generation = auth.sessionGeneration
  const f = { ...operationForm }, warn = (message: string) => { ElMessage.warning(message); return false }
  const validEnum = (options: { value: string }[], value: string) => options.some(item => item.value === value)
  const validText = (text: string, max: number) => !!text.trim() && text.trim().length <= max
  if (type !== 'create' && (!row || !Number.isInteger(row.version))) return warn('请重新加载最新档案')
  const version = type === 'create' ? {} : { expectedVersion: row!.version }
  let payload: Record<string, any> = { ...version }, futureDates: string[] = []
  if (type === 'create' || type === 'profile') {
    if (!validText(f.name, 50) || !validEnum(intentOptions, f.intent)) return warn('姓名须为 1—50 字，请选择有效意向')
    if (f.note.trim().length > 1000) return warn('备注最多 1000 字')
    if (!localAdvisors.value.some(item => item.id === f.ownerId) || (!canAssign.value && f.ownerId !== auth.userInfo?.id)) return warn('请选择可用的负责顾问')
    payload = { ...version, name: f.name.trim(), intent: f.intent, ownerId: f.ownerId, note: f.note.trim() }
    if (type === 'create') {
      if (!/^1[3-9]\d{9}$/.test(f.phone.trim()) || !validEnum(sourceOptions, f.source) || !operationOptions.value.venues.some(item => item.id === f.venueId)) return warn('请填写有效大陆手机号，并选择来源和门店')
      payload = { ...payload, phone: f.phone.trim(), source: f.source, venueId: f.venueId, ...(f.nextFollowAt ? { nextFollowAt: f.nextFollowAt } : {}) }; if (f.nextFollowAt) futureDates.push(f.nextFollowAt)
    }
  } else if (type === 'follow') {
    if (!validText(f.content, 1000) || !validEnum(methodOptions, f.method) || !allowedOutcomes.value.some(item => item.value === f.outcome)) return warn('请填写跟进内容并选择有效方式与结果')
    payload = { ...version, method: f.method, content: f.content.trim(), outcome: f.outcome, ...(f.nextFollowAt ? { nextFollowAt: f.nextFollowAt } : {}) }; if (f.nextFollowAt) futureDates.push(f.nextFollowAt)
  } else if (type === 'trial') {
    if (!canBook(row!) || !validText(f.content, 1000) || !localAdvisors.value.some(item => item.id === f.hostId)) return warn('请填写体验内容并选择本门店接待顾问')
    const duration = (shanghaiTime(f.endsAt) - shanghaiTime(f.startsAt)) / 60000
    if (!Number.isFinite(duration) || duration < 15 || duration > 180) return warn('体验时长须为 15—180 分钟，请检查上海时间')
    payload = { ...version, hostId: f.hostId, startsAt: f.startsAt, endsAt: f.endsAt, content: f.content.trim() }; futureDates.push(f.startsAt)
  } else if (type === 'result') {
    if (!trial || trial.status !== 'BOOKED' || !['ATTENDED', 'NO_SHOW', 'CANCELLED'].includes(f.status) || !validText(f.note, 1000)) return warn('请选择有效体验结果并填写说明')
    if (f.status !== 'CANCELLED' && (!Number.isFinite(shanghaiTime(trial.startsAt)) || shanghaiTime(trial.startsAt) > Date.now())) return warn('预约开始后才能登记已体验或未到店')
    payload = { ...version, status: f.status, note: f.note.trim() }
  } else if (type === 'convert') {
    if (!canConvert(row!)) return warn('请先处理体验预约或重新跟进潜客')
    if (f.mode === 'NEW') {
      if (!localProducts.value.some(item => item.id === f.productId) || !validEnum(paymentOptions, f.paymentMethod) || !/^\d+(\.\d{1,2})?$/.test(f.paidAmount.trim()) || !Number.isFinite(Number(f.paidAmount)) || Number(f.paidAmount) < 0.01) return warn('请选择有效产品、收款渠道，金额至少 0.01 且最多两位小数')
      payload = { ...version, mode: 'NEW', productId: f.productId, paymentMethod: f.paymentMethod, paidAmount: Number(f.paidAmount) }
    } else if (f.mode === 'LINK' && candidateOrders.value.some(item => item.orderId === f.orderId)) payload = { ...version, mode: 'LINK', orderId: f.orderId }
    else return warn('请选择已核验的合格开卡订单')
  }
  const fingerprint = JSON.stringify([type, row?.id, trial?.id, payload]), isRetry = retryIntent?.fingerprint === fingerprint
  // A lost response may already have saved the booking; unchanged retries must reach server replay even after the date passes.
  if (!isRetry && futureDates.some(value => !Number.isFinite(shanghaiTime(value)) || shanghaiTime(value) <= Date.now())) return warn('下次联系或体验开始时间必须晚于当前上海时间')
  try { if (!isRetry) retryIntent = { fingerprint, key: secureUuid() } } catch { return ElMessage.error('无法生成记录编号，请稍后重试') }
  const body = Object.freeze({ ...payload, idempotencyKey: retryIntent!.key })
  saving.value = true; operationError.value = ''
  try {
    const result = type === 'create' ? await createLead(body as CreateLeadRequest) : type === 'profile' ? await updateLeadProfile(row!.id, body as ProfileLeadRequest) : type === 'follow' ? await addLeadFollowUp(row!.id, body as FollowLeadRequest) : type === 'trial' ? await bookLeadTrial(row!.id, body as BookTrialRequest) : type === 'result' ? await recordLeadTrialResult(row!.id, trial!.id, body as TrialResultRequest) : await convertLead(row!.id, body as ConvertLeadRequest)
    if (!sessionIsCurrent(generation)) return
    retryIntent = null; operationDialog.value = false; actionSequence++; optionsSequence++
    if (detailDialog.value && (!row || detailData.value?.lead.id === row.id)) { detailData.value = result.data; current.value = Object.freeze({ ...result.data.lead }) }
    ElMessage.success(type === 'convert' ? '开卡已记录' : '记录已保存'); await load()
  } catch (error: any) {
    if (sessionIsCurrent(generation)) {
      conflict.value = isConflict(error)
      operationError.value = conflict.value ? '档案已更新，请重新加载最新档案，再重新填写本次操作' : `${safeError(error, '保存失败，请稍后重试')}。相同内容可重试，不会重复记录`
    }
  } finally { if (sessionIsCurrent(generation)) saving.value = false }
}
onMounted(async () => {
  const generation = auth.sessionGeneration
  if (!sessionIsCurrent(generation)) return
  clockTimer = setInterval(() => { if (sessionIsCurrent(generation)) currentTime.value = Date.now() }, 1000)
  const tasks = [load(), getLeadOptions().then(result => { if (sessionIsCurrent(generation)) venues.value = result.data.venues }).catch(() => {})]
  await Promise.all(tasks)
})
</script>

<style scoped>
.leads-page :deep(.el-button.el-button--primary.is-plain:not(.is-disabled)){background:#edf5ff;border-color:#9bbce3;color:#2461a4;box-shadow:none}
.leads-page :deep(.el-button.el-button--primary.is-plain:not(.is-disabled):hover){background:#e1eeff;border-color:#6b9dd6;color:#194d87}
.leads-page{max-width:1360px;margin:auto;color:#234565}.hero-note{color:#476481;font-size:12px;line-height:1.8}.page-error{margin-bottom:18px}.metrics{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px}.metrics article,.metric{display:flex;flex-direction:column;align-items:flex-start;text-align:left;padding:20px;border:1px solid #d4e4f5;border-radius:16px;background:linear-gradient(135deg,#fff,#f5faff);color:#234565;box-shadow:0 5px 20px #31578205;font:inherit}.metric{cursor:pointer}.metric:hover{border-color:#8cb7e4;background:#eef6ff}.metrics span{font-size:12px;color:#476481}.metrics strong{font-size:30px;margin:10px 0 8px;line-height:1;font-weight:800}.metrics small{font-size:11px;color:#4b6987;line-height:1.6}.metrics em{font-style:normal;font-size:12px}.attention strong{color:#89611c}.overdue strong,.overdueText{color:#a1393a!important}.converted strong{color:#356e57}.section-heading{display:flex;align-items:center;justify-content:space-between;gap:14px;margin:28px 0 14px}.section-heading h2{margin:0 0 5px;font-size:18px}.section-heading p{margin:0;color:#4b6987;font-size:12px;line-height:1.8}.count{padding:7px 12px;border-radius:20px;white-space:nowrap;background:#e2efff;font-size:11px}.workbench-card,.channel-card{border:1px solid #d6e5f6;border-radius:18px;background:#fafdff}.filter-row{display:flex;justify-content:space-between;gap:16px;flex-wrap:wrap}.filter-tabs{display:flex;gap:7px;flex-wrap:wrap}.filter-tabs button{border:1px solid #d7e5f6;border-radius:10px;padding:9px 13px;background:#fff;color:#476481;font:inherit;font-size:12px;cursor:pointer}.filter-tabs button.selected{color:#2461a4;background:#e8f2ff;border-color:#8db9ed;font-weight:700}.leads-page button:focus-visible{outline:2px solid #438ce6;outline-offset:3px}.search{width:310px;max-width:100%;display:flex;gap:8px}.select-row{display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin:14px 0 18px}.select-row .el-select{width:155px}.select-row>span{font-size:11px;line-height:1.8;color:#4b6987}.lead-list{display:grid;gap:12px;min-height:120px}.lead-card{padding:20px;border:1px solid #dce9f7;border-radius:15px;background:#fff}.lead-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:12px}.lead-heading strong{font-size:16px;margin-right:10px}.lead-heading small{display:block;color:#4b6987;font-size:11px;margin-top:8px;line-height:1.8}.lead-facts{display:grid;grid-template-columns:1fr 1.4fr 1fr;gap:14px;margin:17px 0;padding:14px;background:#f4f9ff;border-radius:10px}.lead-facts>div{min-width:0}.lead-facts span,.profile-facts span{display:block;font-size:11px;color:#4b6987;margin-bottom:6px}.lead-facts strong,.profile-facts strong{font-size:12px;line-height:1.7;overflow-wrap:anywhere}.lead-facts small{display:block;margin-top:5px;font-size:11px;color:#4b6987;overflow-wrap:anywhere}.lead-footer{display:flex;gap:16px;align-items:center;justify-content:space-between;border-top:1px solid #edf3fa;padding-top:12px}.lead-footer>span{min-width:0;font-size:12px;color:#4b6987;line-height:1.8;overflow-wrap:anywhere}.lead-footer .el-button{flex:none}.pagination{justify-content:flex-end;margin-top:20px}.unavailable{text-align:center;font-size:13px;padding:18px}.detail-body{min-height:120px}.profile-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.profile-heading h2{margin:0 0 8px;font-size:23px;color:#234565}.profile-heading p{font-size:12px;color:#4b6987}.profile-facts{display:grid;grid-template-columns:1fr 1fr;gap:18px;background:#f4f9ff;border:1px solid #deebf8;border-radius:12px;padding:16px;margin:16px 0}.profile-note,.record-text{white-space:pre-wrap;overflow-wrap:anywhere;font-size:13px;line-height:1.8;color:#476481}.detail-actions{display:flex;flex-wrap:wrap;gap:8px}.detail-actions :deep(.el-button+.el-button){margin-left:0}.detail-tip,.dialog-note{font-size:12px;line-height:1.8;color:#4b6987}.detail-body h3{font-size:16px;margin:26px 0 12px;color:#234565}.trial-card{padding:15px;border:1px solid #dbe8f7;background:#fafdff;border-radius:12px;margin:12px 0}.trial-card>div{display:flex;justify-content:space-between;gap:12px;font-size:13px}.trial-card p{font-size:12px;line-height:1.8;color:#476481}.trial-card small,.event-heading span{color:#4b6987;font-size:11px}.trial-card .el-button{display:block;margin-top:12px}.event-heading{display:flex;gap:10px;align-items:center;flex-wrap:wrap;font-size:12px;color:#234565}.event-heading+ p{margin:8px 0}.conversion-record,.order-preview{border:1px solid #cfe5dc;border-radius:12px;background:#f3faf6;padding:16px;margin:16px 0;color:#356e57;font-size:12px;line-height:1.8}.conversion-record p,.order-preview p{margin:5px 0}.conversion-record small,.order-preview small{font-size:11px}.dialog-context{display:flex;flex-direction:column;gap:6px;border:1px solid #deebf8;border-radius:10px;background:#f4f9ff;padding:12px;color:#406585;font-size:12px;line-height:1.7}.dialog-context span{font-size:11px}.operation-error{margin-top:12px}.operation-form{margin-top:20px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 16px}.operation-form :deep(.el-select),.operation-form :deep(.el-date-editor.el-input){width:100%;min-width:0}.operation-form :deep(.el-form-item){min-width:0}.retry-detail{margin-top:14px}.leads-page :deep(.el-input__inner::placeholder),.leads-page :deep(.el-textarea__inner::placeholder),.leads-page :deep(.el-empty__description p),.leads-page :deep(.el-table th.el-table__cell){color:#4b6987}.leads-page :deep(.el-button.el-button--primary:not(.is-disabled):not(.is-plain)){background:#2461a4;border-color:#2461a4;color:#fff}
@media(max-width:1100px){.metrics{grid-template-columns:repeat(3,minmax(0,1fr))}.metrics article,.metric{padding:16px}}
@media(max-width:760px){.metrics{grid-template-columns:repeat(2,minmax(0,1fr))}.metrics .converted{grid-column:span 2}.metrics strong{font-size:27px}.filter-row{gap:12px}.search{width:100%}.lead-facts{grid-template-columns:1fr 1fr}.lead-facts>div:last-child{grid-column:span 2}.lead-card{padding:16px}.section-heading{margin-top:23px}.workbench-card :deep(.el-card__body){padding:14px}.form-grid{grid-template-columns:1fr}.select-row{gap:8px}.select-row>span{flex-basis:100%}}
@media(max-width:420px){.lead-heading{flex-direction:column;gap:10px}.lead-facts{grid-template-columns:1fr;gap:12px}.lead-facts>div:last-child{grid-column:auto}.lead-footer{align-items:flex-start}.section-heading{align-items:flex-start;flex-direction:column}.metrics article,.metric{padding:13px}.metrics strong{font-size:25px}.metrics small{font-size:10px}.profile-facts{gap:12px;padding:12px}.detail-actions{display:grid;grid-template-columns:1fr 1fr}.detail-actions :deep(.el-button){width:100%;padding:8px}.operation-form :deep(.el-radio-group){display:flex;flex-direction:column;align-items:stretch;gap:7px}.operation-form :deep(.el-radio-button__inner){border:1px solid #d7e5f6!important;border-radius:6px!important;width:100%;box-sizing:border-box}}
</style>
