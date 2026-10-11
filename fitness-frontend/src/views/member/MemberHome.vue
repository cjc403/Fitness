<template>
  <div class="member-home" v-loading="loading">
    <WorkspaceHero>
      <template #heading>
        <span>MEMBER PORTAL</span>
        <h1>你好，{{ profile?.realName || userInfo?.realName }}</h1>
        <p>{{ userInfo?.organizationName }} · 会员编号 {{ profile?.memberNo || '—' }}</p>
      </template>
      <template #footer>
        <div class="hero-stats">
          <div><strong>{{ activeBookingCount }}</strong><small>待参加课程</small></div>
          <div><strong>{{ attendedCount }}</strong><small>已完成训练</small></div>
          <div><strong>{{ activeEntitlementCount }}</strong><small>生效中权益</small></div>
        </div>
      </template>
    </WorkspaceHero>

    <el-tabs v-model="activeTab" class="portal-tabs">
      <el-tab-pane label="发现课程" name="schedules">
        <section class="recommendation-panel">
          <div class="recommendation-copy"><span>AI COURSE MATCH</span><h2>为你推荐</h2><p>{{ recommendation?.summary || '结合训练目标和历史偏好，为你筛选更合适的课程。' }}</p><small>{{ recommendation?.source || '可解释规则引擎' }}</small></div>
          <div class="recommendation-controls">
            <el-input v-model="goal" placeholder="例如：减脂、增肌、改善柔韧性" maxlength="100" clearable />
            <el-select v-model="preferredTime"><el-option label="不限时段" value="ANY"/><el-option label="上午" value="MORNING"/><el-option label="下午" value="AFTERNOON"/><el-option label="晚上" value="EVENING"/></el-select>
            <el-button type="primary" :loading="recommendationLoading" @click="loadRecommendations">重新推荐</el-button>
          </div>
          <div v-if="recommendation?.recommendations.length" class="recommendation-list">
            <article v-for="item in recommendation.recommendations.slice(0,3)" :key="item.scheduleId">
              <div><strong>{{ item.courseName }}</strong><el-tag size="small" type="success">匹配度 {{ item.score }}%</el-tag></div>
              <p>{{ item.reasons.join(' · ') }}</p><small>{{ formatDateTime(item.startTime) }} · 剩余 {{ item.remainingPlaces }} 位</small>
              <el-button link type="primary" @click="bookRecommended(item)">预约这节课</el-button>
            </article>
          </div>
        </section>
        <div class="section-head"><div><h2>未来 30 天可预约课程</h2><p>选择适合你的时间，系统会自动检查名额与重复预约。</p></div><div class="schedule-filter"><el-radio-group v-model="courseFilter" size="small"><el-radio-button value="ALL">全部</el-radio-button><el-radio-button value="PRIVATE">仅私教课</el-radio-button></el-radio-group><el-tag effect="plain">{{ filteredSchedules.length }} 场可选</el-tag></div></div>
        <div class="schedule-grid">
          <article v-for="schedule in filteredSchedules" :key="schedule.scheduleId" class="schedule-card">
            <div class="card-top"><el-tag size="small" effect="plain">{{ typeText(schedule.courseType) }}</el-tag><strong>¥{{ schedule.fee }}</strong></div>
            <h3>{{ schedule.courseName }}</h3>
            <p>{{ schedule.description || '科学训练，保持最佳状态。' }}</p>
            <div class="schedule-info"><span><el-icon><Calendar /></el-icon>{{ formatDay(schedule.startTime) }}</span><span><el-icon><Clock /></el-icon>{{ formatTime(schedule.startTime) }}</span><span><el-icon><User /></el-icon>{{ schedule.coachName }}</span></div>
            <div class="capacity"><el-progress :percentage="capacity(schedule)" :stroke-width="7" :show-text="false"/><small>{{ schedule.enrolledCount }}/{{ schedule.maxStudents }} 人</small></div>
            <el-button v-if="schedule.bookingStatus==='WAITLISTED'" disabled>候补第 {{ waitlistPosition(schedule.scheduleId) }} 位</el-button>
            <el-button v-else-if="schedule.booked" disabled>已预约</el-button>
            <el-button v-else-if="schedule.full" type="warning" plain @click="joinWaitlist(schedule)">加入候补</el-button>
            <el-button v-else type="primary" :loading="bookingId===schedule.scheduleId" @click="book(schedule)">立即预约</el-button>
          </article>
        </div>
        <el-empty v-if="!loading&&!filteredSchedules.length" :description="courseFilter==='PRIVATE'?'本门店未来 30 天没有私教课可预约，请联系前台安排排课':'未来 30 天暂无可预约课程'" />
      </el-tab-pane>

      <el-tab-pane :label="`我的预约 (${bookings.length})`" name="bookings">
        <el-card shadow="never" class="booking-table">
          <el-table :data="bookings">
            <el-table-column label="课程" min-width="170"><template #default="{row}"><strong>{{ row.courseName }}</strong><small>{{ row.coachName }}</small></template></el-table-column>
            <el-table-column label="上课时间" min-width="190"><template #default="{row}">{{ formatDateTime(row.startTime) }}</template></el-table-column>
            <el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="170"><template #default="{row}"><template v-if="row.cancellationAllowed"><el-button link type="warning" @click="leave(row)">请假</el-button><el-button link type="danger" @click="cancel(row)">取消</el-button></template><span v-else class="muted">—</span></template></el-table-column>
          </el-table>
          <el-empty v-if="!bookings.length" description="你还没有预约记录" />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="私教签课" name="training"><TrainingPanel ref="trainingPanel" @changed="load" @go="onTrainingGuide" /></el-tab-pane>
      <el-tab-pane label="我的训练" name="training-plans"><TrainingPlanPanel v-if="activeTab==='training-plans'" member-mode /></el-tab-pane>
      <el-tab-pane label="购买会籍" name="shop">
        <section><div class="section-head"><div><h2>会员卡与课程包</h2><p>与门店在售商品实时同步；其他门店的商品仅作展示，需到该门店办理。演示环境不接入真实支付。</p></div><el-tag effect="plain">{{ products.length }} 款在售</el-tag></div>
          <div class="entitlement-grid">
            <article v-for="item in products" :key="item.id">
              <div><el-tag effect="plain">{{ productTypeText(item.productType) }}</el-tag><strong>¥{{ item.price }}</strong></div>
              <h3>{{ item.name }}</h3><p>{{ item.description || '—' }}</p>
              <small>{{ productTerm(item) }}</small>
              <small class="venue-line">{{ item.venueName || '—' }}{{ item.purchasable ? '' : ' · 仅限该店会员购买' }}</small>
              <el-button type="primary" style="margin-top:14px;width:100%" :disabled="!item.purchasable" :loading="purchasingId===item.id" @click="purchase(item)">{{ item.purchasable ? '立即购买' : '仅限本店会员' }}</el-button>
            </article>
          </div>
          <el-empty v-if="!products.length" description="暂无可购买的会员卡或课包，请联系前台" />
        </section>
      </el-tab-pane>
      <el-tab-pane :label="`我的会籍 (${entitlements.length})`" name="membership">
        <div class="membership-layout">
          <section><div class="section-head"><div><h2>会员卡与课程包</h2><p>到期时间、可预约次数和预占状态实时同步。</p></div></div><div class="entitlement-grid"><article v-for="item in entitlements" :key="item.id"><div><el-tag :type="item.status==='ACTIVE'?'success':'warning'">{{ entitlementStatus(item.status) }}</el-tag><span>{{ item.entitlementNo }}</span></div><h3>{{ item.productName }}</h3><strong>{{ item.remainingUses==null?'不限次':item.productType==='PRIVATE_PACKAGE'?`可约 ${item.availableUses??item.remainingUses} 次`:`${item.remainingUses} 次` }}</strong><small v-if="item.productType==='PRIVATE_PACKAGE'">未消耗 {{ item.remainingUses }} 次 · 已预占 {{ item.reservedUses||0 }} 次</small><p>{{ item.validFrom }} 至 {{ item.validUntil }}</p><small v-if="item.reminderLevel!=='NONE'">{{ reminderText(item.reminderLevel) }}</small></article></div><el-empty v-if="!entitlements.length" description="暂未办理会员卡或课包，可前往「购买会籍」自助办理"/></section>
          <aside class="qr-card"><span>MEMBER QR PASS</span><h2>扫码入场</h2><img v-if="qrImage" :src="qrImage" alt="会员入场二维码"/><p>{{ accessCode?.memberName }} · {{ accessCode?.memberNo }}</p><small class="qr-ttl">{{ accessSecondsLeft > 0 ? `二维码 ${accessSecondsLeft} 秒后自动刷新` : '二维码已过期，正在刷新…' }}</small><small>一次性二维码：被扫过或超时即失效，截图无法重复入场。次卡核验通过后自动扣减 1 次。</small></aside>
        </div>
      </el-tab-pane>
      <el-tab-pane label="体测档案" name="body">
        <section>
          <div class="section-head"><div><h2>体测与训练档案</h2><p>记录体重、体脂、围度与静息心率，设定训练目标并查看变化趋势。</p></div></div>
          <BodyMeasurePanel />
        </section>
      </el-tab-pane>
      <el-tab-pane :label="`我的优惠券 (${coupons.length})`" name="coupons">
        <section>
          <div class="section-head"><div><h2>我的优惠券</h2><p>购买会籍时可抵扣；一张券只能用一次，退款按折后实付计算。</p></div></div>
          <ul class="coupon-list">
            <li v-for="c in coupons" :key="c.id" :class="{ dim: !c.usable }">
              <div class="coupon-left"><strong>{{ c.templateName }}</strong><span>{{ couponText(c) }}</span></div>
              <div class="coupon-right"><el-tag :type="c.status==='UNUSED'?'success':c.status==='USED'?'info':'warning'">{{ c.status==='UNUSED'?'可用':c.status==='USED'?'已使用':'已过期' }}</el-tag><small>{{ c.validFrom }} ~ {{ c.validUntil }}</small></div>
            </li>
          </ul>
          <el-empty v-if="!coupons.length" description="暂无优惠券，可向前台或会籍顾问索取" :image-size="65" />
        </section>
      </el-tab-pane>
      <el-tab-pane :label="pendingReviews.length ? `课程评价 (${pendingReviews.length})` : '课程评价'" name="reviews">
        <section>
          <div class="section-head"><div><h2>待评价课程</h2><p>只有你已签到的课才能评价，每节课只能评价一次。</p></div></div>
          <el-table v-if="pendingReviews.length" :data="pendingReviews" stripe>
            <el-table-column label="课程 / 教练" min-width="180"><template #default="{row}"><strong>{{ row.courseName }}</strong><small>{{ row.coachName || '—' }}</small></template></el-table-column>
            <el-table-column label="上课时间" min-width="140"><template #default="{row}">{{ formatDateTime(row.startTime) }}</template></el-table-column>
            <el-table-column label="我的评分" width="190"><template #default="{row}"><el-rate v-model="reviewForms[row.bookingId].rating" /></template></el-table-column>
            <el-table-column label="评价（可选）" min-width="200"><template #default="{row}"><el-input v-model="reviewForms[row.bookingId].content" size="small" placeholder="写点感受，教练和门店能看到" /></template></el-table-column>
            <el-table-column label="操作" width="90" fixed="right"><template #default="{row}"><el-button type="primary" size="small" :disabled="busyReview" @click="submitMyReview(row)">提交</el-button></template></el-table-column>
          </el-table>
          <el-empty v-else description="没有待评价的课程（已签到的课才会出现在这里）" :image-size="65" />
          <div class="section-head" style="margin-top:26px"><div><h2>我评价过的课</h2><p>评分会汇总给教练与门店。</p></div></div>
          <ul class="review-list">
            <li v-for="r in myReviews" :key="r.id"><div class="review-head"><strong>{{ r.courseName }}</strong><el-rate :model-value="r.rating" disabled /></div><p v-if="r.content">{{ r.content }}</p><small>{{ formatDateTime(r.createdAt) }} · {{ r.coachName || '—' }}</small></li>
          </ul>
          <el-empty v-if="!myReviews.length" description="还没有提交过评价" :image-size="65" />
        </section>
      </el-tab-pane>
      <el-tab-pane name="notices">
        <div class="msg-switch">
          <el-radio-group v-model="memberMsgTab" size="small">
            <el-radio-button value="chat">私信</el-radio-button>
            <el-radio-button value="notices">系统通知<template v-if="unreadCount">（{{ unreadCount }}）</template></el-radio-button>
          </el-radio-group>
        </div>
        <!-- 站内私信：与员工/教练/前台真实互发，不是模拟通道 -->
        <ChatPanel v-show="memberMsgTab === 'chat'" embedded/>
        <template #label>消息中心<span v-if="unreadCount" class="notice-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span></template>
        <section v-show="memberMsgTab === 'notices'"><div class="section-head"><div><h2>我的消息</h2><p>排课变动、商品下架、退款审批结果都会在这里通知你。</p></div><el-button :disabled="!unreadCount" @click="readAll">全部标为已读</el-button></div>
          <ul class="notice-list">
            <li v-for="n in notices" :key="n.id" :class="{ unread: !n.read }">
              <div class="notice-head"><strong>{{ n.title }}</strong><span>{{ formatDateTime(n.createdAt) }}</span></div>
              <p>{{ n.body }}</p>
              <el-button v-if="!n.read" link type="primary" @click="readOne(n)">标为已读</el-button>
            </li>
          </ul>
          <el-empty v-if="!notices.length" description="暂无消息" :image-size="65" />
        </section>
      </el-tab-pane>
      <el-tab-pane v-if="can('MEMBER_SELF_SERVICE')" label="我的退款" name="refunds">
        <section class="refund-apply"><div class="section-head"><div><h2>申请退款</h2><p>选择要退的会员卡或课包，可退金额由服务端按价值账核定，提交后由总部或店长审核。</p></div></div><div class="refund-apply-form"><el-select v-model="refundEntitlementId" placeholder="选择本人名下的会员卡或课包" clearable @change="resetRefundQuote"><el-option v-for="item in entitlements" :key="item.id" :label="`${item.productName} · ${item.entitlementNo} · ${entitlementStatus(item.status)}`" :value="item.id" /></el-select><el-button :disabled="!refundEntitlementId" :loading="quoteLoading" @click="loadRefundQuote">获取退款报价</el-button></div>
          <el-empty v-if="!entitlements.length" description="暂无可退款的会籍" :image-size="60" />
          <div v-if="refundQuote" class="refund-quote"><div class="quote-amount"><span>服务端核定可退金额</span><strong>¥{{ refundMoney(refundQuote.refundableAmount) }}</strong><small v-if="refundQuote.remainingUses!=null">剩余 {{ refundQuote.remainingUses }} 次</small></div>
            <el-alert v-if="refundQuote.verificationStatus!=='VERIFIED'" type="warning" :closable="false" title="该会籍的价值尚未核对，暂时无法自助退款。请联系前台核对价值后再试。" />
            <template v-else>
              <el-alert v-if="refundQuote.blockedReasons.length" type="warning" :closable="false" title="当前不能申请退款"><ul class="quote-blocks"><li v-for="r in refundQuote.blockedReasons" :key="r">{{ r }}</li></ul></el-alert><div v-if="refundBlockers.length" class="blocker-box">
                <div class="blocker-head"><span>共 {{ refundBlockers.length }} 项未完成内容挡住退款，其中 <strong>{{ refundBlockers.filter(b=>b.cancellable).length }}</strong> 项可自动取消</span><el-button type="warning" size="small" :loading="clearingBlockers" :disabled="!refundBlockers.some(b=>b.cancellable)" @click="clearBlockers">一键取消</el-button></div>
                <ul class="blocker-list">
                  <li v-for="b in refundBlockers" :key="b.type + '-' + (b.bookingId || b.lessonId)">
                    <el-tag size="small" :type="b.cancellable ? 'warning' : 'info'">{{ b.cancellable ? '可取消' : '需人工' }}</el-tag>
                    <strong>{{ b.label }}</strong><small>{{ b.detail }}</small>
                  </li>
                </ul>
              </div>
              <el-alert v-else-if="refundQuote.activeRefundId" type="info" :closable="false" title="该会籍已有进行中的退款申请，可在下方进度中查看或撤回。" />
              <template v-else><el-input v-model="refundReason" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="请填写退款原因，便于审核" /><el-button type="primary" style="margin-top:12px" :disabled="!refundReason.trim()" :loading="refundSubmitting" @click="submitRefund">提交退款申请</el-button></template>
            </template>
          </div>
        </section>
        <section class="refund-history"><div class="section-head"><div><h2>本人退款进度</h2><p>仅展示当前登录会员的脱敏退款状态。</p></div><el-button @click="loadRefunds">刷新</el-button></div><el-alert v-if="refundsFailed" title="退款状态加载失败，未显示为空记录。" type="error" :closable="false"><template #default><el-button link type="primary" @click="loadRefunds">重试</el-button></template></el-alert><el-table v-else :data="myRefunds" stripe><el-table-column prop="id" label="记录号" width="95"/><el-table-column prop="refundNo" label="退款单号" min-width="180"/><el-table-column prop="entitlementNo" label="权益编号" min-width="170"/><el-table-column label="状态" width="120"><template #default="{row}"><el-tag :type="refundStatusType(row.status)">{{ refundStatus(row.status) }}</el-tag></template></el-table-column><el-table-column label="金额" width="130"><template #default="{row}">¥{{ refundMoney(row.amount) }}</template></el-table-column><el-table-column label="申请时间" min-width="170"><template #default="{row}">{{ formatDateTime(row.requestedAt) }}</template></el-table-column><el-table-column label="审核时间" min-width="170"><template #default="{row}">{{ row.approvedAt?formatDateTime(row.approvedAt):'—' }}</template></el-table-column><el-table-column label="登记支付" min-width="170"><template #default="{row}">{{ row.paidAt?formatDateTime(row.paidAt):'—' }}</template></el-table-column><el-table-column label="操作" width="90"><template #default="{row}"><el-button v-if="row.status==='PENDING'" link type="danger" @click="withdrawMyRefund(row)">撤回</el-button><span v-else class="muted">—</span></template></el-table-column></el-table><el-empty v-if="!myRefunds.length&&!refundsFailed" description="暂无本人退款记录" :image-size="65"/></section>
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="packageDialog" title="选择私教课包" width="min(520px, 92vw)">
      <p>预约会预占 1 次；授课结束后本人确认签课才正式扣次。</p>
      <el-select v-model="selectedPackage" placeholder="选择有效私教课包" style="width:100%"><el-option v-for="item in eligiblePackages" :key="item.id" :value="item.id" :label="`${item.productName} · 可约 ${item.availableUses ?? item.remainingUses} 次 · ${item.validUntil} 到期`" /></el-select>
      <el-alert v-if="!eligiblePackages.length" title="没有覆盖上课日期的可用私教课包，请联系前台购买或续费。" type="warning" :closable="false" style="margin-top:16px" />
      <template #footer><el-button @click="packageDialog=false">取消</el-button><el-button type="primary" :disabled="!selectedPackage" :loading="bookingId!=null" @click="confirmPackageBooking">确认预占并预约</el-button></template>
    </el-dialog>
  </div>
    <el-dialog v-model="couponPickerVisible" title="选择优惠券" width="470px">
      <p class="muted">{{ pickingProduct?.name }} · 标价 ¥{{ pickingProduct?.price }}</p>
      <el-radio-group v-model="pickedCouponId" class="coupon-picker">
        <el-radio :value="0" border>不使用优惠券</el-radio>
        <el-radio v-for="o in pickOptions" :key="o.memberCouponId" :value="o.memberCouponId" border>
          {{ o.name }}（减 ¥{{ (o.discountCents/100).toFixed(2) }}，实收 ¥{{ Number(o.payable).toFixed(2) }}）
        </el-radio>
      </el-radio-group>
      <template #footer><el-button @click="couponPickerVisible=false">取消</el-button><el-button type="primary" :loading="!!purchasingId" @click="confirmCouponPick">确认购买</el-button></template>
    </el-dialog>
