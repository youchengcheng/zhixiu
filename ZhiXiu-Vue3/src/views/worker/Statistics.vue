<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SectionCard from '@/components/SectionCard.vue'
import StatCard from '@/components/StatCard.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getWorkerStatistics } from '@/api/workerOrder'
import { formatMinutes, formatPercent } from '@/utils/format'

// 个人统计：接口 GET /api/worker/statistics，纯聚合数据
const loading = ref(true)
const failed = ref(false)
const stats = ref(null)

// 完工率可能是 0.958 这样的比率，统一转成百分比展示
const finishRatePercent = computed(() => {
  const rate = Number(stats.value?.finishRate)
  if (Number.isNaN(rate)) return 0
  return Math.round(rate * 100)
})

async function load() {
  loading.value = true
  failed.value = false
  try {
    stats.value = await getWorkerStatistics()
  } catch {
    stats.value = null
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="layout-page">
    <PageHeader title="个人统计" description="接单量、完工率与评分，反映近期服务表现">
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </PageHeader>

    <SectionCard v-if="loading">
      <AppSkeleton variant="card" :rows="6" :columns="3" />
    </SectionCard>

    <SectionCard v-else-if="failed">
      <EmptyState
        icon="Warning"
        title="统计数据加载失败"
        description="请确认后端服务已启动后重试。"
        action-text="重新加载"
        @action="load"
      />
    </SectionCard>

    <template v-else>
      <!-- 指标卡用 4 列栅格，避免出现规范禁止的「三列等宽卡片阵列」 -->
      <div class="grid grid-4">
        <StatCard label="累计接单" :value="stats?.totalOrders ?? 0" icon="Tickets" tone="default" hint="历史承接工单总数" />
        <StatCard label="已完工" :value="stats?.finishOrders ?? 0" icon="Finished" tone="success" hint="成功完成的工单" />
        <StatCard label="当前负载" :value="stats?.currentLoad ?? 0" icon="Odometer" tone="warning" hint="在办工单数量" />
        <StatCard
          label="完工率"
          :value="formatPercent(stats?.finishRate)"
          icon="TrendCharts"
          tone="primary"
          hint="已完工 / 累计接单"
        />
        <StatCard
          label="平均处理时长"
          :value="formatMinutes(stats?.avgHandleMinutes)"
          icon="Stopwatch"
          tone="default"
          hint="从接单到提交完工"
        />
        <StatCard
          label="平均评分"
          :value="stats?.avgScore ?? 0"
          unit="分"
          icon="Stamp"
          tone="success"
          hint="来自业主评价"
        />
      </div>

      <!-- 完工率可视化 -->
      <SectionCard title="完工率" description="已完工工单占总接单量的比例">
        <div class="rate-row">
          <el-progress
            class="rate-row__bar"
            :percentage="finishRatePercent"
            :stroke-width="10"
            :show-text="false"
          />
          <span class="rate-row__value text-mono">{{ finishRatePercent }}%</span>
        </div>
        <p class="rate-row__hint">
          统计口径：<strong class="text-mono">{{ stats?.finishOrders ?? 0 }}</strong> 单已完工 /
          <strong class="text-mono">{{ stats?.totalOrders ?? 0 }}</strong> 单累计接单
        </p>
      </SectionCard>
    </template>
  </div>
</template>

<style scoped>
.rate-row {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.rate-row__bar {
  flex: 1 1 auto;
  max-width: 520px;
}

.rate-row__value {
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  color: var(--color-text-1);
}

.rate-row__hint {
  margin-top: var(--space-3);
  font-size: var(--fs-sm);
  color: var(--color-text-3);
}
</style>
