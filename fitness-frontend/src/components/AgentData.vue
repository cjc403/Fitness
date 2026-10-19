<template>
  <span v-if="value == null" class="agent-empty">—</span>
  <span v-else-if="typeof value !== 'object'" class="agent-value">{{ text(value) }}</span>
  <div v-else-if="Array.isArray(value)" class="agent-list">
    <span v-if="!value.length" class="agent-empty">暂无记录</span>
    <article v-for="(entry, index) in value.slice(0, 50)" :key="index"><AgentData :value="entry" :depth="depth + 1" /></article>
    <small v-if="value.length > 50">界面显示前 50 条，请缩小查询范围。</small>
  </div>
  <dl v-else-if="depth < 7" class="agent-fields">
    <div v-for="([key, entry]) in entries" :key="key"><dt>{{ labels[key] || key }}</dt><dd><AgentData :value="entry" :depth="depth + 1" /></dd></div>
  </dl>
  <span v-else>详细内容请在对应业务页面查看</span>
</template>
<script setup lang="ts">
import { computed } from 'vue'
const props = withDefaults(defineProps<{ value: unknown; depth?: number }>(), { depth: 0 })
const entries = computed(() => Object.entries(props.value && typeof props.value === 'object' ? props.value : {}))
const labels: Record<string, string> = {
  id: '编号', scheduleId: '排课编号', bookingId: '预约编号', memberId: '会员编号', leadId: '潜客编号', hostId: '接待人编号',
  member: '会员信息', schedule: '排课信息', course: '课程信息', plan: '训练计划', lead: '潜客信息', trials: '体验安排',
  courseId: '课程编号', coachId: '教练编号', venueId: '门店编号', roomId: '教室编号', ownerId: '顾问编号', userId: '账号编号',
  name: '名称', realName: '姓名', memberName: '会员', memberNo: '会员号', courseName: '课程', coachName: '教练', venueName: '门店', title: '标题',
  startTime: '开始时间', endTime: '结束时间', startsAt: '开始时间', endsAt: '结束时间', startDate: '开始日期', endDate: '结束日期',
  scheduledStartTime: '开始时间', scheduledEndTime: '结束时间', courseType: '课程类型', type: '类型',
  status: '状态', version: '版本', expectedVersion: '当前版本', goal: '训练目标', note: '备注', content: '内容', phone: '联系电话',
  total: '总数', totalElements: '总数', records: '记录', items: '明细', summary: '汇总', plans: '训练计划', bookings: '预约', courses: '课程',
  exercises: '训练动作', mode: '训练方式', sets: '组数', reps: '每组次数', weightKg: '重量（kg）', minutes: '时长（分钟）',
  remainingUses: '剩余次数', availableUses: '可用次数', remainingPlaces: '剩余名额', maxStudents: '容量', enrolledCount: '已约人数',
  createdAt: '创建时间', updatedAt: '更新时间', date: '日期', fee: '费用', price: '金额', amount: '金额', totalCents: '总金额（分）',
  bookedAt: '预约时间', checkedInAt: '签到时间', cancelledAt: '取消时间', cancelReason: '取消原因', waitlistPosition: '候补顺位',
  role: '角色', healthScore: '健康指数', durationMinutes: '时长（分钟）', intensity: '强度', maxCapacity: '容量',
  source: '来源', page: '页码', size: '每页数量', totalPages: '总页数', truncated: '是否截断', organizationName: '所属门店', roleName: '角色',
  DRAFT: '草稿', from: '开始日期', to: '结束日期', revenue: '区间营收（元）', newMembers: '新增会员', totalMembers: '会员总数', activeMembers: '活跃会员', silentMembers: '沉默会员', churnedMembers: '流失会员', totalAttendances: '到店次数', highRiskMembers: '高风险会员'
}
const values: Record<string, string> = { ACTIVE: '生效中', DRAFT: '草稿', ARCHIVED: '已归档', CANCELLED: '已取消', CONFIRMED: '已确认', BOOKED: '已预约', ATTENDED: '已签到', WAITLISTED: '候补中', STRENGTH: '力量训练', DURATION: '时长训练', FAT_LOSS: '减脂', MUSCLE_GAIN: '增肌', POSTURE: '体态改善', ENDURANCE: '耐力', GENERAL: '综合体能', REGULAR: '普通课程', GROUP: '团体课程', PRIVATE: '私教课程', SCHEDULED: '待开课', COMPLETED: '已完成', SUPER_ADMIN: '总部管理员', MANAGER: '店长', RECEPTION: '前台', SALES: '会籍顾问', COACH: '教练', MEMBER: '会员' }
const text = (value: unknown) => typeof value === 'boolean' ? value ? '是' : '否' : values[String(value)] || String(value).replace(/^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2}:\d{2})/, '$1 $2')
</script>
<style scoped>
.agent-fields{display:grid;gap:7px;margin:0}.agent-fields>div{display:grid;grid-template-columns:minmax(65px,30%) minmax(0,1fr);gap:8px}.agent-fields dt{font-size:12px;color:#6b7c93;overflow-wrap:anywhere}.agent-fields dd{margin:0;min-width:0;font-size:13px;overflow-wrap:anywhere}.agent-list{display:grid;gap:9px}.agent-list>article{padding:10px;border:1px solid #e1e9f4;border-radius:10px;background:#f8fbff}.agent-empty{color:#8795a7}.agent-value{white-space:pre-wrap;overflow-wrap:anywhere}
</style>
