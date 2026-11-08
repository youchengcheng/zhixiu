<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SectionCard from '@/components/SectionCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import UrgencyTag from '@/components/UrgencyTag.vue'
import DictSelect from '@/components/DictSelect.vue'
import RelativeTime from '@/components/RelativeTime.vue'
import Pagination from '@/components/Pagination.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import { listAllOrders } from '@/api/adminOrder'
import { getCategoryTree } from '@/api/category'
import { ORDER_STATUS, URGENCY } from '@/utils/dict'

// 工单管理：全量工单查询，支持状态 / 紧急程度 / 类型 / 是否 SLA 升级 / 关键字
const router = useRouter()

const query = reactive({ status: '', urgency: '', categoryId: '', escalated: false, keyword: '' })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const rows = ref([])
const loading = ref(false)
const failed = ref(false)
const categoryOptions = ref([])

async function loadCategories() {
  try {
    const tree = (await getCategoryTree()) || []
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

function buildParams() {
  const params = { pageNum: page.pageNum, pageSize: page.pageSize }
  if (query.status) params.status = query.status
  if (query.urgency !== '') params.urgency = Number(query.urgency)
  if (query.categoryId) params.categoryId = query.categoryId
  if (query.escalated) params.escalated = 1
  if (query.keyword.trim()) params.keyword = query.keyword.trim()
  return params
}

async function load() {
  loading.value = true
  failed.value = false
  try {
    const data = await listAllOrders(buildParams())
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
  Object.assign(query, { status: '', urgency: '', categoryId: '', escalated: false, keyword: '' })
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

function goDetail(row) {
  router.push({ name: 'AdminOrderDetail', params: { id: row.id } })
}
</script>

<template>
  <div class="layout-page">
    <PageHeader title="工单管理" description="全量工单查询、派单与改派；SLA 升级工单可单独筛选" />

    <SectionCard padding="none">
      <div class="aorder-filter page-filter">
        <DictSelect
          v-model="query.status"
          :dict="ORDER_STATUS"
          placeholder="全部状态"
          aria-label="全部状态"
          class="aorder-filter__status"
          @change="onSearch"
        />
        <DictSelect
          v-model="query.urgency"
          :dict="URGENCY"
          placeholder="全部紧急度"
          aria-label="全部紧急度"
          class="aorder-filter__urgency"
          @change="onSearch"
        />
        <el-select
          v-model="query.categoryId"
          placeholder="全部类型"
          clearable
          class="aorder-filter__category"
          @change="onSearch"
        >
          <el-option v-for="item in categoryOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="工单号 / 标题 / 业主"
          aria-label="工单号 / 标题 / 业主"
          clearable
          class="aorder-filter__keyword"
          :prefix-icon="Search"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-checkbox v-model="query.escalated" @change="onSearch">只看 SLA 升级</el-checkbox>
        <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
        <el-button :icon="Refresh" @click="onReset">重置</el-button>
      </div>

      <AppSkeleton v-if="loading" variant="table" :rows="6" />

      <EmptyState
        v-else-if="failed"
        icon="Warning"
        title="工单加载失败"
        description="请确认后端服务已启动后重试。"
        action-text="重新加载"
        @action="load"
      />

      <EmptyState
        v-else-if="!rows.length"
        icon="Tickets"
        title="没有符合条件的工单"
        description="换个筛选条件试试，或重置后查看全部工单。"
        action-text="重置筛选"
        @action="onReset"
      />

      <template v-else>
        <el-table
          class="aorder-table"
          :data="rows"
          row-class-name="aorder-table__row"
          style="width: 100%"
          @row-click="goDetail"
        >
          <el-table-column label="工单信息" min-width="300">
            <template #default="{ row }">
              <div class="aorder-primary">
                <button class="aorder-title" type="button" @click.stop="goDetail(row)">
                  {{ row.title }}
                </button>
                <div class="aorder-meta">
                  <span class="text-mono aorder-no">{{ row.orderNo }}</span>
                  <span class="aorder-category">{{ row.categoryName || '未分类' }}</span>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="处理状态" width="210">
            <template #default="{ row }">
              <div class="aorder-state">
                <StatusTag :status="row.status" :dot="false" />
                <UrgencyTag :urgency="row.urgency" :safety="row.safetyFlag" size="small" />
                <span v-if="row.slaEscalated === 1" class="aorder-escalated">
                  {{ row.slaOverdue ? 'SLA 已超时' : 'SLA 已升级' }}
                </span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="服务对象" min-width="210">
            <template #default="{ row }">
              <div class="aorder-person">
                <span class="aorder-person__name">{{ row.ownerName || '未知业主' }}</span>
                <span class="aorder-person__meta">{{ row.fullAddress || '地址信息未填写' }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="负责人" width="130">
            <template #default="{ row }">
              <span :class="['aorder-worker', { 'is-empty': !row.workerName }]">
                {{ row.workerName || '待派单' }}
              </span>
            </template>
          </el-table-column>

          <el-table-column label="提交时间" width="120">
            <template #default="{ row }">
              <span class="aorder-time"><RelativeTime :value="row.createTime" /></span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="68" align="center">
            <template #default="{ row }">
              <el-button
                class="aorder-open"
                text
                :icon="ArrowRight"
                :aria-label="`查看工单：${row.title}`"
                @click.stop="goDetail(row)"
              />
            </template>
          </el-table-column>
        </el-table>

        <div class="aorder-mobile-list" aria-label="工单列表">
          <article
            v-for="row in rows"
            :key="row.id"
            class="aorder-mobile-item"
            role="link"
            tabindex="0"
            @click="goDetail(row)"
            @keydown.enter="goDetail(row)"
            @keydown.space.prevent="goDetail(row)"
          >
            <div class="aorder-mobile-item__head">
              <h2>{{ row.title }}</h2>
              <StatusTag :status="row.status" :dot="false" />
            </div>

            <div class="aorder-meta">
              <span class="text-mono aorder-no">{{ row.orderNo }}</span>
              <span class="aorder-category">{{ row.categoryName || '未分类' }}</span>
            </div>

            <div class="aorder-mobile-item__details">
              <div>
                <span class="aorder-mobile-item__label">服务对象</span>
                <strong>{{ row.ownerName || '未知业主' }}</strong>
                <small>{{ row.fullAddress || '地址信息未填写' }}</small>
              </div>
              <div>
                <span class="aorder-mobile-item__label">负责人</span>
                <strong>{{ row.workerName || '待派单' }}</strong>
                <UrgencyTag :urgency="row.urgency" :safety="row.safetyFlag" size="small" />
              </div>
            </div>

            <div class="aorder-mobile-item__foot">
              <span v-if="row.slaEscalated === 1" class="aorder-escalated">
                {{ row.slaOverdue ? 'SLA 已超时' : 'SLA 已升级' }}
              </span>
              <span class="aorder-time"><RelativeTime :value="row.createTime" /></span>
              <el-icon aria-hidden="true"><ArrowRight /></el-icon>
            </div>
          </article>
        </div>

        <div class="aorder-pager page-pager">
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
  </div>
</template>

<style scoped>
.aorder-filter {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
  padding: var(--space-5) var(--space-5) var(--space-4);
}

.aorder-filter__status,
.aorder-filter__urgency,
.aorder-filter__category {
  width: 150px;
}

.aorder-filter__keyword {
  width: 220px;
}

.aorder-no {
  font-size: var(--fs-sm);
  color: var(--color-text-3);
}

.aorder-primary,
.aorder-person {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.aorder-title {
  display: block;
  max-width: 100%;
  padding: 0;
  border: none;
  background: transparent;
  font: inherit;
  font-size: 15px;
  font-weight: var(--fw-semibold);
  color: var(--color-text-1);
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color var(--dur-fast) var(--ease-out);
}

.aorder-title:hover,
.aorder-title:focus-visible {
  color: var(--color-primary);
}

.aorder-title:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 3px;
  border-radius: var(--radius-xs);
}

.aorder-meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
  color: var(--color-text-3);
  font-size: var(--fs-sm);
}

.aorder-category {
  display: inline-flex;
  align-items: center;
  min-width: 0;
}

.aorder-category::before {
  content: '';
  width: 1px;
  height: 12px;
  margin-right: var(--space-2);
  background: var(--color-border-strong);
}

.aorder-state {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.aorder-escalated {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  padding-inline: 7px;
  border: 1px solid color-mix(in srgb, var(--color-danger) 34%, transparent);
  border-radius: var(--radius-sm);
  color: var(--color-danger-strong);
  font-size: 11px;
  font-weight: var(--fw-medium);
  line-height: 1;
  white-space: nowrap;
}

.aorder-person__name,
.aorder-worker {
  color: var(--color-text-1);
  font-weight: var(--fw-medium);
}

.aorder-person__meta {
  display: block;
  max-width: 100%;
  overflow: hidden;
  color: var(--color-text-3);
  font-size: var(--fs-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.aorder-worker.is-empty {
  color: var(--color-warning-strong);
}

.aorder-time {
  color: var(--color-text-2);
  font-size: var(--fs-sm);
  white-space: nowrap;
}

.aorder-open {
  color: var(--color-text-3);
}

.aorder-open:hover,
.aorder-open:focus-visible {
  color: var(--color-primary);
}

.aorder-table :deep(.aorder-table__row) {
  cursor: pointer;
}

.aorder-table :deep(.aorder-table__row td.el-table__cell) {
  padding-block: 18px;
}

.aorder-mobile-list {
  display: none;
}

.aorder-pager {
  padding: 0 var(--space-5) var(--space-4);
}

@media (max-width: 767px) {
  .aorder-filter {
    align-items: stretch;
  }

  .aorder-filter__status,
  .aorder-filter__urgency,
  .aorder-filter__category,
  .aorder-filter__keyword {
    width: 100%;
  }

  .aorder-table {
    display: none;
  }

  .aorder-mobile-list {
    display: grid;
  }

  .aorder-mobile-item {
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
    padding: var(--space-4);
    border-bottom: 1px solid var(--color-divider);
    background: var(--color-bg-card);
    cursor: pointer;
    transition: background-color var(--dur-fast) var(--ease-out);
  }

  .aorder-mobile-item:hover {
    background: var(--color-bg-hover);
  }

  .aorder-mobile-item:focus-visible {
    position: relative;
    z-index: 1;
    outline: 2px solid var(--color-primary);
    outline-offset: -2px;
  }

  .aorder-mobile-item__head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: var(--space-3);
  }

  .aorder-mobile-item__head h2 {
    min-width: 0;
    color: var(--color-text-1);
    font-size: 15px;
    font-weight: var(--fw-semibold);
    line-height: 1.55;
  }

  .aorder-mobile-item__details {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--space-4);
    padding: var(--space-3);
    border-radius: var(--radius-md);
    background: var(--color-bg-page);
  }

  .aorder-mobile-item__details > div {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-1);
    min-width: 0;
  }

  .aorder-mobile-item__details strong,
  .aorder-mobile-item__details small {
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .aorder-mobile-item__details strong {
    color: var(--color-text-1);
    font-size: var(--fs-sm);
    font-weight: var(--fw-medium);
  }

  .aorder-mobile-item__details small,
  .aorder-mobile-item__label {
    color: var(--color-text-3);
    font-size: var(--fs-xs);
  }

  .aorder-mobile-item__foot {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    color: var(--color-text-3);
  }

  .aorder-mobile-item__foot .aorder-time {
    margin-left: auto;
  }

  .aorder-pager {
    padding-inline: var(--space-4);
  }
}

@media (max-width: 420px) {
  .aorder-mobile-item__details {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