</template>

<script setup lang="ts">
import WorkspaceHero from '@/components/WorkspaceHero.vue'
import ChatPanel from '@/components/ChatPanel.vue'
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { Calendar, Clock, User } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/store/modules/auth'
import QRCode from 'qrcode'
import TrainingPanel from '@/components/TrainingPanel.vue'
import TrainingPlanPanel from '@/components/TrainingPlanPanel.vue'
import { cancelSelfBooking, createSelfBooking, getCourseRecommendations, getMemberProfile, joinScheduleWaitlist, listMemberBookings, listMemberProducts, listMemberSchedules, purchaseMembership, requestBookingLeave, type CourseRecommendation, type MemberBooking, type MemberProduct, type MemberProfile, type MemberSchedule, type RecommendationResult } from '@/api/modules/memberPortal'
import { getMyAccessCode, getMyEntitlements, type AccessCode, type Entitlement } from '@/api/modules/commercial'
import { cancelMyRefund, createMyRefund, getMyEntitlementQuote, listMyRefunds, type EntitlementQuote, type MemberRefund, getMyRefundBlockers, clearMyRefundBlockers, type RefundBlocker, type ClearBlockersResult } from '@/api/modules/finance'
import { getNotificationSummary, listNotifications, markAllNotificationsRead, markNotificationRead, type NotificationItem } from '@/api/modules/notification'
import { listMyCoupons, quoteMyCoupon, type CouponQuoteOption, type MemberCoupon } from '@/api/modules/coupon'
import BodyMeasurePanel from '@/components/BodyMeasurePanel.vue'
import { listMyReviews, listPendingReviews, submitReview, type CourseReview, type PendingReview } from '@/api/modules/review'
import { formatMoney, refundIntent } from '@/utils/financeUi.mjs'

