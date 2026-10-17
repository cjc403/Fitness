<template>
  <div class="page-shell">
    <WorkspaceHero compact>
      <template #heading><span class="eyebrow">OPERATIONS</span><h1>预约与签到</h1><p>{{ canWriteBooking ? '集中管理会员约课、取消与到店签到' : '查看本场馆预约并完成到店签到' }}</p></template>
      <template #actions><el-button v-if="canWriteBooking" type="primary" size="large" @click="openCreate"><el-icon><Plus /></el-icon> 新增预约</el-button></template>
    </WorkspaceHero>
    <div class="stats-row"><div><span>待上课</span><strong>{{ counts.BOOKED }}</strong></div><div><span>候补中</span><strong>{{ counts.WAITLISTED }}</strong></div><div><span>已签到</span><strong>{{ counts.ATTENDED }}</strong></div><div><span>请假/爽约</span><strong>{{ counts.LEAVE+counts.NO_SHOW }}</strong></div><div><span>总记录</span><strong>{{ bookings.length }}</strong></div></div>
    <el-alert v-if="overdue.length" type="warning" :closable="false" class="overdue-bar">
      <template #title><div class="overdue-head"><span>有 <strong>{{ overdue.length }}</strong> 条预约已结束但未核销 —— 这些记录会一直挡住会员申请退款</span><el-button v-if="canCheckIn" type="warning" size="small" :loading="overdueLoading" @click="doResolveOverdue">一键核销</el-button></div></template>
      <ul class="overdue-list"><li v-for="o in overdue" :key="o.id"><el-tag size="small" :type="o.status==='BOOKED'?'warning':'info'">{{ o.status==='BOOKED'?'将记爽约':'候补失效' }}</el-tag><strong>{{ o.memberName }}</strong><span>{{ o.courseName }}</span><small>{{ formatDateTime(o.startTime||'') }}</small></li></ul>
    </el-alert>
    <el-card shadow="never" class="table-card"><div class="filter-bar"><el-input v-model="keyword" clearable placeholder="搜索会员、课程或教练" :prefix-icon="Search"/><el-select v-model="status" style="width:150px"><el-option label="全部状态" value=""/><el-option label="待上课" value="BOOKED"/><el-option label="候补中" value="WAITLISTED"/><el-option label="已签到" value="ATTENDED"/><el-option label="已请假" value="LEAVE"/><el-option label="爽约" value="NO_SHOW"/><el-option label="已取消" value="CANCELLED"/></el-select></div>
      <el-table v-loading="loading" :data="filtered"><el-table-column label="会员" min-width="160"><template #default="{row}"><div class="member-cell"><span>{{ row.member?.realName?.slice(0,1) }}</span><div><strong>{{ row.member?.realName }}</strong><small>{{ row.member?.memberNo }}</small></div></div></template></el-table-column><el-table-column label="课程" min-width="170"><template #default="{row}"><strong>{{ row.schedule?.course?.name }}</strong><small>{{ row.schedule?.coachName }}</small></template></el-table-column><el-table-column label="上课时间" min-width="180"><template #default="{row}">{{ formatDateTime(row.schedule?.scheduledStartTime) }}</template></el-table-column><el-table-column label="状态" width="120"><template #default="{row}"><el-tag :type="tagType(row.status)">{{ statusText(row.status) }}</el-tag><small v-if="row.status==='WAITLISTED'">第 {{ row.waitlistPosition }} 位</small></template></el-table-column><el-table-column label="操作" width="220" fixed="right"><template #default="{row}"><el-button v-if="row.status==='BOOKED'&&canCheckIn" link type="success" @click="doCheckIn(row)" data-agent-type="booking" :data-agent-id="row.id" :data-agent-label="`${row.member?.realName || ''} · ${row.schedule?.course?.name || ''}`" data-agent-action="check-in">签到</el-button><el-button v-if="row.status==='BOOKED'&&canCheckIn&&new Date(row.schedule.scheduledStartTime)<new Date()" link type="warning" @click="doNoShow(row)" data-agent-type="booking" :data-agent-id="row.id" :data-agent-label="`${row.member?.realName || ''} · ${row.schedule?.course?.name || ''}`" data-agent-action="no-show">爽约</el-button><el-button v-if="['BOOKED','WAITLISTED'].includes(row.status)&&canWriteBooking" link type="danger" @click="doCancel(row)" data-agent-type="booking" :data-agent-id="row.id" :data-agent-label="`${row.member?.realName || ''} · ${row.schedule?.course?.name || ''}`" data-agent-action="cancel">取消</el-button><span v-if="!['BOOKED','WAITLISTED'].includes(row.status)" class="muted">—</span></template></el-table-column></el-table><el-empty v-if="!loading&&!filtered.length" description="暂无匹配预约" /></el-card>
    <el-dialog v-model="dialog" title="新增预约" width="520px">
      <el-alert title="私教预约必须选择会员自己的有效课包；预约预占 1 次，本人签课后正式扣次。" type="info" :closable="false" show-icon/>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" class="booking-form">
        <el-form-item label="会员" prop="memberId" data-agent-field="memberId"><el-select v-model="form.memberId" filterable style="width:100%" placeholder="搜索会员"><el-option v-for="m in members" :key="m.id" :label="`${m.realName} · ${m.memberNo}`" :value="m.id" :disabled="m.status!=='ACTIVE'||m.blacklistFlag"/></el-select></el-form-item>
        <el-form-item label="排课" prop="scheduleId" data-agent-field="scheduleId"><el-select v-model="form.scheduleId" style="width:100%" placeholder="选择未来排课"><el-option v-for="s in availableSchedules" :key="s.id" :label="`${s.course?.name} · ${formatDateTime(s.scheduledStartTime)} · ${s.enrolledCount}/${s.maxStudents}`" :value="s.id" :disabled="s.enrolledCount>=s.maxStudents"/></el-select></el-form-item>
        <el-form-item v-if="isPrivate" label="私教课包" data-agent-field="entitlementId"><el-select v-model="form.entitlementId" v-loading="packagesLoading" placeholder="选择可用课包" style="width:100%"><el-option v-for="e in eligiblePackages" :key="e.id" :value="e.id" :label="`${e.productName} · 可约 ${e.availableUses??e.remainingUses} 次 · ${e.validUntil} 到期`" /></el-select><small v-if="!packagesLoading&&!eligiblePackages.length">暂无有效私教课包，请先销售开卡或续费。</small></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="isPrivate&&(!form.entitlementId||packagesLoading)" @click="save">确认预约</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import WorkspaceHero from '@/components/WorkspaceHero.vue'
