<script setup lang="ts">
import { computed, reactive, ref, watch, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/store/modules/auth'
import { hasPermission } from '@/utils/accessControl.mjs'
import * as api from '@/api/modules/trainingPlans'
import type { Detail, PlanSummary, SessionView, Workbench, Student, PageView, BookingOption, Goal } from '@/api/modules/trainingPlans'
import WorkspaceHero from '@/components/WorkspaceHero.vue'
import BodyMeasurePanel from '@/components/BodyMeasurePanel.vue'
const props=withDefaults(defineProps<{ memberMode?:boolean; initialMemberId?:number; initialMemberName?:string }>(),{memberMode:false,initialMemberName:''})
const auth=useAuthStore()
type Operation='create'|'edit'|'publish'|'archive'|'record'|'void'|'feedback'
interface FormExercise { id?:number; exerciseId?:number; name:string; mode:'STRENGTH'|'DURATION'; status?:'DONE'|'SKIPPED'; sets:number|null; reps:number|null; weightKg:number|null; minutes:number|null; note:string }
const data=ref<Workbench|null>(null), venues=ref<Workbench['venues']>([]), loading=ref(false),loadError=ref('')
const keyword=ref(props.initialMemberName),status=ref('ALL'),page=ref(1),venueId=ref<number>()
const detailData=ref<Detail|null>(null),detailDialog=ref(false),detailLoading=ref(false),detailError=ref(''),historyPage=ref(1),targetId=ref<number|null>(null)
const actionDialog=ref(false),action=ref<Operation|null>(null),saving=ref(false),operationError=ref(''),conflict=ref(false),bodyDialog=ref(false)
const students=ref<PageView<Student>|null>(null),studentKeyword=ref(''),studentPage=ref(1),studentLoading=ref(false),studentError=ref('')
const bookingOptions=ref<BookingOption[]>([]),bookingLoading=ref(false),bookingError=ref('')
const form=reactive({ memberId:null as number|null,title:'',goal:'GENERAL' as Goal,startDate:'',endDate:'',note:'',exercises:[] as FormExercise[],performedOn:'',durationMinutes:null as number|null,effort:null as number|null,bookingId:null as number|null,reason:'',rating:null as number|null,content:'' })
const blankForm=()=>({memberId:null,title:'',goal:'GENERAL' as Goal,startDate:'',endDate:'',note:'',exercises:[] as FormExercise[],performedOn:'',durationMinutes:null,effort:null,bookingId:null,reason:'',rating:null,content:''})
const goals:Record<Goal,string>={FAT_LOSS:'减脂',MUSCLE_GAIN:'增肌',POSTURE:'体态',ENDURANCE:'体能',GENERAL:'综合训练'}
const planStatuses:Record<string,string>={DRAFT:'草稿',ACTIVE:'已发布',ARCHIVED:'已归档'}
const eventTypes:Record<string,string>={CREATE:'创建计划',UPDATE:'修改草稿',DRAFT:'修改草稿',PUBLISH:'发布计划',ARCHIVE:'归档计划',RECORD:'登记训练',SESSION:'登记训练',VOID:'作废记录',FEEDBACK:'会员反馈'}
const actionLabels:Record<Operation,string>={create:'创建训练计划',edit:'修改草稿',publish:'发布计划',archive:'归档计划',record:'登记训练',void:'作废训练记录',feedback:'训练反馈'}
const role=computed(()=>auth.userInfo?.role),isHQ=computed(()=>!props.memberMode&&role.value==='SUPER_ADMIN')
const permitted=(code:string)=>hasPermission(auth.userInfo?.permissions,code)
const coachWrite=computed(()=>!props.memberMode&&role.value==='COACH'&&permitted('TRAINING_PLAN_WRITE'))
const scopeKey=()=>JSON.stringify([auth.sessionGeneration,auth.userInfo?.id,auth.userInfo?.role,auth.userInfo?.organizationId,auth.userInfo?.permissions,props.memberMode])
let alive=true,enabled=true,scopeEpoch=0,listEpoch=0,detailEpoch=0,actionEpoch=0,studentEpoch=0,bookingEpoch=0,formRevision=0
let originalScope=scopeKey(),actionPlan:PlanSummary|null=null,actionSession:SessionView|null=null,retry:{signature:string;body:any}|null=null
const candidateIds=new Set<number>()
const sessionCurrent=()=>alive&&enabled&&originalScope===scopeKey()&&!!auth.userInfo&&(props.memberMode?role.value==='MEMBER'&&permitted('MEMBER_SELF_SERVICE'):['COACH','MANAGER','SUPER_ADMIN'].includes(role.value||'')&&permitted('TRAINING_PLAN_READ'))
const scopeGuard=()=>{const epoch=scopeEpoch,key=scopeKey();return()=>sessionCurrent()&&epoch===scopeEpoch&&key===scopeKey()}
const canShowBody=computed(()=>sessionCurrent()&&!!detailData.value?.plan.canViewBody)
const canCreate=computed(()=>sessionCurrent()&&coachWrite.value)
function canAction(op:Operation,s?:SessionView){
 if(!sessionCurrent())return false
 if(op==='create')return coachWrite.value
 const p=detailData.value?.plan;if(!p)return false
 if(props.memberMode)return op==='feedback'&&!!s?.canFeedback&&s.status==='COMPLETED'&&p.status!=='DRAFT'
 if(op==='archive')return ['COACH','MANAGER','SUPER_ADMIN'].includes(role.value||'')&&permitted('TRAINING_PLAN_ARCHIVE')&&p.canArchive
 if(!coachWrite.value)return false
 if(op==='void')return !!s?.canVoid&&s.status==='COMPLETED'
 if(op==='edit')return p.canEdit&&p.status==='DRAFT'
 if(op==='publish')return p.canPublish&&p.status==='DRAFT'
 return op==='record'&&p.canRecord&&p.status==='ACTIVE'
}
function sanitize(value:Detail):Detail {if(!props.memberMode)return value;return {...value,plan:{...value.plan,canEdit:false,canPublish:false,canArchive:false,canRecord:false},events:[],eventTotal:0,sessions:{...value.sessions,records:value.sessions.records.map(s=>({...s,canVoid:false}))}}}
function closeAction(){actionEpoch++;studentEpoch++;bookingEpoch++;actionDialog.value=false;action.value=null;saving.value=false;operationError.value='';conflict.value=false;retry=null;actionPlan=null;actionSession=null;students.value=null;studentError.value='';studentLoading.value=false;bookingOptions.value=[];bookingError.value='';bookingLoading.value=false;candidateIds.clear();Object.assign(form,blankForm());studentKeyword.value='';studentPage.value=1}
function closeDetail(){detailEpoch++;targetId.value=null;detailDialog.value=false;detailData.value=null;detailError.value='';detailLoading.value=false;bodyDialog.value=false;closeAction()}
function clearPrivate(){scopeEpoch++;listEpoch++;data.value=null;venues.value=[];keyword.value='';loadError.value='';loading.value=false;closeDetail()}
watch(scopeKey,()=>{enabled=false;clearPrivate()},{flush:'sync'})
watch([venueId,status,keyword,page],()=>{listEpoch++;data.value=null;loadError.value='';loading.value=false;closeDetail()},{flush:'sync'})
watch(form,()=>{if(!saving.value){formRevision++;retry=null}},{deep:true,flush:'sync'})
watch(()=>form.performedOn,()=>{bookingEpoch++;bookingOptions.value=[];bookingError.value='';bookingLoading.value=false;form.bookingId=null},{flush:'sync'})
watch(studentKeyword,()=>{studentEpoch++;students.value=null;candidateIds.clear();studentPage.value=1;studentError.value='';studentLoading.value=false},{flush:'sync'})
onUnmounted(()=>{alive=false;clearPrivate()})
const message=(err:any,fallback:string)=>err?.response?.data?.message||err?.message||fallback
async function load(){
 if(!sessionCurrent())return
 const gen=++listEpoch,scope=scopeGuard(),filter=JSON.stringify([venueId.value,status.value,keyword.value,page.value]);const current=()=>scope()&&gen===listEpoch&&filter===JSON.stringify([venueId.value,status.value,keyword.value,page.value]);loading.value=true;loadError.value=''
 try{const result=props.memberMode?await api.getMemberTrainingPlans({status:status.value,page:page.value,size:20},{isRequestCurrent:current}):await api.getTrainingWorkbench({...(isHQ.value&&venueId.value?{venueId:venueId.value}:{}),status:status.value,keyword:keyword.value.trim(),page:page.value,size:20},{isRequestCurrent:current});if(!current())return;data.value=props.memberMode?{...result.data,venues:[],plans:{...result.data.plans,records:result.data.plans.records.filter(p=>p.status!=='DRAFT')}}:result.data;venues.value=isHQ.value?result.data.venues:[]}
 catch(err){if(current()){data.value=null;loadError.value=message(err,'训练计划加载失败，请重试')}}finally{if(current())loading.value=false}
}
function changeFilter(){page.value=1;void load()}
async function openDetail(p:PlanSummary){
 if(!sessionCurrent()||(props.memberMode&&p.status==='DRAFT')||saving.value)return
 closeDetail();targetId.value=p.id;detailDialog.value=true;historyPage.value=1;await loadHistory(1)
}
async function loadHistory(next:number){
 if(!sessionCurrent()||!detailDialog.value||targetId.value===null||saving.value)return
 historyPage.value=next;const id=targetId.value,gen=++detailEpoch,scope=scopeGuard();const current=()=>scope()&&gen===detailEpoch&&detailDialog.value&&targetId.value===id;detailLoading.value=true;detailError.value=''
 try{const result=await api.getTrainingDetail(id,{sessionPage:next,sessionSize:20},props.memberMode,{isRequestCurrent:current});if(!current())return;if(props.memberMode&&result.data.plan.status==='DRAFT'){closeDetail();return}detailData.value=sanitize(result.data)}catch(err){if(current()){detailData.value=null;detailError.value=message(err,'计划详情加载失败')}}finally{if(current())detailLoading.value=false}
}
const today=()=>new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date())
const newExercise=():FormExercise=>({name:'',mode:'STRENGTH',sets:null,reps:null,weightKg:null,minutes:null,note:''})
function addExercise(){if(!saving.value&&form.exercises.length<20)form.exercises.push(newExercise())}
function changeMode(e:FormExercise){e.sets=null;e.reps=null;e.weightKg=null;e.minutes=null}
async function openAction(op:Operation,s?:SessionView){
 if(saving.value||!canAction(op,s))return
 closeAction();action.value=op;actionDialog.value=true;actionPlan=detailData.value?{...detailData.value.plan}:null;actionSession=s?{...s}:null
 if(op==='create'){
  const act=actionEpoch,scope=scopeGuard(),hintId=props.initialMemberId
  form.startDate=today();form.endDate=today();form.exercises=[newExercise()];studentKeyword.value=props.initialMemberName
  const candidateGeneration=studentEpoch+1
  await loadStudents(1)
  if(!scope()||act!==actionEpoch||!actionDialog.value||action.value!=='create'||candidateGeneration!==studentEpoch)return
  if(form.memberId==null&&hintId&&candidateIds.has(hintId))form.memberId=hintId
 }
 else if(op==='edit'&&detailData.value){const p=detailData.value.plan;Object.assign(form,{title:p.title,goal:p.goal,startDate:p.startDate,endDate:p.endDate,note:p.note||'',exercises:detailData.value.exercises.map(e=>({...e,note:e.note||''}))})}
 else if(op==='record'&&detailData.value){form.performedOn=today();form.exercises=detailData.value.exercises.map(e=>({...e,exerciseId:e.id,status:'DONE',sets:null,reps:null,weightKg:null,minutes:null,note:''}));await loadBookingOptions()}
 else if(op==='feedback'&&s){form.rating=s.feedbackRating;form.content=s.feedbackContent||''}
}
async function loadStudents(next=1){
 if(!sessionCurrent()||!coachWrite.value||!actionDialog.value||action.value!=='create')return
 studentPage.value=next;const gen=++studentEpoch,act=actionEpoch,scope=scopeGuard(),kw=studentKeyword.value.trim();const current=()=>scope()&&gen===studentEpoch&&act===actionEpoch&&actionDialog.value&&action.value==='create'&&kw===studentKeyword.value.trim();studentLoading.value=true;studentError.value=''
 try{const result=await api.getTrainingStudents({keyword:kw,page:next,size:20},{isRequestCurrent:current});if(current()){students.value=result.data;result.data.records.forEach(s=>candidateIds.add(s.id))}}catch(err){if(current()){students.value=null;studentError.value=message(err,'学员搜索失败')}}finally{if(current())studentLoading.value=false}
}
async function loadBookingOptions(){
 if(!sessionCurrent()||!actionDialog.value||action.value!=='record'||!actionPlan||!form.performedOn)return
 const gen=++bookingEpoch,act=actionEpoch,scope=scopeGuard(),id=actionPlan.id,date=form.performedOn;const current=()=>scope()&&gen===bookingEpoch&&act===actionEpoch&&actionDialog.value&&action.value==='record'&&actionPlan?.id===id&&date===form.performedOn;bookingLoading.value=true;bookingError.value=''
 try{const result=await api.getTrainingBookings(id,date,{isRequestCurrent:current});if(current())bookingOptions.value=result.data}catch(err){if(current())bookingError.value=message(err,'当天预约加载失败')}finally{if(current())bookingLoading.value=false}
}
function text(value:string,max:number,required=false){const v=value.trim();if((required&&!v)||v.length>max)throw Error(required?`请填写完整内容（最多${max}字）`:`内容不得超过${max}字`);return v}
function integer(value:any,min:number,max:number,optional=false):number|null{if(value===null||value===undefined||value===''){if(optional)return null;throw Error('请填写必填数值')}if(typeof value!=='number'||!Number.isSafeInteger(value)||value<min||value>max)throw Error(`请输入${min}至${max}之间的整数`);return value}
function weight(value:any):number|null{if(value===null||value===undefined||value==='')return null;if(typeof value!=='number'||!Number.isFinite(value)||value<0||value>500||Math.abs(value*100-Math.round(value*100))>1e-7)throw Error('重量须为0至500千克，最多2位小数');return value}
function dateNumber(value:string){if(!/^\d{4}-\d{2}-\d{2}$/.test(value))throw Error('请选择有效日期');const n=Date.parse(value+'T00:00:00Z');if(!Number.isFinite(n)||new Date(n).toISOString().slice(0,10)!==value)throw Error('请选择有效日期');return n/86400000}
function exerciseBody(e:FormExercise,record=false){
 const skipped=record&&e.status==='SKIPPED';if(record&&!['DONE','SKIPPED'].includes(e.status||''))throw Error('请选择完成或跳过')
 if(!['STRENGTH','DURATION'].includes(e.mode))throw Error('请选择动作模式')
 const strength=e.mode==='STRENGTH'&&!skipped,duration=e.mode==='DURATION'&&!skipped
 return {...(record?{exerciseId:integer(e.exerciseId,1,Number.MAX_SAFE_INTEGER)!,status:e.status!}:{name:text(e.name,100,true),mode:e.mode}),sets:strength?integer(e.sets,1,20):null,reps:strength?integer(e.reps,1,200):null,weightKg:strength?weight(e.weightKg):null,minutes:duration?integer(e.minutes,1,240):null,note:text(e.note,skipped?300:record?300:300,skipped)}
}
function buildBody(){
 const op=action.value;if(!op)throw Error('请选择操作')
 if(op==='create'||op==='edit'){
  const start=dateNumber(form.startDate),end=dateNumber(form.endDate),day=dateNumber(today());if(end<start||end-start>365||start<day-365||end>day+365)throw Error('周期最多366天，日期须在今天前后365天范围内')
  if(!Object.keys(goals).includes(form.goal))throw Error('请选择训练目标');if(form.exercises.length<1||form.exercises.length>20)throw Error('请设置1至20项动作')
  if(op==='create'&&(!form.memberId||!candidateIds.has(form.memberId)))throw Error('请从当前授权学员搜索结果中选择学员')
  return {...(op==='create'?{memberId:form.memberId}:{expectedVersion:actionPlan!.version}),title:text(form.title,100,true),goal:form.goal,startDate:form.startDate,endDate:form.endDate,note:text(form.note,1000),exercises:form.exercises.map(e=>exerciseBody(e))}
 }
 if(op==='feedback')return {expectedVersion:actionSession!.version,rating:integer(form.rating,1,5)!,content:text(form.content,500)}
 if(op==='void')return {expectedVersion:actionSession!.version,reason:text(form.reason,300,true)}
 if(op==='archive')return {expectedVersion:actionPlan!.version,reason:text(form.reason,300,true)}
 if(op==='publish')return {expectedVersion:actionPlan!.version}
 const date=dateNumber(form.performedOn),day=dateNumber(today());if(date>day||date<day-365||date<dateNumber(actionPlan!.startDate)||date>dateNumber(actionPlan!.endDate))throw Error('训练日期须在计划周期内，且为今天或过去365天内')
 const ids=detailData.value!.exercises.map(e=>e.id),actual=form.exercises.map(e=>e.exerciseId);if(actual.length!==ids.length||new Set(actual).size!==ids.length||ids.some(id=>!actual.includes(id)))throw Error('训练记录必须包含每个计划动作且恰好一次')
 if(!form.exercises.some(e=>e.status==='DONE'))throw Error('至少完成一个动作')
 const bookingId=form.bookingId==null?null:integer(form.bookingId,1,Number.MAX_SAFE_INTEGER)
 if(bookingId!==null&&!bookingOptions.value.some(b=>b.id===bookingId))throw Error('请重新选择当天有效预约')
 return {expectedVersion:actionPlan!.version,performedOn:form.performedOn,durationMinutes:integer(form.durationMinutes,1,240)!,effort:integer(form.effort,1,10,true),note:text(form.note,1000),bookingId,exercises:form.exercises.map(e=>exerciseBody(e,true))}
}
function freeze<T>(value:T):T{if(value&&typeof value==='object'){Object.values(value).forEach(freeze);Object.freeze(value)}return value}
function newKey(){return 'train_'+Date.now().toString(36)+'_'+Math.random().toString(36).slice(2,12)}
async function submitOperation(){
 if(saving.value||conflict.value||!actionDialog.value||!action.value||!canAction(action.value,actionSession||undefined))return
 const op=action.value,act=actionEpoch,scope=scopeGuard(),target=actionPlan?.id,sessionId=actionSession?.id;const current=()=>scope()&&act===actionEpoch&&actionDialog.value&&action.value===op&&actionPlan?.id===target&&actionSession?.id===sessionId
 let body:any;try{const signature=JSON.stringify([op,target,sessionId,formRevision]);if(!retry||retry.signature!==signature){const payload=buildBody();retry={signature,body:freeze({...payload,idempotencyKey:newKey()})}}body=retry.body}catch(err){if(current())operationError.value=message(err,'请检查表单');return}
 saving.value=true;operationError.value=''
 try{
  const config={isRequestCurrent:current};const result=op==='create'?await api.createTrainingPlan(body,config):op==='edit'?await api.updateTrainingDraft(target!,body,config):op==='publish'?await api.publishTrainingPlan(target!,body,config):op==='archive'?await api.archiveTrainingPlan(target!,body,config):op==='record'?await api.recordTrainingSession(target!,body,config):op==='void'?await api.voidTrainingSession(target!,sessionId!,body,config):await api.feedbackTrainingSession(target!,sessionId!,body,config)
  if(!current())return;const fresh=sanitize(result.data);closeAction();detailData.value=fresh;targetId.value=fresh.plan.id;detailDialog.value=true;historyPage.value=fresh.sessions.page;ElMessage.success('操作成功');await loadAfterWrite()
 }catch(err:any){if(current()){operationError.value=message(err,'操作失败，请重试');conflict.value=(err?.code??err?.response?.data?.code)===2600}}finally{if(current())saving.value=false}
}
async function loadAfterWrite(){await load()}
async function reloadConflict(){
 if(!sessionCurrent()||!conflict.value||saving.value||!action.value)return
 if(action.value==='create'){
  const act=actionEpoch,scope=scopeGuard();const current=()=>scope()&&act===actionEpoch&&actionDialog.value&&action.value==='create'
  try{await api.getTrainingWorkbench({status:status.value,keyword:keyword.value.trim(),page:page.value,size:20},{isRequestCurrent:current});if(current()){closeAction();await openAction('create')}}catch(err){if(current())operationError.value=message(err,'最新范围加载失败，请重试')}
  return
 }
 if(!actionPlan)return
 const op=action.value,id=actionPlan.id,sid=actionSession?.id,act=actionEpoch,scope=scopeGuard(),gen=++detailEpoch
 const ownsLoading=()=>scope()&&gen===detailEpoch&&detailDialog.value&&targetId.value===id
 const current=()=>ownsLoading()&&act===actionEpoch&&actionDialog.value&&actionPlan?.id===id;detailLoading.value=true
 try{const result=await api.getTrainingDetail(id,{sessionPage:historyPage.value,sessionSize:20},props.memberMode,{isRequestCurrent:current});if(!current())return;detailData.value=sanitize(result.data);const session=detailData.value.sessions.records.find(s=>s.id===sid);if(sid&&!session){operationError.value='记录不在当前页，请关闭操作并重新打开对应历史页';return}closeAction();await openAction(op,session)}catch(err){if(current())operationError.value=message(err,'最新详情加载失败，请重试')}finally{if(ownsLoading())detailLoading.value=false}
}
function valueText(v:number|null|undefined){return v==null?'—':String(v)}
function exerciseText(e:{mode:string;sets:number|null;reps:number|null;weightKg:number|null;minutes:number|null}){return e.mode==='DURATION'?`${valueText(e.minutes)} 分钟`:`${valueText(e.sets)} 组 × ${valueText(e.reps)} 次 · ${e.weightKg==null?'重量未设定':e.weightKg+' kg'}`}
function targetText(e:api.SessionExercise){return exerciseText({mode:e.mode,sets:e.targetSets,reps:e.targetReps,weightKg:e.targetWeightKg,minutes:e.targetMinutes})}
function dateText(v:string|null){return v?v.replace('T',' ').slice(0,19):'—'}
onMounted(load)
</script>

