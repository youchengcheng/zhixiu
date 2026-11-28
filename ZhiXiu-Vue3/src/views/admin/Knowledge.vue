<script setup>
import { nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, genFileId } from 'element-plus'
import { Refresh, Search, Upload, UploadFilled } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SectionCard from '@/components/SectionCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import Pagination from '@/components/Pagination.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import MarkdownView from '@/components/MarkdownView.vue'
import {
  createKnowledge,
  deleteKnowledge,
  listKnowledge,
  revectorizeKnowledge,
  testKnowledgeSearch,
  updateKnowledge
} from '@/api/knowledge'
import { getCategoryTree } from '@/api/category'
import { KNOWLEDGE_VECTORIZED } from '@/utils/dict'
import { formatDateTime, formatFileSize } from '@/utils/format'

// 知识库管理：文件导入、正文分片向量化及检索测试
const query = reactive({ categoryId: '', keyword: '' })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const rows = ref([])
const loading = ref(true)
const failed = ref(false)
const acting = ref('')
const categoryOptions = ref([])

// 向量化由后端异步消费，提交后先在本地标记为「向量化中」，轮询列表等待状态回写
const vectorizingIds = ref(new Set())
const POLL_INTERVAL = 2000
const POLL_MAX_TICKS = 45
let pollTimer = null
let pollTicks = 0

function isVectorizing(row) {
  return vectorizingIds.value.has(String(row.id))
}

/** 标记已提交向量化任务的条目并开始轮询 */
function markVectorizing(ids) {
  const next = new Set(vectorizingIds.value)
  ids.filter(Boolean).forEach((id) => next.add(String(id)))
  vectorizingIds.value = next
  pollTicks = 0
  if (!pollTimer) pollTimer = window.setInterval(pollVectorized, POLL_INTERVAL)
}

function stopPolling() {
  if (pollTimer) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
  pollTicks = 0
}

async function pollVectorized() {
  pollTicks += 1
  await load(true)
  // 超时后放弃等待，回到后端真实状态，避免一直停留在「向量化中」
  if (pollTicks >= POLL_MAX_TICKS) {
    vectorizingIds.value = new Set()
    stopPolling()
  } else if (!vectorizingIds.value.size) {
    stopPolling()
  }
}

onUnmounted(stopPolling)

// 正文查看：列表展示完整正文，检索展示命中的分片
const previewVisible = ref(false)
const preview = reactive({ title: '', content: '', categoryName: '', tags: '' })

function openPreview(item, searchResult = false) {
  const title = item.title || '知识正文'
  const metadata = searchResult
    ? rows.value.find((row) => String(row.id) === String(item.id))
    : item
  Object.assign(preview, {
    title: searchResult ? `${title}（检索片段）` : title,
    content: item.content || '',
    categoryName: metadata ? metadata.categoryName || '未关联类型' : '未提供',
    tags: metadata ? metadata.tags || '无标签' : '未提供'
  })
  previewVisible.value = true
}

// 新增 / 编辑
const dialogVisible = ref(false)
const editingId = ref('')
const submitting = ref(false)
const formRef = ref(null)
const uploadRef = ref(null)
const fileList = ref([])
const fileError = ref('')
const uploadProgress = ref(0)
const form = reactive({ title: '', categoryId: '', file: null, tags: '' })

const rules = {
  title: [
    { required: true, whitespace: true, message: '请填写标题', trigger: 'blur' },
    { max: 200, message: '标题不超过 200 个字', trigger: 'blur' }
  ],
  file: [{
    validator: (_rule, value, callback) => {
      if (fileError.value) return callback(new Error(fileError.value))
      if (!editingId.value && !value) return callback(new Error('请上传知识文件'))
      callback()
    },
    trigger: 'change'
  }],
  tags: [{ max: 255, message: '标签不超过 255 个字', trigger: 'blur' }]
}

// 检索测试
const search = reactive({ query: '', topK: 3 })
const searching = ref(false)
const searchResults = ref([])
const searched = ref(false)
const searchFailed = ref(false)

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

