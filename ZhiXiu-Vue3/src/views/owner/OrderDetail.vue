<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import SectionCard from '@/components/SectionCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import UrgencyTag from '@/components/UrgencyTag.vue'
import LetterAvatar from '@/components/LetterAvatar.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import RelativeTime from '@/components/RelativeTime.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import {
  cancelOrder,
  confirmOrder,
  evaluateOrder,
  getOrderDetail,
  listOrderLogs
} from '@/api/ownerOrder'
import { publicFileUrl } from '@/api/file'
import { formatDateTime, untilNow } from '@/utils/format'
import {
  AFFECT_SCOPE,
  ORDER_LOG_ACTION,
  OWNER_CANCELABLE_STATUS,
  ORDER_STATUS,
  dictText,
  slaState
} from '@/utils/dict'

// 工单详情：流转时间线 + 按状态展示可执行动作（取消 / 确认完工 / 评价）
const route = useRoute()
const router = useRouter()

const orderId = route.params.id

const detail = ref(null)
const logs = ref([])
const loading = ref(true)
const failed = ref(false)
const acting = ref(false)

const cancelVisible = ref(false)
const cancelReason = ref('')

const evaluateVisible = ref(false)
const evaluateForm = reactive({
  score: 5,
  speedScore: 0,
  attitudeScore: 0,
  qualityScore: 0,
  content: '',
  images: []
})

const canCancel = computed(() =>
  OWNER_CANCELABLE_STATUS.includes(detail.value?.status || '')
)
const canConfirm = computed(() => detail.value?.status === 'SUBMITTED')
const canEvaluate = computed(() => detail.value?.status === 'COMPLETED')

const imageList = computed(() => (detail.value?.images || []).map((url) => publicFileUrl(url)))
const finishImageList = computed(() =>
  (detail.value?.finishImages || []).map((url) => publicFileUrl(url))
)

const sla = computed(() => slaState(detail.value))
const responseLeft = computed(() => untilNow(detail.value?.slaResponseDeadline))
const handleLeft = computed(() => untilNow(detail.value?.slaHandleDeadline))

async function load() {
  loading.value = true
  failed.value = false

  const [detailResult, logsResult] = await Promise.allSettled([
    getOrderDetail(orderId),
    listOrderLogs(orderId)
  ])

  if (detailResult.status === 'fulfilled') {
    detail.value = detailResult.value
  } else {
    detail.value = null
    failed.value = true
  }

  // 流转记录失败不影响详情主体展示
  logs.value = logsResult.status === 'fulfilled' ? logsResult.value || [] : []
  loading.value = false
}

onMounted(load)

// ---------- 取消工单 ----------
function openCancel() {
  cancelReason.value = ''
  cancelVisible.value = true
}

async function submitCancel() {
  const reason = cancelReason.value.trim()
  if (!reason) {
    ElMessage.warning('请填写取消原因')
    return
  }

  acting.value = true
  try {
    await cancelOrder(orderId, reason)
    ElMessage.success('工单已取消')
    cancelVisible.value = false
    await load()
  } catch {
    // 业务码 2002 等已由拦截器提示
  } finally {
    acting.value = false
  }
}

// ---------- 确认完工 ----------
async function onConfirm() {
  try {
    await ElMessageBox.confirm('确认「已提交完工」的工单处理完成？确认后即可进行评价。', '确认完工', {
      confirmButtonText: '确认',
      cancelButtonText: '再想想',
      type: 'warning'
    })
  } catch {
    return
  }

  acting.value = true
  try {
    await confirmOrder(orderId)
    ElMessage.success('已确认完工')
    await load()
  } catch {
    // 忽略，拦截器已提示
  } finally {
    acting.value = false
  }
}

// ---------- 提交评价 ----------
function openEvaluate() {
  evaluateForm.score = 5
  evaluateForm.speedScore = 0
  evaluateForm.attitudeScore = 0
  evaluateForm.qualityScore = 0
  evaluateForm.content = ''
  evaluateForm.images = []
  evaluateVisible.value = true
}

