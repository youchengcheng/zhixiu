<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, genFileId } from 'element-plus'
import { Refresh, Search, Upload, UploadFilled } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SectionCard from '@/components/SectionCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import Pagination from '@/components/Pagination.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import {
  createKnowledge,
  deleteKnowledge,
  listKnowledge,
  revectorizeKnowledge,
  testKnowledgeSearch,
  updateKnowledge
} from '@/api/knowledge'
import { getCategoryTree } from '@/api/category'
import { ENABLED_STATUS, KNOWLEDGE_VECTORIZED } from '@/utils/dict'
import { formatDateTime, formatFileSize } from '@/utils/format'

// 知识库管理：文件导入、正文分片向量化及检索测试
const query = reactive({ categoryId: '', keyword: '' })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const rows = ref([])
const loading = ref(true)
const failed = ref(false)
const acting = ref('')
const categoryOptions = ref([])

// 新增 / 编辑
const dialogVisible = ref(false)
const editingId = ref('')
const submitting = ref(false)
const formRef = ref(null)
const uploadRef = ref(null)
const fileList = ref([])
const fileError = ref('')
const uploadProgress = ref(0)
const existingContent = ref('')
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

  const params = { pageNum: page.pageNum, pageSize: page.pageSize }
  if (query.categoryId) params.categoryId = query.categoryId
  if (query.keyword.trim()) params.keyword = query.keyword.trim()

  try {
    const data = await listKnowledge(params)
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
  existingContent.value = ''
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
  existingContent.value = item.content || ''
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
      ElMessage.success('知识已更新，已提交重新分片向量化任务')
    } else {
      await createKnowledge(payload, onUploadProgress)
      ElMessage.success('文件已导入，已提交分片向量化任务')
    }
    dialogVisible.value = false
    await load()
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
    ElMessage.success('已提交重新分片向量化任务')
    await load()
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
  try {
    searchResults.value = (await testKnowledgeSearch({ query: keyword, topK: search.topK })) || []
  } catch {
    searchResults.value = []
  } finally {
    searching.value = false
  }
}

/** 清空检索条件与结果，回到初始态 */
function clearSearch() {
  search.query = ''
  searchResults.value = []
  searched.value = false
}
</script>

<template>
  <div class="layout-page">
    <PageHeader title="知识库" description="上传维修文档，自动分片并建立智能助手检索知识">
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
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
        @action="load"
      />

      <EmptyState
        v-else-if="!rows.length"
        icon="Collection"
        title="还没有知识条目"
        description="把常见故障的排查步骤沉淀进来，智能助手会引用这些内容回答业主。"
        action-text="上传知识"
        @action="openCreate"
      />

      <template v-else>
        <el-table :data="rows" style="width: 100%">
          <el-table-column label="标题" min-width="240">
            <template #default="{ row }">
              <button class="aknow-title" type="button" @click="openEdit(row)">{{ row.title }}</button>
            </template>
          </el-table-column>

          <el-table-column label="关联类型" width="160">
            <template #default="{ row }">{{ row.categoryName || '-' }}</template>
          </el-table-column>

          <el-table-column label="标签" min-width="140">
            <template #default="{ row }">
              <span class="aknow-tags">{{ row.tags || '-' }}</span>
            </template>
          </el-table-column>

          <el-table-column label="向量化" width="110">
            <template #default="{ row }">
              <StatusTag
                :status="row.vectorized ?? 0"
                :dict="KNOWLEDGE_VECTORIZED"
                :dot="false"
                size="small"
              />
            </template>
          </el-table-column>

          <el-table-column label="启用" width="100">
            <template #default="{ row }">
              <StatusTag :status="row.status ?? 1" :dict="ENABLED_STATUS" :dot="false" size="small" />
            </template>
          </el-table-column>

          <el-table-column label="更新时间" width="170">
            <template #default="{ row }">
              <span class="text-mono">{{ formatDateTime(row.updateTime) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="190">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button
                  text
                  type="primary"
                  :loading="acting === row.id"
                  :disabled="row.vectorized === 1"
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
    <SectionCard title="检索测试" description="按相似度返回命中的知识分片，用于检查文档内容能否被准确检索">
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
        v-else-if="searched && !searchResults.length"
        icon="Search"
        title="没有匹配到知识条目"
        description="换个说法试试，或先补充相关知识并完成向量化。"
        size="compact"
        action-text="清空条件"
        @action="clearSearch"
      />

      <ul v-else-if="searchResults.length" class="aknow-result">
        <li v-for="(item, index) in searchResults" :key="`${item.id}-${index}`" class="aknow-result__item">
          <div class="aknow-result__head">
            <span class="aknow-result__title">{{ item.title }}</span>
            <span class="aknow-result__score text-mono">相似度 {{ item.score }}</span>
          </div>
          <p class="aknow-result__meta">
            {{ item.categoryName || '未关联类型' }}
            <span v-if="item.tags">· {{ item.tags }}</span>
          </p>
          <p class="aknow-result__content clamp-2">{{ item.content }}</p>
        </li>
      </ul>
    </SectionCard>

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
              {{ editingId ? '不上传新文件将保留当前正文。' : '保存后自动读取正文并分片入库。' }}
              提取的正文最多 64KB，较长资料请拆分上传；PDF 需包含可复制的文字。
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

        <el-form-item v-if="editingId && existingContent" label="当前正文（只读）">
          <el-input
            :model-value="existingContent"
            type="textarea"
            :rows="5"
            readonly
            aria-label="当前知识正文，只读"
          />
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

.aknow-title {
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

.aknow-title:hover {
  color: var(--color-primary);
}

.aknow-tags {
  font-size: var(--fs-sm);
  color: var(--color-text-2);
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
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-sunken);
}

.aknow-result__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
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
  margin-top: 2px;
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.aknow-result__content {
  margin-top: var(--space-2);
  font-size: var(--fs-sm);
  color: var(--color-text-2);
  line-height: var(--lh-base);
}
</style>
