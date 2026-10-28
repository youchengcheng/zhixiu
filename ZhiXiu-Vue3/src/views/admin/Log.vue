<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SectionCard from '@/components/SectionCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import DictSelect from '@/components/DictSelect.vue'
import Pagination from '@/components/Pagination.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import { listOperationLogs } from '@/api/log'
import { LOG_RESULT, OPERATOR_TYPE } from '@/utils/dict'
import { formatDateTime } from '@/utils/format'

// 操作日志：由后端 @ApiLog 切面写入 operation_log，这里只做查询与筛选
// 后端查询接口尚未就绪，由 mock 兜底
const query = reactive({ module: '', username: '', result: '' })
const dateRange = ref(null)
const page = reactive({ pageNum: 1, pageSize: 20, total: 0 })

const rows = ref([])
const loading = ref(true)
const failed = ref(false)

const detailVisible = ref(false)
const detailItem = ref(null)

function buildParams() {
  const params = { pageNum: page.pageNum, pageSize: page.pageSize }
  if (query.module.trim()) params.module = query.module.trim()
  if (query.username.trim()) params.username = query.username.trim()
  if (query.result !== '') params.result = Number(query.result)
  if (dateRange.value?.length === 2) {
    params.startTime = dateRange.value[0]
    params.endTime = dateRange.value[1]
  }
  return params
}

