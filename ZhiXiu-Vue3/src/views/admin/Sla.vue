<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SectionCard from '@/components/SectionCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import {
  createSlaConfig,
  deleteSlaConfig,
  listSlaConfigs,
  updateSlaConfig,
  updateSlaEnabled
} from '@/api/sla'
import { getCategoryTree } from '@/api/category'
import { AFFECT_SCOPE, ROLE, SAFETY_FLAGS, URGENCY, dictOptions, dictText } from '@/utils/dict'
import { formatMinutes } from '@/utils/format'

// SLA 配置：按「类型 + 紧急程度 + 安全隐患 + 影响范围」四维匹配，
// 命中维度多者优先，其次比 priorityWeight，最后比 id（与后端 SlaService 规则一致）
const rows = ref([])
const loading = ref(true)
const failed = ref(false)
const acting = ref('')
const categoryOptions = ref([])

const urgencyOptions = dictOptions(URGENCY)
const scopeOptions = dictOptions(AFFECT_SCOPE)
const roleOptions = dictOptions(ROLE)

const dialogVisible = ref(false)
const editingId = ref('')
const submitting = ref(false)
const formRef = ref(null)
const form = reactive({
  categoryId: '',
  urgency: '',
  safetyFlag: '',
  affectScope: '',
  responseTimeout: 120,
  handleTimeout: 1440,
  escalateTo: 'ADMIN',
  priorityWeight: 0
})

const rules = {
  responseTimeout: [{ required: true, message: '请填写响应超时时间', trigger: 'blur' }],
  handleTimeout: [{ required: true, message: '请填写处理超时时间', trigger: 'blur' }]
}

async function loadCategories() {
  try {
    const tree = (await getCategoryTree()) || []
    categoryOptions.value = tree.flatMap((parent) =>
      (parent.children || []).map((child) => ({
        id: String(child.id),
        name: `${parent.name} > ${child.name}`
      }))
    )
  } catch {
    categoryOptions.value = []
  }
}

async function load() {
  loading.value = true
  failed.value = false
  try {
    rows.value = (await listSlaConfigs()) || []
  } catch {
    rows.value = []
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadCategories()
  await load()
})

function categoryName(id) {
  return categoryOptions.value.find((item) => item.id === String(id))?.name || id
}

/** 把四维匹配条件拼成一行可读文案；全为空即全局兜底 */
function matchText(item) {
  const dims = []
  if (item.categoryId) dims.push(`类型：${categoryName(item.categoryId)}`)
  if (item.urgency !== null && item.urgency !== undefined) dims.push(`紧急度：${dictText(URGENCY, item.urgency)}`)
  if (item.safetyFlag) dims.push(`隐患：${item.safetyFlag}`)
  if (item.affectScope !== null && item.affectScope !== undefined) {
    dims.push(`范围：${dictText(AFFECT_SCOPE, item.affectScope)}`)
  }
  return dims.length ? dims.join(' · ') : '全局兜底（所有工单）'
}

/** 全局兜底配置不允许删除（文档 11.5） */
function isGlobalFallback(item) {
  return !item.categoryId && !item.safetyFlag && (item.urgency ?? null) === null && (item.affectScope ?? null) === null
}

function openCreate() {
  editingId.value = ''
  Object.assign(form, {
    categoryId: '',
    urgency: '',
    safetyFlag: '',
    affectScope: '',
    responseTimeout: 120,
    handleTimeout: 1440,
    escalateTo: 'ADMIN',
    priorityWeight: 0
  })
  dialogVisible.value = true
}

function openEdit(item) {
  editingId.value = item.id
  Object.assign(form, {
    categoryId: item.categoryId ? String(item.categoryId) : '',
    urgency: item.urgency ?? '',
    safetyFlag: item.safetyFlag || '',
    affectScope: item.affectScope ?? '',
    responseTimeout: item.responseTimeout ?? 120,
    handleTimeout: item.handleTimeout ?? 1440,
    escalateTo: item.escalateTo || 'ADMIN',
    priorityWeight: item.priorityWeight ?? 0
  })
  dialogVisible.value = true
}

/** 空值统一转成 null：表示该维度「不限制」，与后端约定一致 */
function buildPayload() {
  return {
    categoryId: form.categoryId || null,
    urgency: form.urgency === '' ? null : Number(form.urgency),
    safetyFlag: form.safetyFlag || null,
    affectScope: form.affectScope === '' ? null : Number(form.affectScope),
    responseTimeout: Number(form.responseTimeout),
    handleTimeout: Number(form.handleTimeout),
    escalateTo: form.escalateTo || null,
    priorityWeight: Number(form.priorityWeight || 0)
  }
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (editingId.value) {
      await updateSlaConfig(editingId.value, buildPayload())
      ElMessage.success('SLA 规则已更新')
    } else {
      await createSlaConfig(buildPayload())
      ElMessage.success('SLA 规则已新增')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 拦截器已统一提示
  } finally {
    submitting.value = false
  }
}

