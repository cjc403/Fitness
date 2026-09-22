<template>
  <section class="training-panel" v-loading="loading">
    <div class="heading"><div><span>PRIVATE TRAINING</span><h2>{{ isMember ? '我的私教签课' : '私教消课与月结' }}</h2><p>预约预占 → 到课签到 → 教练提交 → 会员签课 → 正式消课</p></div><el-button @click="load">刷新记录</el-button></div>
    <el-alert title="仅新关联课包的私教预约进入本流程；历史预约不会自动扣课。爽约只释放预占，不自动扣次。" type="info" :closable="false" show-icon />
    <section v-if="isMember" class="guide">
      <el-alert v-if="!hasPackage" type="warning" :closable="false" title="你还没有私教课包"><template #default><p class="guide-line">私教消课要求先有私教课包，再去「发现课程」预约私教课。没有课包时无法预约私教。</p><el-button size="small" type="primary" @click="go('shop')">去购买会籍</el-button></template></el-alert>
      <el-alert v-else-if="!privateSchedules.length" type="warning" :closable="false" title="本门店暂未开放私教课排期"><template #default><p class="guide-line">你已有私教课包，但本门店最近没有私教课可预约，请联系前台安排排课。</p></template></el-alert>
      <el-alert v-else type="success" :closable="false" title="已就绪，可以去预约私教课了"><template #default><p class="guide-line">你有私教课包，本门店也有私教课可约（{{ privateSchedules.length }} 节）。预约后按「预约预占 → 到店签到 → 教练提交 → 本人签课」完成消课。</p><el-button size="small" type="primary" @click="go('book-private')">去预约私教课</el-button></template></el-alert>
    </section>
    <section v-else class="guide">
      <div class="guide-title">私教消课要能产生记录，这 4 条必须同时满足</div>
      <ul class="checklist">
        <li :class="{ok:readiness.privateCourses>0}"><span class="mark">{{ readiness.privateCourses>0?'✓':'✗' }}</span>本门店有私教课程（当前 {{ readiness.privateCourses }} 门）<small v-if="!readiness.privateCourses">去「课程与排课」新建一门课程类型为私教的课</small></li>
        <li :class="{ok:readiness.scheduledPrivateLessons>0}"><span class="mark">{{ readiness.scheduledPrivateLessons>0?'✓':'✗' }}</span>已排期的私教课（当前 {{ readiness.scheduledPrivateLessons }} 节）<small v-if="!readiness.scheduledPrivateLessons">在「课程与排课」里给私教课新增排课</small></li>
        <li :class="{ok:readiness.lessonsWithCoach>0}"><span class="mark">{{ readiness.lessonsWithCoach>0?'✓':'✗' }}</span>排课已绑定可用教练账号（当前 {{ readiness.lessonsWithCoach }} 节）<small v-if="!readiness.lessonsWithCoach">私教排课必须绑定教练账号，只填姓名会员一约就报错</small></li>
        <li :class="{ok:readiness.usablePackages>0}"><span class="mark">{{ readiness.usablePackages>0?'✓':'✗' }}</span>本门店有效的私教课包（当前 {{ readiness.usablePackages }} 份）<small v-if="!readiness.usablePackages">会员没有私教课包无法预约私教，可在「会籍商品」上架私教课包</small></li>
      </ul>
      <p class="guide-line">4 条都齐了但这里仍是 0 条，说明还没有会员预约私教课。</p>
    </section>
    <div class="metrics"><article><span>已预占</span><strong>{{ count('RESERVED') }}</strong></article><article><span>待会员签课</span><strong>{{ count('PENDING') }}</strong></article><article><span>已正式消课</span><strong>{{ count('CONFIRMED') }}</strong></article><article v-if="!isMember"><span>签课产值 / 提成</span><strong>¥{{ money(revenue) }} / ¥{{ money(commission) }}</strong></article></div>
    <el-alert v-if="failed" title="记录加载失败，未将失败请求显示为零数据。请点击刷新重试。" type="error" :closable="false" />
    <el-tabs v-else v-model="tab">
      <el-tab-pane label="签课记录" name="lessons">
        <el-table :data="lessons" stripe>
          <el-table-column label="课程 / 预约" min-width="170"><template #default="{row}"><strong>{{ row.courseName }}</strong><small>预约 #{{ row.bookingId }} · 课包 #{{ row.entitlementId }}</small></template></el-table-column>
          <el-table-column v-if="!isMember" prop="memberName" label="会员" width="100" />
          <el-table-column prop="coachName" label="教练" width="100" />
          <el-table-column label="上课时间" min-width="160"><template #default="{row}">{{ dateTime(row.startTime) }}</template></el-table-column>
          <el-table-column label="状态" width="130"><template #default="{row}"><el-tag :type="row.status==='CONFIRMED'?'success':row.status==='PENDING'?'warning':'info'">{{ statusText(row.status) }}</el-tag></template></el-table-column>
          <el-table-column label="课时金额" width="115"><template #default="{row}">¥{{ money(row.unitPrice) }}<small v-if="['RESERVED','PENDING'].includes(row.status)">签课时确定金额</small></template></el-table-column>
          <el-table-column v-if="!isMember" label="提成快照" width="120"><template #default="{row}">{{ row.commissionRate==null ? '尚未签课' : `${row.commissionRate}% / ¥${money(row.commissionAmount)}` }}</template></el-table-column>
          <el-table-column label="操作" min-width="155"><template #default="{row}">
            <el-button v-if="isCoach&&row.status==='RESERVED'" link type="primary" :disabled="new Date(row.endTime)>new Date()||busy" @click="submit(row)">提交授课</el-button>
            <el-button v-if="isMember&&row.status==='PENDING'" type="primary" size="small" :disabled="busy" @click="confirm(row)">本人确认签课</el-button>
            <el-button v-if="canManage&&['PENDING','CONFIRMED'].includes(row.status)&&!row.settlementId" link type="danger" :disabled="busy" @click="reverse(row)">纠错冲正</el-button>
            <span v-if="row.settlementId" class="muted">结算单 #{{ row.settlementId }}</span><small v-if="row.note">{{ row.note }}</small>
          </template></el-table-column>
        </el-table>
        <el-empty v-if="!lessons.length&&!loading" description="暂无新关联私教预约；购买私教课包后预约私教即可开始" :image-size="70" />
      </el-tab-pane>
      <el-tab-pane v-if="!isMember" label="提成规则与月结" name="settlements">
        <p class="muted">默认提成为 40%，总部或店长可调整。签课后金额和比例不随规则修改而变化。当前月可生成、更新草稿，次月才可审核；月结单不是实际打款凭证。</p>
        <div class="rule-grid"><article v-for="rule in rules" :key="rule.coachId"><strong>{{ rule.coachName }} · 门店 #{{ rule.venueId }}</strong><p>提成 {{ rule.rate }}% <el-tag size="small" v-if="rule.defaultRule" type="info">默认规则</el-tag></p><el-button v-if="canManage" size="small" :disabled="busy" @click="editRule(rule)">调整比例</el-button></article></div>
        <div v-if="canManage" class="settlement-controls"><el-select v-model="coachId" placeholder="选择教练"><el-option v-for="rule in rules" :key="rule.coachId" :value="rule.coachId" :label="`${rule.coachName} · 门店 #${rule.venueId}`" /></el-select><el-date-picker v-model="month" type="month" value-format="YYYY-MM" placeholder="结算月份" :disabled-date="futureMonth" /><el-button type="primary" :disabled="!coachId||!month||busy" @click="generate">生成 / 更新月结草稿</el-button></div>
        <el-table :data="settlements" stripe><el-table-column prop="coachName" label="教练" min-width="110"/><el-table-column prop="month" label="结算月份" width="110"/><el-table-column prop="lessonCount" label="已签课节数" width="110"/><el-table-column label="产值" min-width="110"><template #default="{row}">¥{{ money(row.serviceAmount) }}</template></el-table-column><el-table-column label="提成" min-width="110"><template #default="{row}">¥{{ money(row.commissionAmount) }}</template></el-table-column><el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="row.status==='APPROVED'?'success':'warning'">{{ row.status==='APPROVED'?'已审核':'草稿' }}</el-tag></template></el-table-column><el-table-column label="操作" min-width="170"><template #default="{row}"><el-button v-if="canManage&&row.status==='DRAFT'" link type="primary" :disabled="row.month>=currentMonth||busy" @click="approve(row)">{{ row.month>=currentMonth ? '次月可审核' : '审核月结' }}</el-button><span v-else class="muted">{{ row.approvedAt ? dateTime(row.approvedAt) : '只读' }}</span></template></el-table-column></el-table>
        <el-empty v-if="!settlements.length&&!loading" description="暂无月结单，完成签课后由总部或店长生成" :image-size="70" />
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/store/modules/auth'
import { listPrivateLessons, submitPrivateLesson, confirmPrivateLesson, reversePrivateLesson, listCommissionRules, setCommissionRule, listCoachSettlements, generateCoachSettlement, approveCoachSettlement, getPrivateReadiness, type PrivateLesson, type CommissionRule, type CoachSettlement, type PrivateReadiness } from '@/api/modules/training'
import { getMyEntitlements, type Entitlement } from '@/api/modules/commercial'
import { listMemberSchedules, type MemberSchedule } from '@/api/modules/memberPortal'

