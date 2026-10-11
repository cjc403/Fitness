<template>
  <div class="page">
    <WorkspaceHero compact>
      <template #heading><span>FITNESS OPERATIONS</span><h2>员工账号</h2><p>创建岗位账号，管理门店归属、登录状态与初始密码</p></template>
      <template #actions><el-button type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新增员工账号</el-button></template>
    </WorkspaceHero>
    <el-card shadow="never">
      <div class="filters">
        <el-input v-model="filters.keyword" clearable placeholder="账号、姓名或手机号" @keyup.enter="search"><template #append><el-button :icon="Search" @click="search" /></template></el-input>
        <el-select v-model="filters.roleCode" clearable placeholder="全部角色" @change="search"><el-option v-for="item in roleOptions" :key="item.code" :label="item.name" :value="item.code" /></el-select>
        <el-select v-model="filters.status" clearable placeholder="全部状态" @change="search"><el-option label="正常" value="ACTIVE" /><el-option label="已停用" value="DISABLED" /></el-select>
      </div>
      <el-table v-loading="loading" :data="accounts" stripe>
        <el-table-column prop="username" label="登录账号" min-width="130" />
        <el-table-column prop="realName" label="姓名" min-width="100" />
        <el-table-column prop="roleName" label="角色" width="110"><template #default="{row}"><el-tag>{{ row.roleName }}</el-tag></template></el-table-column>
        <el-table-column prop="venueName" label="所属场馆" min-width="150" />
        <el-table-column prop="phone" label="手机号" width="135" />
        <el-table-column label="账号状态" width="105"><template #default="{row}"><el-tag :type="row.status==='ACTIVE'?'success':'info'">{{ row.status==='ACTIVE'?'正常':'已停用' }}</el-tag></template></el-table-column>
        <el-table-column label="密码状态" width="115"><template #default="{row}"><el-tag v-if="row.mustChangePassword" type="warning">待首次修改</el-tag><span v-else>已设置</span></template></el-table-column>
        <el-table-column label="最近登录" min-width="165"><template #default="{row}">{{ formatTime(row.lastLoginAt) }}</template></el-table-column>
        <el-table-column label="操作" width="250" fixed="right"><template #default="{row}">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="openReset(row)">重置密码</el-button>
          <el-button v-if="row.id!==currentUserId" link :type="row.status==='ACTIVE'?'danger':'success'" @click="toggleStatus(row)">{{ row.status==='ACTIVE'?'停用':'启用' }}</el-button>
        </template></el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="page" v-model:page-size="size" layout="total, sizes, prev, pager, next" :total="total" :page-sizes="[10,20,50]" @change="load" /></div>
    </el-card>

    <el-dialog v-model="createVisible" :title="editing ? '编辑员工资料' : '新增员工账号'" width="560px">
      <el-alert v-if="!editing" title="员工首次登录后必须修改临时密码" type="info" :closable="false" />
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px" class="dialog-form">
        <el-form-item label="登录账号" prop="username"><el-input v-model="createForm.username" :disabled="!!editing" placeholder="以字母开头，至少4位" /></el-form-item>
        <el-form-item label="姓名" prop="realName"><el-input v-model="createForm.realName" /></el-form-item>
        <el-form-item label="角色" prop="roleCode"><el-select v-model="createForm.roleCode" style="width:100%"><el-option v-for="item in roleOptions" :key="item.code" :label="item.name" :value="item.code" /></el-select></el-form-item>
        <el-form-item v-if="isSuperAdmin" label="所属场馆" prop="venueId"><el-select v-model="createForm.venueId" style="width:100%"><el-option v-for="venue in venues" :key="venue.id" :label="venue.name" :value="venue.id" /></el-select></el-form-item>
        <el-form-item label="手机号" prop="phone"><el-input v-model="createForm.phone" maxlength="11" /></el-form-item>
        <el-form-item label="邮箱" prop="email"><el-input v-model="createForm.email" /></el-form-item>
        <el-form-item v-if="!editing" label="临时密码" prop="password"><el-input v-model="createForm.password" type="password" show-password placeholder="大小写字母、数字、特殊字符" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="createVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitCreate">创建账号</el-button></template>
    </el-dialog>

    <el-dialog v-model="resetVisible" title="重置临时密码" width="460px">
      <p class="reset-copy">账号：<b>{{ resetting?.username }}</b>。重置后，该员工下次登录必须修改密码。</p>
      <el-form ref="resetFormRef" :model="resetForm" :rules="resetRules" label-position="top"><el-form-item label="新临时密码" prop="newPassword"><el-input v-model="resetForm.newPassword" type="password" show-password /></el-form-item></el-form>
      <template #footer><el-button @click="resetVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitReset">确认重置</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import WorkspaceHero from '@/components/WorkspaceHero.vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { createEmployee, listCreatableRoles, listEmployees, resetEmployeePassword, updateEmployee, updateEmployeeStatus, type EmployeeAccount, type EmployeeAccountRequest, type RoleOption } from '@/api/modules/account'
import { listVenues, type Venue } from '@/api/modules/venue'
import { useAuthStore } from '@/store/modules/auth'