async function load() {
  loading.value = true
  failed.value = false
  try {
    const data = await listOperationLogs(buildParams())
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
  Object.assign(query, { module: '', username: '', result: '' })
  dateRange.value = null
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

function openDetail(row) {
  detailItem.value = row
  detailVisible.value = true
}

/** 操作人展示：系统操作没有用户名 */
function operatorText(row) {
  // OPERATOR_TYPE 是「取值 → 文案」的纯映射（非 {text,tone} 结构），直接按键取值
  const roleLabel = OPERATOR_TYPE[row.role] || row.role || '-'
  if (!row.username) return roleLabel
  return `${row.username}（${roleLabel}）`
}
</script>

<template>
  <div class="layout-page">
    <PageHeader title="操作日志" description="记录登录、派单、审核等关键操作，便于追溯问题">
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </PageHeader>

    <SectionCard padding="none">
      <div class="alog-filter page-filter">
        <el-input
          v-model="query.module"
          placeholder="模块，如「工单管理」"
          aria-label="模块，如「工单管理」"
          clearable
          class="alog-filter__module"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-input
          v-model="query.username"
          placeholder="操作人"
          aria-label="操作人"
          clearable
          class="alog-filter__username"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <DictSelect
          v-model="query.result"
          :dict="LOG_RESULT"
          placeholder="全部结果"
          aria-label="全部结果"
          class="alog-filter__result"
          @change="onSearch"
        />
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          aria-label="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          class="alog-filter__date"
          @change="onSearch"
        />
        <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
        <el-button :icon="Refresh" @click="onReset">重置</el-button>
      </div>

      <AppSkeleton v-if="loading" variant="table" :rows="8" />

      <EmptyState
        v-else-if="failed"
        icon="Warning"
        title="日志加载失败"
        description="请稍后重试。"
        action-text="重新加载"
        @action="load"
      />

      <EmptyState
        v-else-if="!rows.length"
        icon="Document"
        title="没有符合条件的日志"
        description="换个筛选条件或时间范围试试。"
        action-text="重置筛选"
        @action="onReset"
      />

      <template v-else>
        <el-table :data="rows" style="width: 100%">
          <el-table-column label="时间" width="170">
            <template #default="{ row }">
              <span class="text-mono alog-time">{{ formatDateTime(row.createTime) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="操作人" width="180">
            <template #default="{ row }">
              <span class="alog-operator">{{ operatorText(row) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="模块" width="120">
            <template #default="{ row }">{{ row.module || '-' }}</template>
          </el-table-column>

          <el-table-column label="操作" min-width="140">
            <template #default="{ row }">{{ row.action || '-' }}</template>
          </el-table-column>

          <el-table-column label="接口" min-width="240">
            <template #default="{ row }">
              <span class="alog-url text-mono">
                <span v-if="row.method" class="alog-method">{{ row.method }}</span>
                {{ row.url || '-' }}
              </span>
            </template>
          </el-table-column>

          <el-table-column label="IP" width="130">
            <template #default="{ row }">
              <span class="text-mono">{{ row.ip || '-' }}</span>
            </template>
          </el-table-column>

          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <StatusTag :status="row.result ?? 1" :dict="LOG_RESULT" :dot="false" size="small" />
            </template>
          </el-table-column>

          <el-table-column label="耗时" width="90" align="right" class-name="cell--number">
            <template #default="{ row }">
              <span class="text-mono">{{ row.costTime != null ? `${row.costTime} ms` : '-' }}</span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button text type="primary" @click="openDetail(row)">详情</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="alog-pager page-pager">
          <Pagination
            :total="page.total"
            :page="page.pageNum"
            :size="page.pageSize"
            :page-sizes="[20, 50, 100]"
            @update:page="onPageChange"
            @update:size="onSizeChange"
          />
        </div>
      </template>
    </SectionCard>

    <!-- 日志详情 -->
    <el-dialog v-model="detailVisible" title="日志详情" width="560px">
      <dl v-if="detailItem" class="alog-detail">
        <div>
          <dt>时间</dt>
          <dd class="text-mono">{{ formatDateTime(detailItem.createTime) }}</dd>
        </div>
        <div>
          <dt>操作人</dt>
          <dd>{{ operatorText(detailItem) }}</dd>
        </div>
        <div>
          <dt>模块 / 操作</dt>
          <dd>{{ detailItem.module || '-' }} · {{ detailItem.action || '-' }}</dd>
        </div>
        <div>
          <dt>接口</dt>
          <dd class="text-mono">{{ detailItem.method || '-' }} {{ detailItem.url || '-' }}</dd>
        </div>
        <div>
          <dt>IP / 耗时</dt>
          <dd class="text-mono">
            {{ detailItem.ip || '-' }} · {{ detailItem.costTime != null ? `${detailItem.costTime} ms` : '-' }}
          </dd>
        </div>
        <div>
          <dt>结果</dt>
          <dd>
            <StatusTag :status="detailItem.result ?? 1" :dict="LOG_RESULT" :dot="false" size="small" />
          </dd>
        </div>
        <div v-if="detailItem.params" class="alog-detail__full">
          <dt>请求参数</dt>
          <dd class="alog-detail__code">{{ detailItem.params }}</dd>
        </div>
        <div v-if="detailItem.errorMsg" class="alog-detail__full">
          <dt>错误信息</dt>
          <dd class="alog-detail__code">{{ detailItem.errorMsg }}</dd>
        </div>
      </dl>
    </el-dialog>
  </div>
</template>

<style scoped>
.alog-filter {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
  padding: var(--space-5) var(--space-5) var(--space-4);
}

.alog-filter__module,
.alog-filter__username {
  width: 180px;
}

.alog-filter__result {
  width: 140px;
}

.alog-filter__date {
  width: 260px;
}

.alog-time {
  font-size: var(--fs-sm);
  color: var(--color-text-2);
}

.alog-operator {
  font-size: var(--fs-sm);
  color: var(--color-text-1);
}

.alog-url {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--fs-xs);
  color: var(--color-text-2);
  word-break: break-all;
}

.alog-method {
  flex: 0 0 auto;
  padding: 0 6px;
  border-radius: var(--radius-xs);
  background: var(--color-bg-sunken);
  color: var(--color-text-2);
  font-size: 11px;
  line-height: 18px;
}

.alog-pager {
  padding: 0 var(--space-5) var(--space-4);
}

.alog-detail {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4) var(--space-5);
}

.alog-detail dt {
  margin-bottom: var(--space-1);
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.alog-detail dd {
  font-size: var(--fs-sm);
  color: var(--color-text-1);
}

.alog-detail__full {
  grid-column: 1 / -1;
}

.alog-detail__code {
  padding: var(--space-3);
  border-radius: var(--radius-sm);
  background: var(--color-bg-sunken);
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  color: var(--color-text-2);
  word-break: break-all;
  white-space: pre-wrap;
}
</style>