const authStore = useAuthStore()
const userInfo = computed(() => authStore.userInfo)
const memberMsgTab = ref<'chat' | 'notices'>('chat')
const profile = ref<MemberProfile>()
const schedules = ref<MemberSchedule[]>([])
const bookings = ref<MemberBooking[]>([])
const entitlements = ref<Entitlement[]>([])
const products = ref<MemberProduct[]>([])
const purchasingId = ref<number>()
const accessCode = ref<AccessCode>()
const qrImage = ref('')
const loading = ref(false)
const bookingId = ref<number>()
const activeTab = ref('schedules')
const courseFilter = ref<'ALL' | 'PRIVATE'>('ALL')
const filteredSchedules = computed(() => courseFilter.value === 'PRIVATE' ? schedules.value.filter(s => s.courseType === 'PRIVATE') : schedules.value)
const onTrainingGuide = (target: string) => { if (target === 'book-private') { courseFilter.value = 'PRIVATE'; activeTab.value = 'schedules' } }
const trainingPanel=ref<{refresh:()=>Promise<void>}>()
watch(activeTab,value=>{if(value==='training')trainingPanel.value?.refresh();if(value==='shop')loadProducts()})
const recommendation = ref<RecommendationResult>()
const recommendationLoading = ref(false)
const goal = ref('')
const preferredTime = ref('ANY')
const packageDialog=ref(false), selectedPackage=ref<number>(), selectedSchedule=ref<MemberSchedule>()
const myRefunds=ref<MemberRefund[]>([]), refundsFailed=ref(false)
const notices=ref<NotificationItem[]>([]), unreadCount=ref(0)
const coupons=ref<MemberCoupon[]>([]), pendingReviews=ref<PendingReview[]>([]), myReviews=ref<CourseReview[]>([])
const reviewForms=reactive<Record<number,{rating:number,content:string}>>({})
const busyReview=ref(false)
const couponPickerVisible=ref(false), pickingProduct=ref<MemberProduct|null>(null)
const pickOptions=ref<CouponQuoteOption[]>([]), pickedCouponId=ref(0)
const accessSecondsLeft=ref(0)
let accessTimer:number|undefined
let disposed=false
let accessRequestVersion=0
let loadVersion=0
const sessionGeneration=authStore.sessionGeneration
const isCurrentView=()=>!disposed&&authStore.sessionGeneration===sessionGeneration
const refundEntitlementId=ref<number>(), refundQuote=ref<EntitlementQuote>(), refundReason=ref(''), quoteLoading=ref(false), refundSubmitting=ref(false), refundIntentRef=ref<{idempotencyKey:string}>()
const refundBlockers=ref<RefundBlocker[]>([]), clearingBlockers=ref(false), lastClearResult=ref<ClearBlockersResult>()
const can=(permission:string)=>authStore.userInfo?.permissions?.includes(permission)===true
const eligiblePackages=computed(()=>entitlements.value.filter(item=>item.productType==='PRIVATE_PACKAGE'&&item.status==='ACTIVE'&&(item.availableUses??item.remainingUses??0)>0&&!!selectedSchedule.value&&item.validFrom<=selectedSchedule.value.startTime.slice(0,10)&&item.validUntil>=selectedSchedule.value.startTime.slice(0,10)))
const activeBookingCount = computed(() => bookings.value.filter(item => item.status === 'BOOKED').length)
const attendedCount = computed(() => bookings.value.filter(item => item.status === 'ATTENDED').length)
const activeEntitlementCount = computed(() => entitlements.value.filter(item => item.status === 'ACTIVE').length)

