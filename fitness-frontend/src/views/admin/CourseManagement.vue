<template>
  <div class="page-shell">
    <WorkspaceHero compact>
      <template #heading><span class="eyebrow">TRAINING PROGRAM</span><h1>课程与排课</h1><p>{{ canWriteCourse ? '维护课程模板，安排教练与上课时间' : '查看本场馆课程模板与近期排课' }}</p></template>
      <template #actions><el-button v-if="canWriteCourse" type="primary" size="large" @click="openCourse"><el-icon><Plus /></el-icon> 新增课程</el-button></template>
    </WorkspaceHero>
    <div class="section-head"><h3>课程库</h3><span>{{ courses.length }} 个课程模板</span></div>
    <div class="course-grid">
      <article v-for="course in courses" :key="course.id" class="course-card" data-agent-type="course" :data-agent-id="course.id" :data-agent-venue="course.venueId" :data-agent-label="course.name">
        <div class="course-accent" :class="course.type.toLowerCase()" />
        <div class="course-top"><span class="tag-group"><el-tag effect="plain">{{ typeText(course.type) }}</el-tag><el-tag v-if="isSuperAdmin" effect="plain" type="info">{{ venueName(course.venueId) }}</el-tag></span><span>¥{{ course.fee }}</span></div>
        <h3>{{ course.name }}</h3><p>{{ course.description || '暂无课程简介' }}</p>
        <div class="course-meta"><span><el-icon><Timer /></el-icon>{{ course.durationMinutes }} 分钟</span><span><el-icon><User /></el-icon>{{ course.maxCapacity }} 人</span><span>{{ intensityText(course.intensity) }}</span></div>
        <el-button v-if="canWriteCourse" data-agent-action="edit" link type="primary" @click="openEdit(course)">编辑课程</el-button>
      </article>
    </div>
    <div class="section-head schedule-head"><div><h3>近期排课</h3><span>未来 30 天课程安排</span></div><div class="head-actions"><el-button v-if="canWriteSchedule" @click="openBatch"><el-icon><Timer /></el-icon> 批量排课</el-button><el-button v-if="canWriteSchedule" @click="openSchedule"><el-icon><Calendar /></el-icon> 新增排课</el-button></div></div>
    <el-card shadow="never" class="table-card"><el-table v-loading="loading" :data="schedules">
      <el-table-column label="时间" min-width="190"><template #default="{row}"><strong>{{ formatDate(row.scheduledStartTime) }}</strong><small>{{ formatTime(row.scheduledStartTime) }} - {{ formatEndTime(row) }}</small></template></el-table-column>
      <el-table-column v-if="isSuperAdmin" label="门店" min-width="120"><template #default="{row}">{{ venueName(row.venueId) }}</template></el-table-column>
      <el-table-column label="课程" min-width="160"><template #default="{row}">{{ row.course?.name }}</template></el-table-column>
      <el-table-column label="教练" min-width="160"><template #default="{row}"><template v-if="row.coachId">{{ row.coachName }}</template><template v-else-if="row.course?.type==='PRIVATE'"><el-tag type="danger" size="small">未绑定教练账号</el-tag><small class="warn-line">私教课必须绑定教练，否则会员无法预约</small></template><span v-else class="muted">{{ row.coachName || '—' }}</span></template></el-table-column>
      <el-table-column label="教室" min-width="120"><template #default="{row}"><template v-if="row.room">{{ row.room.name }}</template><span v-else class="muted">未指定</span></template></el-table-column>
      <el-table-column label="预约进度" min-width="190"><template #default="{row}"><div class="capacity"><el-progress :percentage="Math.round(row.enrolledCount / row.maxStudents * 100)" :stroke-width="7" :show-text="false"/><span>{{ row.enrolledCount }}/{{ row.maxStudents }}</span></div></template></el-table-column>
      <el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="row.status==='SCHEDULED'?'success':'info'">{{ row.status==='SCHEDULED'?'可预约':'已结束' }}</el-tag></template></el-table-column>
      <el-table-column v-if="canWriteSchedule" label="操作" width="140" fixed="right"><template #default="{row}"><el-button link type="primary" @click="openEditSchedule(row)" data-agent-type="schedule" :data-agent-id="row.id" :data-agent-label="row.name || row.realName || row.title || row.memberName || ''">编辑</el-button><el-button link type="danger" @click="removeSchedule(row)" data-agent-type="schedule" :data-agent-id="row.id" :data-agent-label="row.name || row.realName || row.title || row.memberName || ''">删除</el-button></template></el-table-column>
    </el-table></el-card>

    <el-dialog v-model="courseDialog" :title="editingId ? '编辑课程' : '新增课程'" width="620px"><el-form ref="courseFormRef" :model="courseForm" :rules="courseRules" label-width="90px" data-agent-type="course" :data-agent-id="editingId || 'new'" :data-agent-venue="courseForm.venueId" :data-agent-label="courseForm.name">
      <el-form-item label="课程名称" prop="name" data-agent-field="name"><el-input v-model="courseForm.name" /></el-form-item>
      <el-form-item label="课程类型" prop="type" data-agent-field="type"><el-select v-model="courseForm.type" style="width:100%"><el-option label="团体课" value="GROUP"/><el-option label="常规课" value="REGULAR"/><el-option label="私教课" value="PRIVATE"/></el-select></el-form-item>
      <el-form-item v-if="isSuperAdmin" label="所属场馆" prop="venueId" data-agent-field="venueId"><el-select v-model="courseForm.venueId" style="width:100%"><el-option v-for="v in venues" :key="v.id" :label="v.name" :value="v.id" /></el-select></el-form-item>
      <el-form-item label="课程简介"><el-input v-model="courseForm.description" type="textarea" :rows="3" /></el-form-item>
      <el-row :gutter="12"><el-col :span="8"><el-form-item label="时长" prop="durationMinutes" label-width="52px" data-agent-field="durationMinutes"><el-input-number v-model="courseForm.durationMinutes" :min="15" :max="300" controls-position="right" style="width:100%" /></el-form-item></el-col><el-col :span="8"><el-form-item label="容量" prop="maxCapacity" label-width="52px" data-agent-field="maxCapacity"><el-input-number v-model="courseForm.maxCapacity" :min="1" :max="500" controls-position="right" style="width:100%" /></el-form-item></el-col><el-col :span="8"><el-form-item label="价格" prop="fee" label-width="52px" data-agent-field="fee"><el-input-number v-model="courseForm.fee" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col></el-row>
      <el-form-item label="强度"><el-radio-group v-model="courseForm.intensity"><el-radio-button label="LOW">低</el-radio-button><el-radio-button label="MEDIUM">中</el-radio-button><el-radio-button label="HIGH">高</el-radio-button></el-radio-group></el-form-item>
      <div class="form-hint">时长 15 ~ 300 分钟，容量 1 ~ 500 人，价格不能为负。</div>
    </el-form><template #footer><div class="dialog-footer"><el-button v-if="editingId && canWriteCourse" type="danger" plain :loading="saving" @click="removeCourse">删除课程</el-button><span class="footer-right"><el-button @click="courseDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveCourse">保存</el-button></span></div></template></el-dialog>

    <section v-if="canReadReview" class="room-block">
      <div class="section-head"><div><h3>教室与场地</h3><span>同一时段同一教室不能排两节课；占比按每日 08:00–22:00 计算</span></div><el-button v-if="canWriteVenue" type="primary" @click="openRoom()"><el-icon><Plus/></el-icon> 新增教室</el-button></div>
      <el-card shadow="never" class="table-card">
        <el-table :data="rooms" stripe>
          <el-table-column label="教室" min-width="140"><template #default="{row}"><strong>{{ row.name }}</strong><small>{{ row.note || '—' }}</small></template></el-table-column>
          <el-table-column v-if="isSuperAdmin" label="门店" width="130"><template #default="{row}">{{ venueName(row.venueId) }}</template></el-table-column>
          <el-table-column label="可容纳" width="90"><template #default="{row}">{{ row.capacity ? row.capacity + ' 人' : '未维护' }}</template></el-table-column>
          <el-table-column label="今日占用" min-width="200"><template #default="{row}"><template v-if="roomOccupancy(row.id)"><el-progress :percentage="roomOccupancy(row.id)!.occupancyRate" :stroke-width="7" :show-text="false"/><small>{{ roomOccupancy(row.id)!.sessions.length }} 节课 · 占用 {{ roomOccupancy(row.id)!.occupancyRate }}%</small></template><small v-else class="muted">今日无排课</small></template></el-table-column>
          <el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.status==='ACTIVE'?'success':'info'">{{ row.status==='ACTIVE'?'启用':'停用' }}</el-tag></template></el-table-column>
          <el-table-column v-if="canWriteVenue" label="操作" width="180" fixed="right"><template #default="{row}"><el-button link @click="openRoom(row)" data-agent-type="room" :data-agent-id="row.id" :data-agent-label="row.name || row.realName || row.title || row.memberName || ''">编辑</el-button><el-button link :type="row.status==='ACTIVE'?'danger':'primary'" @click="toggleRoom(row)" data-agent-type="room" :data-agent-id="row.id" :data-agent-label="row.name || row.realName || row.title || row.memberName || ''">{{ row.status==='ACTIVE'?'停用':'启用' }}</el-button><el-button link type="danger" @click="removeRoom(row)" data-agent-type="room" :data-agent-id="row.id" :data-agent-label="row.name || row.realName || row.title || row.memberName || ''">删除</el-button></template></el-table-column>
        </el-table>
        <el-empty v-if="!rooms.length" description="还没有教室，点右上角新增；不指定教室的排课不受影响" :image-size="60" />
      </el-card>
    </section>
    <section v-if="canReadReview" class="review-block">
      <div class="section-head"><div><h3>课程评价汇总</h3><span>各教练平均分与低分记录（≤3 星）</span></div><el-tag effect="plain">{{ reviewSummary?.reviewCount || 0 }} 条评价</el-tag></div>
      <el-card shadow="never" class="table-card">
        <div class="review-summary-row">
          <div class="review-total"><strong>{{ reviewSummary?.averageRating ?? '—' }}</strong><span>门店平均分</span></div>
          <el-table :data="reviewSummary?.coaches || []" style="flex:1"><el-table-column label="教练" min-width="150"><template #default="{row}">{{ row.coachName || '—' }}</template></el-table-column><el-table-column label="评价数" width="90" prop="reviewCount" /><el-table-column label="平均分" width="110"><template #default="{row}">{{ row.averageRating ?? '—' }}</template></el-table-column></el-table>
        </div>
        <div class="section-head" style="margin:20px 0 10px"><div><h3 style="font-size:14px">低分记录</h3></div></div>
        <el-table v-if="reviewSummary?.lowRated?.length" :data="reviewSummary.lowRated" stripe><el-table-column label="课程" min-width="150"><template #default="{row}">{{ row.courseName || '—' }}</template></el-table-column><el-table-column label="教练" width="120"><template #default="{row}">{{ row.coachName || '—' }}</template></el-table-column><el-table-column label="评分" width="150"><template #default="{row}"><el-rate :model-value="row.rating" disabled /></template></el-table-column><el-table-column label="评价" min-width="220"><template #default="{row}">{{ row.content || '—' }}</template></el-table-column></el-table>
        <el-empty v-else description="暂无低分记录" :image-size="55" />
      </el-card>
    </section>
    <el-dialog v-model="scheduleDialog" :title="editingScheduleId ? '编辑排课' : '新增排课'" width="560px"><el-form ref="scheduleFormRef" :model="scheduleForm" :rules="scheduleRules" label-width="90px"><el-form-item v-if="isSuperAdmin" label="所属场馆" prop="venueId" data-agent-field="venueId"><el-select v-model="scheduleForm.venueId" style="width:100%" @change="onScheduleVenueChange"><el-option v-for="v in venues" :key="v.id" :label="v.name" :value="v.id" /></el-select></el-form-item><el-form-item label="课程" prop="courseId" data-agent-field="courseId"><el-select v-model="scheduleForm.courseId" style="width:100%" :placeholder="scheduleCourses.length ? '请选择课程' : '该场馆暂无课程模板，请先在课程库新增'"><el-option v-for="c in scheduleCourses" :key="c.id" :label="c.name" :value="c.id" /></el-select><div v-if="editingScheduleId" class="form-hint">更换课程时，该排课会跟随新课程所属门店</div></el-form-item><el-alert v-if="privatePackageMissing" type="warning" :closable="false" style="margin-bottom:14px" title="本门店暂无在售私教课包" description="私教课需要会员先持有私教课包才能预约。建议先在「会籍与商业中心 → 会籍商品」上架私教课包，否则会员约不了这节课。" /><el-form-item label="教练" prop="coachId" data-agent-field="coachId"><el-select v-model="scheduleForm.coachId" style="width:100%" filterable :placeholder="coaches.length ? '请选择教练账号' : '暂无教练账号，请先在员工账号里创建教练'"><el-option v-for="k in coaches" :key="k.id" :label="k.name" :value="k.id" /></el-select><div class="form-hint">必须绑定真实教练账号，否则教练工作台看不到这条排课</div></el-form-item><el-form-item label="开始时间" prop="scheduledStartTime" data-agent-field="scheduledStartTime"><el-date-picker v-model="scheduleForm.scheduledStartTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></el-form-item><el-form-item label="结束时间" prop="scheduledEndTime" data-agent-field="scheduledEndTime"><el-date-picker v-model="scheduleForm.scheduledEndTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /><div class="form-hint">按课程时长自动带出，可手动调整</div></el-form-item><el-form-item label="教室"><el-select v-model="scheduleForm.roomId" clearable style="width:100%" placeholder="可不指定（不占教室）"><el-option v-for="r in scheduleRooms" :key="r.id" :label="r.capacity ? `${r.name}（可容纳 ${r.capacity} 人）` : r.name" :value="r.id" /></el-select><div class="form-hint">同一时段同一教室只能排一节课，容量不足会被拦下</div></el-form-item><el-form-item label="容量"><el-input-number v-model="scheduleForm.maxStudents" :min="1" /></el-form-item></el-form><template #footer><el-button @click="scheduleDialog=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!scheduleCourses.length || !coaches.length" @click="saveSchedule">{{ editingScheduleId ? '保存修改' : '创建排课' }}</el-button></template></el-dialog>
    <el-dialog v-model="roomDialog" :title="editingRoomId ? '编辑教室' : '新增教室'" width="480px">
      <el-form label-width="96px">
        <el-form-item v-if="isSuperAdmin" label="所属场馆"><el-select v-model="roomForm.venueId" style="width:100%"><el-option v-for="v in venues" :key="v.id" :label="v.name" :value="v.id" /></el-select></el-form-item>
        <el-form-item label="名称"><el-input v-model="roomForm.name" placeholder="例如：操课房 A / 单车房" /></el-form-item>
        <el-form-item label="可容纳"><el-input-number v-model="roomForm.capacity" :min="0" /><span class="form-hint">填 0 表示不限制</span></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="roomForm.status"><el-radio-button value="ACTIVE">启用</el-radio-button><el-radio-button value="INACTIVE">停用</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="备注"><el-input v-model="roomForm.note" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="roomDialog=false">取消</el-button><el-button type="primary" :loading="roomSaving" @click="saveRoom">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="batchDialog" title="批量排课（按周循环生成）" width="640px">
      <el-alert type="info" :closable="false" title="选好周几与日期区间即可一次生成整段课表；与该教练已有排课冲突的课次会自动跳过。" />
      <el-form label-width="92px" style="margin-top:16px">
        <el-form-item v-if="isSuperAdmin" label="所属场馆"><el-select v-model="batchForm.venueId" style="width:100%" clearable placeholder="不选则跟随课程所属门店" @change="onBatchVenueChange"><el-option v-for="v in venues" :key="v.id" :label="v.name" :value="v.id" /></el-select></el-form-item>
        <el-form-item label="课程"><el-select v-model="batchForm.courseId" style="width:100%" placeholder="请选择课程" @change="batchPreview=null"><el-option v-for="c in batchCourses" :key="c.id" :label="c.name" :value="c.id" /></el-select></el-form-item>
        <el-form-item label="教练"><el-select v-model="batchForm.coachId" style="width:100%" filterable placeholder="请选择教练账号" @change="batchPreview=null"><el-option v-for="k in coaches" :key="k.id" :label="k.name" :value="k.id" /></el-select></el-form-item>
        <el-form-item label="重复周几"><el-checkbox-group v-model="batchForm.weekdays"><el-checkbox-button v-for="d in weekdayOptions" :key="d.value" :value="d.value">{{ d.label }}</el-checkbox-button></el-checkbox-group></el-form-item>
        <el-form-item label="时段"><el-time-picker v-model="batchRange" is-range format="HH:mm" value-format="HH:mm" style="width:100%" /></el-form-item>
        <el-form-item label="日期区间"><el-date-picker v-model="batchDates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" style="width:100%" /></el-form-item>
        <el-form-item label="教室"><el-select v-model="batchForm.roomId" clearable style="width:100%" placeholder="可不指定"><el-option v-for="r in batchRooms" :key="r.id" :label="r.name" :value="r.id" /></el-select></el-form-item>
        <el-form-item label="容量"><el-input-number v-model="batchForm.maxStudents" :min="1" /></el-form-item>
      </el-form>
      <div v-if="batchPreview" class="batch-preview">
        <p class="batch-summary">预检结果：将生成 <strong>{{ batchPreview.created.length }}</strong> 节，跳过 <strong>{{ batchPreview.skipped.length }}</strong> 节</p>
        <ul class="batch-list"><li v-for="s in batchPreview.created" :key="s.label"><el-tag size="small" type="success">生成</el-tag> {{ s.label }}</li></ul>
        <ul class="batch-list"><li v-for="s in batchPreview.skipped" :key="s.label"><el-tag size="small" type="warning">跳过</el-tag> {{ s.label }}<small>{{ s.note }}</small></li></ul>
      </div>
      <template #footer><el-button @click="batchDialog=false">关闭</el-button><el-button :loading="batchChecking" @click="previewBatch">预检</el-button><el-button type="primary" :loading="saving" :disabled="!batchPreview || !batchPreview.created.length" @click="saveBatch">确认生成</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import WorkspaceHero from '@/components/WorkspaceHero.vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'; import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'; import { Calendar, Plus, Timer, User } from '@element-plus/icons-vue'; import { createBatchSchedules, createCourse, createSchedule, deleteCourse, deleteSchedule, listCoaches, listCourses, listSchedules, updateCourse, updateSchedule, type CoachOption, type Course, type Schedule, type ScheduleBatchResult } from '@/api/modules/course'