async function load(silent = false) {
  if (!silent) {
    loading.value = true
    failed.value = false
  }

  const params = { pageNum: page.pageNum, pageSize: page.pageSize }
  if (query.categoryId) params.categoryId = query.categoryId
  if (query.keyword.trim()) params.keyword = query.keyword.trim()

  try {
    const data = await listKnowledge(params)
    rows.value = data?.records || []
    page.total = data?.total || 0
    // 后端回写已向量化后，清除本地「向量化中」标记
    if (vectorizingIds.value.size) {
      const next = new Set(vectorizingIds.value)
      rows.value.forEach((row) => {
        if (Number(row.vectorized) === 1) next.delete(String(row.id))
      })
      vectorizingIds.value = next
    }
  } catch {
    if (!silent) {
      rows.value = []
      page.total = 0
      failed.value = true
    }
  } finally {
    if (!silent) loading.value = false
  }
}

onMounted(async () => {
  await loadCategories()
  await load()
})

function onSearch() {
  page.pageNum = 1
  load()
}

function onReset() {
  Object.assign(query, { categoryId: '', keyword: '' })
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
  Object.assign(form, { title: '', categoryId: '', file: null, tags: '' })
  resetUpload()
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function openEdit(item) {
  editingId.value = item.id
  Object.assign(form, {
    title: item.title || '',
    categoryId: item.categoryId ? String(item.categoryId) : '',
    file: null,
    tags: item.tags || ''
  })
  resetUpload()
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function resetUpload() {
  fileList.value = []
  fileError.value = ''
  uploadProgress.value = 0
}

function onFileChange(uploadFile) {
  const file = uploadFile.raw
  if (!file) return
  const extension = file.name.split('.').pop().toLowerCase()
  fileError.value = ''
  if (!['pdf', 'docx', 'txt', 'md', 'markdown'].includes(extension)) {
    fileError.value = '仅支持 PDF、DOCX、TXT、MD/Markdown 文件'
  } else if (!file.size) {
    fileError.value = '文件为空，请选择包含文字的文件'
  } else if (file.size > 10 * 1024 * 1024) {
    fileError.value = '文件不能超过 10MB，请拆分后上传'
  }
  if (fileError.value) {
    fileList.value = []
    form.file = null
  } else {
    form.file = file
    if (!form.title.trim()) form.title = file.name.replace(/\.[^.]+$/, '').slice(0, 200)
  }
  formRef.value?.validateField('file').catch(() => {})
}

function onFileRemove() {
  form.file = null
  fileError.value = ''
  formRef.value?.validateField('file').catch(() => {})
}

function onFileExceed(files) {
  uploadRef.value?.clearFiles()
  const file = files[0]
  file.uid = genFileId()
  uploadRef.value?.handleStart(file)
}

function onUploadProgress(event) {
  if (event.total) uploadProgress.value = Math.min(100, Math.round(event.loaded * 100 / event.total))
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const payload = {
    title: form.title.trim(),
    file: form.file,
    tags: form.tags.trim()
  }
  if (form.categoryId) payload.categoryId = form.categoryId

  submitting.value = true
  uploadProgress.value = 0
  try {
    if (editingId.value) {
      await updateKnowledge(editingId.value, payload, onUploadProgress)
      markVectorizing([editingId.value])
      ElMessage.success('知识已更新，已提交重新分片向量化任务')
    } else {
      const id = await createKnowledge(payload, onUploadProgress)
      markVectorizing([id])
      ElMessage.success('文件已导入，已提交分片向量化任务')
    }
    dialogVisible.value = false
    await load(true)
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

async function onRevectorize(item) {
  acting.value = item.id
  try {
    await revectorizeKnowledge(item.id)
    markVectorizing([item.id])
    ElMessage.success('已提交重新分片向量化任务，正在处理…')
    await load(true)
  } catch {
    // 忽略
  } finally {
    acting.value = ''
  }
}

async function onDelete(item) {
  try {
    await ElMessageBox.confirm(`确认删除知识「${item.title}」？向量库中的对应向量会同步清理。`, '删除知识', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  try {
    await deleteKnowledge(item.id)
    // 删除后不再等待该条目的向量化结果
    const next = new Set(vectorizingIds.value)
    next.delete(String(item.id))
    vectorizingIds.value = next
    ElMessage.success('知识已删除')
    await load()
  } catch {
    // 忽略
  }
}

async function onTestSearch() {
  const keyword = search.query.trim()
  if (!keyword) {
    ElMessage.warning('请输入检索语句')
    return
  }

  searching.value = true
  searched.value = true
  searchFailed.value = false
  try {
    searchResults.value = (await testKnowledgeSearch({ query: keyword, topK: search.topK })) || []
  } catch {
    searchResults.value = []
    searchFailed.value = true
  } finally {
    searching.value = false
  }
}

/** 清空检索条件与结果，回到初始态 */
function clearSearch() {
  search.query = ''
  searchResults.value = []
  searched.value = false
  searchFailed.value = false
}
</script>

<template>
  <div class="layout-page">
    <PageHeader title="知识库">
      <el-button :icon="Refresh" :loading="loading" @click="load()">刷新</el-button>
      <el-button type="primary" :icon="Upload" @click="openCreate">上传知识</el-button>
    </PageHeader>

    <SectionCard padding="none">
      <div class="aknow-filter page-filter">
        <el-select
          v-model="query.categoryId"
          placeholder="全部类型"
          clearable
          class="aknow-filter__category"
          @change="onSearch"
        >
          <el-option v-for="item in categoryOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="标题 / 正文关键字"
          aria-label="标题 / 正文关键字"
          clearable
          class="aknow-filter__keyword"
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
        title="知识库加载失败"
        description="请稍后重试。"
        action-text="重新加载"
        @action="load()"
      />

      <EmptyState
        v-else-if="!rows.length"
        icon="Collection"
        title="还没有知识条目"
        action-text="上传知识"
        @action="openCreate"
      />

      <template v-else>
        <el-table :data="rows" style="width: 100%">
          <el-table-column label="知识条目" min-width="340">
            <template #default="{ row }">
              <button
                class="aknow-entry record-cell"
                type="button"
                :aria-label="`查看「${row.title}」正文`"
                @click="openPreview(row)"
              >
                <span class="aknow-title record-cell__primary">
                  {{ row.title }}
                </span>
                <span class="record-cell__meta">
                  <span>{{ row.categoryName || '未关联类型' }}</span>
                  <span>{{ row.tags || '无标签' }}</span>
                </span>
              </button>
            </template>
          </el-table-column>

          <el-table-column label="向量化状态" width="130">
            <template #default="{ row }">
              <div class="record-cell__statuses">
                <span class="aknow-vector-state">
                  <Transition name="aknow-state" mode="out-in">
                    <StatusTag
                      :key="isVectorizing(row) ? 'VECTORIZING' : (row.vectorized ?? 0)"
                      :status="isVectorizing(row) ? 'VECTORIZING' : (row.vectorized ?? 0)"
                      :class="{ 'is-vectorizing': isVectorizing(row) }"
                      :dict="KNOWLEDGE_VECTORIZED"
                      :dot="isVectorizing(row)"
                    />
                  </Transition>
                </span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="更新时间" width="170">
            <template #default="{ row }">
              <span class="record-cell__secondary text-mono">{{ formatDateTime(row.updateTime) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
                <!-- 兜底重试：仅未向量化且不在处理中时出现，正常条目只保留编辑/删除 -->
                <el-button
                  v-if="Number(row.vectorized) !== 1 && !isVectorizing(row)"
                  text
                  type="primary"
                  :disabled="acting === row.id"
                  @click="onRevectorize(row)"
                >
                  向量化
                </el-button>
                <el-button text type="danger" @click="onDelete(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="aknow-pager page-pager">
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

    <!-- 检索测试 -->
    <SectionCard title="检索测试">
      <div class="aknow-search">
        <el-input
          v-model="search.query"
          placeholder="例如「空调不制冷」"
          clearable
          class="aknow-search__input"
          @keyup.enter="onTestSearch"
        />
        <el-select v-model="search.topK" class="aknow-search__topk">
          <el-option :value="1" label="Top 1" />
          <el-option :value="3" label="Top 3" />
          <el-option :value="5" label="Top 5" />
        </el-select>
        <el-button type="primary" :loading="searching" @click="onTestSearch">测试检索</el-button>
      </div>

      <AppSkeleton v-if="searching" variant="detail" :rows="3" />

      <EmptyState
        v-else-if="searchFailed"
        icon="Search"
        title="检索暂不可用，请稍后重试"
        size="compact"
      />

      <EmptyState
        v-else-if="searched && !searchResults.length"
        icon="Search"
        title="未找到相关知识"
        size="compact"
        action-text="清空条件"
        @action="clearSearch"
      />

      <ul v-else-if="searchResults.length" class="aknow-result">
        <li v-for="(item, index) in searchResults" :key="`${item.id}-${index}`">
          <button
            class="aknow-result__item"
            type="button"
            :aria-label="`查看「${item.title}」检索片段`"
            @click="openPreview(item, true)"
          >
            <span class="aknow-result__head">
              <span class="aknow-result__title">{{ item.title }}</span>
              <span class="aknow-result__score text-mono">相似度 {{ Number(item.score).toFixed(3) }}</span>
            </span>
            <span v-if="item.categoryName || item.tags" class="aknow-result__meta">
              {{ item.categoryName }}
              <span v-if="item.categoryName && item.tags">·</span>
              {{ item.tags }}
            </span>
            <span class="aknow-result__content clamp-2">{{ item.content }}</span>
          </button>
        </li>
      </ul>
    </SectionCard>

    <!-- 正文查看弹窗 -->
    <el-dialog v-model="previewVisible" :title="preview.title" width="800px" destroy-on-close>
      <template #header="{ titleId, titleClass }">
        <span :id="titleId" :class="titleClass">{{ preview.title }}</span>
        <dl class="aknow-preview-meta">
          <dt>关联类型</dt>
          <dd>{{ preview.categoryName }}</dd>
          <dt>标签</dt>
          <dd>{{ preview.tags }}</dd>
        </dl>
      </template>
      <MarkdownView v-if="preview.content.trim()" class="aknow-preview" :content="preview.content" />
      <EmptyState v-else title="暂无正文" size="compact" />
    </el-dialog>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑知识' : '上传知识文件'"
      width="640px"
      :close-on-click-modal="!submitting"
      :close-on-press-escape="!submitting"
      :show-close="!submitting"
    >
      <el-form ref="formRef" :model="form" :rules="rules" :disabled="submitting" label-position="top">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="200" show-word-limit placeholder="例如「空调不制冷的常见原因与排查」" />
        </el-form-item>

        <div class="grid grid-2">
          <el-form-item label="关联报修类型">
            <el-select v-model="form.categoryId" placeholder="可不关联" clearable>
              <el-option v-for="item in categoryOptions" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="标签" prop="tags">
            <el-input v-model="form.tags" maxlength="255" placeholder="逗号分隔，例如「空调,不制冷,滤网」" />
          </el-form-item>
        </div>

        <el-form-item :label="editingId ? '替换知识文件（选填）' : '知识文件'" prop="file">
          <div class="aknow-upload">
            <el-upload
              ref="uploadRef"
              v-model:file-list="fileList"
              drag
              :auto-upload="false"
              :limit="1"
              :disabled="submitting"
              accept=".pdf,.docx,.txt,.md,.markdown"
              :on-change="onFileChange"
              :on-remove="onFileRemove"
              :on-exceed="onFileExceed"
            >
              <el-icon class="aknow-upload__icon"><UploadFilled /></el-icon>
              <div class="aknow-upload__title">拖拽文件到这里，或点击选择文件</div>
              <div class="aknow-upload__hint">PDF、DOCX、TXT、Markdown · 单个文件不超过 10MB</div>
            </el-upload>
            <p class="aknow-upload__hint">
              <span v-if="editingId">未选择新文件时保留原正文。</span>
              正文限 64KB；长文请拆分，PDF 须含可选中的文字。
            </p>
            <p v-if="form.file" class="aknow-upload__selected" role="status">
              已选择：{{ form.file.name }}（{{ formatFileSize(form.file.size) }}）
            </p>
            <div v-if="submitting && form.file" class="aknow-upload__progress" role="status" aria-live="polite">
              <el-progress :percentage="uploadProgress" :stroke-width="6" />
              <span>{{ uploadProgress === 100 ? '上传完成，正在解析文件…' : '正在上传文件…' }}</span>
            </div>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button :disabled="submitting" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">{{ editingId ? '保存修改' : '上传并保存' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.aknow-upload {
  width: 100%;
}

.aknow-upload__icon {
  margin-bottom: var(--space-2);
  font-size: 32px;
  color: var(--color-primary);
}

.aknow-upload__title {
  font-weight: var(--fw-medium);
  color: var(--color-text-1);
}

.aknow-upload__hint,
.aknow-upload__progress {
  margin-top: var(--space-2);
  color: var(--color-text-2);
  font-size: var(--fs-sm);
  line-height: var(--lh-base);
}

.aknow-upload__selected {
  margin-top: var(--space-2);
  color: var(--color-text-1);
  font-size: var(--fs-sm);
  overflow-wrap: anywhere;
}

.aknow-upload :deep(.el-upload),
.aknow-upload :deep(.el-upload-dragger) {
  width: 100%;
}

/* ---------- 向量化状态：切换时淡入淡出，处理中用呼吸圆点提示 ---------- */
.aknow-vector-state {
  display: inline-flex;
  flex: 0 0 auto;
  /* 固定宽度，切换文案时相邻状态标签不跳动 */
  min-width: 76px;
}

.aknow-vector-state .status-tag {
  width: 100%;
  justify-content: center;
}

.aknow-state-enter-active,
.aknow-state-leave-active {
  transition: opacity var(--dur-fast) var(--ease-out), transform var(--dur-fast) var(--ease-out);
}

.aknow-state-enter-from {
  opacity: 0;
  transform: translateY(3px);
}

.aknow-state-leave-to {
  opacity: 0;
  transform: translateY(-3px);
}

.aknow-vector-state .status-tag.is-vectorizing :deep(.status-tag__dot) {
  animation: aknow-breathe 1.6s var(--ease-in-out) infinite;
}

@keyframes aknow-breathe {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }

  50% {
    opacity: 0.35;
    transform: scale(0.7);
  }
}

@media (prefers-reduced-motion: reduce) {
  .aknow-state-enter-active,
  .aknow-state-leave-active {
    transition-duration: 1ms;
  }

  .aknow-state-enter-from,
  .aknow-state-leave-to {
    transform: none;
  }

  .aknow-vector-state .status-tag.is-vectorizing :deep(.status-tag__dot) {
    animation: none;
  }
}

.aknow-filter {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
  padding: var(--space-5) var(--space-5) var(--space-4);
}

.aknow-filter__category {
  width: 200px;
}

.aknow-filter__keyword {
  width: 240px;
}

.aknow-entry {
  width: 100%;
  padding: 0;
  border: none;
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.aknow-title {
  font-size: var(--fs-body);
  font-weight: var(--fw-medium);
  color: var(--color-text-1);
  transition: color var(--dur-fast) var(--ease-out);
}

.aknow-entry:hover .aknow-title,
.aknow-entry:focus-visible .aknow-title {
  color: var(--color-primary);
}

.aknow-entry:focus-visible,
.aknow-result__item:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 3px;
  border-radius: var(--radius-xs);
}

.aknow-pager {
  padding: 0 var(--space-5) var(--space-4);
}

.aknow-search {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
  flex-wrap: wrap;
}

.aknow-search__input {
  width: 320px;
}

.aknow-search__topk {
  width: 110px;
}

.aknow-result {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.aknow-result__item {
  display: block;
  width: 100%;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-sunken);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease-out);
}

.aknow-result__item:hover {
  border-color: var(--color-primary);
}

.aknow-result__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.aknow-result__title {
  font-size: var(--fs-body);
  font-weight: var(--fw-medium);
  color: var(--color-text-1);
}

.aknow-result__score {
  flex: 0 0 auto;
  padding: 0 6px;
  border-radius: var(--radius-xs);
  background: var(--color-ai-soft);
  color: var(--color-ai-strong);
  font-size: 11px;
  line-height: 18px;
}

.aknow-result__meta {
  display: block;
  margin-top: 2px;
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.aknow-result__content {
  margin-top: var(--space-2);
  font-size: var(--fs-sm);
  color: var(--color-text-2);
  line-height: var(--lh-base);
  overflow-wrap: anywhere;
}

.aknow-preview-meta {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-1) var(--space-3);
  margin-top: var(--space-2);
  max-height: 25dvh;
  overflow-y: auto;
  font-size: var(--fs-sm);
  line-height: var(--lh-base);
}

.aknow-preview-meta dt {
  color: var(--color-text-3);
}

.aknow-preview-meta dd {
  margin: 0;
  color: var(--color-text-2);
  overflow-wrap: anywhere;
}

.aknow-preview {
  max-width: 75ch;
  margin-inline: auto;
}
</style>
