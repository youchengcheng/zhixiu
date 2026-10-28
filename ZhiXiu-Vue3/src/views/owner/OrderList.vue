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
import { listMyOrders } from '@/api/ownerOrder'
import { ORDER_STATUS } from '@/utils/dict'

// 我的工单列表：查询表单 + 表格 + 分页（列表页统一结构）
const router = useRouter()

const query = reactive({ status: '', keyword: '' })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const rows = ref([])
const loading = ref(false)
const failed = ref(false)

async function load() {
  loading.value = true
  failed.value = false

  const params = { pageNum: page.pageNum, pageSize: page.pageSize }
  if (query.status) params.status = query.status
  if (query.keyword.trim()) params.keyword = query.keyword.trim()

  try {
    const data = await listMyOrders(params)
    rows.value = data?.records || []
    page.total = data?.total || 0
  } catch {
    // 拦截器已统一提示，这里只切换到可重试的空状态
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
  query.status = ''
  query.keyword = ''
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
  router.push({ name: 'OwnerOrderDetail', params: { id: row.id } })
}
</script>

<template>
  <div class="layout-page">
    <PageHeader title="我的工单" description="跟踪每一单的处理进度，支持按状态与关键字筛选">
      <el-button type="primary" @click="router.push({ name: 'OwnerOrderCreate' })">提交报修</el-button>
    </PageHeader>

    <SectionCard padding="none">
      <!-- 查询表单 -->
      <div class="order-filter page-filter">
        <DictSelect
          v-model="query.status"
          :dict="ORDER_STATUS"
          placeholder="全部状态"
          aria-label="全部状态"
          class="order-filter__status"
          @change="onSearch"
        />
        <el-input
          v-model="query.keyword"
          placeholder="搜索标题或工单号"
          aria-label="搜索标题或工单号"
          clearable
          class="order-filter__keyword"
          :prefix-icon="Search"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
        <el-button :icon="Refresh" @click="onReset">重置</el-button>
      </div>

      <!-- 加载中：与表格同构的骨架，避免切换时高度跳动 -->
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
        description="换个筛选条件试试，或者直接提交一条新的报修。"
        action-text="提交报修"
        @action="router.push({ name: 'OwnerOrderCreate' })"
      />

      <template v-else>
        <el-table :data="rows" style="width: 100%">
          <el-table-column label="工单号" width="200">
            <template #default="{ row }">
              <span class="text-mono order-no">{{ row.orderNo }}</span>
            </template>
          </el-table-column>

          <el-table-column label="标题" min-width="220">
            <template #default="{ row }">
              <button class="order-title" type="button" @click="goDetail(row)">{{ row.title }}</button>
            </template>
          </el-table-column>

          <el-table-column label="类型" width="120">
            <template #default="{ row }">{{ row.categoryName || '-' }}</template>
          </el-table-column>

          <el-table-column label="紧急程度" width="150">
            <template #default="{ row }">
              <UrgencyTag :urgency="row.urgency" :safety="row.safetyFlag" size="small" />
            </template>
          </el-table-column>

          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <StatusTag :status="row.status" size="small" />
            </template>
          </el-table-column>

          <el-table-column label="提交时间" width="140">
            <template #default="{ row }">
              <RelativeTime :value="row.createTime" />
            </template>
          </el-table-column>

          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button text type="primary" @click="goDetail(row)">查看</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="order-pager page-pager">
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
.order-filter {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
  padding: var(--space-5) var(--space-5) var(--space-4);
}

.order-filter__status {
  width: 160px;
}

.order-filter__keyword {
  width: 260px;
}

.order-no {
  font-size: var(--fs-sm);
  color: var(--color-text-2);
}

.order-title {
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

.order-title:hover {
  color: var(--color-primary);
}

.order-pager {
  padding: 0 var(--space-5) var(--space-4);
}
</style>
