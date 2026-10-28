<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SectionCard from '@/components/SectionCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import LetterAvatar from '@/components/LetterAvatar.vue'
import DictSelect from '@/components/DictSelect.vue'
import Pagination from '@/components/Pagination.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import { auditWorker, listWorkerLoad, listWorkers, updateWorkerStatus } from '@/api/worker'
import { AUDIT_STATUS, WORK_STATUS } from '@/utils/dict'

// 师傅管理：资质审核、启用/禁用，并按师傅负载给出派单参考
const query = reactive({ auditStatus: '', workStatus: '', keyword: '' })
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const rows = ref([])
const loads = ref([])
const loading = ref(false)
const failed = ref(false)
const acting = ref('')

const auditVisible = ref(false)
const auditTarget = ref(null)
const auditForm = reactive({ auditStatus: 1, auditRemark: '' })

// 负载按师傅 ID 建索引：负载接口的字段是 workerId，列表行的主键是 id
const loadMap = computed(() => {
  const map = {}
  loads.value.forEach((item) => {
    map[String(item.workerId)] = item
  })
  return map
})

function buildParams() {
  const params = { pageNum: page.pageNum, pageSize: page.pageSize }
  if (query.auditStatus !== '') params.auditStatus = Number(query.auditStatus)
  if (query.workStatus !== '') params.workStatus = Number(query.workStatus)
  if (query.keyword.trim()) params.keyword = query.keyword.trim()
  return params
}

async function load() {
  loading.value = true
  failed.value = false

  const [listResult, loadResult] = await Promise.allSettled([
    listWorkers(buildParams()),
    listWorkerLoad()
  ])

  if (listResult.status === 'fulfilled') {
    rows.value = listResult.value?.records || []
    page.total = listResult.value?.total || 0
  } else {
    rows.value = []
    page.total = 0
    failed.value = true
  }

  loads.value = loadResult.status === 'fulfilled' ? loadResult.value || [] : []
  loading.value = false
}

onMounted(load)

function onSearch() {
  page.pageNum = 1
  load()
}

