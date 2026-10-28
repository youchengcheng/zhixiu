<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SectionCard from '@/components/SectionCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import UrgencyTag from '@/components/UrgencyTag.vue'
import LetterAvatar from '@/components/LetterAvatar.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import {
  assignOrder,
  cancelOrderByAdmin,
  getAdminOrderDetail,
  listAdminOrderLogs,
  reassignOrder,
  recommendWorkers
} from '@/api/adminOrder'
import { listWorkers } from '@/api/worker'
import { publicFileUrl } from '@/api/file'
import { formatDateTime, untilNow } from '@/utils/format'
import { AFFECT_SCOPE, ORDER_LOG_ACTION, ORDER_STATUS, dictText, slaState } from '@/utils/dict'

// 工单详情（管理视角）：派单 / 改派 / 取消，并可调用 AI 推荐师傅
const route = useRoute()
const router = useRouter()
const orderId = route.params.id

const detail = ref(null)
const logs = ref([])
const loading = ref(true)
const failed = ref(false)
const acting = ref(false)

const workers = ref([])

// 派单 / 改派共用弹窗，通过 mode 区分
const assignVisible = ref(false)
const assignMode = ref('assign')
const assignForm = reactive({ workerId: '', remark: '' })

const cancelVisible = ref(false)
const cancelReason = ref('')

const recommendVisible = ref(false)
const recommendLoading = ref(false)
const recommends = ref([])

const TERMINAL = ['COMPLETED', 'CLOSED', 'CANCELED']

const status = computed(() => detail.value?.status || '')
const canAssign = computed(() => status.value === 'PENDING')
// 转单申请中同样允许改派：管理员同意转单即把工单改派给新师傅
const canReassign = computed(() =>
  ['ASSIGNED', 'ACCEPTED', 'TRANSFER_PENDING'].includes(status.value)
)
// 当前是否处于转单申请中，用于按钮与弹窗文案
const isTransferPending = computed(() => status.value === 'TRANSFER_PENDING')
const canCancel = computed(() => status.value && !TERMINAL.includes(status.value))

// 该工单是否已经改派过：存在「原状态不是待接单」的派单记录即视为改派
const hasReassigned = computed(() =>
  logs.value.some((log) => log.action === 'ASSIGN' && log.fromStatus && log.fromStatus !== 'PENDING')
)

const imageList = computed(() => (detail.value?.images || []).map((url) => publicFileUrl(url)))
const finishImageList = computed(() =>
  (detail.value?.finishImages || []).map((url) => publicFileUrl(url))
)

const sla = computed(() => slaState(detail.value))
const responseLeft = computed(() => untilNow(detail.value?.slaResponseDeadline))
const handleLeft = computed(() => untilNow(detail.value?.slaHandleDeadline))

/** 师傅下拉：只列出可用（已过审且未被禁用）的师傅，减少误派 */
const workerOptions = computed(() =>
  workers.value.filter((item) => item.auditStatus === 1 && item.workStatus !== 4)
)

async function load() {
  loading.value = true
  failed.value = false

  const [detailResult, logsResult, workerResult] = await Promise.allSettled([
    getAdminOrderDetail(orderId),
    listAdminOrderLogs(orderId),
    listWorkers({ pageNum: 1, pageSize: 100 })
  ])

  if (detailResult.status === 'fulfilled') {
    detail.value = detailResult.value
  } else {
    detail.value = null
    failed.value = true
  }

  logs.value = logsResult.status === 'fulfilled' ? logsResult.value || [] : []
  workers.value = workerResult.status === 'fulfilled' ? workerResult.value?.records || [] : []
  loading.value = false
}

onMounted(load)

// ---------- 派单 / 改派 ----------
// 改派按钮文案：有待处理的转单申请时优先显示「同意转单」，否则区分首次改派与已改派
const reassignLabel = computed(() => {
  if (isTransferPending.value) return '同意转单'
  return hasReassigned.value ? '已改派' : '改派'
})

