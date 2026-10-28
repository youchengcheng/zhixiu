<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import SectionCard from '@/components/SectionCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import UrgencyTag from '@/components/UrgencyTag.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import {
  acceptOrder,
  arriveOrder,
  getWorkerOrderDetail,
  grabOrder,
  pauseOrder,
  resumeOrder,
  submitOrder,
  transferOrder
} from '@/api/workerOrder'
import { publicFileUrl } from '@/api/file'
import { formatDateTime, untilNow } from '@/utils/format'
import { AFFECT_SCOPE, dictText, slaState } from '@/utils/dict'

// 处理工单：按状态展示可执行动作（抢单/接受派单/签到/暂停/恢复/转单/提交完工）
// 状态流转由后端状态机控制，前端只负责在合法状态下给出按钮
const route = useRoute()
const router = useRouter()
const orderId = route.params.id

const detail = ref(null)
const loading = ref(true)
const failed = ref(false)
const acting = ref(false)

// 暂停
const pauseVisible = ref(false)
const pauseRemark = ref('')
// 转单
const transferVisible = ref(false)
const transferReason = ref('')
// 提交完工
const submitVisible = ref(false)
const submitForm = reactive({ processNote: '', finishImages: [] })

const status = computed(() => detail.value?.status || '')
const canGrab = computed(() => status.value === 'PENDING')
const canAccept = computed(() => status.value === 'ASSIGNED')
const canArrive = computed(() => status.value === 'ACCEPTED')
const canPause = computed(() => status.value === 'GOING')
const canResume = computed(() => status.value === 'PAUSED')
const canTransfer = computed(() => status.value === 'GOING')

const imageList = computed(() => (detail.value?.images || []).map((url) => publicFileUrl(url)))
const finishImageList = computed(() =>
  (detail.value?.finishImages || []).map((url) => publicFileUrl(url))
)

const sla = computed(() => slaState(detail.value))
const responseLeft = computed(() => untilNow(detail.value?.slaResponseDeadline))
const handleLeft = computed(() => untilNow(detail.value?.slaHandleDeadline))

/** 状态提示语：告诉师傅当前该做什么、在等谁 */
const nextHint = computed(() => {
  switch (status.value) {
    case 'PENDING':
      return '该工单尚未被接单，可以点击「抢单」承接。'
    case 'ASSIGNED':
      return '管理员已指派给你，请点击「接受派单」承接。'
    case 'ACCEPTED':
      return '已接单，到达现场后点击「到场签到」开始计时。'
    case 'GOING':
      return '处理中，可随时暂停（缺配件等）、申请转单或提交完工。'
    case 'PAUSED':
      return '工单已暂停，条件具备后点击「恢复工单」继续处理。'
    case 'TRANSFER_PENDING':
      return '转单申请已提交，等待管理员处理。'
    case 'SUBMITTED':
      return '已提交完工，等待业主确认。'
    case 'COMPLETED':
      return '业主已确认完工，等待评价。'
    default:
      return '该工单已结束，仅可查看记录。'
  }
})

