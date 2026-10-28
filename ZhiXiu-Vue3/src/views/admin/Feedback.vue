<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SectionCard from '@/components/SectionCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import DictSelect from '@/components/DictSelect.vue'
import RelativeTime from '@/components/RelativeTime.vue'
import Pagination from '@/components/Pagination.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import { listFeedback, replyFeedback } from '@/api/feedback'
import { FEEDBACK_STATUS, FEEDBACK_TYPE, ROLE, dictText } from '@/utils/dict'
import { formatDateTime } from '@/utils/format'

// 反馈管理：按状态/类型筛选并回复业主或师傅的反馈；后端反馈模块尚未就绪，由 mock 兜底
const query = reactive({ status: '', type: '' })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const rows = ref([])
const loading = ref(true)
const failed = ref(false)

const replyVisible = ref(false)
const replyTarget = ref(null)
const replySubmitting = ref(false)
const replyForm = reactive({ reply: '', status: 2 })

async function load() {
  loading.value = true
  failed.value = false

  const params = { pageNum: page.pageNum, pageSize: page.pageSize }
  if (query.status !== '') params.status = Number(query.status)
  if (query.type) params.type = query.type

  try {
    const data = await listFeedback(params)
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
  Object.assign(query, { status: '', type: '' })
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

function openReply(row) {
  replyTarget.value = row
  replyForm.reply = row.reply || ''
  // 已有回复时保留原状态，否则默认置为「已处理」
  replyForm.status = row.status === 0 || row.status === 1 ? 2 : row.status
  replyVisible.value = true
}

async function submitReply() {
  const text = replyForm.reply.trim()
  if (!text) {
    ElMessage.warning('请填写回复内容')
    return
  }

  replySubmitting.value = true
  try {
    await replyFeedback(replyTarget.value.id, {
      reply: text,
      status: Number(replyForm.status)
    })
    ElMessage.success('回复已发送')
    replyVisible.value = false
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    replySubmitting.value = false
  }
}
</script>

<template>
  <div class="layout-page">
    <PageHeader title="反馈管理" description="处理业主与师傅提交的功能异常、建议与投诉">
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </PageHeader>

    <SectionCard padding="none">
      <div class="afb-filter page-filter">
        <DictSelect
          v-model="query.status"
          :dict="FEEDBACK_STATUS"
          placeholder="全部状态"
          aria-label="全部状态"
          class="afb-filter__select"
          @change="onSearch"
        />
        <DictSelect
          v-model="query.type"
          :dict="FEEDBACK_TYPE"
          placeholder="全部类型"
          aria-label="全部类型"
          class="afb-filter__select"
          @change="onSearch"
        />
        <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
        <el-button :icon="Refresh" @click="onReset">重置</el-button>
      </div>

      <AppSkeleton v-if="loading" variant="table" :rows="6" />

      <EmptyState
        v-else-if="failed"
        icon="Warning"
        title="反馈列表加载失败"
        description="请稍后重试。"
        action-text="重新加载"
        @action="load"
      />

      <EmptyState
        v-else-if="!rows.length"
        icon="ChatLineSquare"
        title="没有符合条件的反馈"
        description="换个筛选条件试试，或重置后查看全部反馈。"
        action-text="重置筛选"
        @action="onReset"
      />

      <template v-else>
        <el-table :data="rows" style="width: 100%">
          <el-table-column label="标题" min-width="200">
            <template #default="{ row }">
              <button class="afb-title" type="button" @click="openReply(row)">{{ row.title }}</button>
            </template>
          </el-table-column>

          <el-table-column label="提交人" width="150">
            <template #default="{ row }">
              <div class="afb-user">
                <span>{{ row.userName || '-' }}</span>
                <span class="afb-user__role">{{ dictText(ROLE, row.userRole, '-') }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              <StatusTag :status="row.type" :dict="FEEDBACK_TYPE" :dot="false" size="small" />
            </template>
          </el-table-column>

          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <StatusTag :status="row.status ?? 0" :dict="FEEDBACK_STATUS" :dot="false" size="small" />
            </template>
          </el-table-column>

          <el-table-column label="回复状态" min-width="180">
            <template #default="{ row }">
              <span v-if="row.reply" class="afb-reply truncate">{{ row.reply }}</span>
              <span v-else class="afb-reply--none">尚未回复</span>
            </template>
          </el-table-column>

          <el-table-column label="提交时间" width="140">
            <template #default="{ row }">
              <RelativeTime :value="row.createTime" />
            </template>
          </el-table-column>

          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button text type="primary" @click="openReply(row)">
                  {{ row.reply ? '查看' : '回复' }}
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="afb-pager page-pager">
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

    <!-- 回复弹窗 -->
    <el-dialog v-model="replyVisible" title="回复反馈" width="560px">
      <template v-if="replyTarget">
        <div class="afb-detail">
          <div class="afb-detail__head">
            <span class="afb-detail__title">{{ replyTarget.title }}</span>
            <StatusTag :status="replyTarget.type" :dict="FEEDBACK_TYPE" :dot="false" size="small" />
          </div>
          <p class="afb-detail__meta">
            {{ replyTarget.userName || '-' }} · {{ dictText(ROLE, replyTarget.userRole, '-') }} ·
            {{ formatDateTime(replyTarget.createTime) }}
            <span v-if="replyTarget.contact"> · 联系方式 {{ replyTarget.contact }}</span>
          </p>
          <p class="afb-detail__content">{{ replyTarget.content }}</p>
          <p v-if="replyTarget.reply" class="afb-detail__history">
            上次回复（{{ formatDateTime(replyTarget.replyTime) }}）：{{ replyTarget.reply }}
          </p>
        </div>

        <el-form label-position="top">
          <el-form-item label="回复内容" required>
            <el-input
              v-model="replyForm.reply"
              type="textarea"
              :rows="4"
              maxlength="500"
              show-word-limit
              placeholder="说明处理结果或后续安排"
            />
          </el-form-item>
          <el-form-item label="处理状态">
            <el-select v-model="replyForm.status" class="afb-detail__status">
              <el-option :value="1" label="处理中" />
              <el-option :value="2" label="已处理" />
              <el-option :value="3" label="关闭" />
            </el-select>
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button type="primary" :loading="replySubmitting" @click="submitReply">发送回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.afb-filter {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
  padding: var(--space-5) var(--space-5) var(--space-4);
}

.afb-filter__select {
  width: 160px;
}

.afb-title {
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

.afb-title:hover {
  color: var(--color-primary);
}

.afb-user {
  display: flex;
  flex-direction: column;
}

.afb-user__role {
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.afb-reply {
  display: block;
  max-width: 100%;
  font-size: var(--fs-sm);
  color: var(--color-text-2);
}

.afb-reply--none {
  font-size: var(--fs-sm);
  color: var(--color-text-disabled);
}

.afb-pager {
  padding: 0 var(--space-5) var(--space-4);
}

.afb-detail {
  margin-bottom: var(--space-4);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-sunken);
}

.afb-detail__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.afb-detail__title {
  font-size: var(--fs-h3);
  font-weight: var(--fw-medium);
  color: var(--color-text-1);
}

.afb-detail__meta {
  margin-top: var(--space-1);
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.afb-detail__content {
  margin-top: var(--space-3);
  font-size: var(--fs-sm);
  color: var(--color-text-2);
  line-height: var(--lh-base);
  white-space: pre-wrap;
}

.afb-detail__history {
  margin-top: var(--space-3);
  padding-top: var(--space-3);
  border-top: 1px dashed var(--color-border);
  font-size: var(--fs-sm);
  color: var(--color-text-3);
  line-height: var(--lh-base);
}

.afb-detail__status {
  width: 160px;
}
</style>