/** 改派入口：已改派过的工单不再重复改派，只提示；转单审批不受此限制 */
function onReassignClick() {
  // 转单申请优先处理，允许改派给新师傅
  if (!isTransferPending.value && hasReassigned.value) {
    ElMessage.info('工单已改派')
    return
  }
  openAssign('reassign')
}

function openAssign(mode) {
  assignMode.value = mode
  // 改派时默认选中当前受理师傅；转单申请中不能默认原师傅，需强制另选新师傅
  assignForm.workerId = mode === 'reassign' && !isTransferPending.value
    ? detail.value?.worker?.workerId || ''
    : ''
  assignForm.remark = ''
  assignVisible.value = true
}

async function submitAssign() {
  if (!assignForm.workerId) {
    ElMessage.warning('请选择要指派的师傅')
    return
  }
  // 改派必须说明原因（文档 9.4 remark 必填）
  if (assignMode.value === 'reassign' && !assignForm.remark.trim()) {
    ElMessage.warning('请填写改派原因')
    return
  }

  const payload = { workerId: assignForm.workerId }
  if (assignForm.remark.trim()) payload.remark = assignForm.remark.trim()

  acting.value = true
  try {
    if (assignMode.value === 'reassign') {
      await reassignOrder(orderId, payload)
      // 转单申请中改派即视为同意转单，提示更贴合场景
      ElMessage.success(isTransferPending.value ? '已同意转单并改派' : '已改派')
    } else {
      await assignOrder(orderId, payload)
      ElMessage.success('已派单')
    }
    assignVisible.value = false
    recommendVisible.value = false
    await load()
  } catch {
    // 2002 状态不允许等由拦截器提示
  } finally {
    acting.value = false
  }
}

// ---------- AI 推荐 ----------
async function openRecommend() {
  recommendVisible.value = true
  recommendLoading.value = true
  recommends.value = []
  try {
    recommends.value = (await recommendWorkers(orderId)) || []
  } catch {
    recommends.value = []
  } finally {
    recommendLoading.value = false
  }
}

function pickRecommend(item) {
  assignMode.value = canAssign.value ? 'assign' : 'reassign'
  assignForm.workerId = item.workerId
  assignForm.remark = item.reason || ''
  recommendVisible.value = false
  assignVisible.value = true
}

// ---------- 取消 ----------
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

  try {
    await ElMessageBox.confirm('取消后工单将终止流转，确认操作？', '取消工单', {
      confirmButtonText: '确认取消',
      cancelButtonText: '再想想',
      type: 'warning'
    })
  } catch {
    return
  }

  acting.value = true
  try {
    await cancelOrderByAdmin(orderId, reason)
    ElMessage.success('工单已取消')
    cancelVisible.value = false
    await load()
  } catch {
    // 忽略
  } finally {
    acting.value = false
  }
}
</script>