async function submitEvaluate() {
  if (!evaluateForm.score) {
    ElMessage.warning('请先给出总分')
    return
  }

  const payload = { score: evaluateForm.score }
  if (evaluateForm.speedScore) payload.speedScore = evaluateForm.speedScore
  if (evaluateForm.attitudeScore) payload.attitudeScore = evaluateForm.attitudeScore
  if (evaluateForm.qualityScore) payload.qualityScore = evaluateForm.qualityScore
  if (evaluateForm.content.trim()) payload.content = evaluateForm.content.trim()
  if (evaluateForm.images.length) payload.images = evaluateForm.images.join(',')

  acting.value = true
  try {
    await evaluateOrder(orderId, payload)
    ElMessage.success('评价已提交，感谢反馈')
    evaluateVisible.value = false
    await load()
  } catch {
    // 忽略，拦截器已提示
  } finally {
    acting.value = false
  }
}

function backToList() {
  router.push({ name: 'OwnerOrderList' })
}
</script>

<template>
  <div class="layout-page detail-page">
    <PageHeader v-if="detail" :title="detail.title" :description="detail.orderNo" :back="true">
      <StatusTag :status="detail.status" />
      <el-button v-if="canCancel" :loading="acting" @click="openCancel">取消工单</el-button>
      <el-button v-if="canConfirm" type="primary" :loading="acting" @click="onConfirm">确认完工</el-button>
      <el-button v-if="canEvaluate" type="primary" @click="openEvaluate">去评价</el-button>
    </PageHeader>

    <SectionCard v-if="loading">
      <AppSkeleton variant="detail" :rows="6" />
    </SectionCard>

    <SectionCard v-else-if="failed">
      <EmptyState
        icon="Warning"
        title="工单不存在或加载失败"
        description="它可能已被删除，或者后端服务暂时不可用。"
        action-text="返回列表"
        @action="backToList"
      />
    </SectionCard>

    <div v-else class="order-detail-layout">
      <div class="order-detail-main">
        <!-- 基本信息 -->
        <SectionCard title="报修信息" divided>
          <dl class="detail-grid">
            <div class="detail-grid__item">
              <dt>报修地址</dt>
              <dd>{{ detail.fullAddress || '-' }}</dd>
            </div>
            <div class="detail-grid__item">
              <dt>联系电话</dt>
              <dd class="text-mono">{{ detail.contactPhone || '-' }}</dd>
            </div>
            <div class="detail-grid__item">
              <dt>报修类型</dt>
              <dd>{{ detail.categoryName || '-' }}</dd>
            </div>
            <div class="detail-grid__item">
              <dt>紧急程度</dt>
              <dd><UrgencyTag :urgency="detail.urgency" :safety="detail.safetyFlag" size="small" /></dd>
            </div>
            <div class="detail-grid__item">
              <dt>影响范围</dt>
              <dd>{{ dictText(AFFECT_SCOPE, detail.affectScope) }}</dd>
            </div>
            <div class="detail-grid__item">
              <dt>设备型号</dt>
              <dd>{{ detail.deviceInfo || '-' }}</dd>
            </div>
            <div class="detail-grid__item">
              <dt>提交时间</dt>
              <dd>{{ formatDateTime(detail.createTime) }}</dd>
            </div>
            <div class="detail-grid__item">
              <dt>时限状态</dt>
              <dd><StatusTag :text="sla.text" :tone="sla.tone" :dot="false" size="small" /></dd>
            </div>
          </dl>

          <p v-if="detail.description" class="detail-desc">{{ detail.description }}</p>
        </SectionCard>

        <!-- 现场照片 -->
        <SectionCard v-if="imageList.length || finishImageList.length" title="现场照片">
          <div class="detail-images">
            <div v-if="imageList.length" class="detail-images__group">
              <p class="detail-images__label">报修照片</p>
              <div class="detail-images__list">
                <el-image
                  v-for="(url, index) in imageList"
                  :key="url"
                  class="detail-images__item"
                  :src="url"
                  :preview-src-list="imageList"
                  :initial-index="index"
                  fit="cover"
                  preview-teleported
                  hide-on-click-modal
                />
              </div>
            </div>

            <div v-if="finishImageList.length" class="detail-images__group">
              <p class="detail-images__label">完工照片</p>
              <div class="detail-images__list">
                <el-image
                  v-for="(url, index) in finishImageList"
                  :key="url"
                  class="detail-images__item"
                  :src="url"
                  :preview-src-list="finishImageList"
                  :initial-index="index"
                  fit="cover"
                  preview-teleported
                  hide-on-click-modal
                />
              </div>
            </div>
          </div>
        </SectionCard>

      </div>
      <aside class="order-detail-side">
        <!-- SLA 时限 -->
        <SectionCard title="服务时限" description="查看接单与完成维修的约定时间">
          <div class="grid grid-2">
            <div class="sla-item">
              <p class="sla-item__label">响应时限</p>
              <p class="sla-item__value text-mono">{{ formatDateTime(detail.slaResponseDeadline) }}</p>
              <p v-if="responseLeft" class="sla-item__left" :class="{ 'is-expired': responseLeft.expired }">
                {{ responseLeft.text }}
              </p>
            </div>
            <div class="sla-item">
              <p class="sla-item__label">处理时限</p>
              <p class="sla-item__value text-mono">{{ formatDateTime(detail.slaHandleDeadline) }}</p>
              <p v-if="handleLeft" class="sla-item__left" :class="{ 'is-expired': handleLeft.expired }">
                {{ handleLeft.text }}
              </p>
            </div>
          </div>
        </SectionCard>

        <!-- 维修师傅 -->
        <SectionCard v-if="detail.worker" title="维修师傅">
          <div class="worker-card">
            <LetterAvatar :name="detail.worker.realName" :size="40" />
            <div class="worker-card__info">
              <p class="worker-card__name">{{ detail.worker.realName }}</p>
              <p class="worker-card__meta text-mono">
                {{ detail.worker.phone || '未提供电话' }}
                <span v-if="detail.worker.avgScore">· 评分 {{ detail.worker.avgScore }}</span>
              </p>
            </div>
          </div>
        </SectionCard>

        <!-- 流转记录：放在师傅信息下方，长时间线在卡片内滚动 -->
        <SectionCard title="流转记录" description="每一次状态变更都会写入工单日志">
          <EmptyState
            v-if="!logs.length"
            icon="List"
            title="暂无流转记录"
            size="compact"
            description="工单状态发生变化后会在这里留下时间线。"
            action-text="刷新"
            @action="load"
          />
          <div v-else class="order-log-scroll" tabindex="0" role="region" aria-label="工单流转记录">
            <el-timeline>
              <el-timeline-item
                v-for="log in logs"
                :key="log.id"
                :timestamp="formatDateTime(log.createTime)"
                placement="top"
              >
                <p class="log-item__action">
                  {{ ORDER_LOG_ACTION[log.action] || log.action }}
                </p>
                <p v-if="log.fromStatus || log.toStatus" class="log-item__status">
                  {{ dictText(ORDER_STATUS, log.fromStatus, '-') }}
                  <span class="log-item__arrow">→</span>
                  {{ dictText(ORDER_STATUS, log.toStatus) }}
                </p>
                <p v-if="log.remark" class="log-item__remark">{{ log.remark }}</p>
              </el-timeline-item>
            </el-timeline>
          </div>
        </SectionCard>

      </aside>
    </div>

    <!-- 取消工单弹窗 -->
    <el-dialog v-model="cancelVisible" title="取消工单" width="440px">
      <p class="dialog-tip">取消后工单将终止流转，请说明原因。</p>
      <el-input
        v-model="cancelReason"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        placeholder="例如「问题已自行解决」"
      />
      <template #footer>
        <el-button @click="cancelVisible = false">再想想</el-button>
        <el-button type="danger" :loading="acting" @click="submitCancel">确认取消</el-button>
      </template>
    </el-dialog>

    <!-- 评价弹窗 -->
    <el-dialog v-model="evaluateVisible" title="服务评价" width="520px">
      <el-form label-position="top">
        <el-form-item label="总分">
          <el-rate v-model="evaluateForm.score" :max="5" show-score score-template="{value} 分" />
        </el-form-item>

        <div class="grid grid-3">
          <el-form-item label="响应速度">
            <el-rate v-model="evaluateForm.speedScore" :max="5" />
          </el-form-item>
          <el-form-item label="服务态度">
            <el-rate v-model="evaluateForm.attitudeScore" :max="5" />
          </el-form-item>
          <el-form-item label="维修质量">
            <el-rate v-model="evaluateForm.qualityScore" :max="5" />
          </el-form-item>
        </div>

        <el-form-item label="评价内容">
          <el-input
            v-model="evaluateForm.content"
            type="textarea"
            :rows="3"
            maxlength="300"
            show-word-limit
            placeholder="说说这次维修体验，帮助物业改进服务"
          />
        </el-form-item>

        <el-form-item label="评价图片">
          <ImageUpload v-model="evaluateForm.images" :limit="3" biz-type="evaluate" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="evaluateVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitEvaluate">提交评价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: var(--space-4) var(--space-5);
}