import { listVenues, type Venue } from '@/api/modules/venue'
import { getVenueReviewSummary, type VenueReviewSummary } from '@/api/modules/review'
import { createRoom, deleteRoom, getRoomOccupancy, listRooms, updateRoom, type Room, type RoomOccupancy } from '@/api/modules/room'
import { listProducts, type Product } from '@/api/modules/commercial'
import { hasPermission, hasRole } from '@/utils/auth'
const loading=ref(false),saving=ref(false),courseDialog=ref(false),scheduleDialog=ref(false),editingId=ref<number|null>(null),editingScheduleId=ref<number|null>(null); const courses=ref<Course[]>([]),schedules=ref<Schedule[]>([]),venues=ref<Venue[]>([]),coaches=ref<CoachOption[]>([]),products=ref<Product[]>([])
const reviewSummary=ref<VenueReviewSummary>()
const rooms=ref<Room[]>([]),occupancy=ref<RoomOccupancy[]>([]),roomDialog=ref(false),roomSaving=ref(false),editingRoomId=ref<number|null>(null)
const roomForm=reactive({name:'',capacity:20,status:'ACTIVE',note:'',venueId:undefined as number|undefined})
const roomOccupancy=(id:number)=>occupancy.value.find(o=>o.roomId===id)
const scheduleRooms=computed(()=>scheduleForm.venueId==null?rooms.value:rooms.value.filter(r=>r.venueId===scheduleForm.venueId&&r.status==='ACTIVE'))
const batchRooms=computed(()=>batchForm.venueId==null?rooms.value:rooms.value.filter(r=>r.venueId===batchForm.venueId&&r.status==='ACTIVE')); const courseFormRef=ref<FormInstance>(),scheduleFormRef=ref<FormInstance>()
const canReadReview=hasPermission('COURSE_REVIEW_READ'),canWriteVenue=hasPermission('VENUE_WRITE'),canWriteCourse=hasPermission('COURSE_WRITE'),canWriteSchedule=hasPermission('SCHEDULE_WRITE'),isSuperAdmin=hasRole('SUPER_ADMIN'),canReadProduct=hasPermission('PRODUCT_READ')
const blankCourse=()=>({name:'',type:'GROUP',description:'',durationMinutes:60,intensity:'MEDIUM',maxCapacity:20,fee:0,status:'ACTIVE',venueId:undefined as number|undefined}); const courseForm=reactive(blankCourse()); const scheduleForm=reactive({venueId:undefined as number|undefined,courseId:undefined as number|undefined,coachId:undefined as number|undefined,roomId:undefined as number|undefined,scheduledStartTime:'',scheduledEndTime:'',maxStudents:20})
const courseRules:FormRules={venueId:isSuperAdmin?[{required:true,message:'请选择所属场馆'}]:[],name:[{required:true,message:'请输入课程名称'}],type:[{required:true,message:'请选择课程类型'}],durationMinutes:[{required:true,message:'请输入时长'},{type:'number',min:15,max:300,message:'时长需在 15 ~ 300 分钟之间'}],maxCapacity:[{required:true,message:'请输入容量'},{type:'number',min:1,max:500,message:'容量需在 1 ~ 500 人之间'}],fee:[{required:true,message:'请输入价格'},{type:'number',min:0,message:'价格不能为负数'}]}; const scheduleRules:FormRules={venueId:isSuperAdmin?[{required:true,message:'请选择所属场馆'}]:[],courseId:[{required:true,message:'请选择课程'}],coachId:[{required:true,message:'请选择教练账号'}],scheduledStartTime:[{required:true,message:'请选择开始时间'}],scheduledEndTime:[{required:true,message:'请选择结束时间'}]}
const scheduleCourses=computed(()=>scheduleForm.venueId==null?courses.value:courses.value.filter(c=>c.venueId===scheduleForm.venueId))
// ---- 批量排课 ----
const batchDialog=ref(false),batchChecking=ref(false),batchPreview=ref<ScheduleBatchResult|null>(null)
const weekdayOptions=[{value:1,label:'一'},{value:2,label:'二'},{value:3,label:'三'},{value:4,label:'四'},{value:5,label:'五'},{value:6,label:'六'},{value:7,label:'日'}]
const batchForm=reactive({venueId:undefined as number|undefined,courseId:undefined as number|undefined,coachId:undefined as number|undefined,roomId:undefined as number|undefined,maxStudents:20,weekdays:[1,2,3,4,5] as number[]})
const batchRange=ref<[string,string]>(['19:00','20:00'])
const batchDates=ref<[string,string]|undefined>()
const batchCourses=computed(()=>batchForm.venueId==null?courses.value:courses.value.filter(c=>c.venueId===batchForm.venueId))
const loadRooms=async()=>{if(!hasPermission('VENUE_READ'))return;try{const [r,o]=await Promise.all([listRooms(),getRoomOccupancy()]);rooms.value=r.data;occupancy.value=o.data}catch{rooms.value=[];occupancy.value=[]}}
const openRoom=(row?:Room)=>{editingRoomId.value=row?.id??null;roomForm.name=row?.name??'';roomForm.capacity=row?.capacity??20;roomForm.status=row?.status??'ACTIVE';roomForm.note=row?.note??'';roomForm.venueId=row?.venueId??(isSuperAdmin?undefined:venues.value[0]?.id);roomDialog.value=true}
const saveRoom=async()=>{
  if(!roomForm.name.trim())return ElMessage.warning('请填写教室名称')
  if(isSuperAdmin&&!roomForm.venueId)return ElMessage.warning('请选择所属场馆')
  roomSaving.value=true
  try{
    const body={name:roomForm.name.trim(),capacity:roomForm.capacity,status:roomForm.status,note:roomForm.note,venueId:roomForm.venueId}
    if(editingRoomId.value)await updateRoom(editingRoomId.value,body);else await createRoom(body)
    ElMessage.success('教室已保存');roomDialog.value=false;await loadRooms()
  }finally{roomSaving.value=false}
}
const toggleRoom=async(row:Room)=>{roomSaving.value=true;try{await updateRoom(row.id,{name:row.name,capacity:row.capacity,status:row.status==='ACTIVE'?'INACTIVE':'ACTIVE',note:row.note});await loadRooms()}finally{roomSaving.value=false}}
const removeRoom=async(row:Room)=>{
  try{await ElMessageBox.confirm('确认删除教室「'+row.name+'」？已被排课使用的教室不能删除，只能停用。','删除教室',{type:'warning'})}catch{return}
  try{await deleteRoom(row.id);ElMessage.success('已删除');await loadRooms()}catch{}
}
const loadReviewSummary=async()=>{if(!canReadReview)return;try{reviewSummary.value=(await getVenueReviewSummary()).data}catch{reviewSummary.value=undefined}}
const openBatch=()=>{batchPreview.value=null;batchForm.courseId=undefined;batchForm.coachId=undefined;batchForm.roomId=undefined;batchForm.venueId=isSuperAdmin?undefined:venues.value[0]?.id;batchDialog.value=true}
const onBatchVenueChange=()=>{batchForm.courseId=undefined;batchPreview.value=null}
const batchValidate=()=>{
  if(!batchForm.courseId)return '请选择课程'
  if(!batchForm.coachId)return '请选择教练账号'
  if(!batchForm.weekdays.length)return '请至少选择一个周几'
  if(!batchDates.value)return '请选择日期区间'
  if(!(batchRange.value[0]<batchRange.value[1]))return '结束时间必须晚于开始时间'
  return null
}
const batchPayload=(dryRun:boolean)=>({courseId:batchForm.courseId!,coachId:batchForm.coachId,coachName:coaches.value.find(k=>k.id===batchForm.coachId)?.name||'',venueId:batchForm.venueId,roomId:batchForm.roomId,weekdays:batchForm.weekdays,startTime:batchRange.value[0],endTime:batchRange.value[1],fromDate:batchDates.value![0],toDate:batchDates.value![1],maxStudents:batchForm.maxStudents,dryRun})
const previewBatch=async()=>{const err=batchValidate();if(err)return ElMessage.warning(err);batchChecking.value=true;try{batchPreview.value=(await createBatchSchedules(batchPayload(true))).data}finally{batchChecking.value=false}}
const saveBatch=async()=>{const err=batchValidate();if(err)return ElMessage.warning(err);saving.value=true;try{const r=(await createBatchSchedules(batchPayload(false))).data;ElMessage.success(`已生成 ${r.created.length} 节，跳过 ${r.skipped.length} 节`);batchDialog.value=false;batchPreview.value=null;await load()}finally{saving.value=false}}