<template>
  <div class="layout-page detail-page">
    <PageHeader v-if="detail" :title="detail.title" :description="detail.orderNo" :back="true">
      <StatusTag :status="detail.status" />
      <el-button
        v-if="(canAssign || canReassign) && (!hasReassigned || isTransferPending)"
        :icon="MagicStick"
        @click="openRecommend"
      >
        AI 推荐师傅
      </el-button>
      <el-button v-if="canAssign" type="primary" :loading="acting" @click="openAssign('assign')">派单</el-button>
      <!-- 已改派过的工单只展示状态，点击给出提示，不再重复改派 -->
      <el-button
        v-if="canReassign"
        :type="hasReassigned && !isTransferPending ? 'default' : 'primary'"
        :loading="acting"
        @click="onReassignClick"
      >
        {{ reassignLabel }}
      </el-button>
      <el-button v-if="canCancel" :loading="acting" @click="openCancel">取消工单</el-button>
    </PageHeader>

    <SectionCard v-if="loading">
      <AppSkeleton variant="detail" :rows="6" />
    </SectionCard>

    <SectionCard v-else-if="failed">
      <EmptyState
        icon="Warning"
        title="工单不存在或加载失败"
        description="请确认后端服务已启动后重试。"
        action-text="返回列表"
        @action="router.push({ name: 'AdminOrderList' })"
      />
    </SectionCard>

    <template v-else>
      <el-alert
        v-if="detail.escalated === 1"
        type="error"
        :closable="false"
        show-icon
        title="该工单已触发 SLA 升级"
        description="响应或处理超时，请优先处理并关注派单与处理进度。"
      />

      <!-- 转单申请中：把师傅的转单原因置顶展示，便于管理员决定改派给谁 -->
      <el-alert
        v-if="isTransferPending"
        type="warning"
        :closable="false"
        show-icon
        title="师傅已申请转单，等待管理员处理"
        :description="detail.transferReason ? `转单原因：${detail.transferReason}` : '未填写转单原因'"
      />

      <SectionCard title="报修信息" divided>
        <dl class="adetail-grid">
          <div class="adetail-grid__item">
            <dt>报修类型</dt>
            <dd>{{ detail.categoryName || '-' }}</dd>
          </div>
          <div class="adetail-grid__item">
            <dt>紧急程度</dt>
            <dd><UrgencyTag :urgency="detail.urgency" :safety="detail.safetyFlag" size="small" /></dd>
          </div>
          <div class="adetail-grid__item">
            <dt>影响范围</dt>
            <dd>{{ dictText(AFFECT_SCOPE, detail.affectScope) }}</dd>
          </div>
          <div class="adetail-grid__item">
            <dt>设备型号</dt>
            <dd>{{ detail.deviceInfo || '-' }}</dd>
          </div>
          <div class="adetail-grid__item">
            <dt>提交时间</dt>
            <dd>{{ formatDateTime(detail.createTime) }}</dd>
          </div>
          <div class="adetail-grid__item">
            <dt>SLA 状态</dt>
            <dd><StatusTag :text="sla.text" :tone="sla.tone" :dot="false" size="small" /></dd>
          </div>
        </dl>

        <p v-if="detail.description" class="adetail-desc">{{ detail.description }}</p>
      </SectionCard>

      <SectionCard v-if="imageList.length || finishImageList.length" title="现场照片">
        <div class="adetail-images">
          <div v-if="imageList.length" class="adetail-images__group">
            <p class="adetail-images__label">报修照片</p>
            <div class="adetail-images__list">
              <el-image
                v-for="(url, index) in imageList"
                :key="url"
                class="adetail-images__item"
                :src="url"
                :preview-src-list="imageList"
                :initial-index="index"
                fit="cover"
                preview-teleported
                hide-on-click-modal
              />
            </div>
          </div>

          <div v-if="finishImageList.length" class="adetail-images__group">
            <p class="adetail-images__label">完工照片</p>
            <div class="adetail-images__list">
              <el-image
                v-for="(url, index) in finishImageList"
                :key="url"
                class="adetail-images__item"
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

      <div class="grid grid-2">
        <!-- 业主与地址 -->
        <SectionCard title="业主信息">
          <dl class="adetail-grid">
            <div class="adetail-grid__item">
              <dt>业主</dt>
              <dd>{{ detail.ownerName || '-' }}</dd>
            </div>
            <div class="adetail-grid__item">
              <dt>联系电话</dt>
              <dd class="text-mono">{{ detail.contactPhone || '-' }}</dd>
            </div>
            <div class="adetail-grid__item">
              <dt>报修地址</dt>
              <dd>{{ detail.fullAddress || '-' }}</dd>
            </div>
          </dl>
        </SectionCard>

        <!-- 指派师傅 -->
        <SectionCard title="指派师傅">
          <div v-if="detail.worker || detail.workerName" class="adetail-worker">
            <LetterAvatar :name="detail.worker?.realName || detail.workerName" :size="40" />
            <div>
              <p class="adetail-worker__name">{{ detail.worker?.realName || detail.workerName }}</p>
              <p class="adetail-worker__meta text-mono">
                {{ detail.worker?.phone || '未提供电话' }}
                <span v-if="detail.worker?.avgScore">· 评分 {{ detail.worker.avgScore }}</span>
              </p>
            </div>
          </div>
          <EmptyState
            v-else
            icon="Tools"
            title="尚未指派师傅"
            description="可使用 AI 推荐或手动选择师傅完成派单。"
            size="compact"
            action-text="刷新"
            @action="load"
          />
        </SectionCard>
      </div>

      <!-- SLA 时限 -->
      <SectionCard title="SLA 时限">
        <div class="grid grid-2">
          <div class="asla-box">
            <p class="asla-box__label">响应时限</p>
            <p class="asla-box__value text-mono">{{ formatDateTime(detail.slaResponseDeadline) }}</p>
            <p v-if="responseLeft" class="asla-box__left" :class="{ 'is-expired': responseLeft.expired }">
              {{ responseLeft.text }}
            </p>
          </div>
          <div class="asla-box">
            <p class="asla-box__label">处理时限</p>
            <p class="asla-box__value text-mono">{{ formatDateTime(detail.slaHandleDeadline) }}</p>
            <p v-if="handleLeft" class="asla-box__left" :class="{ 'is-expired': handleLeft.expired }">
              {{ handleLeft.text }}
            </p>
          </div>
        </div>
      </SectionCard>

      <!-- 流转记录 -->
      <SectionCard title="流转记录">
        <EmptyState
          v-if="!logs.length"
          icon="List"
          title="暂无流转记录"
          size="compact"
          description="状态变化后会在这里留下时间线。"
          action-text="刷新"
          @action="load"
        />
        <el-timeline v-else>
          <el-timeline-item
            v-for="log in logs"
            :key="log.id"
            :timestamp="formatDateTime(log.createTime)"
            placement="top"
          >
            <p class="alog-action">{{ ORDER_LOG_ACTION[log.action] || log.action }}</p>
            <p v-if="log.fromStatus || log.toStatus" class="alog-status">
              {{ dictText(ORDER_STATUS, log.fromStatus, '-') }}
              <span class="alog-arrow">→</span>
              {{ dictText(ORDER_STATUS, log.toStatus) }}
            </p>
            <p v-if="log.remark" class="alog-remark">{{ log.remark }}</p>
          </el-timeline-item>
        </el-timeline>
      </SectionCard>
    </template>

    <!-- 派单 / 改派弹窗 -->
    <el-dialog
      v-model="assignVisible"
      :title="assignMode === 'assign' ? '派单' : (isTransferPending ? '同意转单（改派师傅）' : '改派师傅')"
      width="460px"
    >
      <el-form label-position="top">
        <el-form-item label="指派师傅" required>
          <el-select v-model="assignForm.workerId" placeholder="选择师傅" filterable>
            <el-option
              v-for="item in workerOptions"
              :key="item.id"
              :label="`${item.realName}（${item.workerNo}）负载 ${item.currentLoad ?? 0}`"
              :value="String(item.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="assignMode === 'reassign' ? '改派原因' : '派单备注'" :required="assignMode === 'reassign'">
          <el-input
            v-model="assignForm.remark"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            :placeholder="assignMode === 'reassign' ? '说明改派原因' : '选填，便于师傅了解情况'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitAssign">
          {{ assignMode === 'reassign' ? '确认改派' : '确认派单' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- AI 推荐弹窗 -->
    <el-dialog v-model="recommendVisible" title="智能派单推荐" width="560px">
      <AppSkeleton v-if="recommendLoading" variant="detail" :rows="3" />
      <EmptyState
        v-else-if="!recommends.length"
        icon="MagicStick"
        title="暂无推荐结果"
        description="推荐依赖师傅技能与当前负载，可稍后重试或手动派单。"
        size="compact"
      />
      <ul v-else class="arecommend-list">
        <li v-for="item in recommends" :key="item.workerId" class="arecommend-item">
          <LetterAvatar :name="item.realName" :size="36" />
          <div class="arecommend-item__body">
            <p class="arecommend-item__name">
              {{ item.realName }}
              <span class="arecommend-item__score text-mono">匹配 {{ item.matchScore }}</span>
            </p>
            <p class="arecommend-item__meta">
              {{ item.skillTags || '未填写技能' }} · 当前负载 {{ item.currentLoad ?? 0 }}
            </p>
            <p v-if="item.reason" class="arecommend-item__reason">{{ item.reason }}</p>
          </div>
          <el-button type="primary" size="small" @click="pickRecommend(item)">选他</el-button>
        </li>
      </ul>
    </el-dialog>

    <!-- 取消弹窗 -->
    <el-dialog v-model="cancelVisible" title="取消工单" width="440px">
      <el-input
        v-model="cancelReason"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        placeholder="例如「重复工单」「业主已自行处理」"
      />
      <template #footer>
        <el-button @click="cancelVisible = false">再想想</el-button>
        <el-button type="danger" :loading="acting" @click="submitCancel">确认取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.adetail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--space-4) var(--space-5);
}

