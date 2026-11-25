<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight, Plus, Refresh } from '@element-plus/icons-vue'
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
const expandedIds = ref(new Set())
let initializedExpansion = false

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
    if (!initializedExpansion && rows.value.length) {
      expandedIds.value.add(rows.value[0].id)
      initializedExpansion = true
    }
  } catch {
    rows.value = []
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)

function toggleGroup(id) {
  if (expandedIds.value.has(id)) expandedIds.value.delete(id)
  else expandedIds.value.add(id)
}

function rowClassName({ row }) {
  return String(row.parentId) === '0' ? 'category-row--primary' : 'category-row--secondary'
}

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
    <PageHeader title="报修类型">
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      <el-button type="primary" :icon="Plus" @click="openForm()">新增一级分类</el-button>
    </PageHeader>

    <SectionCard class="category-surface" padding="none">
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

      <template v-else>
        <el-table
          class="category-table"
          :data="rows"
          row-key="id"
          :tree-props="{ children: 'children' }"
          :row-class-name="rowClassName"
          :indent="36"
          default-expand-all
          style="width: 100%"
        >
          <el-table-column prop="name" label="分类名称" min-width="320">
            <template #default="{ row }">
              <div class="category-name" :class="{ 'is-primary': String(row.parentId) === '0' }">
                <span class="category-name__main" :title="row.name">{{ row.name }}</span>
                <span class="category-name__level">
                  {{ String(row.parentId) === '0' ? '一级分类' : '二级分类' }}
                </span>
                <span v-if="String(row.parentId) === '0'" class="category-name__count">
                  {{ row.children?.length || 0 }} 个子分类
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

        <div class="category-mobile">
          <section v-for="group in rows" :key="group.id" class="category-group surface">
            <h2 class="category-group__heading">
              <button
                class="category-group__toggle"
                type="button"
                :aria-expanded="expandedIds.has(group.id)"
                :aria-controls="`category-children-${group.id}`"
                :aria-label="`${expandedIds.has(group.id) ? '收起' : '展开'}${group.name}的二级分类`"
                @click="toggleGroup(group.id)"
              >
                <span class="category-group__title">
                  <span class="category-group__name">{{ group.name }}</span>
                  <span class="category-name__level">一级分类</span>
                  <span class="category-group__count">{{ group.children?.length || 0 }} 个二级分类</span>
                </span>
                <el-icon :class="{ 'is-expanded': expandedIds.has(group.id) }"><ArrowRight /></el-icon>
              </button>
            </h2>

            <div class="category-group__controls">
              <div class="category-mobile__meta">
                <span>排序 <span class="tabular">{{ group.sort }}</span></span>
                <el-switch
                  :model-value="group.status === 1"
                  :loading="actingId === group.id"
                  inline-prompt
                  active-text="启用"
                  inactive-text="停用"
                  :aria-label="`${group.name}启用状态`"
                  @change="(value) => toggleStatus(group, value)"
                />
              </div>
              <div class="category-mobile__actions">
                <el-button :icon="Plus" @click="openForm(null, group.id)">添加子分类</el-button>
                <el-button text type="primary" @click="openForm(group)">编辑</el-button>
                <el-button text type="danger" :loading="actingId === group.id" @click="remove(group)">删除</el-button>
              </div>
            </div>

            <div v-show="expandedIds.has(group.id)" :id="`category-children-${group.id}`" class="category-group__children">
              <article v-for="child in group.children || []" :key="child.id" class="category-child">
                <div class="category-child__heading">
                  <h3>{{ child.name }}</h3>
                  <span class="category-child__level">二级分类</span>
                </div>
                <div class="category-mobile__meta">
                  <span>排序 <span class="tabular">{{ child.sort }}</span></span>
                  <el-switch
                    :model-value="child.status === 1"
                    :loading="actingId === child.id"
                    inline-prompt
                    active-text="启用"
                    inactive-text="停用"
                    :aria-label="`${child.name}启用状态`"
                    @change="(value) => toggleStatus(child, value)"
                  />
                </div>
                <div class="category-mobile__actions">
                  <el-button text type="primary" @click="openForm(child)">编辑</el-button>
                  <el-button text type="danger" :loading="actingId === child.id" @click="remove(child)">删除</el-button>
                </div>
              </article>
              <p v-if="!group.children?.length" class="category-group__empty">还没有二级分类，点击“添加子分类”创建。</p>
            </div>
          </section>
        </div>
      </template>
    </SectionCard>

    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="min(480px, 100%)" append-to-body>
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
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
  max-width: calc(100% - 60px);
  vertical-align: middle;
}