const load = async () => {
  if(!isCurrentView())return
  const version=++loadVersion
  const codeVersion=++accessRequestVersion
  loading.value = true
  try {
    const [profileResponse, scheduleResponse, bookingResponse, entitlementResponse, codeResponse] = await Promise.all([
      getMemberProfile(), listMemberSchedules(), listMemberBookings(), getMyEntitlements(), getMyAccessCode()
    ])
    if(!isCurrentView()||version!==loadVersion)return
    profile.value = profileResponse.data
    schedules.value = scheduleResponse.data
    bookings.value = bookingResponse.data
    entitlements.value = entitlementResponse.data
    const image=await QRCode.toDataURL(codeResponse.data.qrPayload, { width: 220, margin: 1, color: { dark: '#132445', light: '#ffffff' } })
    if(!isCurrentView()||version!==loadVersion)return
    if(codeVersion===accessRequestVersion){
      accessCode.value=codeResponse.data
      qrImage.value=image
      startAccessCountdown(codeResponse.data.ttlSeconds || 60)
    }
    if(can('MEMBER_SELF_SERVICE')) await Promise.all([loadRefunds(), loadNotices(), loadCoupons(), loadReviews()])
  } finally { if(isCurrentView()&&version===loadVersion)loading.value = false }
}

const loadRefunds=async()=>{refundsFailed.value=false;try{myRefunds.value=(await listMyRefunds()).data}catch{refundsFailed.value=true}}
const resetRefundQuote=()=>{refundQuote.value=undefined;refundReason.value='';refundIntentRef.value=undefined}
const loadRefundBlockers=async()=>{
  refundBlockers.value=[]
  if(!refundEntitlementId.value)return
  try{refundBlockers.value=(await getMyRefundBlockers(refundEntitlementId.value)).data}catch{refundBlockers.value=[]}
}
const clearBlockers=async()=>{
  const targets=refundBlockers.value.filter(b=>b.cancellable)
  if(!targets.length)return
  const lines=['将取消以下 '+targets.length+' 项未完成的预约：']
  targets.forEach(b=>lines.push('· '+b.label))
  lines.push('')
  lines.push('取消只释放预占，不扣次数、不影响已结算记录。是否继续？')
  try{
    await ElMessageBox.confirm(lines.join('\n'),'一键取消未完成预约',{type:'warning',confirmButtonText:'确认取消',cancelButtonText:'再想想'})
  }catch{return}
  clearingBlockers.value=true
  try{
    const r=(await clearMyRefundBlockers(refundEntitlementId.value!)).data
    lastClearResult.value=r
    const parts:string[]=[]
    if(r.cancelled.length)parts.push(`已取消 ${r.cancelled.length} 项`)
    if(r.skipped.length)parts.push(`${r.skipped.length} 项需前台或店长办理`)
    ElMessage[parts.length?'success':'info'](parts.length?parts.join('，'):'没有需要取消的项目')
    await loadRefundQuote()
  }finally{clearingBlockers.value=false}
}
const loadRefundQuote=async()=>{
  if(!refundEntitlementId.value)return
  quoteLoading.value=true
  try{
    refundQuote.value=(await getMyEntitlementQuote(refundEntitlementId.value)).data
    if(refundQuote.value&&refundQuote.value.blockedReasons&&refundQuote.value.blockedReasons.length){await loadRefundBlockers()}else{refundBlockers.value=[]}
    try{refundIntentRef.value=refundIntent()}catch{refundIntentRef.value=undefined;ElMessage.error('无法生成安全随机申请编号，请检查浏览器后重试')}
  }catch{refundQuote.value=undefined;refundBlockers.value=[]}finally{quoteLoading.value=false}
}
const submitRefund=async()=>{
  if(!refundQuote.value||!refundIntentRef.value||!refundReason.value.trim()||refundSubmitting.value)return
  try{await ElMessageBox.confirm(`确认申请退款？服务端核定可退金额 ¥${refundMoney(refundQuote.value.refundableAmount)}，提交后需管理员审核。`,'申请退款',{type:'warning',confirmButtonText:'提交申请',cancelButtonText:'再想想'})}catch{return}
  refundSubmitting.value=true
  try{
    await createMyRefund({entitlementId:refundQuote.value.entitlementId,version:refundQuote.value.version,reason:refundReason.value.trim(),idempotencyKey:refundIntentRef.value.idempotencyKey})
    ElMessage.success('退款申请已提交，等待审核')
    refundEntitlementId.value=undefined;resetRefundQuote();await load()
  }finally{refundSubmitting.value=false}
}
const withdrawMyRefund=async(row:MemberRefund)=>{
  let reason=''
  try{reason=(await ElMessageBox.prompt('请填写撤回原因','撤回退款申请',{inputValue:'本人暂时不退了'})).value}catch{return}
  await cancelMyRefund(row.id,reason);ElMessage.success('已撤回退款申请');await Promise.all([load(),loadRefunds()])
}