.adetail-grid__item dt {
  margin-bottom: var(--space-1);
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.adetail-grid__item dd {
  font-size: var(--fs-body);
  color: var(--color-text-1);
}

.adetail-desc {
  margin-top: var(--space-5);
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-divider);
  font-size: var(--fs-body);
  line-height: var(--lh-base);
  color: var(--color-text-2);
  white-space: pre-wrap;
}

.adetail-images {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.adetail-images__label {
  margin-bottom: var(--space-2);
  font-size: var(--fs-sm);
  color: var(--color-text-3);
}

.adetail-images__list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.adetail-images__item {
  width: 120px;
  height: 120px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-sunken);
}

.adetail-worker {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.adetail-worker__name {
  font-size: var(--fs-h3);
  font-weight: var(--fw-medium);
  color: var(--color-text-1);
}

.adetail-worker__meta {
  margin-top: 2px;
  font-size: var(--fs-sm);
  color: var(--color-text-3);
}

.asla-box {
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-sunken);
}

.asla-box__label {
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.asla-box__value {
  margin-top: var(--space-1);
  font-size: var(--fs-h3);
  color: var(--color-text-1);
}

.asla-box__left {
  margin-top: var(--space-1);
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.asla-box__left.is-expired {
  color: var(--color-danger-strong);
}

.alog-action {
  font-size: var(--fs-body);
  font-weight: var(--fw-medium);
  color: var(--color-text-1);
}

.alog-status {
  margin-top: var(--space-1);
  font-size: var(--fs-sm);
  color: var(--color-text-3);
}

.alog-arrow {
  margin: 0 var(--space-1);
  color: var(--color-text-disabled);
}

.alog-remark {
  margin-top: var(--space-1);
  font-size: var(--fs-sm);
  color: var(--color-text-2);
  line-height: var(--lh-base);
}

.arecommend-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.arecommend-item {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.arecommend-item__body {
  flex: 1 1 auto;
  min-width: 0;
}

.arecommend-item__name {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--fs-body);
  font-weight: var(--fw-medium);
  color: var(--color-text-1);
}

.arecommend-item__score {
  padding: 0 6px;
  border-radius: var(--radius-xs);
  background: var(--color-ai-soft);
  color: var(--color-ai-strong);
  font-size: 11px;
  line-height: 18px;
}

.arecommend-item__meta {
  margin-top: 2px;
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.arecommend-item__reason {
  margin-top: var(--space-1);
  font-size: var(--fs-sm);
  color: var(--color-text-2);
  line-height: var(--lh-base);
}
</style>
