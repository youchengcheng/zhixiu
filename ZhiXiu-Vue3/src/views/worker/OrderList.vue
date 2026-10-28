<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
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
import { acceptOrder, listMyWorkerOrders } from '@/api/workerOrder'
import { ORDER_STATUS } from '@/utils/dict'

// 我的工单（师傅视角）：被指派且未接受的工单可直接接受派单
const router = useRouter()

const query = reactive({ status: '' })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const rows = ref([])
const loading = ref(false)
const failed = ref(false)
const accepting = ref('')

async function load() {
  loading.value = true
  failed.value = false

  const params = { pageNum: page.pageNum, pageSize: page.pageSize }
  if (query.status) params.status = query.status

  try {
    const data = await listMyWorkerOrders(params)
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

onMounted(load)

function onSearch() {
  page.pageNum = 1
  load()
}

function onReset() {
  query.status = ''
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
  router.push({ name: 'WorkerOrderDetail', params: { id: row.id } })
}

async function onAccept(row) {
  accepting.value = row.id
  try {
    await acceptOrder(row.id)
    ElMessage.success('已接受派单，请尽快联系业主')
    goDetail(row)
  } catch {
    // 2002 状态不允许等由拦截器提示
    load()
  } finally {
    accepting.value = ''
  }
}
</script>

<template>
  <div class="layout-page">
    <PageHeader title="我的工单" description="我承接的全部工单与处理进度">
      <el-button @click="router.push({ name: 'WorkerPending' })">去待接单</el-button>
    </PageHeader>

    <SectionCard padding="none">
      <div class="worder-filter page-filter">
        <DictSelect
          v-model="query.status"
          :dict="ORDER_STATUS"
          placeholder="全部状态"
          aria-label="全部状态"
          class="worder-filter__status"
          @change="onSearch"
        />
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
        description="换个状态筛选，或去待接单池抢一单。"
        action-text="去待接单"
        @action="router.push({ name: 'WorkerPending' })"
      />

      <template v-else>
        <el-table :data="rows" style="width: 100%">
          <el-table-column label="工单号" width="200">
            <template #default="{ row }">
              <span class="text-mono worder-no">{{ row.orderNo }}</span>
            </template>
          </el-table-column>

          <el-table-column label="标题" min-width="220">
            <template #default="{ row }">
              <button class="worder-title" type="button" @click="goDetail(row)">{{ row.title }}</button>
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

          <el-table-column label="状态" width="130">
            <template #default="{ row }">
              <StatusTag :status="row.status" size="small" />
            </template>
          </el-table-column>

          <el-table-column label="提交时间" width="140">
            <template #default="{ row }">
              <RelativeTime :value="row.createTime" />
            </template>
          </el-table-column>

          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button
                  v-if="row.status === 'ASSIGNED'"
                  text
                  type="primary"
                  :loading="accepting === row.id"
                  @click="onAccept(row)"
                >
                  接受派单
                </el-button>
                <el-button v-else text type="primary" @click="goDetail(row)">处理</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="worder-pager page-pager">
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
.worder-filter {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
  padding: var(--space-5) var(--space-5) var(--space-4);
}

.worder-filter__status {
  width: 170px;
}

.worder-no {
  font-size: var(--fs-sm);
  color: var(--color-text-2);
}

.worder-title {
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

.worder-title:hover {
  color: var(--color-primary);
}

.worder-pager {
  padding: 0 var(--space-5) var(--space-4);
}
</style>