import { computed,onMounted,reactive,ref,watch } from 'vue';import { ElMessage,ElMessageBox,type FormInstance,type FormRules } from 'element-plus';import { Plus,Search } from '@element-plus/icons-vue';import { cancelBooking,checkIn,createBooking,listBookings,markNoShow,listOverdueBookings,resolveOverdueBookings,type Booking,type OverdueBooking } from '@/api/modules/booking';import { listMembers,type Member } from '@/api/modules/member';import { listSchedules,type Schedule } from '@/api/modules/course'
import { hasPermission } from '@/utils/auth'
import { listEntitlements,type Entitlement } from '@/api/modules/commercial'
import { useAuthStore } from '@/store/modules/auth'
const auth=useAuthStore()
const loading=ref(false),saving=ref(false),dialog=ref(false),keyword=ref(''),status=ref('');const bookings=ref<Booking[]>([]),members=ref<Member[]>([]),schedules=ref<Schedule[]>([]);const overdue=ref<OverdueBooking[]>([]),overdueLoading=ref(false);const formRef=ref<FormInstance>();const form=reactive({memberId:undefined as number|undefined,scheduleId:undefined as number|undefined,entitlementId:undefined as number|undefined});const rules:FormRules={memberId:[{required:true,message:'请选择会员'}],scheduleId:[{required:true,message:'请选择排课'}]}
const packages=ref<Entitlement[]>([]),packagesLoading=ref(false)
const selectedSchedule=computed(()=>schedules.value.find(s=>s.id===form.scheduleId))
const isPrivate=computed(()=>selectedSchedule.value?.course?.type==='PRIVATE')
const eligiblePackages=computed(()=>packages.value.filter(e=>e.productType==='PRIVATE_PACKAGE'&&e.status==='ACTIVE'&&(e.availableUses??e.remainingUses??0)>0&&!!selectedSchedule.value&&e.venueId===selectedSchedule.value.venueId&&e.validFrom<=selectedSchedule.value.scheduledStartTime.slice(0,10)&&e.validUntil>=selectedSchedule.value.scheduledStartTime.slice(0,10)))
let packageRequest=0
watch(()=>[form.memberId,form.scheduleId],async()=>{const epoch=++packageRequest;form.entitlementId=undefined;packages.value=[];packagesLoading.value=false;if(!form.memberId||!isPrivate.value)return;packagesLoading.value=true;try{const response=await listEntitlements({memberId:form.memberId});if(epoch===packageRequest)packages.value=response.data}catch{}finally{if(epoch===packageRequest)packagesLoading.value=false}})
const canWriteBooking=hasPermission('BOOKING_WRITE'),canCheckIn=hasPermission('CHECKIN_WRITE')
const counts=computed(()=>Object.fromEntries(['BOOKED','WAITLISTED','ATTENDED','LEAVE','NO_SHOW','CANCELLED'].map(s=>[s,bookings.value.filter(x=>x.status===s).length])) as Record<string,number>);const filtered=computed(()=>bookings.value.filter(x=>(!status.value||x.status===status.value)&&(!keyword.value||`${x.member?.realName}${x.member?.memberNo}${x.schedule?.course?.name}${x.schedule?.coachName}`.toLowerCase().includes(keyword.value.toLowerCase()))));const availableSchedules=computed(()=>schedules.value.filter(x=>x.status==='SCHEDULED'&&new Date(x.scheduledStartTime)>new Date()))
const load=async()=>{loading.value=true;try{const requests:Promise<any>[]=[listBookings(),listSchedules(),canCheckIn?listOverdueBookings():Promise.resolve({data:[]})];if(canWriteBooking)requests.push(listMembers({page:1,size:100}));const [b,s,od,m]=await Promise.all(requests);bookings.value=b.data;schedules.value=s.data;overdue.value=od?.data||[];if(m)members.value=m.data.items}finally{loading.value=false}}
const openCreate=()=>{form.memberId=undefined;form.scheduleId=undefined;form.entitlementId=undefined;dialog.value=true}
const save=async()=>{if(saving.value||!await formRef.value?.validate().catch(()=>false)||!form.memberId||!form.scheduleId)return;if(isPrivate.value&&!form.entitlementId)return ElMessage.warning('请选择有效私教课包');saving.value=true;try{await createBooking({memberId:form.memberId,scheduleId:form.scheduleId,entitlementId:form.entitlementId});ElMessage.success('预约成功');dialog.value=false;await load()}finally{saving.value=false}}
const doCheckIn=async(row:Booking)=>{await ElMessageBox.confirm(`确认 ${row.member.realName} 已到店上课？`,'签到确认',{type:'success'});await checkIn(row.id);ElMessage.success('签到成功');await load()}
const doNoShow=async(row:Booking)=>{await ElMessageBox.confirm(`确认将 ${row.member.realName} 标记为爽约？系统将记录处罚积分。`,'爽约处理',{type:'warning'});await markNoShow(row.id);ElMessage.success('已记录爽约');await load()}
const doResolveOverdue=async()=>{
  const willNoShow=overdue.value.filter(o=>o.status==='BOOKED').length, willCancel=overdue.value.length-willNoShow
  const lines=['将核销 '+overdue.value.length+' 条已结束未核销的预约：']
  overdue.value.slice(0,8).forEach(o=>lines.push('· '+(o.memberName||'会员')+' 《'+(o.courseName||'课程')+'》'))
  if(overdue.value.length>8)lines.push('· …')
  if(willNoShow)lines.push('其中 '+willNoShow+' 条记为爽约：释放预占、不扣次数')
  if(willCancel)lines.push('另有 '+willCancel+' 条候补失效')
  lines.push('核销后这些会员即可正常申请退款。是否继续？')
  try{await ElMessageBox.confirm(lines.join('\n'),'一键核销过期预约',{type:'warning',confirmButtonText:'确认核销',cancelButtonText:'再想想'})}catch{return}
  overdueLoading.value=true
  try{
    const r=(await resolveOverdueBookings()).data
    const parts:string[]=[]
    if(r.noShow.length)parts.push('已记爽约 '+r.noShow.length+' 条')
    if(r.cancelled.length)parts.push('候补失效 '+r.cancelled.length+' 条')
    if(r.skipped.length)parts.push(r.skipped.length+' 条需店长处理')
    ElMessage[parts.length?'success':'info'](parts.length?parts.join('，'):'没有需要核销的预约')
    await load()
  }finally{overdueLoading.value=false}
}
const doCancel=async(row:Booking)=>{try{const late=new Date(row.schedule.scheduledStartTime).getTime()<Date.now()+2*60*60*1000;if(late&&!['SUPER_ADMIN','MANAGER'].includes(auth.userInfo?.role||''))return ElMessage.warning('开课前两小时内请由店长办理取消');let reason='后台取消';if(late){const {value}=await ElMessageBox.prompt('临时取消必须填写原因，预占将释放。','店长临时取消',{inputValidator:v=>!!v?.trim()&&v.length<=200||'请填写 1–200 字原因'});reason=value}else await ElMessageBox.confirm('确认取消该预约？课包预占将释放，有效候补自动递补。','取消预约',{type:'warning'});await cancelBooking(row.id,reason);ElMessage.success('预约已取消');await load()}catch(error){if(error!=='cancel'&&error!=='close')console.warn('取消预约未完成')}}
const formatDateTime=(v:string)=>v?new Date(v).toLocaleString('zh-CN',{month:'2-digit',day:'2-digit',weekday:'short',hour:'2-digit',minute:'2-digit'}):'—'
const statusText=(v:string)=>({BOOKED:'待上课',WAITLISTED:'候补中',ATTENDED:'已签到',LEAVE:'已请假',NO_SHOW:'爽约',CANCELLED:'已取消'}[v]||v)
const tagType=(v:string)=>v==='ATTENDED'?'success':['CANCELLED','LEAVE'].includes(v)?'info':v==='NO_SHOW'?'danger':'warning'
onMounted(load)
</script>
<style scoped>
.page-title{display:flex;justify-content:space-between;align-items:center;margin-bottom:24px}.eyebrow{color:#2768ff;font-size:11px;font-weight:800;letter-spacing:1.5px}.page-title h1{margin:4px 0 6px;font-size:28px;color:#172033}.page-title p{color:#8490a4}.stats-row{display:grid;grid-template-columns:repeat(5,1fr);gap:14px;margin-bottom:20px}.stats-row>div{padding:20px;background:#fff;border:1px solid #e9edf5;border-radius:15px}.stats-row span{display:block;color:#8590a3;font-size:13px}.stats-row strong{display:block;margin-top:8px;color:#19243a;font-size:28px}.table-card{border-radius:16px}.filter-bar{display:flex;justify-content:space-between;gap:14px;margin-bottom:18px}.filter-bar .el-input{max-width:350px}.member-cell{display:flex;align-items:center;gap:10px}.member-cell>span{display:grid;place-items:center;width:36px;height:36px;border-radius:10px;background:#eaf1ff;color:#2768ff;font-weight:800}.member-cell strong,.table-card small{display:block}.table-card small{margin-top:4px;color:#8a95a8}.muted{color:#b1b8c4}.booking-form{margin-top:20px}@media(max-width:1000px){.stats-row{grid-template-columns:repeat(3,1fr)}}@media(max-width:760px){.stats-row{grid-template-columns:repeat(2,1fr)}.filter-bar{flex-direction:column}.filter-bar .el-input{max-width:none}}
.overdue-bar{margin-bottom:14px;border-radius:14px}.overdue-head{display:flex;align-items:center;justify-content:space-between;gap:12px}.overdue-head strong{color:#d8871a}.overdue-list{list-style:none;margin:8px 0 0;padding:0}.overdue-list li{display:flex;align-items:center;gap:8px;padding:4px 0;font-size:12px;color:#5b6880;border-top:1px solid #f6ead6}.overdue-list li strong{color:#31405a}.overdue-list li small{color:#9aa5b8}</style>
