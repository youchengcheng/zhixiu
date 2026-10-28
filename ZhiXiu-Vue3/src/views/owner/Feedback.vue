<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import SectionCard from '@/components/SectionCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import DictSelect from '@/components/DictSelect.vue'
import RelativeTime from '@/components/RelativeTime.vue'
import Pagination from '@/components/Pagination.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import { illustrations } from '@/assets'
import { createFeedback, listMyFeedback } from '@/api/feedback'
import { FEEDBACK_STATUS, FEEDBACK_TYPE } from '@/utils/dict'

// 问题反馈：左侧提交表单，右侧「我的反馈」列表与处理进度
// 反馈模块后端尚未就绪，由 mock 兜底
const formRef = ref(null)
const submitting = ref(false)
const form = reactive({ type: 'BUG', title: '', content: '', contact: '' })

const rows = ref([])
const total = ref(0)
const loading = ref(true)
const failed = ref(false)
const page = reactive({ pageNum: 1, pageSize: 5 })

const rules = {
  type: [{ required: true, message: '请选择反馈类型', trigger: 'change' }],
  title: [
    { required: true, message: '请填写标题', trigger: 'blur' },
    { max: 100, message: '标题不超过 100 个字', trigger: 'blur' }
  ],
  content: [{ required: true, message: '请填写反馈内容', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  failed.value = false
  try {
    const data = await listMyFeedback({ pageNum: page.pageNum, pageSize: page.pageSize })
    rows.value = data?.records || []
    total.value = data?.total || 0
  } catch {
    rows.value = []
    total.value = 0
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const payload = {
    type: form.type,
    title: form.title.trim(),
    content: form.content.trim()
  }
  if (form.contact.trim()) payload.contact = form.contact.trim()

  submitting.value = true
  try {
    await createFeedback(payload)
    ElMessage.success('反馈已提交，我们会尽快处理')
    form.title = ''
    form.content = ''
    page.pageNum = 1
    await load()
  } catch {
    // 拦截器已统一提示
  } finally {
    submitting.value = false
  }
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
</script>

<template>
  <div class="layout-page">
    <PageHeader title="问题反馈" description="功能异常、优化建议或投诉，都可以在这里提交，物业会给出回复" />

    <div class="feedback-layout">
      <!-- 提交反馈 -->
      <SectionCard title="提交反馈" description="描述越具体，越容易定位与处理">
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item label="反馈类型" prop="type">
            <DictSelect v-model="form.type" :dict="FEEDBACK_TYPE" :clearable="false" />
          </el-form-item>

          <el-form-item label="标题" prop="title">
            <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="一句话概括问题" />
          </el-form-item>

          <el-form-item label="详细描述" prop="content">
            <el-input
              v-model="form.content"
              type="textarea"
              :rows="5"
              maxlength="500"
              show-word-limit
              placeholder="说明发生场景、复现步骤与预期结果"
            />
          </el-form-item>

          <el-form-item label="联系方式">
            <el-input v-model="form.contact" placeholder="选填，便于我们回访" />
          </el-form-item>

          <el-button type="primary" :loading="submitting" @click="onSubmit">提交反馈</el-button>
        </el-form>
      </SectionCard>

      <!-- 我的反馈 -->
      <SectionCard class="feedback-history" title="我的反馈" description="按提交时间倒序">
        <div class="feedback-scroll" tabindex="0" role="region" aria-label="我的反馈列表">
          <AppSkeleton v-if="loading" variant="detail" :rows="4" />

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
            :illustration="illustrations.emptyFeedback"
            title="还没有提交过反馈"
            description="遇到问题或有好建议，左侧填一填就能提交。"
            size="compact"
            action-text="刷新"
            @action="load"
          />

          <ul v-else class="feedback-list">
            <li v-for="item in rows" :key="item.id" class="feedback-item">
              <div class="feedback-item__head">
                <span class="feedback-item__title">{{ item.title }}</span>
                <StatusTag :status="item.status" :dict="FEEDBACK_STATUS" :dot="false" size="small" />
              </div>
              <div class="feedback-item__meta">
                <StatusTag :status="item.type" :dict="FEEDBACK_TYPE" :dot="false" size="small" />
                <RelativeTime :value="item.createTime" />
              </div>
              <p class="feedback-item__content">{{ item.content }}</p>
              <div v-if="item.reply" class="feedback-reply">
                <p class="feedback-reply__label">物业回复 · {{ item.replierName || '物业' }}</p>
                <p class="feedback-reply__content">{{ item.reply }}</p>
              </div>
            </li>
          </ul>
        </div>
        <template v-if="!loading && !failed && rows.length" #footer>
          <Pagination
            :total="total"
            :page="page.pageNum"
            :size="page.pageSize"
            layout="total, sizes, prev, pager, next"
            @update:page="onPageChange"
            @update:size="onSizeChange"
          />
        </template>
      </SectionCard>
    </div>
  </div>
</template>

<style scoped>
.feedback-layout {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: var(--space-5);
  align-items: stretch;
}

.feedback-history {
  /* 由左侧表单决定行高，长列表不参与网格的固有高度计算。 */
  contain: size;
  min-height: 0;
  overflow: hidden;
}

.feedback-history :deep(.section-card__head),
.feedback-history :deep(.section-card__foot) {
  flex: 0 0 auto;
}

.feedback-history :deep(.section-card__body) {
  display: flex;
  flex: 1;
  min-height: 0;
}

.feedback-history :deep(.pagination) { padding-top: 0; }
.feedback-history :deep(.el-pagination) { flex-wrap: wrap; row-gap: var(--space-2); }

.feedback-scroll {
  flex: 1;
  min-width: 0;
  min-height: 0;
  padding: 4px;
  overflow-y: auto;
  overflow-wrap: anywhere;
  scrollbar-gutter: stable;
  overscroll-behavior-y: contain;
}

@media (max-width: 1024px) {
  .feedback-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .feedback-history {
    contain: none;
    height: clamp(440px, 70dvh, 600px);
  }
}

@media (max-width: 640px) {
  .feedback-history :deep(.pagination .el-pagination) {
    width: 100%;
    justify-content: center;
    column-gap: var(--space-1);
  }
}

@media (max-width: 374px) {
  .feedback-history :deep(.el-pagination .btn-prev),
  .feedback-history :deep(.el-pagination .btn-next),
  .feedback-history :deep(.el-pager li) {
    min-width: 28px;
  }
}

.feedback-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.feedback-item {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.feedback-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.feedback-item__title {
  min-width: 0;
  font-size: var(--fs-h3);
  font-weight: var(--fw-medium);
  color: var(--color-text-1);
}

.feedback-item__head :deep(.status-tag) { flex: 0 0 auto; }

.feedback-item__meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.feedback-item__content {
  font-size: var(--fs-sm);
  color: var(--color-text-2);
  line-height: var(--lh-base);
}

.feedback-reply {
  padding: var(--space-3);
  border-radius: var(--radius-sm);
  background: var(--color-bg-sunken);
}

.feedback-reply__label {
  margin-bottom: var(--space-1);
  font-size: var(--fs-xs);
  font-weight: var(--fw-medium);
  color: var(--color-text-2);
}

.feedback-reply__content {
  font-size: var(--fs-sm);
  color: var(--color-text-2);
  line-height: var(--lh-base);
}
</style>