const emit=defineEmits<{ changed:[]; go:[target:string] }>()
const auth=useAuthStore()
const role=computed(()=>auth.userInfo?.role)
const isMember=computed(()=>role.value==='MEMBER')
const isCoach=computed(()=>role.value==='COACH')
const canManage=computed(()=>['SUPER_ADMIN','MANAGER'].includes(role.value||''))
const lessons=ref<PrivateLesson[]>([]), rules=ref<CommissionRule[]>([]), settlements=ref<CoachSettlement[]>([])
const myPackages=ref<Entitlement[]>([]), privateSchedules=ref<MemberSchedule[]>([])
const readiness=ref<PrivateReadiness>({privateCourses:0,scheduledPrivateLessons:0,lessonsWithCoach:0,usablePackages:0})
const hasPackage=computed(()=>myPackages.value.length>0)
const go=(target:string)=>emit('go',target)
const loading=ref(false), failed=ref(false), busy=ref(false), tab=ref('lessons'), coachId=ref<number>()
const now=new Date()
const currentMonth=`${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}`
const month=ref(currentMonth)
const count=(status:string)=>lessons.value.filter(l=>l.status===status).length
const revenue=computed(()=>lessons.value.filter(l=>l.status==='CONFIRMED').reduce((s,l)=>s+Number(l.unitPrice),0))
const commission=computed(()=>lessons.value.filter(l=>l.status==='CONFIRMED').reduce((s,l)=>s+Number(l.commissionAmount||0),0))
const money=(v?:number)=>Number(v||0).toFixed(2)
const dateTime=(v:string)=>new Date(v).toLocaleString('zh-CN',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'})
const statusText=(v:string)=>({RESERVED:'预约预占',PENDING:'待会员签课',CONFIRMED:'已消课',RELEASED:'已释放',REVERSED:'已冲正'}[v]||v)
const futureMonth=(date:Date)=>date.getFullYear()>now.getFullYear()||(date.getFullYear()===now.getFullYear()&&date.getMonth()>now.getMonth())
const load=async()=>{
  loading.value=true;failed.value=false
  try{
    lessons.value=(await listPrivateLessons()).data as PrivateLesson[]
    if(isMember.value){
      const [pkgRes,schedRes]=await Promise.all([getMyEntitlements().catch(()=>null),listMemberSchedules().catch(()=>null)])
      myPackages.value=((pkgRes?.data??[]) as Entitlement[]).filter(e=>e.productType==='PRIVATE_PACKAGE'&&e.status==='ACTIVE'&&(e.availableUses??e.remainingUses??0)>0)
      privateSchedules.value=((schedRes?.data??[]) as MemberSchedule[]).filter(s=>s.courseType==='PRIVATE')
    }else{
      const [ruleRes,setRes,readyRes]=await Promise.all([listCommissionRules(),listCoachSettlements(),getPrivateReadiness().catch(()=>null)])
      rules.value=ruleRes.data as CommissionRule[]
      settlements.value=setRes.data as CoachSettlement[]
      if(readyRes?.data)readiness.value=readyRes.data as PrivateReadiness
    }
  }catch{failed.value=true}finally{loading.value=false}
}
const execute=async(action:()=>Promise<unknown>)=>{if(busy.value)return;busy.value=true;try{await action();ElMessage.success('操作成功');await load();emit('changed')}catch(error){if(error!=='cancel'&&error!=='close')console.warn('私教操作未完成')}finally{busy.value=false}}
const submit=(row:PrivateLesson)=>execute(async()=>{await ElMessageBox.confirm(`确认已完成“${row.courseName}”授课并已签到？提交后等待会员本人签课。`,'提交授课',{type:'warning'});await submitPrivateLesson(row.bookingId)})
const confirm=(row:PrivateLesson)=>execute(async()=>{await ElMessageBox.confirm(`确认已接受“${row.courseName}”授课？将正式扣减课包 1 次，非会员本人不得代签。`,'本人确认签课',{type:'warning'});await confirmPrivateLesson(row.bookingId)})
const reverse=(row:PrivateLesson)=>execute(async()=>{const {value}=await ElMessageBox.prompt('请填写纠错依据。已进入月结的消课不能直接冲正。','消课纠错',{inputValidator:v=>!!v?.trim()&&v.length<=200||'请填写 1–200 字的原因'});await reversePrivateLesson(row.bookingId,value)})
const editRule=(rule:CommissionRule)=>execute(async()=>{const {value}=await ElMessageBox.prompt('设置 0–100 的百分比（最多两位小数），只影响以后签课的提成。','提成规则',{inputValue:String(rule.rate),inputValidator:v=>/^\d{1,3}(\.\d{1,2})?$/.test(v)&&Number(v)<=100||'请输入 0–100 的百分比'});await setCommissionRule(rule.coachId,Number(value))})
const generate=()=>execute(async()=>{if(coachId.value&&month.value)await generateCoachSettlement(coachId.value,month.value)})
const approve=(row:CoachSettlement)=>execute(async()=>{await ElMessageBox.confirm('审核时会自动纳入同月未结算签课，并锁定金额。审核后不可直接修改或冲正。','审核月结',{type:'warning'});await approveCoachSettlement(row.id)})
onMounted(load)
defineExpose({refresh:load})
</script>

<style scoped>
.training-panel{margin-top:24px;padding:24px;background:#fff;border:1px solid #e3eaf5;border-radius:18px}.heading{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:18px}.heading span{font-size:10px;letter-spacing:2px;color:#3474ff;font-weight:800}.heading h2{margin:8px 0;color:#17243c}.heading p,.muted{color:#8792a5;font-size:12px;line-height:1.7}.metrics{display:flex;gap:12px;flex-wrap:wrap;margin:18px 0}.metrics article{flex:1;min-width:120px;padding:16px;background:#f5f8fd;border-radius:12px}.metrics span,.metrics strong{display:block}.metrics span{color:#7a879c;font-size:12px}.metrics strong{margin-top:7px;color:#203350;font-size:22px}.training-panel small{display:block;margin-top:4px;color:#8b97aa;font-size:11px}.rule-grid{display:flex;gap:12px;flex-wrap:wrap;margin:16px 0}.rule-grid article{padding:16px;border:1px solid #e8edf5;border-radius:12px;min-width:170px}.settlement-controls{display:flex;gap:12px;flex-wrap:wrap;margin:20px 0}.guide{margin:16px 0}.guide .guide-title{margin-bottom:8px;color:#203350;font-size:13px;font-weight:500}.guide .guide-line{margin:6px 0 10px;color:#5c6b83;font-size:12px;line-height:1.7}.checklist{list-style:none;margin:0;padding:0}.checklist li{display:flex;align-items:flex-start;gap:8px;padding:9px 0;border-bottom:1px dashed #eef1f6;color:#8a6a2a;font-size:12px;line-height:1.7}.checklist li:last-child{border-bottom:none}.checklist li.ok{color:#2f7d55}.checklist .mark{flex:none;width:14px;color:#c54949;font-weight:500}.checklist li.ok .mark{color:#2f7d55}.checklist li small{display:block;color:#96a1b3;font-size:11px;line-height:1.6}@media(max-width:650px){.training-panel{padding:14px}.heading{align-items:flex-start}.heading p{max-width:230px}.metrics strong{font-size:18px}}
</style>