const privatePackageMissing=computed(()=>{
  const course=courses.value.find(c=>c.id===scheduleForm.courseId)
  if(course?.type!=='PRIVATE')return false
  const venueId=scheduleForm.venueId??course.venueId
  return !products.value.some(p=>p.productType==='PRIVATE_PACKAGE'&&p.status==='ACTIVE'&&(venueId==null||p.venueId===venueId))
})
const venueName=(id?:number|null)=>venues.value.find(v=>v.id===id)?.name??(id?`场馆 ${id}`:'—')
const syncScheduleEnd=()=>{const course=courses.value.find(c=>c.id===scheduleForm.courseId);if(!course||!scheduleForm.scheduledStartTime)return;const start=new Date(scheduleForm.scheduledStartTime);if(Number.isNaN(start.getTime()))return;const pad=(n:number)=>String(n).padStart(2,'0');const end=new Date(start.getTime()+course.durationMinutes*60000);scheduleForm.scheduledEndTime=`${end.getFullYear()}-${pad(end.getMonth()+1)}-${pad(end.getDate())}T${pad(end.getHours())}:${pad(end.getMinutes())}:00`}
watch(()=>[scheduleForm.courseId,scheduleForm.scheduledStartTime],syncScheduleEnd)
const onScheduleVenueChange=()=>{scheduleForm.courseId=courses.value.find(c=>c.venueId===scheduleForm.venueId)?.id;syncScheduleEnd()}
const load=async()=>{loading.value=true;try{const [c,s,v,k,p]=await Promise.all([listCourses(),listSchedules(),listVenues().catch(()=>null),listCoaches().catch(()=>null),canReadProduct?listProducts().catch(()=>null):Promise.resolve(null)]);courses.value=c.data;schedules.value=s.data;venues.value=Array.isArray(v?.data)?v.data:[];coaches.value=Array.isArray(k?.data)?k.data:[];products.value=Array.isArray(p?.data)?p.data:[]}finally{loading.value=false}}
const openCourse=()=>{Object.assign(courseForm,blankCourse(),{venueId:venues.value[0]?.id});editingId.value=null;courseDialog.value=true}; const openEdit=(c:Course)=>{Object.assign(courseForm,c);editingId.value=c.id;courseDialog.value=true}; const openSchedule=()=>{editingScheduleId.value=null;const venueId=isSuperAdmin?(venues.value.find(v=>courses.value.some(c=>c.venueId===v.id))?.id??venues.value[0]?.id):undefined;Object.assign(scheduleForm,{venueId,courseId:courses.value.find(c=>venueId==null||c.venueId===venueId)?.id,coachId:undefined,roomId:undefined,scheduledStartTime:'',scheduledEndTime:'',maxStudents:20});scheduleDialog.value=true}; const openEditSchedule=(row:Schedule)=>{editingScheduleId.value=row.id;Object.assign(scheduleForm,{venueId:row.venueId,courseId:row.courseId,coachId:row.coachId??undefined,roomId:row.roomId??undefined,scheduledStartTime:row.scheduledStartTime,scheduledEndTime:row.scheduledEndTime,maxStudents:row.maxStudents});scheduleDialog.value=true}
const clampNumber=(v:any,min:number,max:number)=>typeof v==='number'&&Number.isFinite(v)?Math.min(max,Math.max(min,v)):v
const removeCourse=async()=>{if(!editingId.value)return;try{await ElMessageBox.confirm(`确认删除课程「${courseForm.name}」？该操作不可恢复。`,'删除课程',{type:'warning',confirmButtonText:'确认删除',cancelButtonText:'取消'})}catch{return}saving.value=true;try{await deleteCourse(editingId.value);ElMessage.success('课程已删除');courseDialog.value=false;await load()}finally{saving.value=false}}
const saveCourse=async()=>{if(!await courseFormRef.value?.validate().catch(()=>false))return;saving.value=true;try{const payload={...courseForm,durationMinutes:clampNumber(courseForm.durationMinutes,15,300),maxCapacity:clampNumber(courseForm.maxCapacity,1,500),fee:clampNumber(courseForm.fee,0,Number.MAX_SAFE_INTEGER)};editingId.value?await updateCourse(editingId.value,payload):await createCourse(payload);ElMessage.success('课程已保存');courseDialog.value=false;await load()}finally{saving.value=false}}
const removeSchedule=async(row:Schedule)=>{try{await ElMessageBox.confirm(`确认删除排课「${row.course?.name||''} ${formatDate(row.scheduledStartTime)}」？`,'删除排课',{type:'warning',confirmButtonText:'确认删除',cancelButtonText:'取消'})}catch{return}saving.value=true;try{await deleteSchedule(row.id);ElMessage.success('排课已删除');await load()}finally{saving.value=false}}
const saveSchedule=async()=>{if(!await scheduleFormRef.value?.validate().catch(()=>false)||!scheduleForm.courseId||!scheduleForm.coachId)return;saving.value=true;try{const base={courseId:scheduleForm.courseId,coachId:scheduleForm.coachId,coachName:coaches.value.find(c=>c.id===scheduleForm.coachId)?.name??'',scheduledStartTime:scheduleForm.scheduledStartTime,scheduledEndTime:scheduleForm.scheduledEndTime,maxStudents:scheduleForm.maxStudents};if(editingScheduleId.value){await updateSchedule(editingScheduleId.value,{...base,roomId:scheduleForm.roomId});ElMessage.success('排课已更新')}else{await createSchedule({...base,venueId:scheduleForm.venueId,roomId:scheduleForm.roomId});ElMessage.success('排课已创建')}scheduleDialog.value=false;await load()}finally{saving.value=false}}
const typeText=(v:string)=>({GROUP:'团体课',REGULAR:'常规课',PRIVATE:'私教课'}[v]||v);const intensityText=(v:string)=>({LOW:'低强度',MEDIUM:'中强度',HIGH:'高强度'}[v]||v);const formatDate=(v:string)=>new Date(v).toLocaleDateString('zh-CN',{month:'long',day:'numeric',weekday:'short'});const formatTime=(v:string)=>new Date(v).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'});const formatEndTime=(row:Schedule)=>{const s=new Date(row.scheduledStartTime),e=new Date(row.scheduledEndTime);return s.toDateString()===e.toDateString()?formatTime(row.scheduledEndTime):`${e.getMonth()+1}月${e.getDate()}日 ${formatTime(row.scheduledEndTime)}`};onMounted(load)
if(canReadReview)loadReviewSummary()
loadRooms()
</script>

