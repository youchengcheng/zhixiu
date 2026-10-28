<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, ChatDotRound, Plus, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SectionCard from '@/components/SectionCard.vue'
import StatCard from '@/components/StatCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import RelativeTime from '@/components/RelativeTime.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import { listMyOrders } from '@/api/ownerOrder'
import { listAnnouncements } from '@/api/announcement'
import { listMyProperties } from '@/api/property'
import { useUserStore } from '@/stores/user'
import { ANNOUNCEMENT_TYPE, dictText, ORDER_ACTIVE_STATUS, ORDER_FINISHED_STATUS } from '@/utils/dict'
import communityImage from '@/assets/auth/community-courtyard.webp'

// 业主首页：工单概览 + 最新公告 + 快捷入口
// 数据来源：工单与房产走真实后端，公告走后端未就绪时的 mock（适配器自动分流）
const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const failed = ref(false)

const orders = ref([])
const orderTotal = ref(0)
const announcements = ref([])
const property = ref(null)

// 概览计数基于首页一次拉取的记录（pageSize=100）在客户端聚合；
// 后端尚未提供业主侧聚合接口，这里不额外发明接口
const stats = computed(() => {
  const list = orders.value
  return [
    { label: '全部工单', value: orderTotal.value, icon: 'Tickets', tone: 'default', hint: '累计提交' },
    {
      label: '进行中',
      value: list.filter((item) => ORDER_ACTIVE_STATUS.includes(item.status)).length,
      icon: 'Stopwatch',
      tone: 'warning',
      hint: '含待接单与处理中'
    },
    {
      label: '待确认完工',
      value: list.filter((item) => item.status === 'SUBMITTED').length,
      icon: 'Finished',
      tone: 'primary',
      hint: '师傅已提交，等你确认'
    },
    {
      label: '已完结',
      value: list.filter((item) => ORDER_FINISHED_STATUS.includes(item.status)).length,
      icon: 'Finished',
      tone: 'success',
      hint: '完工、关闭与取消'
    }
  ]
})

const recentOrders = computed(() => orders.value.slice(0, 5))
const propertyText = computed(() =>
  property.value ? `默认房产 · ${property.value.fullAddress}` : '还没有登记房产，先添加一处再来报修'
)

async function load() {
  loading.value = true
  failed.value = false

  // 三类数据相互独立，用 allSettled 保证任一路失败不影响其余区域展示
  const [orderResult, announcementResult, propertyResult] = await Promise.allSettled([
    listMyOrders({ pageNum: 1, pageSize: 100 }),
    listAnnouncements({ pageNum: 1, pageSize: 4 }),
    listMyProperties()
  ])

  if (orderResult.status === 'fulfilled') {
    orders.value = orderResult.value?.records || []
    orderTotal.value = orderResult.value?.total || 0
  } else {
    orders.value = []
    orderTotal.value = 0
  }

  if (announcementResult.status === 'fulfilled') {
    announcements.value = announcementResult.value?.records || []
  } else {
    announcements.value = []
  }

  if (propertyResult.status === 'fulfilled') {
    const list = propertyResult.value || []
    property.value = list.find((item) => item.isDefault === 1) || list[0] || null
  } else {
    property.value = null
  }

  // 工单是首页主数据，它失败才整页提示重试；公告/房产失败只影响各自区块
  failed.value = orderResult.status === 'rejected'
  loading.value = false
}

onMounted(load)

function goDetail(order) {
  router.push({ name: 'OwnerOrderDetail', params: { id: order.id } })
}
</script>