async function load() {
  loading.value = true
  failed.value = false

  // 师傅侧没有流转记录接口（文档第八章未定义），只取工单详情
  try {
    detail.value = await getWorkerOrderDetail(orderId)
  } catch {
    detail.value = null
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)

// ---------- 抢单 / 接受派单 ----------
async function onGrab() {
  acting.value = true
  try {
    await grabOrder(orderId)
    ElMessage.success('抢单成功，请尽快联系业主确认上门时间')
    await load()
  } catch {
    // 2003 已被抢 / 2002 状态不允许，拦截器已提示
    load()
  } finally {
    acting.value = false
  }
}

async function onAccept() {
  acting.value = true
  try {
    await acceptOrder(orderId)
    ElMessage.success('已接受派单')
    await load()
  } catch {
    // 忽略
  } finally {
    acting.value = false
  }
}

/** 尽量取一次定位随签到上报；浏览器不支持或用户拒绝时留空 */
function getPosition() {
  return new Promise((resolve) => {
    if (!navigator.geolocation) {
      resolve({})
      return
    }
    navigator.geolocation.getCurrentPosition(
      (position) =>
        resolve({
          longitude: Number(position.coords.longitude.toFixed(6)),
          latitude: Number(position.coords.latitude.toFixed(6))
        }),
      () => resolve({}),
      { timeout: 3000, maximumAge: 60000 }
    )
  })
}

// ---------- 到场签到 ----------
async function onArrive() {
  acting.value = true
  try {
    const coords = await getPosition()
    await arriveOrder(orderId, coords)
    ElMessage.success('已签到，工单进入处理中')
    await load()
  } catch {
    // 忽略
  } finally {
    acting.value = false
  }
}

// ---------- 暂停 / 恢复 ----------
function openPause() {
  pauseRemark.value = ''
  pauseVisible.value = true
}

async function submitPause() {
  const remark = pauseRemark.value.trim()
  if (!remark) {
    ElMessage.warning('请填写暂停原因')
    return
  }

  acting.value = true
  try {
    await pauseOrder(orderId, remark)
    ElMessage.success('工单已暂停')
    pauseVisible.value = false
    await load()
  } catch {
    // 忽略
  } finally {
    acting.value = false
  }
}

async function onResume() {
  acting.value = true
  try {
    await resumeOrder(orderId)
    ElMessage.success('已恢复处理')
    await load()
  } catch {
    // 忽略
  } finally {
    acting.value = false
  }
}

// ---------- 申请转单 ----------
function openTransfer() {
  transferReason.value = ''
  transferVisible.value = true
}

async function submitTransfer() {
  const reason = transferReason.value.trim()
  if (!reason) {
    ElMessage.warning('请填写转单原因')
    return
  }

  try {
    await ElMessageBox.confirm('转单需管理员审批，审批期间工单将挂起。确认提交申请？', '申请转单', {
      confirmButtonText: '提交申请',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  acting.value = true
  try {
    await transferOrder(orderId, reason)
    ElMessage.success('转单申请已提交，等待管理员处理')
    transferVisible.value = false
    await load()
  } catch {
    // 忽略
  } finally {
    acting.value = false
  }
}

// ---------- 提交完工 ----------
function openSubmit() {
  submitForm.processNote = ''
  submitForm.finishImages = []
  submitVisible.value = true
}

async function submitFinish() {
  const note = submitForm.processNote.trim()
  if (!note) {
    ElMessage.warning('请填写处理过程记录')
    return
  }
  if (!submitForm.finishImages.length) {
    ElMessage.warning('请至少上传 1 张完工照片')
    return
  }

  acting.value = true
  try {
    await submitOrder(orderId, {
      processNote: note,
      finishImages: submitForm.finishImages.join(',')
    })
    ElMessage.success('已提交完工，等待业主确认')
    submitVisible.value = false
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
      <el-button v-if="canGrab" type="primary" :loading="acting" @click="onGrab">抢单</el-button>
      <el-button v-if="canAccept" type="primary" :loading="acting" @click="onAccept">接受派单</el-button>
      <el-button v-if="canArrive" type="primary" :loading="acting" @click="onArrive">到场签到</el-button>
      <el-button v-if="canPause" :loading="acting" @click="openPause">暂停</el-button>
      <el-button v-if="canResume" type="primary" :loading="acting" @click="onResume">恢复工单</el-button>
      <el-button v-if="canTransfer" :loading="acting" @click="openTransfer">申请转单</el-button>
      <el-button v-if="canPause" type="primary" :loading="acting" @click="openSubmit">提交完工</el-button>
    </PageHeader>

    <SectionCard v-if="loading">
      <AppSkeleton variant="detail" :rows="6" />
    </SectionCard>

    <SectionCard v-else-if="failed">
      <EmptyState
        icon="Warning"
        title="工单不存在或加载失败"
        description="它可能已被删除，或者不属于你的接单范围。"
        action-text="返回列表"
        @action="router.push({ name: 'WorkerOrderList' })"
      />
    </SectionCard>

    <template v-else>
      <!-- 当前该做什么 -->
      <el-alert :title="nextHint" type="info" :closable="false" show-icon />

      <SectionCard title="报修信息" divided>
        <dl class="worder-grid">
          <div class="worder-grid__item">
            <dt>报修类型</dt>
            <dd>{{ detail.categoryName || '-' }}</dd>
          </div>
          <div class="worder-grid__item">
            <dt>紧急程度</dt>
            <dd><UrgencyTag :urgency="detail.urgency" :safety="detail.safetyFlag" size="small" /></dd>
          </div>
          <div class="worder-grid__item">
            <dt>影响范围</dt>
            <dd>{{ dictText(AFFECT_SCOPE, detail.affectScope) }}</dd>
          </div>
          <div class="worder-grid__item">
            <dt>设备型号</dt>
            <dd>{{ detail.deviceInfo || '-' }}</dd>
          </div>
          <div class="worder-grid__item">
            <dt>提交时间</dt>
            <dd>{{ formatDateTime(detail.createTime) }}</dd>
          </div>
          <div class="worder-grid__item">
            <dt>SLA 状态</dt>
            <dd><StatusTag :text="sla.text" :tone="sla.tone" :dot="false" size="small" /></dd>
          </div>
        </dl>

        <p v-if="detail.description" class="worder-desc">{{ detail.description }}</p>
      </SectionCard>

      <!-- 业主与地址：使用工单详情返回的联系人电话和房产地址 -->
      <SectionCard
        v-if="detail.ownerName || detail.contactPhone || detail.fullAddress"
        title="业主信息"
      >
        <dl class="worder-grid">
          <div v-if="detail.ownerName" class="worder-grid__item">
            <dt>业主</dt>
            <dd>{{ detail.ownerName }}</dd>
          </div>
          <div v-if="detail.contactPhone" class="worder-grid__item">
            <dt>联系电话</dt>
            <dd class="text-mono">{{ detail.contactPhone }}</dd>
          </div>
          <div v-if="detail.fullAddress" class="worder-grid__item">
            <dt>上门地址</dt>
            <dd>{{ detail.fullAddress }}</dd>
          </div>
        </dl>
      </SectionCard>

      <!-- 报修照片 -->
      <SectionCard v-if="imageList.length" title="报修照片">
        <div class="worder-images">
          <el-image
            v-for="(url, index) in imageList"
            :key="url"
            class="worder-images__item"
            :src="url"
            :preview-src-list="imageList"
            :initial-index="index"
            fit="cover"
            preview-teleported
            hide-on-click-modal
          />
        </div>
      </SectionCard>

      <!-- 完工记录 -->
      <SectionCard v-if="detail.processNote || finishImageList.length" title="完工记录">
        <p v-if="detail.processNote" class="worder-desc">{{ detail.processNote }}</p>
        <div v-if="finishImageList.length" class="worder-images">
          <el-image
            v-for="(url, index) in finishImageList"
            :key="url"
            class="worder-images__item"
            :src="url"
            :preview-src-list="finishImageList"
            :initial-index="index"
            fit="cover"
            preview-teleported
            hide-on-click-modal
          />
        </div>
      </SectionCard>

      <!-- SLA 时限 -->
      <SectionCard title="SLA 时限" description="超时未达标会触发升级并通知管理员">
        <div class="grid grid-2">
          <div class="sla-box">
            <p class="sla-box__label">响应时限</p>
            <p class="sla-box__value text-mono">{{ formatDateTime(detail.slaResponseDeadline) }}</p>
            <p v-if="responseLeft" class="sla-box__left" :class="{ 'is-expired': responseLeft.expired }">
              {{ responseLeft.text }}
            </p>
          </div>
          <div class="sla-box">
            <p class="sla-box__label">处理时限</p>
            <p class="sla-box__value text-mono">{{ formatDateTime(detail.slaHandleDeadline) }}</p>
            <p v-if="handleLeft" class="sla-box__left" :class="{ 'is-expired': handleLeft.expired }">
              {{ handleLeft.text }}
            </p>
          </div>
        </div>
      </SectionCard>
    </template>

    <!-- 暂停弹窗 -->
    <el-dialog v-model="pauseVisible" title="暂停工单" width="440px">
      <el-input
        v-model="pauseRemark"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        placeholder="例如「缺少配件，需等供应商发货」"
      />
      <template #footer>
        <el-button @click="pauseVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitPause">确认暂停</el-button>
      </template>
    </el-dialog>

    <!-- 转单弹窗 -->
    <el-dialog v-model="transferVisible" title="申请转单" width="440px">
      <el-input
        v-model="transferReason"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        placeholder="例如「该类型不在我的技能范围，建议转给水电师傅」"
      />
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitTransfer">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 提交完工弹窗 -->
    <el-dialog v-model="submitVisible" title="提交完工" width="520px">
      <el-form label-position="top">
        <el-form-item label="处理过程记录" required>
          <el-input
            v-model="submitForm.processNote"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="说明故障原因与处理方式，便于业主确认与后续追溯"
          />
        </el-form-item>
        <el-form-item label="完工照片" required>
          <ImageUpload v-model="submitForm.finishImages" :limit="3" biz-type="order" tip="至少 1 张，用于业主确认完工" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="submitVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitFinish">提交完工</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.worder-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: var(--space-4) var(--space-5);
}

.worder-grid__item dt {
  margin-bottom: var(--space-1);
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.worder-grid__item dd {
  font-size: var(--fs-body);
  color: var(--color-text-1);
}

.worder-desc {
  margin-top: var(--space-5);
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-divider);
  font-size: var(--fs-body);
  line-height: var(--lh-base);
  color: var(--color-text-2);
  white-space: pre-wrap;
}

.worder-images {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.worder-images__item {
  width: 120px;
  height: 120px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-sunken);
}

.sla-box {
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-sunken);
}

.sla-box__label {
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.sla-box__value {
  margin-top: var(--space-1);
  font-size: var(--fs-h3);
  color: var(--color-text-1);
}

.sla-box__left {
  margin-top: var(--space-1);
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.sla-box__left.is-expired {
  color: var(--color-danger-strong);
}
</style>
