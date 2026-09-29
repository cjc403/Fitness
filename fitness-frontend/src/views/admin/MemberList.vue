<template>
  <div class="page">
    <div class="page-toolbar">
      <div>
        <h2>会员管理</h2>
        <p>查看会员资料并维护会员状态</p>
      </div>
      <el-button v-if="canWrite" type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>
        新增会员
      </el-button>
    </div>

    <el-card shadow="never">
      <div class="search-bar">
        <el-input v-model="keyword" clearable placeholder="搜索会员编号、姓名或手机号" @keyup.enter="search">
          <template #append><el-button :icon="Search" @click="search" /></template>
        </el-input>
      </div>
      <el-table v-loading="loading" :data="members" stripe>
        <el-table-column prop="memberNo" label="会员编号" min-width="130" />
        <el-table-column prop="realName" label="姓名" min-width="100" />
        <el-table-column prop="phone" label="手机号" min-width="140" />
        <el-table-column prop="gender" label="性别" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="黑名单" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.blacklistFlag" type="danger">是</el-tag>
            <span v-else>否</span>
          </template>
        </el-table-column>
        <el-table-column label="登录账号" min-width="140">
          <template #default="{ row }">
            <div v-if="row.accountOpened" class="account-cell"><span>{{ row.accountUsername }}</span><el-tag size="small" :type="row.accountStatus === 'ACTIVE' ? 'success' : 'info'">{{ row.accountStatus === 'ACTIVE' ? '正常' : '已停用' }}</el-tag></div>
            <span v-else class="muted">未开通</span>
          </template>
        </el-table-column>
        <el-table-column label="体测" width="110"><template #default="{row}"><el-button link type="primary" @click="openBody(row)">体测档案</el-button></template></el-table-column>
        <el-table-column v-if="canWrite" label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="!row.accountOpened" link type="primary" @click="openAccountDialog(row, 'open')">开通账号</el-button>
            <el-button v-else link type="primary" @click="openAccountDialog(row, 'reset')">重置密码</el-button>
            <el-button v-if="row.accountOpened" link :type="row.accountStatus === 'ACTIVE' ? 'danger' : 'success'" @click="toggleAccount(row)">{{ row.accountStatus === 'ACTIVE' ? '停用账号' : '启用账号' }}</el-button>
            <el-button v-if="row.status === 'ACTIVE'" link type="warning" @click="changeStatus(row, 'pause')">暂停</el-button>
            <el-button v-else link type="success" @click="changeStatus(row, 'renew')">恢复</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && members.length === 0" description="暂无会员" />
      <div class="pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          layout="total, sizes, prev, pager, next"
          :total="total"
          :page-sizes="[10, 20, 50]"
          @current-change="loadMembers"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑会员' : '新增会员'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="会员编号" prop="memberNo">
          <el-input v-model="form.memberNo" placeholder="例如：M20260001" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio label="男">男</el-radio>
            <el-radio label="女">女</el-radio>
            <el-radio label="其他">其他</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="生日">
          <el-date-picker v-model="form.birthday" type="date" value-format="YYYY-MM-DD" placeholder="选择生日" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="黑名单">
          <el-switch v-model="form.blacklistFlag" active-text="是" inactive-text="否" />
        </el-form-item>
        <el-form-item label="专属教练">
          <el-select v-model="form.coachId" clearable filterable placeholder="不指定" style="width:100%"><el-option v-for="c in coaches" :key="c.userId" :label="c.realName" :value="c.userId" /></el-select>
        </el-form-item>
        <el-form-item label="会籍顾问">
          <el-select v-model="form.salesUserId" clearable filterable placeholder="不指定" style="width:100%"><el-option v-for="a in advisors" :key="a.userId" :label="a.realName" :value="a.userId" /></el-select>
          <div class="form-hint">指派后，该会员会出现在对应顾问/教练的工作台里，用于跟进与业绩归属</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="accountVisible" :title="accountMode === 'open' ? '开通会员账号' : '重置会员密码'" width="470px">
      <el-alert title="会员首次使用临时密码登录后，必须设置自己的新密码" type="info" :closable="false" />
      <el-form ref="accountFormRef" :model="accountForm" :rules="accountRules" label-position="top" class="account-form">
        <el-form-item v-if="accountMode === 'open'" label="登录账号" prop="username"><el-input v-model="accountForm.username" placeholder="以字母开头，至少4位" /></el-form-item>
        <el-form-item label="临时密码" prop="password"><el-input v-model="accountForm.password" type="password" show-password placeholder="大小写字母、数字、特殊字符" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="accountVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submitAccount">确认</el-button></template>
    </el-dialog>
  </div>
    <el-drawer v-model="bodyVisible" :title="`${bodyStudent?.member?.realName || bodyStudent?.realName || '会员'} 的体测档案`" size="72%">
      <BodyMeasurePanel v-if="bodyStudent" :member-id="bodyMemberId" :member-name="bodyStudent?.member?.realName || bodyStudent?.realName" />
    </el-drawer>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { hasPermission } from '@/utils/auth'
import {
  createMember,
  listMembers,
  pauseMember,
  openMemberAccount,
  resetMemberPassword,
  updateMemberAccountStatus,
  renewMember,
  updateMember,
  type Member,
  type MemberRequest
} from '@/api/modules/member'
import { listAdvisors, type AdvisorRef } from '@/api/modules/advisor'
import BodyMeasurePanel from '@/components/BodyMeasurePanel.vue'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const accountVisible = ref(false)
const accountMode = ref<'open' | 'reset'>('open')
const accountMember = ref<Member>()
const accountFormRef = ref<FormInstance>()
const accountForm = reactive({ username: '', password: '' })
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const members = ref<Member[]>([])
const keyword = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const canWrite = hasPermission('MEMBER_WRITE')