<style scoped>
.page-title,.section-head,.schedule-head,.course-top,.course-meta,.capacity{display:flex;align-items:center;justify-content:space-between}.page-title{margin-bottom:30px}.eyebrow{color:#2768ff;font-size:11px;font-weight:800;letter-spacing:1.5px}.page-title h1{margin:4px 0 6px;font-size:28px;color:#172033}.page-title p,.section-head span{color:#8490a4;font-size:14px}.section-head{margin:0 0 14px}.section-head h3{margin:0;color:#202a3d}.course-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px}.course-card{position:relative;overflow:hidden;padding:20px;background:#fff;border:1px solid #e9edf5;border-radius:16px}.course-accent{position:absolute;left:0;top:0;right:0;height:4px;background:#2768ff}.course-accent.private{background:#8759e8}.course-accent.regular{background:#22b887}.course-top>span:last-child{font-weight:800;color:#182238}.course-card h3{margin:18px 0 8px}.course-card p{height:44px;color:#7d8798;font-size:13px;line-height:1.65}.course-meta{justify-content:flex-start;gap:14px;padding:14px 0;color:#536078;font-size:12px}.course-meta span{display:flex;align-items:center;gap:4px}.schedule-head{margin-top:30px}.schedule-head>div span{display:block;margin-top:5px}.table-card{border-radius:16px}.table-card small{display:block;margin-top:5px;color:#8490a4}.capacity{gap:12px}.capacity .el-progress{flex:1}.capacity span{font-size:12px;color:#758196}.tag-group{display:flex;align-items:center;gap:6px}.dialog-footer{display:flex;align-items:center}.footer-right{display:flex;margin-left:auto}.form-hint{margin-top:6px;color:#8490a4;font-size:12px;line-height:1.4}.warn-line{color:#d94a4a!important}.head-actions{display:flex;gap:10px}.batch-preview{margin-top:6px;padding:14px;border:1px solid #e7ecf4;border-radius:12px;background:#fafcff}.batch-summary{margin:0 0 10px;color:#31405a;font-size:13px}.batch-list{list-style:none;margin:0 0 8px;padding:0;max-height:170px;overflow:auto}.batch-list li{display:flex;align-items:center;gap:8px;padding:5px 0;color:#5b6880;font-size:12px}.batch-list li small{color:#a08a4a}@media(max-width:1100px){.course-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:700px){.course-grid{grid-template-columns:1fr}.page-title{align-items:flex-end}}
.room-block{margin-top:30px}.room-block .el-table small,.review-block .el-table small{display:block;margin-top:4px;color:#8b96a8}.review-block{margin-top:30px}.review-summary-row{display:flex;align-items:center;gap:28px}.review-total{text-align:center;min-width:110px}.review-total strong{display:block;font-size:30px;color:#172033}.review-total span{color:#8792a5;font-size:12px}</style>
