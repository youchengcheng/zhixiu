<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SectionCard from '@/components/SectionCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import MarkdownView from '@/components/MarkdownView.vue'
import RelativeTime from '@/components/RelativeTime.vue'
import Pagination from '@/components/Pagination.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import {
  createAnnouncement,
  deleteAnnouncement,
  listAnnouncements,
  publishAnnouncement,
  updateAnnouncement
} from '@/api/announcement'
import { ANNOUNCEMENT_STATUS, ANNOUNCEMENT_TARGET, ANNOUNCEMENT_TYPE, dictText } from '@/utils/dict'
import { formatDateTime } from '@/utils/format'

// 公告管理：草稿/发布两态，支持置顶与投放范围；后端公告模块尚未就绪，由 mock 兜底
// 列表接口只提供分页参数（文档 14.1），因此这里不做关键字过滤，避免出现只过滤当前页的假筛选
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const rows = ref([])
const loading = ref(true)
const failed = ref(false)
const acting = ref('')

const dialogVisible = ref(false)
const editingId = ref('')
const submitting = ref(false)
const formRef = ref(null)
const form = reactive({ title: '', content: '', type: 'SYSTEM', targetRole: 'ALL', isTop: 0 })

const rules = {
  title: [
    { required: true, message: '请填写公告标题', trigger: 'blur' },
    { max: 200, message: '标题不超过 200 个字', trigger: 'blur' }
  ],
  content: [{ required: true, message: '请填写公告内容', trigger: 'blur' }]
}

// 预览弹窗
const previewVisible = ref(false)
const previewItem = ref(null)