function onReset() {
  Object.assign(query, { auditStatus: '', workStatus: '', keyword: '' })
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

function loadOf(row) {
  // 先用列表行主键 id 去负载表取当前进行中单量，取不到再退回行内自带值
  return loadMap.value[String(row.id)]?.currentLoad ?? row.currentLoad ?? 0
}

// ---------- 审核 ----------
function openAudit(row) {
  auditTarget.value = row
  auditForm.auditStatus = 1
  auditForm.auditRemark = ''
  auditVisible.value = true
}

async function submitAudit() {
  const target = auditTarget.value
  if (!target) return

  // 列表行主键字段是 id，作为审核接口的路径参数
  acting.value = target.id
  try {
    const payload = { auditStatus: Number(auditForm.auditStatus) }
    if (auditForm.auditRemark.trim()) payload.auditRemark = auditForm.auditRemark.trim()

    // 用师傅主键调用审核接口
    await auditWorker(target.id, payload)
    ElMessage.success(payload.auditStatus === 1 ? '已通过资质审核' : '已驳回资质审核')
    auditVisible.value = false
    await load()
  } catch {
    // 拦截器已统一提示
  } finally {
    acting.value = ''
  }
}

// ---------- 启用 / 禁用 ----------
async function onToggleStatus(row, enabled) {
  // 启用/禁用同样以列表行主键 id 作为路径参数
  acting.value = row.id
  try {
    // 文档 10.4：1在线 / 4禁用
    await updateWorkerStatus(row.id, enabled ? 1 : 4)
    ElMessage.success(enabled ? '已启用该师傅' : '已禁用该师傅')
    await load()
  } catch {
    load()
  } finally {
    acting.value = ''
  }
}
</script>

<template>
  <div class="layout-page">
    <PageHeader title="师傅管理" description="审核师傅资质、控制启用状态，并查看当前负载" />

    <SectionCard padding="none">
      <div class="aworker-filter page-filter">
        <DictSelect
          v-model="query.auditStatus"
          :dict="AUDIT_STATUS"
          placeholder="全部审核状态"
          aria-label="全部审核状态"
          class="aworker-filter__select"
          @change="onSearch"
        />
        <DictSelect
          v-model="query.workStatus"
          :dict="WORK_STATUS"
          placeholder="全部师傅状态"
          aria-label="全部师傅状态"
          class="aworker-filter__select"
          @change="onSearch"
        />
        <el-input
          v-model="query.keyword"
          placeholder="姓名 / 工号 / 手机号"
          aria-label="姓名 / 工号 / 手机号"
          clearable
          class="aworker-filter__keyword"
          :prefix-icon="Search"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
        <el-button :icon="Refresh" @click="onReset">重置</el-button>
      </div>

      <AppSkeleton v-if="loading" variant="table" :rows="6" />

      <EmptyState
        v-else-if="failed"
        icon="Warning"
        title="师傅列表加载失败"
        description="请确认后端服务已启动后重试。"
        action-text="重新加载"
        @action="load"
      />

      <EmptyState
        v-else-if="!rows.length"
        icon="Tools"
        title="没有符合条件的师傅"
        description="换个筛选条件试试，或重置后查看全部师傅。"
        action-text="重置筛选"
        @action="onReset"
      />

      <template v-else>
        <el-table :data="rows" style="width: 100%">
          <el-table-column label="师傅" min-width="160">
            <template #default="{ row }">
              <div class="aworker-cell">
                <LetterAvatar :name="row.realName" :size="32" />
                <div class="aworker-cell__text">
                  <span class="aworker-cell__name">{{ row.realName }}</span>
                  <span class="aworker-cell__sub text-mono">{{ row.workerNo }}</span>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="手机号" width="140">
            <template #default="{ row }">
              <span class="text-mono">{{ row.phone || '-' }}</span>
            </template>
          </el-table-column>

          <el-table-column label="技能标签" min-width="180">
            <template #default="{ row }">
              <span class="aworker-skills">{{ row.skillTags || '-' }}</span>
            </template>
          </el-table-column>

          <el-table-column label="资质" width="100">
            <template #default="{ row }">
              <StatusTag :status="row.auditStatus ?? 0" :dict="AUDIT_STATUS" :dot="false" size="small" />
            </template>
          </el-table-column>

          <el-table-column label="师傅状态" width="110">
            <template #default="{ row }">
              <StatusTag :status="row.workStatus ?? 0" :dict="WORK_STATUS" :dot="false" size="small" />
            </template>
          </el-table-column>

          <!-- 数值列统一右对齐 + 等宽数字，便于纵向比对 -->
          <el-table-column label="当前负载" width="100" align="right" class-name="cell--number">
            <template #default="{ row }">
              <span class="text-mono aworker-load">{{ loadOf(row) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="评分" width="80" align="right" class-name="cell--number">
            <template #default="{ row }">
              <span class="text-mono">{{ row.avgScore ?? '-' }}</span>
            </template>
          </el-table-column>

          <el-table-column label="启用" width="90">
            <template #default="{ row }">
              <el-switch
                :model-value="row.workStatus !== 4"
                :loading="acting === row.id"
                :disabled="row.auditStatus === 0"
                @change="(value) => onToggleStatus(row, value)"
              />
            </template>
          </el-table-column>

          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button text type="primary" @click="openAudit(row)">审核</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="aworker-pager page-pager">
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

    <!-- 审核弹窗 -->
    <el-dialog v-model="auditVisible" title="师傅资质审核" width="460px">
      <p v-if="auditTarget" class="audit-target">
        {{ auditTarget.realName }} · {{ auditTarget.workerNo }}
      </p>
      <el-form label-position="top">
        <el-form-item label="审核结果" required>
          <el-radio-group v-model="auditForm.auditStatus">
            <el-radio :value="1">通过</el-radio>
            <el-radio :value="2">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核备注">
          <el-input
            v-model="auditForm.auditRemark"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="驳回时建议说明原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting === auditTarget?.id" @click="submitAudit">
          提交审核
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.aworker-filter {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
  padding: var(--space-5) var(--space-5) var(--space-4);
}

.aworker-filter__select {
  width: 160px;
}

.aworker-filter__keyword {
  width: 220px;
}

.aworker-cell {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.aworker-cell__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.aworker-cell__name {
  font-size: var(--fs-body);
  font-weight: var(--fw-medium);
  color: var(--color-text-1);
}

.aworker-cell__sub {
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.aworker-skills {
  font-size: var(--fs-sm);
  color: var(--color-text-2);
}

.aworker-load {
  font-weight: var(--fw-medium);
  color: var(--color-text-1);
}

.aworker-pager {
  padding: 0 var(--space-5) var(--space-4);
}

.audit-target {
  margin-bottom: var(--space-4);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  background: var(--color-bg-sunken);
  font-size: var(--fs-sm);
  color: var(--color-text-2);
}
</style>