const emptyForm = (): MemberRequest => ({
  memberNo: '', realName: '', phone: '', email: '', gender: '', birthday: '', address: '', blacklistFlag: false
})
const form = reactive<MemberRequest>(emptyForm())
const advisors=ref<AdvisorRef[]>([]),coaches=ref<AdvisorRef[]>([])
const bodyVisible=ref(false),bodyStudent=ref<any>(null)
const bodyMemberId=computed(()=>bodyStudent.value?.id)
const openBody=(row:any)=>{bodyStudent.value=row;bodyVisible.value=true}
const loadAssignees=async()=>{try{const [a,c]=await Promise.all([listAdvisors('SALES'),listAdvisors('COACH')]);advisors.value=a.data;coaches.value=c.data}catch{}}
const rules: FormRules = {
  memberNo: [{ required: true, message: '请输入会员编号', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入会员姓名', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}
const strongPassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,72}$/.test(value) ? callback() : callback(new Error('至少8位，须包含大小写字母、数字和特殊字符'))
const accountRules: FormRules = {
  username: [{ validator: (_r, value, cb) => accountMode.value === 'reset' || /^[A-Za-z][A-Za-z0-9_.-]{3,49}$/.test(value) ? cb() : cb(new Error('账号须以字母开头，至少4位')), trigger: 'blur' }],
  password: [{ required: true, validator: strongPassword, trigger: 'blur' }]
}

const loadMembers = async () => {
  loading.value = true
  try {
    const response = await listMembers({ keyword: keyword.value || undefined, page: page.value, size: size.value })
    members.value = response.data.items
    total.value = response.data.total
  } finally {
    loading.value = false
  }
}

const search = () => {
  page.value = 1
  loadMembers()
}

const handleSizeChange = (newSize: number) => {
  size.value = newSize
  page.value = 1
  loadMembers()
}

const openCreate = () => {
  Object.assign(form, emptyForm())
  editingId.value = null
  dialogVisible.value = true
}

const openEdit = (member: Member) => {
  Object.assign(form, {
    memberNo: member.memberNo,
    realName: member.realName,
    phone: member.phone,
    email: member.email || '',
    gender: member.gender || '',
    birthday: member.birthday || '',
    address: member.address || '',
    blacklistFlag: member.blacklistFlag
  })
  editingId.value = member.id
  dialogVisible.value = true
}

const submit = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    if (editingId.value) {
      await updateMember(editingId.value, form)
      ElMessage.success('会员信息已更新')
    } else {
      await createMember(form)
      ElMessage.success('会员已创建')
    }
    dialogVisible.value = false
    await loadMembers()
  } finally {
    saving.value = false
  }
}

const changeStatus = async (member: Member, action: 'pause' | 'renew') => {
  await ElMessageBox.confirm(action === 'pause' ? '确定暂停该会员服务吗？' : '确定恢复该会员服务吗？', '请确认', { type: 'warning' })
  if (action === 'pause') await pauseMember(member.id)
  else await renewMember(member.id)
  ElMessage.success(action === 'pause' ? '会员服务已暂停' : '会员服务已恢复')
  await loadMembers()
}

const openAccountDialog = (member: Member, mode: 'open' | 'reset') => {
  accountMember.value = member; accountMode.value = mode
  accountForm.username = mode === 'open' ? `member${member.memberNo.replace(/[^A-Za-z0-9]/g, '').slice(-8)}` : ''
  accountForm.password = ''; accountVisible.value = true
}
const submitAccount = async () => {
  if (!accountMember.value || !accountFormRef.value || !(await accountFormRef.value.validate().catch(() => false))) return
  saving.value = true
  try {
    if (accountMode.value === 'open') await openMemberAccount(accountMember.value.id, accountForm.username, accountForm.password)
    else await resetMemberPassword(accountMember.value.id, accountForm.password)
    ElMessage.success(accountMode.value === 'open' ? '会员账号已开通' : '会员密码已重置')
    accountVisible.value = false; await loadMembers()
  } finally { saving.value = false }
}
const toggleAccount = async (member: Member) => {
  const status = member.accountStatus === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  await ElMessageBox.confirm(`确定${status === 'ACTIVE' ? '启用' : '停用'}会员 ${member.realName} 的登录账号吗？`, '账号状态', { type: 'warning' })
  await updateMemberAccountStatus(member.id, status); ElMessage.success('会员账号状态已更新'); await loadMembers()
}

const statusText = (status: string) => ({ ACTIVE: '正常', PAUSED: '已暂停' }[status] || status)
const statusType = (status: string) => status === 'ACTIVE' ? 'success' : status === 'PAUSED' ? 'warning' : 'info'

onMounted(loadMembers)
loadAssignees()
</script>

<style scoped>
.page { padding: 4px; }
.page-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
.page-toolbar h2 { margin: 0 0 6px; color: #1f2937; }
.page-toolbar p { margin: 0; color: #6b7280; font-size: 14px; }
.search-bar { display: flex; max-width: 420px; margin-bottom: 16px; }
.pagination { display: flex; justify-content: flex-end; margin-top: 18px; }
.account-cell{display:flex;align-items:center;gap:8px}.muted{color:#a0a8b6}.account-form{margin-top:20px}
.form-hint{margin-top:5px;color:#9aa5b8;font-size:12px}</style>
