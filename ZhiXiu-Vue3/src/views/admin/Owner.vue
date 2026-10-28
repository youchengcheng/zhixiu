<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SectionCard from '@/components/SectionCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import LetterAvatar from '@/components/LetterAvatar.vue'
import DictSelect from '@/components/DictSelect.vue'
import Pagination from '@/components/Pagination.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import { illustrations } from '@/assets'
import {
  createOwnerProperty,
  deleteOwnerProperty,
  listOwnerProperties,
  listOwners,
  updateOwnerProperty,
  updateOwnerStatus
} from '@/api/adminOwner'
import { USER_STATUS } from '@/utils/dict'
import { formatDateTime } from '@/utils/format'

// 业主管理：业主启停 + 代业主维护房产
const query = reactive({ status: '', keyword: '' })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const rows = ref([])
const loading = ref(false)
const failed = ref(false)
const acting = ref('')

// 房产抽屉
const drawerVisible = ref(false)
const owner = ref(null)
const properties = ref([])
const propertyLoading = ref(false)

// 房产表单
const propertyVisible = ref(false)
const editingPropertyId = ref('')
const propertyFormRef = ref(null)
const propertySubmitting = ref(false)
const propertyForm = reactive({
  community: '',
  building: '',
  unit: '',
  room: '',
  areaSize: null,
  isDefault: 0
})

const propertyRules = {
  community: [{ required: true, message: '请填写小区名称', trigger: 'blur' }],
  building: [{ required: true, message: '请填写栋号', trigger: 'blur' }],
  unit: [{ required: true, message: '请填写单元', trigger: 'blur' }],
  room: [{ required: true, message: '请填写房号', trigger: 'blur' }]
}

function buildParams() {
  const params = { pageNum: page.pageNum, pageSize: page.pageSize }
  if (query.status !== '') params.status = Number(query.status)
  if (query.keyword.trim()) params.keyword = query.keyword.trim()
  return params
}