<template>
  <div class="layout-page owner-home">
    <PageHeader :title="`你好，${userStore.displayName}`" description="你的社区服务，从这里开始。">
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </PageHeader>
    <section class="home-welcome surface" aria-labelledby="home-service-title">
      <div class="home-welcome__copy">
        <h2 id="home-service-title">家的小问题，<br />在这里妥善解决。</h2>
        <p>从提交到完工，每一步进展都看得见。</p>
        <div class="home-welcome__actions">
          <el-button type="primary" :icon="Plus" @click="router.push({ name: 'OwnerOrderCreate' })">提交报修</el-button>
          <el-button :icon="ChatDotRound" @click="router.push({ name: 'OwnerAiAssistant' })">问问智能助手</el-button>
        </div>
      </div>
      <img class="home-welcome__image" :src="communityImage" alt="绿树环绕的社区住宅" width="1000" height="720" />
    </section>

    <!-- 接口失败：保留一个可重试的空状态，而不是空白页 -->
    <SectionCard v-if="failed && !loading">
      <EmptyState
        icon="Warning"
        title="工单数据加载失败"
        description="可能是后端服务未启动，或网络暂时不可用。完成后可点击下方按钮重试。"
        action-text="重新加载"
        @action="load"
      />
    </SectionCard>

    <template v-else>
      <AppSkeleton v-if="loading" variant="card" :rows="4" :columns="4" />
      <div v-else class="grid grid-4 home-metrics">
        <StatCard
          v-for="item in stats"
          :key="item.label"
          :label="item.label"
          :value="item.value"
          :icon="item.icon"
          :tone="item.tone"
          :hint="item.hint"
        />
      </div>

      <div class="grid home-overview">
        <!-- 最近工单 -->
        <SectionCard title="最近工单" description="按提交时间倒序展示最近 5 条" class="home-orders">
          <template #extra>
            <el-button text type="primary" @click="router.push({ name: 'OwnerOrderList' })">
              查看全部
            </el-button>
          </template>

          <div class="home-panel-scroll" role="region" aria-label="最近工单列表" tabindex="0">
            <AppSkeleton v-if="loading" variant="detail" :rows="3" />
            <EmptyState
              v-else-if="!recentOrders.length"
              icon="Tickets"
              title="还没有报修记录"
              description="家里设施出问题时，可以先找智能助手排查，或直接提交报修。"
              action-text="去提交报修"
              @action="router.push({ name: 'OwnerOrderCreate' })"
            />
            <ul v-else class="home-list">
              <li v-for="order in recentOrders" :key="order.id" class="home-list__item">
                <button class="home-list__main" type="button" @click="goDetail(order)">
                  <span class="home-list__title truncate">{{ order.title }}</span>
                  <span class="home-list__meta">
                    <RelativeTime :value="order.createTime" />
                    <span v-if="order.categoryName">· {{ order.categoryName }}</span>
                  </span>
                </button>
                <StatusTag :status="order.status" />
              </li>
            </ul>
          </div>
        </SectionCard>

        <!-- 最新公告 -->
        <div class="home-community">
        <SectionCard title="我的房产" class="home-property">
          <p>{{ propertyText }}</p>
          <el-button text type="primary" @click="router.push({ name: 'OwnerProperty' })">{{ property ? '管理房产' : '登记房产' }} <el-icon><ArrowRight /></el-icon></el-button>
        </SectionCard>
        <SectionCard title="物业公告" description="置顶公告优先展示" class="home-announcements">
          <template #extra>
            <el-button text type="primary" @click="router.push({ name: 'OwnerAnnouncement' })">
              查看全部
            </el-button>
          </template>

          <div class="home-panel-scroll" role="region" aria-label="物业公告列表" tabindex="0">
            <AppSkeleton v-if="loading" variant="detail" :rows="3" />
            <EmptyState
              v-else-if="!announcements.length"
              icon="Bell"
              title="暂无公告"
              description="物业发布停水、检修或活动通知时会出现在这里。"
              action-text="刷新"
              @action="load"
            />
            <ul v-else class="home-list">
              <li v-for="item in announcements" :key="item.id" class="home-list__item">
                <button
                  class="home-list__main"
                  type="button"
                  @click="router.push({ name: 'OwnerAnnouncement' })"
                >
                  <span class="home-list__title truncate">
                    <span v-if="item.isTop === 1" class="home-list__top">置顶</span>
                    {{ item.title }}
                  </span>
                  <span class="home-list__meta">
                    <RelativeTime :value="item.publishTime" fallback="尚未发布" />
                    <span>· {{ dictText(ANNOUNCEMENT_TYPE, item.type) }}</span>
                  </span>
                </button>
              </li>
            </ul>
          </div>
        </SectionCard>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
/* 概览列表：两行式条目，标题 + 元信息 */
.home-list {
  display: flex;
  flex-direction: column;
}

.home-list__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--color-divider);
}

.home-list__item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.home-list__item:first-child {
  padding-top: 0;
}

.home-list__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1 1 auto;
  padding: 0;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.home-list__title {
  font-size: var(--fs-body);
  font-weight: var(--fw-medium);
  color: var(--color-text-1);
  transition: color var(--dur-fast) var(--ease-out);
}

.home-list__main:hover .home-list__title {
  color: var(--color-primary);
}

.home-list__meta {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.home-list__top {
  margin-right: var(--space-1);
  padding: 0 4px;
  border-radius: var(--radius-xs);
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: 11px;
}
</style>