async function onToggleEnabled(item, enabled) {
  acting.value = item.id
  try {
    await updateSlaEnabled(item.id, enabled ? 1 : 0)
    ElMessage.success(enabled ? '规则已启用' : '规则已禁用')
  } catch {
    // 失败时回读后端状态
  } finally {
    acting.value = ''
    load()
  }
}

async function onDelete(item) {
  try {
    await ElMessageBox.confirm(`确认删除该 SLA 规则？\n${matchText(item)}`, '删除规则', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  try {
    await deleteSlaConfig(item.id)
    ElMessage.success('规则已删除')
    await load()
  } catch {
    // 兜底规则不可删除时后端会返回业务码，拦截器已提示
  }
}
</script>

<template>
  <div class="layout-page">
    <PageHeader title="SLA 配置" description="按四维条件匹配响应与处理时限，超时自动升级">
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增规则</el-button>
    </PageHeader>

    <SectionCard padding="none">
      <AppSkeleton v-if="loading" variant="table" :rows="6" />

      <EmptyState
        v-else-if="failed"
        icon="Warning"
        title="SLA 配置加载失败"
        description="请确认后端服务已启动后重试。"
        action-text="重新加载"
        @action="load"
      />

      <EmptyState
        v-else-if="!rows.length"
        icon="Stopwatch"
        title="还没有 SLA 规则"
        description="至少保留一条全局兜底规则，保证所有工单都有时限约束。"
        action-text="新增规则"
        @action="openCreate"
      />

      <template v-else>
        <el-table :data="rows" style="width: 100%">
          <el-table-column label="匹配条件" min-width="280">
            <template #default="{ row }">
              <span :class="{ 'sla-global': isGlobalFallback(row) }">{{ matchText(row) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="响应超时" width="120">
            <template #default="{ row }">
              <span class="text-mono">{{ formatMinutes(row.responseTimeout) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="处理超时" width="120">
            <template #default="{ row }">
              <span class="text-mono">{{ formatMinutes(row.handleTimeout) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="升级通知" width="120">
            <template #default="{ row }">{{ dictText(ROLE, row.escalateTo, '-') }}</template>
          </el-table-column>

          <el-table-column label="优先级权重" width="110">
            <template #default="{ row }">
              <span class="text-mono">{{ row.priorityWeight ?? 0 }}</span>
            </template>
          </el-table-column>

          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-switch
                :model-value="row.enabled === 1"
                :loading="acting === row.id"
                @change="(value) => onToggleEnabled(row, value)"
              />
            </template>
          </el-table-column>

          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button
                  text
                  type="danger"
                  :disabled="isGlobalFallback(row)"
                  @click="onDelete(row)"
                >
                  删除
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <p class="sla-tip">
          四维条件中留空表示「不限制」。命中维度更多的规则优先；维度相同则比较优先级权重，权重相同取 id 更小者。
          全局兜底规则不可删除。
        </p>
      </template>
    </SectionCard>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑 SLA 规则' : '新增 SLA 规则'"
      width="560px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <p class="sla-form__section">匹配条件（留空表示不限制）</p>

        <div class="grid grid-2">
          <el-form-item label="报修类型">
            <el-select v-model="form.categoryId" placeholder="全部类型" clearable>
              <el-option v-for="item in categoryOptions" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>

          <el-form-item label="紧急程度">
            <el-select v-model="form.urgency" placeholder="全部紧急度" clearable>
              <el-option v-for="item in urgencyOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>

          <el-form-item label="安全隐患">
            <el-select v-model="form.safetyFlag" placeholder="全部隐患" clearable>
              <el-option v-for="item in SAFETY_FLAGS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>

          <el-form-item label="影响范围">
            <el-select v-model="form.affectScope" placeholder="全部范围" clearable>
              <el-option v-for="item in scopeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </div>

        <p class="sla-form__section">时限与升级</p>

        <div class="grid grid-2">
          <el-form-item label="响应超时（分钟）" prop="responseTimeout">
            <el-input-number v-model="form.responseTimeout" :min="1" :max="10080" :step="10" />
          </el-form-item>
          <el-form-item label="处理超时（分钟）" prop="handleTimeout">
            <el-input-number v-model="form.handleTimeout" :min="1" :max="10080" :step="30" />
          </el-form-item>
          <el-form-item label="升级通知角色">
            <el-select v-model="form.escalateTo" placeholder="选择角色" clearable>
              <el-option v-for="item in roleOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="派单优先级权重">
            <el-input-number v-model="form.priorityWeight" :min="0" :max="9999" :step="10" />
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sla-tip {
  padding: var(--space-4) var(--space-5) var(--space-5);
  border-top: 1px solid var(--color-divider);
  font-size: var(--fs-xs);
  color: var(--color-text-3);
  line-height: var(--lh-base);
}

.sla-global {
  color: var(--color-text-2);
}

.sla-form__section {
  margin: var(--space-2) 0 var(--space-3);
  font-size: var(--fs-sm);
  font-weight: var(--fw-medium);
  color: var(--color-text-2);
}

.sla-form__section:first-child {
  margin-top: 0;
}
</style>