async function load() {
  loading.value = true
  failed.value = false
  try {
    const data = await listOwners(buildParams())
    rows.value = data?.records || []
    page.total = data?.total || 0
  } catch {
    rows.value = []
    page.total = 0
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)

function onSearch() {
  page.pageNum = 1
  load()
}

function onReset() {
  Object.assign(query, { status: '', keyword: '' })
  page.pageNum = 1
  load()
}

function onPageChange(value) {
  page.pageNum = value
  load()
}

function onSizeChange(value) {
  page.pageSize = value
  page.pageNum = 1
  load()
}

// ---------- 启用 / 禁用 ----------
async function onToggleStatus(row, enabled) {
  acting.value = row.id || row.userId
  try {
    await updateOwnerStatus(row.id || row.userId, enabled ? 1 : 0)
    ElMessage.success(enabled ? '已启用该业主' : '已禁用该业主')
  } catch {
    // 失败时回读
  } finally {
    acting.value = ''
    load()
  }
}

// ---------- 房产 ----------
async function openProperties(row) {
  owner.value = row
  drawerVisible.value = true
  propertyLoading.value = true
  properties.value = []
  try {
    properties.value = (await listOwnerProperties(row.id || row.userId)) || []
  } catch {
    properties.value = []
  } finally {
    propertyLoading.value = false
  }
}

async function reloadProperties() {
  if (!owner.value) return
  propertyLoading.value = true
  try {
    properties.value = (await listOwnerProperties(owner.value.id || owner.value.userId)) || []
  } catch {
    properties.value = []
  } finally {
    propertyLoading.value = false
  }
}

function openCreateProperty() {
  editingPropertyId.value = ''
  Object.assign(propertyForm, {
    community: '',
    building: '',
    unit: '',
    room: '',
    areaSize: null,
    isDefault: properties.value.length ? 0 : 1
  })
  propertyVisible.value = true
}

function openEditProperty(item) {
  editingPropertyId.value = item.id
  Object.assign(propertyForm, {
    community: item.community || '',
    building: item.building || '',
    unit: item.unit || '',
    room: item.room || '',
    areaSize: item.areaSize ?? null,
    isDefault: item.isDefault ?? 0
  })
  propertyVisible.value = true
}

async function submitProperty() {
  const valid = await propertyFormRef.value?.validate().catch(() => false)
  if (!valid) return

  const payload = {
    community: propertyForm.community.trim(),
    building: propertyForm.building.trim(),
    unit: propertyForm.unit.trim(),
    room: propertyForm.room.trim(),
    isDefault: Number(propertyForm.isDefault)
  }
  if (propertyForm.areaSize !== null && propertyForm.areaSize !== '') {
    payload.areaSize = Number(propertyForm.areaSize)
  }

  propertySubmitting.value = true
  try {
    if (editingPropertyId.value) {
      await updateOwnerProperty(editingPropertyId.value, payload)
      ElMessage.success('房产已更新')
    } else {
      // 管理员代建需额外带上 ownerId（文档 12.4）
      await createOwnerProperty({ ...payload, ownerId: owner.value?.id || owner.value?.userId })
      ElMessage.success('房产已添加')
    }
    propertyVisible.value = false
    await reloadProperties()
  } catch {
    // 拦截器已提示
  } finally {
    propertySubmitting.value = false
  }
}

async function onDeleteProperty(item) {
  try {
    await ElMessageBox.confirm(`确认删除「${item.fullAddress}」？`, '删除房产', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  try {
    await deleteOwnerProperty(item.id)
    ElMessage.success('房产已删除')
    await reloadProperties()
  } catch {
    // 忽略
  }
}
</script>

<template>
  <div class="layout-page">
    <PageHeader title="业主管理" description="查看业主账号状态，并代为维护名下房产" />

    <SectionCard padding="none">
      <div class="aowner-filter page-filter">
        <DictSelect
          v-model="query.status"
          :dict="USER_STATUS"
          placeholder="全部状态"
          aria-label="全部状态"
          class="aowner-filter__status"
          @change="onSearch"
        />
        <el-input
          v-model="query.keyword"
          placeholder="姓名 / 手机号"
          aria-label="姓名 / 手机号"
          clearable
          class="aowner-filter__keyword"
          :prefix-icon="Search"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
        <el-button :icon="Refresh" @click="onReset">重置</el-button>
      </div>

      <AppSkeleton v-if="loading" variant="table" :rows="6" />

      <EmptyState
        v-else-if="failed"
        icon="Warning"
        title="业主列表加载失败"
        description="请确认后端服务已启动后重试。"
        action-text="重新加载"
        @action="load"
      />

      <EmptyState
        v-else-if="!rows.length"
        icon="UserFilled"
        title="没有符合条件的业主"
        description="换个筛选条件试试。"
        size="compact"
      />

      <template v-else>
        <el-table :data="rows" style="width: 100%">
          <el-table-column label="业主" min-width="170">
            <template #default="{ row }">
              <div class="aowner-cell">
                <!-- 有上传头像时显示图片，没有时回退姓名首字 -->
                <LetterAvatar :name="row.realName || row.username" :src="row.avatar" :size="32" />
                <div class="aowner-cell__text">
                  <span class="aowner-cell__name">{{ row.realName || row.username }}</span>
                  <span class="aowner-cell__sub text-mono">{{ row.username }}</span>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="手机号" width="150">
            <template #default="{ row }">
              <span class="text-mono">{{ row.phone || '-' }}</span>
            </template>
          </el-table-column>

          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <StatusTag :status="row.status ?? 1" :dict="USER_STATUS" :dot="false" size="small" />
            </template>
          </el-table-column>

          <el-table-column label="注册时间" width="170">
            <template #default="{ row }">
              <span class="text-mono">{{ formatDateTime(row.createTime) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="启用" width="90">
            <template #default="{ row }">
              <el-switch
                :model-value="(row.status ?? 1) === 1"
                :loading="acting === (row.id || row.userId)"
                @change="(value) => onToggleStatus(row, value)"
              />
            </template>
          </el-table-column>

          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button text type="primary" @click="openProperties(row)">房产</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="aowner-pager page-pager">
          <Pagination
            :total="page.total"
            :page="page.pageNum"
            :size="page.pageSize"
            @update:page="onPageChange"
            @update:size="onSizeChange"
          />
        </div>
      </template>
    </SectionCard>

    <!-- 名下房产抽屉 -->
    <el-drawer v-model="drawerVisible" :title="`${owner?.realName || owner?.username || ''} 的房产`" size="560px">
      <div class="owner-prop">
        <div class="owner-prop__head">
          <span class="owner-prop__count">共 {{ properties.length }} 处</span>
          <el-button type="primary" size="small" :icon="Plus" @click="openCreateProperty">添加房产</el-button>
        </div>

        <AppSkeleton v-if="propertyLoading" variant="card" :rows="2" :columns="1" />

        <EmptyState
          v-else-if="!properties.length"
          :illustration="illustrations.emptyProperty"
          title="该业主还没有登记房产"
          description="可以代为添加，之后业主就能直接选择该地址报修。"
          size="compact"
          action-text="添加房产"
          @action="openCreateProperty"
        />

        <ul v-else class="owner-prop__list">
          <li v-for="item in properties" :key="item.id" class="owner-prop__item">
            <div class="owner-prop__main">
              <p class="owner-prop__address">
                {{ item.fullAddress }}
                <span v-if="item.isDefault === 1" class="owner-prop__default">默认</span>
              </p>
              <p class="owner-prop__meta text-mono">
                {{ item.areaSize ? `${item.areaSize} ㎡` : '未填写面积' }}
              </p>
            </div>
            <div class="table-actions">
              <el-button text type="primary" @click="openEditProperty(item)">编辑</el-button>
              <el-button text type="danger" @click="onDeleteProperty(item)">删除</el-button>
            </div>
          </li>
        </ul>
      </div>
    </el-drawer>

    <!-- 房产表单弹窗 -->
    <el-dialog
      v-model="propertyVisible"
      :title="editingPropertyId ? '编辑房产' : '添加房产'"
      width="520px"
      append-to-body
    >
      <el-form ref="propertyFormRef" :model="propertyForm" :rules="propertyRules" label-position="top">
        <el-form-item label="小区" prop="community">
          <el-input v-model="propertyForm.community" placeholder="例如「阳光嘉园」" />
        </el-form-item>

        <div class="grid grid-3">
          <el-form-item label="栋号" prop="building">
            <el-input v-model="propertyForm.building" placeholder="3" />
          </el-form-item>
          <el-form-item label="单元" prop="unit">
            <el-input v-model="propertyForm.unit" placeholder="2" />
          </el-form-item>
          <el-form-item label="房号" prop="room">
            <el-input v-model="propertyForm.room" placeholder="501" />
          </el-form-item>
        </div>

        <el-form-item label="面积">
          <el-input-number v-model="propertyForm.areaSize" :min="0" :max="2000" :precision="2" :step="1" />
          <span class="form-suffix">㎡（选填）</span>
        </el-form-item>

        <el-form-item label="默认">
          <el-switch v-model="propertyForm.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="propertyVisible = false">取消</el-button>
        <el-button type="primary" :loading="propertySubmitting" @click="submitProperty">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.aowner-filter {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
  padding: var(--space-5) var(--space-5) var(--space-4);
}

.aowner-filter__status {
  width: 160px;
}

.aowner-filter__keyword {
  width: 240px;
}

.aowner-cell {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.aowner-cell__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.aowner-cell__name {
  font-size: var(--fs-body);
  font-weight: var(--fw-medium);
  color: var(--color-text-1);
}

.aowner-cell__sub {
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.aowner-pager {
  padding: 0 var(--space-5) var(--space-4);
}

.owner-prop {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.owner-prop__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.owner-prop__count {
  font-size: var(--fs-sm);
  color: var(--color-text-3);
}

.owner-prop__list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.owner-prop__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.owner-prop__main {
  min-width: 0;
}

.owner-prop__address {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--fs-body);
  font-weight: var(--fw-medium);
  color: var(--color-text-1);
}

.owner-prop__default {
  padding: 0 6px;
  border-radius: var(--radius-xs);
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: 11px;
  line-height: 18px;
}

.owner-prop__meta {
  margin-top: 2px;
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.form-suffix {
  margin-left: var(--space-2);
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}
</style>