.detail-grid__item dt {
  margin-bottom: var(--space-1);
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.detail-grid__item dd {
  font-size: var(--fs-body);
  color: var(--color-text-1);
}

.detail-desc {
  margin-top: var(--space-5);
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-divider);
  font-size: var(--fs-body);
  line-height: var(--lh-base);
  color: var(--color-text-2);
  white-space: pre-wrap;
}

.detail-images {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.detail-images__label {
  margin-bottom: var(--space-2);
  font-size: var(--fs-sm);
  color: var(--color-text-3);
}

.detail-images__list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.detail-images__item {
  width: 120px;
  height: 120px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-sunken);
}

.sla-item {
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-sunken);
}

.sla-item__label {
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.sla-item__value {
  margin-top: var(--space-1);
  font-size: var(--fs-h3);
  color: var(--color-text-1);
}

.sla-item__left {
  margin-top: var(--space-1);
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.sla-item__left.is-expired {
  color: var(--color-danger-strong);
}

.worker-card {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.worker-card__name {
  font-size: var(--fs-h3);
  font-weight: var(--fw-medium);
  color: var(--color-text-1);
}

.worker-card__meta {
  margin-top: 2px;
  font-size: var(--fs-sm);
  color: var(--color-text-3);
}

.order-log-scroll {
  max-height: clamp(280px, 45dvh, 400px);
  min-height: 0;
  padding: 4px;
  overflow-y: auto;
  scrollbar-gutter: stable;
  overscroll-behavior-y: contain;
  overflow-wrap: anywhere;
}

.log-item__action {
  font-size: var(--fs-body);
  font-weight: var(--fw-medium);
  color: var(--color-text-1);
}

.log-item__status {
  margin-top: var(--space-1);
  font-size: var(--fs-sm);
  color: var(--color-text-3);
}

.log-item__arrow {
  margin: 0 var(--space-1);
  color: var(--color-text-disabled);
}

.log-item__remark {
  margin-top: var(--space-1);
  font-size: var(--fs-sm);
  color: var(--color-text-2);
  line-height: var(--lh-base);
}

.dialog-tip {
  margin-bottom: var(--space-3);
  font-size: var(--fs-sm);
  color: var(--color-text-3);
}
</style>
