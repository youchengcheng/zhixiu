<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SectionCard from '@/components/SectionCard.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import {
  createCategory,
  deleteCategory,
  getAdminCategoryTree,
  updateCategory,
  updateCategoryStatus
} from '@/api/category'

const rows = ref([])
const loading = ref(false)
const failed = ref(false)
const actingId = ref('')

const drawerVisible = ref(false)
const formRef = ref(null)
const submitting = ref(false)
const editingId = ref('')
const form = reactive({ name: '', parentId: '0', sort: 0, icon: '' })

const primaryOptions = computed(() => rows.value.map((item) => ({ id: item.id, name: item.name })))
const isPrimary = computed(() => String(form.parentId) === '0')
const drawerTitle = computed(() => {
  if (editingId.value) return isPrimary.value ? '编辑一级分类' : '编辑二级分类'
  return isPrimary.value ? '新增一级分类' : '新增二级分类'
})

const rules = {
  name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { max: 50, message: '分类名称不能超过 50 字', trigger: 'blur' }
  ],
  parentId: [{ required: true, message: '请选择上级分类', trigger: 'change' }],
  sort: [{ required: true, message: '请输入排序值', trigger: 'change' }]
}

async function load() {
  loading.value = true
  failed.value = false
  try {
    rows.value = (await getAdminCategoryTree()) || []
  } catch {
    rows.value = []
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function openForm(item = null, parentId = '0') {
  editingId.value = item?.id || ''
  Object.assign(form, {
    name: item?.name || '',
    parentId: String(item?.parentId ?? parentId),
    sort: Number(item?.sort ?? 0),
    icon: item?.icon || ''
  })
  drawerVisible.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const payload = {
    name: form.name.trim(),
    parentId: Number(form.parentId),
    sort: Number(form.sort),
    icon: form.icon.trim() || null
  }

  submitting.value = true
  try {
    if (editingId.value) {
      await updateCategory(editingId.value, payload)
      ElMessage.success('分类已更新')
    } else {
      await createCategory(payload)
      ElMessage.success('分类已新增')
    }
    drawerVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(item, enabled) {
  actingId.value = item.id
  try {
    await updateCategoryStatus(item.id, enabled ? 1 : 0)
    ElMessage.success(enabled ? '分类已启用' : '分类已停用')
  } finally {
    actingId.value = ''
    await load()
  }
}

async function remove(item) {
  try {
    await ElMessageBox.confirm(
      `确认删除「${item.name}」？已被工单或 SLA 引用的分类不能删除。`,
      '删除报修类型',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }

  actingId.value = item.id
  try {
    await deleteCategory(item.id)
    ElMessage.success('分类已删除')
    await load()
  } finally {
    actingId.value = ''
  }
}
</script>

<template>
  <div class="layout-page category-page">
    <PageHeader title="报修类型" description="维护新建报修时可选的两级分类，停用不会影响历史工单显示">
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      <el-button type="primary" :icon="Plus" @click="openForm()">新增一级分类</el-button>
    </PageHeader>

    <SectionCard padding="none">
      <AppSkeleton v-if="loading" variant="table" :rows="7" />

      <EmptyState
        v-else-if="failed"
        icon="Warning"
        title="报修类型加载失败"
        description="请确认后端服务已启动后重试。"
        action-text="重新加载"
        @action="load"
      />

      <EmptyState
        v-else-if="!rows.length"
        icon="Collection"
        title="还没有报修类型"
        description="先新增一级分类，再为它添加具体的二级分类。"
        action-text="新增一级分类"
        @action="openForm()"
      />

      <el-table
        v-else
        :data="rows"
        row-key="id"
        :tree-props="{ children: 'children' }"
        default-expand-all
        style="width: 100%"
      >
        <el-table-column prop="name" label="分类名称" min-width="240">
          <template #default="{ row }">
            <div class="category-name">
              <span class="category-name__main">{{ row.name }}</span>
              <span class="category-name__level">
                {{ String(row.parentId) === '0' ? '一级' : '二级' }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="100" align="right" />
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              :loading="actingId === row.id"
              inline-prompt
              active-text="启用"
              inactive-text="停用"
              :aria-label="`${row.name}启用状态`"
              @change="(value) => toggleStatus(row, value)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260">
          <template #default="{ row }">
            <div class="table-actions category-actions">
              <el-button
                v-if="String(row.parentId) === '0'"
                text
                type="primary"
                @click="openForm(null, row.id)"
              >
                添加子分类
              </el-button>
              <el-button text type="primary" @click="openForm(row)">编辑</el-button>
              <el-button
                text
                type="danger"
                :loading="actingId === row.id"
                @click="remove(row)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </SectionCard>

    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="480px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="form.name" maxlength="50" show-word-limit placeholder="请输入分类名称" />
        </el-form-item>

        <el-form-item v-if="!isPrimary" label="上级分类" prop="parentId">
          <el-select v-model="form.parentId" placeholder="请选择一级分类" style="width: 100%">
            <el-option
              v-for="item in primaryOptions"
              :key="item.id"
              :label="item.name"
              :value="String(item.id)"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="排序值" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="9999" :step="1" controls-position="right" />
          <span class="category-form__hint">数值越小，同级中越靠前</span>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button :disabled="submitting" @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.category-name {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.category-name__main {
  overflow: hidden;
  color: var(--color-text-1);
  font-weight: var(--fw-medium);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-name__level {
  flex: 0 0 auto;
  padding: 1px 6px;
  border-radius: var(--radius-xs);
  background: var(--color-bg-sunken);
  color: var(--color-text-3);
  font-size: 11px;
  line-height: 18px;
}

.category-actions {
  flex-wrap: nowrap;
}

.category-form__hint {
  margin-left: var(--space-3);
  color: var(--color-text-3);
  font-size: var(--fs-xs);
}
</style>
