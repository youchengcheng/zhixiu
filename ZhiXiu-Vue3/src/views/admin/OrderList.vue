<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, Search } from '@element-plus/icons-vue'
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
        <el-table :data="rows" style="width: 100%">
          <el-table-column label="工单号" width="200">
            <template #default="{ row }">
              <span class="text-mono aorder-no">{{ row.orderNo }}</span>
            </template>
          </el-table-column>

          <el-table-column label="标题" min-width="200">
            <template #default="{ row }">
              <button class="aorder-title" type="button" @click="goDetail(row)">
                {{ row.title }}
                <span v-if="row.escalated === 1" class="aorder-escalated">SLA 升级</span>
              </button>
            </template>
          </el-table-column>

          <el-table-column label="业主" width="110">
            <template #default="{ row }">{{ row.ownerName || '-' }}</template>
          </el-table-column>

          <el-table-column label="类型" width="110">
            <template #default="{ row }">{{ row.categoryName || '-' }}</template>
          </el-table-column>

          <el-table-column label="紧急程度" width="150">
            <template #default="{ row }">
              <UrgencyTag :urgency="row.urgency" :safety="row.safetyFlag" size="small" />
            </template>
          </el-table-column>

          <el-table-column label="状态" width="130">
            <template #default="{ row }">
              <StatusTag :status="row.status" size="small" />
            </template>
          </el-table-column>

          <el-table-column label="派单师傅" width="120">
            <template #default="{ row }">{{ row.workerName || '未派单' }}</template>
          </el-table-column>

          <el-table-column label="提交时间" width="140">
            <template #default="{ row }">
              <RelativeTime :value="row.createTime" />
            </template>
          </el-table-column>

          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button text type="primary" @click="goDetail(row)">详情</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

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
  color: var(--color-text-2);
}

.aorder-title {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
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

.aorder-title:hover {
  color: var(--color-primary);
}

.aorder-escalated {
  flex: 0 0 auto;
  padding: 0 6px;
  border-radius: var(--radius-xs);
  background: var(--color-danger-soft);
  color: var(--color-danger-strong);
  font-size: 11px;
  line-height: 18px;
}

.aorder-pager {
  padding: 0 var(--space-5) var(--space-4);
}
</style>