<template>
 <div class="training-center">
  <WorkspaceHero>
   <template #heading><span>TRAINING PLANS</span><h1>{{ memberMode?'我的训练':'训练计划' }}</h1><p>计划目标、训练执行与历史反馈。训练登记不会签到或扣课。</p></template>
   <template #actions><div class="hero-controls"><el-select v-if="isHQ" v-model="venueId" clearable placeholder="全部服务门店" @change="changeFilter"><el-option v-for="v in venues" :key="v.id" :label="v.name" :value="v.id" /></el-select><el-button :disabled="saving" @click="load">刷新</el-button><el-button v-if="canCreate" type="primary" :disabled="saving" @click="openAction('create')">创建计划</el-button></div></template>
  </WorkspaceHero>
  <div v-if="data" class="metrics"><article v-if="!memberMode"><small>草稿计划</small><strong>{{ data.summary.draftPlans }}</strong></article><article><small>已发布计划</small><strong>{{ data.summary.activePlans }}</strong></article><article><small>已归档计划</small><strong>{{ data.summary.archivedPlans }}</strong></article><article><small>本月有效训练</small><strong>{{ data.summary.sessionsThisMonth }}</strong></article></div>
  <section class="panel" v-loading="loading">
   <div class="filters"><el-input v-if="!memberMode" v-model="keyword" maxlength="100" clearable placeholder="会员姓名、编号或计划标题" @keyup.enter="changeFilter" @clear="changeFilter"/><el-select v-model="status" @change="changeFilter"><el-option label="全部状态" value="ALL"/><el-option v-if="!memberMode" label="草稿" value="DRAFT"/><el-option label="已发布" value="ACTIVE"/><el-option label="已归档" value="ARCHIVED"/></el-select><el-button :disabled="saving" type="primary" plain @click="changeFilter">查询</el-button></div>
   <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false"><el-button link @click="load">重新加载</el-button></el-alert>
   <template v-else-if="data"><el-table :data="data.plans.records" stripe><el-table-column prop="title" label="计划" min-width="180"/><el-table-column v-if="!memberMode" label="会员" min-width="130"><template #default="{row}"><strong>{{ row.memberName }}</strong><small>{{ row.memberNo }}</small></template></el-table-column><el-table-column label="目标" width="100"><template #default="{row}">{{ goals[row.goal as Goal] }}</template></el-table-column><el-table-column label="周期" min-width="185"><template #default="{row}">{{ row.startDate }} 至 {{ row.endDate }}</template></el-table-column><el-table-column label="服务门店 / 教练" min-width="160"><template #default="{row}">{{ row.venueName }}<small>{{ row.coachName }}</small></template></el-table-column><el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.status==='ACTIVE'?'success':row.status==='ARCHIVED'?'info':'warning'">{{ planStatuses[row.status] }}</el-tag></template></el-table-column><el-table-column label="有效训练" width="100"><template #default="{row}">{{ row.sessionCount }} 次<small>{{ row.lastSessionOn || '暂无训练' }}</small></template></el-table-column><el-table-column label="操作" width="90" fixed="right"><template #default="{row}"><el-button link type="primary" :disabled="saving" @click="openDetail(row)">详情</el-button></template></el-table-column></el-table><el-empty v-if="!data.plans.records.length" description="当前筛选下暂无训练计划" :image-size="70"/><el-pagination v-model:current-page="page" :page-size="20" :total="data.plans.total" layout="total, prev, pager, next" :disabled="saving" @current-change="load"/></template>
  </section>
  <el-drawer v-model="detailDialog" title="训练计划详情" size="860px" :before-close="(done:()=>void)=>{closeDetail();done()}" destroy-on-close>
   <div v-loading="detailLoading"><el-alert v-if="detailError" :title="detailError" type="error" :closable="false"><el-button link @click="loadHistory(historyPage)">重试详情</el-button></el-alert>
   <template v-if="detailData"><div class="detail-heading"><div><h2>{{ detailData.plan.title }}</h2><p>{{ detailData.plan.memberName }} · {{ detailData.plan.memberNo }} · {{ detailData.plan.venueName }} · {{ detailData.plan.coachName }}</p></div><el-tag>{{ planStatuses[detailData.plan.status] }}</el-tag></div><p>{{ goals[detailData.plan.goal] }} · {{ detailData.plan.startDate }} 至 {{ detailData.plan.endDate }} · 版本 {{ detailData.plan.version }}</p><p class="pre-wrap">{{ detailData.plan.note || '暂无计划备注' }}</p>
    <div class="action-row"><el-button v-if="canAction('edit')" :disabled="saving" @click="openAction('edit')">修改草稿</el-button><el-button v-if="canAction('publish')" type="primary" :disabled="saving" @click="openAction('publish')">发布计划</el-button><el-button v-if="canAction('record')" type="primary" :disabled="saving" @click="openAction('record')">登记训练</el-button><el-button v-if="canAction('archive')" type="warning" plain :disabled="saving" @click="openAction('archive')">归档计划</el-button><el-button v-if="canShowBody" :disabled="saving" @click="bodyDialog=true">体测档案</el-button></div>
    <h3>计划动作与目标</h3><el-table :data="detailData.exercises"><el-table-column prop="position" label="序号" width="60"/><el-table-column prop="name" label="动作" min-width="130"/><el-table-column label="目标" min-width="230"><template #default="{row}">{{ exerciseText(row) }}</template></el-table-column><el-table-column prop="note" label="说明" min-width="150"/></el-table>
    <h3>训练历史 <small>共 {{ detailData.sessions.total }} 条（含作废记录）</small></h3><el-alert title="仅训练记录，不会签到或扣课。有效训练统计不含作废记录。" type="info" :closable="false"/>
    <article v-for="s in detailData.sessions.records" :key="s.id" class="session"><div class="session-head"><strong>{{ s.performedOn }} · {{ s.durationMinutes }} 分钟</strong><el-tag :type="s.status==='VOIDED'?'danger':'success'">{{ s.status==='VOIDED'?'已作废':'有效训练' }}</el-tag></div><p>{{ s.bookingId==null?'独立训练记录':`${s.courseName || '关联预约'} · 预约号 ${s.bookingId}` }} · {{ s.coachName }} · 主观强度 {{ valueText(s.effort) }}/10</p><p v-if="s.note" class="pre-wrap">{{ s.note }}</p><el-alert v-if="s.status==='VOIDED'" :title="'作废原因：'+(s.voidReason||'—')" type="warning" :closable="false"/>
     <el-table :data="s.exercises" size="small"><el-table-column prop="name" label="动作" min-width="130"/><el-table-column label="目标" min-width="220"><template #default="{row}">{{ targetText(row) }}</template></el-table-column><el-table-column label="实际" min-width="230"><template #default="{row}">{{ row.status==='SKIPPED'?'跳过：'+row.note:exerciseText(row) }}<small v-if="row.status!=='SKIPPED'&&row.note">{{ row.note }}</small></template></el-table-column></el-table>
     <p>会员反馈：{{ s.feedbackRating==null?'尚未反馈':`${s.feedbackRating}/5 分` }}<span v-if="s.feedbackContent"> · {{ s.feedbackContent }}</span><small v-if="s.feedbackAt">{{ dateText(s.feedbackAt) }}</small></p><div class="action-row"><el-button v-if="canAction('feedback',s)" type="primary" plain :disabled="saving" @click="openAction('feedback',s)">{{ s.feedbackRating==null?'提交反馈':'修改反馈' }}</el-button><el-button v-if="canAction('void',s)" type="danger" plain :disabled="saving" @click="openAction('void',s)">作废记录</el-button></div>
    </article><el-empty v-if="!detailData.sessions.records.length" description="本页暂无训练记录" :image-size="60"/><el-pagination :current-page="historyPage" :page-size="20" :total="detailData.sessions.total" layout="total, prev, pager, next" :disabled="saving" @current-change="loadHistory"/>
    <template v-if="!memberMode"><h3>员工审计 <small>共 {{ detailData.eventTotal }} 条，展示最近 {{ detailData.events.length }} 条（最多100条）</small></h3><div v-for="event in detailData.events" :key="event.id" class="audit"><strong>{{ eventTypes[event.type] || event.type }}</strong> · {{ event.actorName }} · {{ dateText(event.createdAt) }}<p v-if="event.reason">{{ event.reason }}</p><small v-if="event.sessionId">训练记录 #{{ event.sessionId }}</small></div></template>
   </template></div>
  </el-drawer>
  <el-dialog v-model="actionDialog" :title="action? actionLabels[action]:''" width="800px" :close-on-click-modal="false" :before-close="(done:()=>void)=>{closeAction();done()}" destroy-on-close>
   <el-alert v-if="operationError" :title="operationError" type="error" show-icon :closable="false"/><el-alert v-if="conflict" title="数据已变化。请显式重新加载最新详情，再确认新的操作；当前表单不会自动覆盖。" type="warning" :closable="false"><el-button @click="reloadConflict">重新加载最新详情</el-button></el-alert>
   <el-form v-if="action" label-width="110px" :disabled="saving||conflict">
    <template v-if="action==='create'||action==='edit'">
     <section v-if="action==='create'" class="student-picker"><div class="filters"><el-input v-model="studentKeyword" maxlength="100" clearable placeholder="搜索授权学员姓名或编号" @keyup.enter="loadStudents(1)"/><el-button @click="loadStudents(1)">搜索学员</el-button></div><el-alert v-if="studentError" :title="studentError" type="error" :closable="false"/><el-table v-else :data="students?.records||[]" v-loading="studentLoading"><el-table-column label="选择" width="80"><template #default="{row}"><el-radio v-model="form.memberId" :value="row.id" :disabled="saving">{{ '' }}</el-radio></template></el-table-column><el-table-column prop="realName" label="学员"/><el-table-column prop="memberNo" label="会员编号"/></el-table><el-pagination :current-page="studentPage" :page-size="20" :total="students?.total||0" layout="total, prev, pager, next" :disabled="saving" @current-change="loadStudents"/><p v-if="form.memberId" class="hint">已选择学员编号 #{{ form.memberId }}；服务权限仍由服务器校验。</p></section>
     <el-form-item label="计划标题" required><el-input v-model="form.title" maxlength="100" show-word-limit/></el-form-item><el-form-item label="训练目标" required><el-select v-model="form.goal"><el-option v-for="(label,key) in goals" :key="key" :value="key" :label="label"/></el-select></el-form-item><el-form-item label="计划周期" required><div class="date-row"><el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" placeholder="开始日期"/><span>至</span><el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" placeholder="结束日期"/></div></el-form-item><el-form-item label="计划备注"><el-input v-model="form.note" type="textarea" maxlength="1000" show-word-limit/></el-form-item>
     <h3>动作目标（1至20项）</h3><article v-for="(e,i) in form.exercises" :key="i" class="exercise-form"><div class="exercise-head"><strong>动作 {{ i+1 }}</strong><el-button type="danger" link :disabled="saving||form.exercises.length<=1" @click="form.exercises.splice(i,1)">删除</el-button></div><el-form-item label="动作名称" required><el-input v-model="e.name" maxlength="100"/></el-form-item><el-form-item label="模式"><el-radio-group v-model="e.mode" @change="changeMode(e)"><el-radio-button value="STRENGTH">力量</el-radio-button><el-radio-button value="DURATION">时长</el-radio-button></el-radio-group></el-form-item><div class="number-row"><template v-if="e.mode==='STRENGTH'"><el-form-item label="组数" required><el-input-number v-model="e.sets" :min="1" :max="20" :step="1"/></el-form-item><el-form-item label="次数" required><el-input-number v-model="e.reps" :min="1" :max="200" :step="1"/></el-form-item><el-form-item label="重量 kg"><el-input-number v-model="e.weightKg" :min="0" :max="500" :precision="2"/></el-form-item></template><el-form-item v-else label="分钟" required><el-input-number v-model="e.minutes" :min="1" :max="240"/></el-form-item></div><el-form-item label="动作说明"><el-input v-model="e.note" maxlength="300"/></el-form-item></article><el-button :disabled="saving||form.exercises.length>=20" @click="addExercise">添加动作</el-button>
    </template>
    <template v-else-if="action==='record'"><el-alert title="仅训练记录，不会签到或扣课；必须包含全部动作，至少完成一个动作。" type="info" :closable="false"/><el-form-item label="训练日期" required><el-date-picker v-model="form.performedOn" type="date" value-format="YYYY-MM-DD" @change="loadBookingOptions"/></el-form-item><el-form-item label="时长（分钟）" required><el-input-number v-model="form.durationMinutes" :min="1" :max="240"/></el-form-item><el-form-item label="主观强度"><el-input-number v-model="form.effort" :min="1" :max="10"/><span class="hint">可空，1至10</span></el-form-item><el-form-item label="关联预约"><el-select v-model="form.bookingId" clearable placeholder="独立训练记录" :loading="bookingLoading"><el-option v-for="b in bookingOptions" :key="b.id" :value="b.id" :label="b.courseName+' · '+dateText(b.startTime)"/></el-select></el-form-item><el-alert v-if="bookingError" :title="bookingError" type="warning" :closable="false"><el-button @click="loadBookingOptions">重试预约</el-button></el-alert><p class="hint">仅可关联当天已签到的本人授课预约；不关联时记为独立训练记录。</p><el-form-item label="公开备注"><el-input v-model="form.note" type="textarea" maxlength="1000" show-word-limit/></el-form-item>
     <article v-for="(e,i) in form.exercises" :key="e.exerciseId" class="exercise-form"><strong>{{ i+1 }}. {{ e.name }}</strong><p class="hint">目标：{{ detailData?.exercises[i]?exerciseText(detailData.exercises[i]!):'—' }}</p><el-form-item label="执行状态"><el-radio-group v-model="e.status"><el-radio-button value="DONE">完成</el-radio-button><el-radio-button value="SKIPPED">跳过</el-radio-button></el-radio-group></el-form-item><div v-if="e.status==='DONE'" class="number-row"><template v-if="e.mode==='STRENGTH'"><el-form-item label="实际组数" required><el-input-number v-model="e.sets" :min="1" :max="20"/></el-form-item><el-form-item label="实际次数" required><el-input-number v-model="e.reps" :min="1" :max="200"/></el-form-item><el-form-item label="实际重量 kg"><el-input-number v-model="e.weightKg" :min="0" :max="500" :precision="2"/></el-form-item></template><el-form-item v-else label="实际分钟" required><el-input-number v-model="e.minutes" :min="1" :max="240"/></el-form-item></div><el-form-item :label="e.status==='SKIPPED'?'跳过原因':'动作备注'" :required="e.status==='SKIPPED'"><el-input v-model="e.note" maxlength="300"/></el-form-item></article>
    </template>
    <template v-else-if="action==='feedback'"><el-form-item label="训练评分" required><el-rate v-model="form.rating"/></el-form-item><el-form-item label="反馈内容"><el-input v-model="form.content" type="textarea" maxlength="500" show-word-limit placeholder="可选，写下训练感受"/></el-form-item></template>
    <template v-else-if="action==='archive'||action==='void'"><el-alert :title="action==='archive'?'归档后不可重新激活；历史记录仍保留。':'作废保留原内容和反馈；不计有效训练。重新登记须计划仍已发布。'" type="warning" :closable="false"/><el-form-item label="原因" required><el-input v-model="form.reason" type="textarea" maxlength="300" show-word-limit/></el-form-item></template>
    <el-alert v-else-if="action==='publish'" title="发布后计划内容固定。同一会员在服务门店同时只能有一个已发布计划，请先显式归档旧计划。" type="info" :closable="false"/>
   </el-form><template #footer><el-button @click="closeAction">关闭</el-button><el-button type="primary" :loading="saving" :disabled="conflict||!action" @click="submitOperation">{{ action==='create'||action==='edit'?'保存草稿':action? actionLabels[action]:'确认' }}</el-button></template>
  </el-dialog>
  <el-dialog v-model="bodyDialog" title="体测档案" width="850px" destroy-on-close><BodyMeasurePanel v-if="bodyDialog&&canShowBody&&detailData" :key="detailData.plan.memberId" :member-id="memberMode?undefined:detailData.plan.memberId" :member-name="detailData.plan.memberName"/></el-dialog>
 </div>