const auth = useAuthStore(); const isSuperAdmin = computed(() => auth.userInfo?.role === 'SUPER_ADMIN'); const currentUserId = computed(() => auth.userInfo?.id)
// 角色选项由后端 /accounts/roles 给出：加角色时只改后端白名单，界面自动跟上
const roleOptions = ref<RoleOption[]>([])
const loadRoleOptions = async () => { try { roleOptions.value = (await listCreatableRoles()).data } catch { roleOptions.value = [] } }
const accounts=ref<EmployeeAccount[]>([]); const venues=ref<Venue[]>([]); const loading=ref(false); const saving=ref(false); const page=ref(1); const size=ref(10); const total=ref(0)
const filters=reactive({keyword:'',roleCode:'',status:''}); const createVisible=ref(false); const resetVisible=ref(false); const createFormRef=ref<FormInstance>(); const resetFormRef=ref<FormInstance>(); const resetting=ref<EmployeeAccount>()
const editing=ref<EmployeeAccount>()
const emptyCreate=():EmployeeAccountRequest=>({username:'',password:'',realName:'',phone:'',email:'',roleCode:isSuperAdmin.value?'MANAGER':'COACH',venueId:isSuperAdmin.value?venues.value[0]?.id:undefined})
const createForm=reactive<EmployeeAccountRequest>(emptyCreate()); const resetForm=reactive({newPassword:''})
const strong=(_r:unknown,v:string,cb:(e?:Error)=>void)=>/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,72}$/.test(v)?cb():cb(new Error('至少8位，须包含大小写字母、数字和特殊字符'))
const createRules:FormRules={username:[{required:true,pattern:/^[A-Za-z][A-Za-z0-9_.-]{3,49}$/,message:'账号格式不正确',trigger:'blur'}],realName:[{required:true,message:'请输入姓名',trigger:'blur'}],roleCode:[{required:true,message:'请选择角色',trigger:'change'}],venueId:[{required:true,message:'请选择场馆',trigger:'change'}],phone:[{pattern:/^$|^1[3-9]\d{9}$/,message:'手机号格式不正确',trigger:'blur'}],email:[{type:'email',message:'邮箱格式不正确',trigger:'blur'}],password:[{validator:(_r,v,cb)=>editing.value?cb():strong(_r,v,cb),trigger:'blur'}]}
const resetRules:FormRules={newPassword:[{required:true,validator:strong,trigger:'blur'}]}
const load=async()=>{loading.value=true;try{const r=await listEmployees({keyword:filters.keyword||undefined,roleCode:filters.roleCode||undefined,status:filters.status||undefined,page:page.value,size:size.value});accounts.value=r.data.items;total.value=r.data.total}finally{loading.value=false}}
const search=()=>{page.value=1;load()}; const openCreate=()=>{editing.value=undefined;Object.assign(createForm,emptyCreate());createVisible.value=true}
const openEdit=(row:EmployeeAccount)=>{editing.value=row;Object.assign(createForm,{username:row.username,password:'',realName:row.realName,phone:row.phone||'',email:row.email||'',roleCode:row.roleCode,venueId:row.venueId});createVisible.value=true}
const submitCreate=async()=>{if(!createFormRef.value||!(await createFormRef.value.validate().catch(()=>false)))return;saving.value=true;try{if(editing.value)await updateEmployee(editing.value.id,{realName:createForm.realName,phone:createForm.phone,email:createForm.email,roleCode:createForm.roleCode,venueId:createForm.venueId});else await createEmployee(createForm);ElMessage.success(editing.value?'员工资料已更新':'员工账号已创建');createVisible.value=false;await load()}finally{saving.value=false}}
const openReset=(row:EmployeeAccount)=>{resetting.value=row;resetForm.newPassword='';resetVisible.value=true}
const submitReset=async()=>{if(!resetting.value||!resetFormRef.value||!(await resetFormRef.value.validate().catch(()=>false)))return;saving.value=true;try{await resetEmployeePassword(resetting.value.id,resetForm.newPassword);ElMessage.success('密码已重置');resetVisible.value=false;await load()}finally{saving.value=false}}
const toggleStatus=async(row:EmployeeAccount)=>{const status=row.status==='ACTIVE'?'DISABLED':'ACTIVE';await ElMessageBox.confirm(`确定${status==='ACTIVE'?'启用':'停用'}账号 ${row.username} 吗？`,'账号状态',{type:'warning'});await updateEmployeeStatus(row.id,status);ElMessage.success('账号状态已更新');await load()}
const formatTime=(v?:string)=>v?new Date(v).toLocaleString('zh-CN'):'从未登录'
loadRoleOptions()
onMounted(async()=>{if(isSuperAdmin.value){const r=await listVenues();venues.value=r.data.filter(v=>v.status==='ACTIVE')}await load()})
</script>

<style scoped>
.page{padding:4px}.page-toolbar{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}.page-toolbar h2{margin:0 0 6px;color:#1f2937}.page-toolbar p{margin:0;color:#6b7280;font-size:14px}.filters{display:grid;grid-template-columns:minmax(240px,1fr) 150px 140px;gap:12px;margin-bottom:16px}.pagination{display:flex;justify-content:flex-end;margin-top:18px}.dialog-form{margin-top:20px}.reset-copy{color:#667085;line-height:1.7}@media(max-width:760px){.filters{grid-template-columns:1fr}.page-toolbar{align-items:flex-start;gap:15px}.page-toolbar p{display:none}}
</style>