const couponText=(c:MemberCoupon)=>c.couponType==='DISCOUNT'
  ? `${(Number(c.discountRate||1)*10).toFixed(1)} 折`+(c.thresholdCents?`（满 ¥${(c.thresholdCents/100).toFixed(2)} 可用）`:'')
  : `满 ¥${((c.thresholdCents||0)/100).toFixed(2)} 减 ¥${((c.discountCents||0)/100).toFixed(2)}`
const loadCoupons=async()=>{try{coupons.value=(await listMyCoupons()).data}catch{coupons.value=[]}}
const loadReviews=async()=>{
  try{
    const [pending,mine]=await Promise.all([listPendingReviews(),listMyReviews()])
    pendingReviews.value=pending.data;myReviews.value=mine.data
    pending.data.forEach(p=>{if(!reviewForms[p.bookingId])reviewForms[p.bookingId]={rating:5,content:''}})
  }catch{pendingReviews.value=[];myReviews.value=[]}
}
const submitMyReview=async(row:PendingReview)=>{
  const form=reviewForms[row.bookingId]
  if(!form)return
  busyReview.value=true
  try{await submitReview({bookingId:row.bookingId,rating:form.rating,content:form.content||undefined});ElMessage.success('感谢评价，已记录');await loadReviews()}finally{busyReview.value=false}
}
const loadNotices=async()=>{try{const [list,summary]=await Promise.all([listNotifications(),getNotificationSummary()]);notices.value=list.data;unreadCount.value=summary.data.unread}catch{notices.value=[]}}
const readOne=async(item:NotificationItem)=>{if(item.read)return;await markNotificationRead(item.id);await loadNotices()}
const readAll=async()=>{if(!unreadCount.value)return;await markAllNotificationsRead();await loadNotices()}

