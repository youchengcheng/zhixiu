<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import SectionCard from '@/components/SectionCard.vue'
import UrgencyTag from '@/components/UrgencyTag.vue'
import RelativeTime from '@/components/RelativeTime.vue'
import Pagination from '@/components/Pagination.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import { illustrations } from '@/assets'
import { grabOrder, listPendingOrders } from '@/api/workerOrder'
import { getCategoryTree } from '@/api/category'
import { AFFECT_SCOPE, dictText } from '@/utils/dict'
import { useUserStore } from '@/stores/user'

// 待接单池：按紧急程度 + 时间排序，抢单走 Redisson 分布式锁（并发被抢返回 2003）
const router = useRouter()
const userStore = useUserStore()

const rows = ref([])
const total = ref(0)
const loading = ref(true)
const failed = ref(false)
const grabbing = ref('')
const page = ref({ pageNum: 1, pageSize: 10 })
const categoryId = ref('')
const categoryOptions = ref([])

const auditPending = computed(() => userStore.auditStatus === 0)
const denied = computed(() => userStore.auditStatus === 2)

async function loadCategories() {
  try {
    const tree = (await getCategoryTree()) || []
    // 只把二级类型作为筛选项，一级分类本身不作为工单类型
    categoryOptions.value = tree.flatMap((parent) =>
      (parent.children || []).map((child) => ({
        id: child.id,
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

  const params = { pageNum: page.value.pageNum, pageSize: page.value.pageSize }
  if (categoryId.value) params.categoryId = categoryId.value

  try {
    const data = await listPendingOrders(params)
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

onMounted(async () => {
  await loadCategories()
  await load()
})

function onFilterChange() {
  page.value.pageNum = 1
  load()
}

async function onGrab(order) {
  grabbing.value = order.id
  try {
    await grabOrder(order.id)
    ElMessage.success('抢单成功，请尽快联系业主确认上门时间')
    router.push({ name: 'WorkerOrderDetail', params: { id: order.id } })
  } catch {
    // 2003 已被抢 / 2002 状态不允许 / 1005 未过审，拦截器已提示，刷新池子
    load()
  } finally {
    grabbing.value = ''
  }
}

function onPageChange(value) {
  page.value.pageNum = value
  load()
}

function onSizeChange(value) {
  page.value.pageSize = value
  page.value.pageNum = 1
  load()
}
</script>

<template>
  <div class="layout-page worker-pending">
    <PageHeader title="待接单" description="按紧急程度与发布时间排序，先抢先得">
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </PageHeader>

    <el-alert
      v-if="auditPending || denied"
      type="warning"
      :closable="false"
      show-icon
      title="当前账号不可抢单"
      :description="auditPending ? '资质审核通过后才能抢单。' : '资质未通过审核，请联系物业管理员。'"
    />

    <SectionCard padding="none">
      <div class="pending-filter page-filter">
        <el-select
          v-model="categoryId"
          placeholder="全部报修类型"
          aria-label="筛选报修类型"
          clearable
          class="pending-filter__select"
          @change="onFilterChange"
        >
          <el-option v-for="item in categoryOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <span class="pending-filter__count">
          共 <strong class="text-mono">{{ total }}</strong> 条待接单
        </span>
      </div>

      <AppSkeleton v-if="loading" variant="card" :rows="3" :columns="3" />

      <EmptyState
        v-else-if="failed"
        icon="Warning"
        title="待接单加载失败"
        description="请确认后端服务已启动后重试。"
        action-text="重新加载"
        @action="load"
      />

      <EmptyState
        v-else-if="!rows.length"
        :illustration="illustrations.emptyOrder"
        title="暂时没有待接单"
        :description="categoryId ? '换个类型看看，或稍后刷新。' : '新工单进入待接单池时会自动出现在这里。'"
        action-text="刷新"
        @action="load"
      />

      <template v-else>
        <ul class="pending-list">
          <li v-for="order in rows" :key="order.id" class="pending-card">
            <div class="pending-card__head">
              <span class="pending-card__title">{{ order.title }}</span>
              <UrgencyTag :urgency="order.urgency" :safety="order.safetyFlag" size="small" />
            </div>

            <p v-if="order.description" class="pending-card__desc clamp-2">{{ order.description }}</p>

            <dl class="pending-card__meta">
              <div>
                <dt>工单号</dt>
                <dd class="text-mono">{{ order.orderNo }}</dd>
              </div>
              <div>
                <dt>类型</dt>
                <dd>{{ order.categoryName || '-' }}</dd>
              </div>
              <div>
                <dt>影响范围</dt>
                <dd>{{ dictText(AFFECT_SCOPE, order.affectScope) }}</dd>
              </div>
              <div>
                <dt>发布时间</dt>
                <dd><RelativeTime :value="order.createTime" /></dd>
              </div>
            </dl>

            <div class="pending-card__foot">
              <el-button
                type="primary"
                :loading="grabbing === order.id"
                :disabled="auditPending || denied"
                @click="onGrab(order)"
              >
                抢单
              </el-button>
            </div>
          </li>
        </ul>

        <div class="pending-pager page-pager">
          <Pagination
            :total="total"
            :page="page.pageNum"
            :size="page.pageSize"
            @update:page="onPageChange"
            @update:size="onSizeChange"
          />
        </div>
      </template>
    </SectionCard>
  </div>
</template>

<style scoped>
.pending-filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-5) var(--space-5) var(--space-4);
  flex-wrap: wrap;
}

.pending-filter__select {
  width: 240px;
}

.pending-filter__count {
  font-size: var(--fs-sm);
  color: var(--color-text-3);
}

.pending-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(320px, 100%), 1fr));
  gap: var(--space-4);
  padding: 0 var(--space-5) var(--space-2);
}

.pending-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  transition: border-color var(--dur-base) var(--ease-out);
}

.pending-card:hover {
  border-color: var(--color-border-strong);
}

.pending-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.pending-card__title {
  font-size: var(--fs-h3);
  font-weight: var(--fw-medium);
  color: var(--color-text-1);
}

.pending-card__desc {
  font-size: var(--fs-sm);
  color: var(--color-text-2);
  line-height: var(--lh-base);
}

.pending-card__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2) var(--space-4);
}

.pending-card__meta dt {
  margin-bottom: 2px;
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.pending-card__meta dd {
  font-size: var(--fs-sm);
  color: var(--color-text-1);
}

.pending-card__foot {
  margin-top: auto;
  padding-top: var(--space-2);
}

.pending-pager {
  padding: 0 var(--space-5) var(--space-4);
}
</style>
