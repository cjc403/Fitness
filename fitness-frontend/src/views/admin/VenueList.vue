<template>
  <div class="page">
    <WorkspaceHero compact>
      <template #heading><span>FITNESS OPERATIONS</span><h2>场馆管理</h2>
        <p>维护总部和分店的基础信息</p></template>
      <template #actions><el-button v-if="canCreate" type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>
        新增场馆
      </el-button></template>
    </WorkspaceHero>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="venues" stripe>
        <el-table-column prop="name" label="场馆名称" min-width="160" />
        <el-table-column prop="code" label="场馆编码" min-width="150" />
        <el-table-column prop="address" label="地址" min-width="220" show-overflow-tooltip />
        <el-table-column prop="contactPhone" label="联系电话" width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '营业中' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && venues.length === 0" description="暂无场馆" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑场馆' : '新增场馆'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="场馆名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：浦东旗舰店" />
        </el-form-item>
        <el-form-item label="场馆编码" prop="code">
          <el-input v-model="form.code" placeholder="例如：PUDONG_001" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" placeholder="请输入详细地址" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" />
        </el-form-item>
        <el-form-item label="联系邮箱" prop="contactEmail">
          <el-input v-model="form.contactEmail" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio label="ACTIVE">营业中</el-radio>
            <el-radio label="INACTIVE">已停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import WorkspaceHero from '@/components/WorkspaceHero.vue'
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { createVenue, listVenues, updateVenue, type Venue } from '@/api/modules/venue'
import { hasPermission } from '@/utils/auth'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const venues = ref<Venue[]>([])
const canCreate = hasPermission('VENUE_CREATE')

const emptyForm = () => ({ name: '', code: '', address: '', contactPhone: '', contactEmail: '', status: 'ACTIVE' })
const form = reactive(emptyForm())
const rules: FormRules = {
  name: [{ required: true, message: '请输入场馆名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入场馆编码', trigger: 'blur' }],
  contactEmail: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

const loadVenues = async () => {
  loading.value = true
  try {
    const response = await listVenues()
    venues.value = response.data
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  Object.assign(form, emptyForm())
  editingId.value = null
  dialogVisible.value = true
}

const openEdit = (venue: Venue) => {
  Object.assign(form, {
    name: venue.name,
    code: venue.code,
    address: venue.address || '',
    contactPhone: venue.contactPhone || '',
    contactEmail: venue.contactEmail || '',
    status: venue.status
  })
  editingId.value = venue.id
  dialogVisible.value = true
}

const submit = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    if (editingId.value) {
      await updateVenue(editingId.value, form)
      ElMessage.success('场馆已更新')
    } else {
      await createVenue(form)
      ElMessage.success('场馆已创建')
    }
    dialogVisible.value = false
    await loadVenues()
  } finally {
    saving.value = false
  }
}

onMounted(loadVenues)
</script>

<style scoped>
.page { padding: 4px; }
.page-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; }
.page-toolbar h2 { margin: 0 0 6px; color: #1f2937; }
.page-toolbar p { margin: 0; color: #6b7280; font-size: 14px; }
</style>