/** 门禁二维码是 60 秒一次性令牌，到期前自动换一张，避免"截图还能用"。 */
const startAccessCountdown=(ttl:number)=>{
  if(!isCurrentView())return
  window.clearInterval(accessTimer)
  accessSecondsLeft.value=ttl
  accessTimer=window.setInterval(()=>{
    if(!isCurrentView()){window.clearInterval(accessTimer);return}
    accessSecondsLeft.value-=1
    if(accessSecondsLeft.value<=0){window.clearInterval(accessTimer);refreshAccessCode()}
  },1000)
}
const refreshAccessCode=async()=>{
  if(!isCurrentView())return
  const version=++accessRequestVersion
  try{
    const res=await getMyAccessCode()
    if(!isCurrentView()||version!==accessRequestVersion)return
    const image=await QRCode.toDataURL(res.data.qrPayload,{width:220,margin:1,color:{dark:'#132445',light:'#ffffff'}})
    if(!isCurrentView()||version!==accessRequestVersion)return
    accessCode.value=res.data
    qrImage.value=image
    startAccessCountdown(res.data.ttlSeconds||60)
  }catch{if(isCurrentView()&&version===accessRequestVersion)accessSecondsLeft.value=0}
}
onUnmounted(()=>{disposed=true;window.clearInterval(accessTimer)})
const loadProducts=async()=>{try{products.value=(await listMemberProducts()).data}catch{products.value=[]}}
const doPurchase=async(item:MemberProduct,couponId?:number)=>{
  let intent:{idempotencyKey:string}
  try{intent=refundIntent()}catch{ElMessage.error('无法生成安全随机申请编号，请检查浏览器后重试');return}
  purchasingId.value=item.id
  try{
    await purchaseMembership(item.id,intent.idempotencyKey,couponId)
    ElMessage.success(couponId?'购买成功，优惠券已核销，已加入“我的会籍”':'购买成功，已加入“我的会籍”')
    couponPickerVisible.value=false
    await load()
  }finally{purchasingId.value=undefined}
}
const purchase=async(item:MemberProduct)=>{
  try{await ElMessageBox.confirm(`确认购买「${item.name}」？标价 ¥${item.price}，演示环境不产生真实扣款。`,'购买会籍',{type:'success',confirmButtonText:'下一步',cancelButtonText:'取消'})}catch{return}
  let options:CouponQuoteOption[]=[]
  try{options=(await quoteMyCoupon(item.id)).data.options}catch{options=[]}
  if(options.length){pickingProduct.value=item;pickOptions.value=options;pickedCouponId.value=0;couponPickerVisible.value=true;return}
  await doPurchase(item,undefined)
}
const confirmCouponPick=async()=>{const p=pickingProduct.value;if(p)await doPurchase(p,pickedCouponId.value||undefined)}

const book = async (schedule: MemberSchedule) => {
  if(schedule.courseType==='PRIVATE'){selectedSchedule.value=schedule;selectedPackage.value=undefined;packageDialog.value=true;return}
  await ElMessageBox.confirm(`确认预约“${schedule.courseName}”吗？`, '预约课程', { type: 'success' })
  bookingId.value = schedule.scheduleId
  try {
    await createSelfBooking(schedule.scheduleId)
    ElMessage.success('预约成功，已加入“我的预约”')
    await load()
  } finally { bookingId.value = undefined }
}
const confirmPackageBooking=async()=>{
  if(!selectedSchedule.value||!selectedPackage.value||bookingId.value!=null)return
  bookingId.value=selectedSchedule.value.scheduleId
  try{await createSelfBooking(selectedSchedule.value.scheduleId,selectedPackage.value);packageDialog.value=false;ElMessage.success('预约成功，已预占课包 1 次');await load()}finally{bookingId.value=undefined}
}

const loadRecommendations = async () => {
  recommendationLoading.value = true
  try { recommendation.value = (await getCourseRecommendations(goal.value, preferredTime.value)).data }
  finally { recommendationLoading.value = false }
}

const bookRecommended = async (item: CourseRecommendation) => {
  if(item.courseType==='PRIVATE'){const schedule=schedules.value.find(s=>s.scheduleId===item.scheduleId);if(!schedule){ElMessage.warning('排课已更新，请刷新课程列表');return}await book(schedule);return}
  await ElMessageBox.confirm(`确认预约推荐课程“${item.courseName}”吗？`, '预约课程', { type: 'success' })
  bookingId.value = item.scheduleId
  try {
    await createSelfBooking(item.scheduleId)
    ElMessage.success('预约成功')
    await Promise.all([load(), loadRecommendations()])
  } finally { bookingId.value = undefined }
}

