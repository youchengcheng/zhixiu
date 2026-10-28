<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import SectionCard from '@/components/SectionCard.vue'
import StatCard from '@/components/StatCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import UrgencyTag from '@/components/UrgencyTag.vue'
import RelativeTime from '@/components/RelativeTime.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getWorkerStatistics, grabOrder, listPendingOrders, updateWorkStatus, listMyWorkerOrders } from '@/api/workerOrder'
import { useUserStore } from '@/stores/user'
import { formatMinutes, formatPercent } from '@/utils/format'

// 师傅工作台：在线状态 + 业务统计 + 待接单与在办工单预览
// 工单相关数据全部走真实后端；师傅未过审时后端会以 1005 拒绝抢单
const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const failed = ref(false)
const switching = ref(false)

const stats = ref(null)
const pending = ref([])
const activeOrders = ref([])

// 在线状态：文档 8.12 只允许 1在线 / 2离线
const online = ref((userStore.workStatus ?? 2) === 1)

const auditPending = ref(userStore.auditStatus === 0)
const denied = ref(userStore.auditStatus === 2)

async function load() {
  loading.value = true
  failed.value = false

  const [statsResult, pendingResult, activeResult] = await Promise.allSettled([
    getWorkerStatistics(),
    listPendingOrders({ pageNum: 1, pageSize: 3 }),
    listMyWorkerOrders({ pageNum: 1, pageSize: 5 })
  ])

  if (statsResult.status === 'fulfilled') {
    stats.value = statsResult.value
  } else {
    stats.value = null
  }

  pending.value = pendingResult.status === 'fulfilled' ? pendingResult.value?.records || [] : []
  activeOrders.value = activeResult.status === 'fulfilled' ? activeResult.value?.records || [] : []

  failed.value = statsResult.status === 'rejected'
  loading.value = false
}

onMounted(load)

async function onToggleOnline(value) {
  switching.value = true
  try {
    await updateWorkStatus(value ? 1 : 2)
    ElMessage.success(value ? '已切换为在线，可接收新工单' : '已切换为离线，不再派单')
    // 同步到登录态，刷新页面后仍保持
    userStore.setUserInfo({ ...(userStore.userInfo || {}), workStatus: value ? 1 : 2 })
  } catch {
    // 失败回滚开关状态
    online.value = !value
  } finally {
    switching.value = false
  }
}

async function onGrab(order) {
  try {
    await grabOrder(order.id)
    ElMessage.success('抢单成功，请尽快联系业主')
    router.push({ name: 'WorkerOrderDetail', params: { id: order.id } })
  } catch {
    // 2003 已被抢 / 2002 状态不允许，均由拦截器提示，这里刷新列表
    load()
  }
}

function goDetail(order) {
  router.push({ name: 'WorkerOrderDetail', params: { id: order.id } })
}
</script>