async function load() {
  loading.value = true
  failed.value = false
  try {
    const data = await listAnnouncements({ pageNum: page.pageNum, pageSize: page.pageSize })
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

/** 前端按标题过滤：列表接口只有分页参数，关键字过滤在此完成 */
const filteredRows = ref([])

function applyFilter() {
  const keyword = query.keyword.trim().toLowerCase()
  filteredRows.value = keyword
    ? rows.value.filter((item) => String(item.title).toLowerCase().includes(keyword))
    : rows.value
}

function onSearch() {
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

function openCreate() {
  editingId.value = ''
  Object.assign(form, { title: '', content: '', type: 'SYSTEM', targetRole: 'ALL', isTop: 0 })
  dialogVisible.value = true
}

function openEdit(item) {
  editingId.value = item.id
  Object.assign(form, {
    title: item.title || '',
    content: item.content || '',
    type: item.type || 'SYSTEM',
    targetRole: item.targetRole || 'ALL',
    isTop: item.isTop ?? 0
  })
  dialogVisible.value = true
}

function openPreview(item) {
  previewItem.value = item
  previewVisible.value = true
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const payload = {
    title: form.title.trim(),
    content: form.content.trim(),
    type: form.type,
    targetRole: form.targetRole,
    isTop: Number(form.isTop)
  }

  submitting.value = true
  try {
    if (editingId.value) {
      await updateAnnouncement(editingId.value, payload)
      ElMessage.success('公告已更新')
    } else {
      await createAnnouncement(payload)
      ElMessage.success('草稿已保存，可在列表中发布')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

async function onTogglePublish(item) {
  acting.value = item.id
  try {
    const nextStatus = item.status === 1 ? 0 : 1
    await publishAnnouncement(item.id, nextStatus)
    ElMessage.success(nextStatus === 1 ? '公告已发布' : '公告已下架')
    await load()
  } catch {
    // 忽略
  } finally {
    acting.value = ''
  }
}

async function onDelete(item) {
  try {
    await ElMessageBox.confirm(`确认删除公告「${item.title}」？`, '删除公告', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  try {
    await deleteAnnouncement(item.id)
    ElMessage.success('公告已删除')
    await load()
  } catch {
    // 忽略
  }
}
</script>

<template>
  <div class="layout-page">
    <PageHeader title="公告管理" description="发布停水检修、社区活动等通知，支持置顶与投放范围">
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建公告</el-button>
    </PageHeader>

    <SectionCard padding="none">
      <div class="aann-filter page-filter">
        <span class="aann-filter__desc">草稿与已发布公告都会显示在列表中，可逐条发布或下架</span>
        <span class="aann-filter__count">共 {{ page.total }} 条</span>
      </div>

      <AppSkeleton v-if="loading" variant="table" :rows="6" />

      <EmptyState
        v-else-if="failed"
        icon="Warning"
        title="公告列表加载失败"
        description="请稍后重试。"
        action-text="重新加载"
        @action="load"
      />

      <EmptyState
        v-else-if="!rows.length"
        icon="Bell"
        title="还没有公告"
        description="新建后先保存为草稿，确认无误再发布。"
        action-text="新建公告"
        @action="openCreate"
      />

      <template v-else>
        <el-table :data="rows" style="width: 100%">
          <el-table-column label="标题" min-width="240">
            <template #default="{ row }">
              <button class="aann-title" type="button" @click="openPreview(row)">
                <span v-if="row.isTop === 1" class="aann-top">置顶</span>
                {{ row.title }}
              </button>
            </template>
          </el-table-column>

          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              <StatusTag :status="row.type" :dict="ANNOUNCEMENT_TYPE" :dot="false" size="small" />
            </template>
          </el-table-column>

          <el-table-column label="投放范围" width="110">
            <template #default="{ row }">
              {{ dictText(ANNOUNCEMENT_TARGET, row.targetRole) }}
            </template>
          </el-table-column>

          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <StatusTag :status="row.status ?? 0" :dict="ANNOUNCEMENT_STATUS" :dot="false" size="small" />
            </template>
          </el-table-column>

          <el-table-column label="发布时间" width="150">
            <template #default="{ row }">
              <RelativeTime :value="row.publishTime" fallback="尚未发布" />
            </template>
          </el-table-column>

          <el-table-column label="阅读" width="80" align="right" class-name="cell--number">
            <template #default="{ row }">
              <span class="text-mono">{{ row.viewCount ?? 0 }}</span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button
                  text
                  type="primary"
                  :loading="acting === row.id"
                  @click="onTogglePublish(row)"
                >
                  {{ row.status === 1 ? '下架' : '发布' }}
                </el-button>
                <el-button text type="danger" @click="onDelete(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="aann-pager page-pager">
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

    <!-- 新建 / 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑公告' : '新建公告'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="200" show-word-limit placeholder="例如「3 栋供水管道计划检修」" />
        </el-form-item>

        <div class="grid grid-3">
          <el-form-item label="类型">
            <el-select v-model="form.type">
              <el-option
                v-for="item in Object.entries(ANNOUNCEMENT_TYPE)"
                :key="item[0]"
                :label="item[1].text"
                :value="item[0]"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="投放范围">
            <el-select v-model="form.targetRole">
              <el-option
                v-for="item in Object.entries(ANNOUNCEMENT_TARGET)"
                :key="item[0]"
                :label="item[1].text"
                :value="item[0]"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="置顶">
            <el-switch v-model="form.isTop" :active-value="1" :inactive-value="0" />
          </el-form-item>
        </div>

        <el-form-item label="内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            maxlength="2000"
            show-word-limit
            placeholder="支持 Markdown：**加粗**、列表、链接等"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewVisible" :title="previewItem?.title || '公告预览'" width="680px">
      <div v-if="previewItem">
        <div class="aann-preview__meta">
          <StatusTag :status="previewItem.type" :dict="ANNOUNCEMENT_TYPE" :dot="false" size="small" />
          <StatusTag :status="previewItem.status ?? 0" :dict="ANNOUNCEMENT_STATUS" :dot="false" size="small" />
          <span>{{ dictText(ANNOUNCEMENT_TARGET, previewItem.targetRole) }}</span>
          <span class="text-mono">{{ formatDateTime(previewItem.publishTime) }}</span>
        </div>
        <!-- 只有正文区域滚动，弹窗标题与元信息保持可见 -->
        <div class="aann-preview__body">
          <MarkdownView :content="previewItem.content || ''" />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.aann-filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-5) var(--space-5) var(--space-4);
  flex-wrap: wrap;
}

.aann-filter__desc {
  font-size: var(--fs-sm);
  color: var(--color-text-3);
}

.aann-filter__count {
  font-size: var(--fs-sm);
  color: var(--color-text-3);
}

.aann-title {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: 0;
  border: none;
  background: transparent;
  font-size: var(--fs-body);
  font-weight: var(--fw-medium);
  color: var(--color-text-1);
  text-align: left;
  cursor: pointer;
  transition: color var(--dur-fast) var(--ease-out);
}

.aann-title:hover {
  color: var(--color-primary);
}

.aann-top {
  flex: 0 0 auto;
  padding: 0 6px;
  border-radius: var(--radius-xs);
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: 11px;
  line-height: 18px;
}

.aann-pager {
  padding: 0 var(--space-5) var(--space-4);
}

.aann-preview__meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--color-divider);
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

/* 正文滚动容器：仅正文区域滚动，弹窗标题与元信息保持固定可见 */
.aann-preview__body {
  max-height: min(60vh, 520px);
  overflow-y: auto;
  padding-right: var(--space-2);
}
</style>