</template>
<style scoped>
.training-center{color:#244463}.hero-controls,.filters,.action-row,.date-row,.session-head,.exercise-head{display:flex;align-items:center;gap:12px}.hero-controls{flex-wrap:wrap;min-width:260px}.hero-controls :deep(.el-select){width:190px;flex:none}.filters{margin-bottom:20px}.filters :deep(.el-input){width:300px}.filters :deep(.el-select){width:160px}.metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px;margin:20px 0}.metrics article,.panel{border:1px solid #d4e4f6;border-radius:16px;background:#fff;box-shadow:0 5px 20px #36597b09}.metrics article{padding:20px}.metrics strong{display:block;font-size:28px;margin-top:10px;color:#20486e}.metrics small,.hint,small{color:#647f99;font-size:12px;line-height:1.8}.panel{margin:20px 0;padding:22px}small{display:block}.el-pagination{justify-content:flex-end;margin-top:20px}.detail-heading,.session-head,.exercise-head{display:flex;justify-content:space-between;gap:14px}.detail-heading h2{margin:0;font-size:21px}.detail-heading p{font-size:13px;color:#647f99}.action-row{flex-wrap:wrap;margin:18px 0}.session,.exercise-form{padding:18px;border:1px solid #dce8f4;background:#f8fbff;border-radius:12px;margin:16px 0}.session p,.audit p{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.7}.session-head{margin-bottom:12px}.audit{padding:15px 0;border-bottom:1px solid #e1eaf5}.pre-wrap{white-space:pre-wrap;overflow-wrap:anywhere}.student-picker{margin-bottom:24px;border-bottom:1px solid #e1eaf5;padding-bottom:16px}.number-row{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}.number-row :deep(.el-input-number){width:150px}.date-row :deep(.el-date-editor){width:215px}.exercise-head{margin-bottom:16px}.training-center :deep(.el-alert){margin:12px 0}.training-center :deep(.el-dialog){max-width:calc(100vw - 70px)}.training-center :deep(.el-button--primary.is-plain){color:#245b95;background:#edf5ff;border-color:#98bae4}.training-center :deep(.el-table){font-variant-numeric:tabular-nums}.training-center h3{margin-top:26px}.training-center h3 small{font-weight:400;display:inline;margin-left:8px}
</style>