.category-name__main {
  overflow: hidden;
  color: var(--color-text-1);
  font-weight: var(--fw-regular);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-name__level {
  flex: 0 0 auto;
  padding: 2px 8px;
  border-radius: var(--radius-xs);
  background: var(--color-bg-sunken);
  color: var(--color-text-2);
  font-size: var(--fs-xs);
  line-height: 18px;
}

.category-name.is-primary .category-name__main {
  font-size: var(--fs-h3);
  font-weight: var(--fw-semibold);
}

.category-name.is-primary {
  max-width: calc(100% - 28px);
}

.category-name.is-primary .category-name__level {
  background: var(--color-primary);
  color: var(--color-on-primary);
}

.category-name__count {
  flex: 0 0 auto;
  color: var(--color-text-2);
  font-size: var(--fs-xs);
  white-space: nowrap;
}

:deep(.category-row--primary) {
  --el-table-tr-bg-color: var(--color-bg-page);
}

:deep(.category-row--primary .el-table__expand-icon) {
  color: var(--color-text-1);
}

.category-mobile {
  display: none;
}

.category-actions {
  flex-wrap: nowrap;
}

.category-form__hint {
  margin-left: var(--space-3);
  color: var(--color-text-3);
  font-size: var(--fs-xs);
}

@media (max-width: 767px) {
  .category-table {
    display: none;
  }

  .category-surface {
    border: 0;
    background: transparent;
  }

  .category-mobile {
    display: grid;
    gap: var(--space-5);
  }

  .category-group {
    overflow: hidden;
  }

  .category-group__heading {
    background: var(--color-primary-soft);
  }

  .category-group__toggle {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-4);
    width: 100%;
    min-height: 88px;
    padding: 20px;
    border: 0;
    background: transparent;
    text-align: left;
    cursor: pointer;
  }

  .category-group__title {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: var(--space-2);
    min-width: 0;
  }

  .category-group__name {
    font-size: var(--fs-h2);
    font-weight: var(--fw-semibold);
    overflow-wrap: anywhere;
  }

  .category-group__count {
    width: 100%;
    color: var(--color-text-2);
    font-size: var(--fs-sm);
    font-weight: var(--fw-regular);
  }

  .category-group__toggle > .el-icon {
    flex: 0 0 auto;
    color: var(--color-text-2);
    transition: transform var(--dur-fast) var(--ease-out);
  }

  .category-group__toggle > .el-icon.is-expanded {
    transform: rotate(90deg);
  }

  .category-group__controls {
    padding: var(--space-3) 20px 20px;
  }

  .category-mobile__meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-4);
    min-height: 44px;
    color: var(--color-text-2);
    font-size: var(--fs-sm);
  }

  .category-mobile__meta .tabular {
    margin-left: var(--space-2);
    color: var(--color-text-1);
  }

  .category-mobile__meta :deep(.el-switch) {
    height: 44px;
  }

  .category-mobile__actions {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: var(--space-2);
    margin-top: var(--space-2);
  }

  .category-mobile__actions :deep(.el-button) {
    min-height: 44px;
    margin: 0;
    padding-inline: var(--space-3);
  }

  .category-group__children {
    padding: 0 20px 20px;
  }

  .category-child {
    margin-left: var(--space-2);
    padding: var(--space-4) 0 var(--space-4) var(--space-4);
    border-left: 2px solid var(--color-border);
    border-top: 1px solid var(--color-divider);
  }

  .category-child__heading {
    display: flex;
    align-items: baseline;
    flex-wrap: wrap;
    gap: var(--space-2);
    margin-bottom: var(--space-2);
  }

  .category-child__heading h3 {
    font-size: var(--fs-h3);
    font-weight: var(--fw-medium);
    overflow-wrap: anywhere;
  }

  .category-child__level {
    color: var(--color-text-3);
    font-size: var(--fs-xs);
  }

  .category-group__empty {
    padding-top: var(--space-4);
    border-top: 1px solid var(--color-divider);
    color: var(--color-text-2);
    font-size: var(--fs-sm);
  }

  .category-form__hint {
    width: 100%;
    margin: var(--space-2) 0 0;
  }
}
</style>