<template>
  <div class="layout-page worker-workbench">
    <PageHeader title="工作台" description="今日接单与处理概览，可随时切换在线状态">
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </PageHeader>

    <!-- 审核状态提示：未过审无法抢单 -->
    <el-alert
      v-if="auditPending"
      type="warning"
      :closable="false"
      show-icon
      title="资质审核中"
      description="审核通过后才能抢单与接单，请留意管理员的审核结果。"
    />
    <el-alert
      v-if="denied"
      type="error"
      :closable="false"
      show-icon
      title="资质未通过"
      description="当前账号的师傅资质未通过审核，请联系物业管理员核实。"
    />

    <SectionCard v-if="failed && !loading">
      <EmptyState
        icon="Warning"
        title="工作台数据加载失败"
        description="请确认后端服务已启动后重试。"
        action-text="重新加载"
        @action="load"
      />
    </SectionCard>

    <template v-else>
      <!-- 在线状态 -->
      <div class="workbench-status surface">
        <div class="workbench-status__main">
          <span class="workbench-status__dot" :class="{ 'is-online': online }" aria-hidden="true" />
          <div>
            <p class="workbench-status__title">{{ online ? '当前在线，可接收派单' : '当前离线，不接收新派单' }}</p>
            <p class="workbench-status__desc">在办工单请在「我的工单」中继续处理</p>
          </div>
        </div>
        <el-switch
          v-model="online"
          aria-label="接单在线状态"
          :loading="switching"
          :disabled="auditPending || denied"
          active-text="在线"
          inactive-text="离线"
          @change="onToggleOnline"
        />
      </div>

      <AppSkeleton v-if="loading" variant="card" :rows="4" :columns="4" />
      <div v-else class="grid grid-4">
        <StatCard label="累计接单" :value="stats?.totalOrders ?? 0" icon="Tickets" tone="default" hint="历史承接工单总数" />
        <StatCard label="已完工" :value="stats?.finishOrders ?? 0" icon="Finished" tone="success" hint="成功完成的工单" />
        <StatCard
          label="完工率"
          :value="formatPercent(stats?.finishRate)"
          icon="TrendCharts"
          tone="primary"
          hint="已完工 / 累计接单"
        />
        <StatCard
          label="当前负载"
          :value="stats?.currentLoad ?? 0"
          icon="Odometer"
          tone="warning"
          hint="在办工单数量"
        />
      </div>

      <div class="grid workbench-tasks">
        <!-- 待接单预览 -->
        <SectionCard title="待接单" description="按紧急程度与发布时间排序">
          <template #extra>
            <el-button text type="primary" @click="router.push({ name: 'WorkerPending' })">去抢单</el-button>
          </template>

          <AppSkeleton v-if="loading" variant="detail" :rows="3" />
          <EmptyState
            v-else-if="!pending.length"
            icon="Aim"
            title="暂时没有待接单"
            description="有新工单进入待接单池时会显示在这里。"
            size="compact"
            action-text="刷新"
            @action="load"
          />
          <ul v-else class="wb-list">
            <li v-for="order in pending" :key="order.id" class="wb-list__item">
              <div class="wb-list__body">
                <span class="wb-list__title truncate">{{ order.title }}</span>
                <span class="wb-list__meta">
                  <UrgencyTag :urgency="order.urgency" :safety="order.safetyFlag" size="small" />
                  <RelativeTime :value="order.createTime" />
                </span>
              </div>
              <el-button type="primary" size="small" @click="onGrab(order)">抢单</el-button>
            </li>
          </ul>
        </SectionCard>

        <!-- 在办工单预览 -->
        <SectionCard class="workbench-active" title="最近工单" description="继续处理你承接的工单">
          <template #extra>
            <el-button text type="primary" @click="router.push({ name: 'WorkerOrderList' })">查看全部</el-button>
          </template>

          <AppSkeleton v-if="loading" variant="detail" :rows="3" />
          <EmptyState
            v-else-if="!activeOrders.length"
            icon="Tickets"
            title="还没有承接工单"
            description="到「待接单」抢一单，或在派单通知中接受指派。"
            size="compact"
            action-text="去抢单"
            @action="router.push({ name: 'WorkerPending' })"
          />
          <ul v-else class="wb-list">
            <li v-for="order in activeOrders" :key="order.id" class="wb-list__item">
              <button class="wb-list__body wb-list__body--clickable" type="button" @click="goDetail(order)">
                <span class="wb-list__title truncate">{{ order.title }}</span>
                <span class="wb-list__meta">
                  <RelativeTime :value="order.createTime" />
                  <span v-if="order.categoryName">· {{ order.categoryName }}</span>
                </span>
              </button>
              <StatusTag :status="order.status" size="small" />
            </li>
          </ul>
        </SectionCard>
      </div>
    </template>
  </div>
</template>

<style scoped>
.workbench-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-5);
  padding: var(--space-4) var(--space-5);
  flex-wrap: wrap;
  background: var(--color-primary-soft);
  padding-block: var(--space-5);
}

.workbench-status__main {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.workbench-status__dot {
  width: 10px;
  height: 10px;
  border-radius: var(--radius-full);
  background: var(--color-text-disabled);
}

.workbench-status__dot.is-online {
  background: var(--color-success);
}

.workbench-status__title {
  font-size: var(--fs-h3);
  font-weight: var(--fw-medium);
  color: var(--color-text-1);
}

.workbench-status__desc {
  margin-top: 2px;
  font-size: var(--fs-sm);
  color: var(--color-text-3);
}

.wb-list {
  display: flex;
  flex-direction: column;
}

.wb-list__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--color-divider);
}

.wb-list__item:first-child {
  padding-top: 0;
}

.wb-list__item:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.wb-list__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1 1 auto;
}

.wb-list__body--clickable {
  padding: 0;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.wb-list__title {
  font-size: var(--fs-body);
  font-weight: var(--fw-medium);
  color: var(--color-text-1);
}

.wb-list__body--clickable:hover .wb-list__title {
  color: var(--color-primary);
}

.wb-list__meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}
</style>