const cancel = async (booking: MemberBooking) => {
  await ElMessageBox.confirm('确认取消该预约？开课前 2 小时内不可自助取消。', '取消预约', { type: 'warning' })
  await cancelSelfBooking(booking.id)
  ElMessage.success('预约已取消')
  await load()
}
const leave = async (booking: MemberBooking) => { const {value}=await ElMessageBox.prompt('请填写请假原因','课程请假',{inputValue:'临时有事'});await requestBookingLeave(booking.id,value);ElMessage.success('请假成功，名额已释放');await load() }
const joinWaitlist = async (schedule: MemberSchedule) => { await ElMessageBox.confirm(`“${schedule.courseName}”已满员，是否加入候补？有名额时将自动递补。`,'加入候补',{type:'warning'});await joinScheduleWaitlist(schedule.scheduleId);ElMessage.success('已加入候补队列');await load() }
const waitlistPosition=(scheduleId:number)=>bookings.value.find(item=>item.scheduleId===scheduleId)?.waitlistPosition||'—'

const capacity = (schedule: MemberSchedule) => Math.min(100, Math.round(schedule.enrolledCount / schedule.maxStudents * 100))
const typeText = (value: string) => ({ GROUP: '团体课', REGULAR: '常规课', PRIVATE: '私教课' }[value] || value)
const statusText = (value: string) => ({ BOOKED: '待参加', ATTENDED: '已完成', CANCELLED: '已取消', WAITLISTED:'候补中', LEAVE:'已请假', NO_SHOW:'爽约' }[value] || value)
const statusType = (value: string) => value === 'ATTENDED' ? 'success' : ['CANCELLED','LEAVE'].includes(value) ? 'info' : value==='NO_SHOW'?'danger':'warning'
const entitlementStatus=(v:string)=>({ACTIVE:'生效中',FROZEN:'冻结中',EXPIRED:'已到期',USED_UP:'已用完',REFUNDED:'已退款'}[v]||v)
const productTypeText=(v:string)=>({MONTH_CARD:'月卡',QUARTER_CARD:'季卡',YEAR_CARD:'年卡',VISIT_CARD:'次卡',TIMES_CARD:'次卡',PRIVATE_PACKAGE:'私教课包'}[v]||v)
const productTerm=(item:MemberProduct)=>item.productType==='PRIVATE_PACKAGE'?`共 ${item.totalUses??'—'} 次`:(item.totalUses?`共 ${item.totalUses} 次`:`有效期 ${item.validityDays} 天`)
const reminderText=(v:string)=>({LOW_BALANCE:'剩余次数不足，请及时续费',EXPIRED:'权益已到期',EXPIRING_7_DAYS:'7 天内到期',EXPIRING_30_DAYS:'30 天内到期'}[v]||v)
const refundMoney=(value:number|null)=>formatMoney(value)
const refundStatus=(value:string)=>({PENDING:'待审批',APPROVED:'已批准',REJECTED:'已拒绝',CANCELLED:'已撤回',PAID:'已登记支付'}[value]||value)
const refundStatusType=(value:string)=>value==='PAID'?'success':value==='APPROVED'?'primary':value==='PENDING'?'warning':'info'
const formatDay = (value: string) => new Date(value).toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' })
const formatTime = (value: string) => new Date(value).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
const formatDateTime = (value: string) => `${formatDay(value)} ${formatTime(value)}`
onMounted(() => { load(); loadRecommendations() })
</script>

<style scoped>
.member-home{max-width:1180px;margin:0 auto}.hero{display:flex;align-items:center;justify-content:space-between;min-height:210px;padding:40px 46px;border-radius:24px;color:#fff;background:radial-gradient(circle at 80% 25%,rgba(99,149,255,.5),transparent 32%),linear-gradient(135deg,#111b31,#1d417a)}.hero>div>span{color:#8eb2ff;font-size:11px;font-weight:800;letter-spacing:2px}.hero h1{margin:12px 0 10px;font-size:36px}.hero p{color:#b7c5dc}.hero-stats{display:flex;gap:12px}.hero-stats>div{min-width:110px;padding:20px;border:1px solid rgba(255,255,255,.14);border-radius:18px;background:rgba(255,255,255,.08)}.hero-stats strong,.hero-stats small{display:block}.hero-stats strong{font-size:30px}.hero-stats small{margin-top:4px;color:#b7c5dc}.portal-tabs{margin-top:24px}.portal-tabs :deep(.el-tabs__header){margin-bottom:22px}.recommendation-panel{margin-bottom:24px;padding:24px;border:1px solid #dce7ff;border-radius:20px;background:linear-gradient(135deg,#eff5ff,#fff)}.recommendation-copy>span{color:#3474ff;font-size:10px;font-weight:800;letter-spacing:2px}.recommendation-copy h2{margin:7px 0 6px;color:#17243c}.recommendation-copy p{margin:0;color:#65738b;font-size:13px}.recommendation-copy small{display:block;margin-top:7px;color:#96a1b3}.recommendation-controls{display:grid;grid-template-columns:1fr 140px 100px;gap:10px;margin-top:17px}.recommendation-list{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin-top:16px}.recommendation-list article{padding:15px;border-radius:14px;background:#fff;box-shadow:0 5px 18px rgba(35,75,150,.07)}.recommendation-list article>div{display:flex;justify-content:space-between;gap:8px}.recommendation-list p{min-height:38px;color:#66748b;font-size:12px;line-height:1.6}.recommendation-list small{display:block;color:#98a2b3;font-size:11px}.section-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px}.schedule-filter{display:flex;align-items:center;gap:10px}.notice-badge{display:inline-block;min-width:16px;margin-left:6px;padding:0 5px;border-radius:9px;background:#e24b4a;color:#fff;font-size:11px;line-height:16px;text-align:center}.notice-list{list-style:none;margin:0;padding:0}.notice-list li{padding:14px 16px;margin-bottom:10px;border:1px solid #e7ecf4;border-radius:14px;background:#fff}.notice-list li.unread{border-color:#c9dcff;background:#f6faff}.notice-head{display:flex;align-items:baseline;justify-content:space-between;gap:12px}.notice-head strong{color:#1c2a44}.notice-head span{color:#9aa5b8;font-size:12px}.notice-list p{margin:6px 0 4px;color:#5f6d85;font-size:13px;line-height:1.7}.qr-ttl{color:#8eb2ff!important}.coupon-list,.review-list{list-style:none;margin:0;padding:0}.coupon-list li{display:flex;align-items:center;justify-content:space-between;gap:14px;padding:14px 16px;margin-bottom:10px;border:1px solid #e7ecf4;border-radius:14px;background:#fff}.coupon-list li.dim{opacity:.6}.coupon-left strong{display:block;color:#1c2a44}.coupon-left span{margin-top:5px;display:block;color:#2768ff;font-size:13px;font-weight:600}.coupon-right{text-align:right}.coupon-right small{display:block;margin-top:6px;color:#9aa5b8;font-size:12px}.review-list li{padding:14px 16px;margin-bottom:10px;border:1px solid #e7ecf4;border-radius:14px;background:#fff}.review-head{display:flex;align-items:center;justify-content:space-between}.review-head strong{color:#1c2a44}.review-list p{margin:8px 0 4px;color:#5f6d85;font-size:13px}.review-list small{color:#9aa5b8;font-size:12px}.coupon-picker{display:flex;flex-direction:column;gap:10px;width:100%}.coupon-picker .el-radio{margin-right:0}.section-head h2{margin:0 0 6px;color:#19243a}.section-head p{margin:0;color:#8792a5;font-size:13px}.schedule-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px}.schedule-card{padding:20px;border:1px solid #e7ecf4;border-radius:17px;background:#fff}.card-top{display:flex;justify-content:space-between}.card-top strong{color:#1e2b42}.schedule-card h3{margin:16px 0 8px;color:#19243a}.schedule-card>p{height:42px;color:#7e899c;font-size:13px;line-height:1.6;overflow:hidden}.schedule-info{display:grid;gap:8px;margin:17px 0;color:#59677e;font-size:12px}.schedule-info span{display:flex;align-items:center;gap:7px}.schedule-info .el-icon{color:#3474ff}.capacity{display:flex;align-items:center;gap:12px;margin:15px 0}.capacity .el-progress{flex:1}.capacity small{color:#7f8a9d}.schedule-card>.el-button{width:100%}.booking-table{border-radius:17px}.booking-table strong,.booking-table small{display:block}.booking-table small{margin-top:4px;color:#8a95a8}.muted{color:#b4bbc7}.membership-layout{display:grid;grid-template-columns:1fr 310px;gap:22px}.entitlement-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:14px}.entitlement-grid article{padding:20px;border:1px solid #e1e8f3;border-radius:17px;background:#fff}.entitlement-grid article>div{display:flex;justify-content:space-between;color:#98a2b3;font-size:10px}.entitlement-grid h3{margin:16px 0 8px}.entitlement-grid strong{font-size:25px;color:#2d6bf3}.entitlement-grid p,.entitlement-grid small{display:block;color:#8792a5;font-size:12px}.entitlement-grid small{color:#e17b21}.qr-card{align-self:start;padding:28px;border-radius:20px;background:linear-gradient(145deg,#17223a,#274e89);color:#fff;text-align:center}.qr-card>span{color:#8eb2ff;font-size:10px;font-weight:800;letter-spacing:2px}.qr-card h2{margin:8px 0 20px}.qr-card img{width:220px;border:10px solid #fff;border-radius:12px}.qr-card p{margin:14px 0 6px}.qr-card small{color:#b9c7dc;line-height:1.6}.entitlement-grid .venue-line{color:#8792a5!important}.refund-apply{margin-bottom:24px;padding:22px;border:1px solid #dce7ff;border-radius:20px;background:linear-gradient(135deg,#f2f7ff,#fff)}.refund-apply-form{display:grid;grid-template-columns:1fr 150px;gap:10px}.refund-quote{margin-top:16px;padding:18px;border:1px solid #e1e8f3;border-radius:16px;background:#fff}.quote-amount{display:flex;align-items:baseline;gap:12px;margin-bottom:14px}.quote-amount span{color:#8792a5;font-size:13px}.quote-amount strong{font-size:28px;color:#2d6bf3}.quote-amount small{color:#e17b21}.quote-blocks{margin:6px 0 0;padding-left:18px;line-height:1.7}.blocker-box{margin-top:12px;padding:12px 14px;border:1px dashed #f0c48a;border-radius:12px;background:#fffbf4}.blocker-head{display:flex;align-items:center;justify-content:space-between;gap:12px;color:#8a6320;font-size:13px}.blocker-head strong{color:#d8871a}.blocker-list{list-style:none;margin:10px 0 0;padding:0}.blocker-list li{display:flex;align-items:center;gap:8px;padding:5px 0;color:#5b6880;font-size:12px;border-top:1px solid #f6ead6}.blocker-list li strong{color:#31405a}.blocker-list li small{color:#9aa5b8}@media(max-width:980px){.schedule-grid,.recommendation-list{grid-template-columns:repeat(2,1fr)}.membership-layout{grid-template-columns:1fr}.qr-card{max-width:300px}}@media(max-width:680px){.hero{padding:30px}.hero-stats{display:none}.schedule-grid,.recommendation-list,.entitlement-grid,.refund-apply-form{grid-template-columns:1fr}.recommendation-controls{grid-template-columns:1fr}.section-head{align-items:flex-start}.section-head .el-tag{display:none}}
.msg-switch{display:flex;justify-content:flex-end;margin-bottom:12px}

/* ===== 会员端首页 hero：与进入界面/管理端一致的极淡网点 + 光晕（纯装饰）===== */
.hero { position: relative; }
.hero::before {
  content: ""; position: absolute; left: -30px; top: -24px; right: 45%; bottom: -8px; pointer-events: none;
  background-image: radial-gradient(rgba(72, 132, 214, .5) 1.1px, transparent 1.6px);
  background-size: 7px 7px; opacity: .14;
  -webkit-mask-image: radial-gradient(circle at 20% 55%, #000 4%, transparent 68%);
  mask-image: radial-gradient(circle at 20% 55%, #000 4%, transparent 68%);
}
.hero::after {
  content: ""; position: absolute; left: -80px; top: -60px; width: 380px; height: 230px; pointer-events: none;
  background: radial-gradient(circle at 40% 50%, rgba(88, 152, 255, .14), transparent 66%);
}
.hero > * { position: relative; z-index: 1; }
</style>
