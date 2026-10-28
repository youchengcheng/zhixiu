<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SectionCard from '@/components/SectionCard.vue'
import StatCard from '@/components/StatCard.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import {
  getAiResolution,
  getCategoryDistribution,
  getOrderTrend,
  getOverview,
  getSatisfaction,
  getWorkerRanking
} from '@/api/statistics'
import { formatMinutes, formatNumber, formatPercent } from '@/utils/format'
import { axisBase, chartPalette, chartTokens, tooltipBase, useChart } from '@/utils/chart'

// 统计看板：KPI 深色区 + 5 张图（趋势 / 类型分布 / 师傅排行 / 满意度 / AI 解决率）
// 后端统计模块尚未就绪，数据由 mock 提供；图表配色全部从设计令牌运行时读取
const loading = ref(true)
const failed = ref(false)
const trendDays = ref(7)

const overview = ref(null)
const trend = ref({ dates: [], counts: [] })
const distribution = ref({ categories: [], counts: [] })
const ranking = ref([])
const satisfaction = ref({ scores: [], counts: [], avgScore: 0, total: 0 })
const ai = ref(null)

async function loadTrend() {
  try {
    const data = await getOrderTrend(trendDays.value)
    trend.value = { dates: data?.dates || [], counts: data?.counts || [] }
  } catch {
    trend.value = { dates: [], counts: [] }
  }
}

async function loadAll() {
  loading.value = true
  failed.value = false

  const results = await Promise.allSettled([
    getOverview(),
    getOrderTrend(trendDays.value),
    getCategoryDistribution(),
    getWorkerRanking({ top: 5 }),
    getSatisfaction(),
    getAiResolution()
  ])

  const [overviewResult, trendResult, distributionResult, rankingResult, satisfactionResult, aiResult] = results

  overview.value = overviewResult.status === 'fulfilled' ? overviewResult.value : null
  trend.value =
    trendResult.status === 'fulfilled'
      ? { dates: trendResult.value?.dates || [], counts: trendResult.value?.counts || [] }
      : { dates: [], counts: [] }
  distribution.value =
    distributionResult.status === 'fulfilled'
      ? distributionResult.value
      : { categories: [], counts: [] }
  ranking.value = rankingResult.status === 'fulfilled' ? rankingResult.value || [] : []
  satisfaction.value =
    satisfactionResult.status === 'fulfilled'
      ? satisfactionResult.value
      : { scores: [], counts: [], avgScore: 0, total: 0 }
  ai.value = aiResult.status === 'fulfilled' ? aiResult.value : null

  failed.value = overviewResult.status === 'rejected'
  loading.value = false
}

onMounted(loadAll)

function onDaysChange() {
  loadTrend()
}

// ---------- 图表配置 ----------

const trendOption = computed(() => {
  const t = chartTokens()
  const axis = axisBase()
  return {
    tooltip: { ...tooltipBase(), trigger: 'axis' },
    grid: { left: 4, right: 16, top: 24, bottom: 0, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trend.value.dates,
      ...axis,
      splitLine: { show: false }
    },
    yAxis: { type: 'value', ...axis },
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: trend.value.counts,
        lineStyle: { width: 2, color: t.primary },
        itemStyle: { color: t.primary },
        areaStyle: { color: t.primary, opacity: 0.08 }
      }
    ]
  }
})

const distributionOption = computed(() => {
  const t = chartTokens()
  const axis = axisBase()
  return {
    tooltip: { ...tooltipBase(), trigger: 'item' },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: t.text2, fontSize: 12 }
    },
    color: chartPalette(),
    series: [
      {
        type: 'pie',
        radius: ['46%', '66%'],
        center: ['50%', '44%'],
        itemStyle: { borderColor: t.bgCard, borderWidth: 2 },
        label: { color: t.text2, fontSize: 12, formatter: '{b} {d}%' },
        labelLine: { lineStyle: { color: axis.axisLine.lineStyle.color } },
        data: distribution.value.categories.map((name, index) => ({
          name,
          value: distribution.value.counts[index] ?? 0
        }))
      }
    ]
  }
})

const rankingOption = computed(() => {
  const t = chartTokens()
  const axis = axisBase()
  const names = ranking.value.map((item) => item.realName)
  const values = ranking.value.map((item) => item.finishOrders)
  return {
    tooltip: { ...tooltipBase(), trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 4, right: 32, top: 16, bottom: 0, containLabel: true },
    xAxis: { type: 'value', ...axis },
    yAxis: { type: 'category', inverse: true, data: names, ...axis, splitLine: { show: false } },
    series: [
      {
        type: 'bar',
        barWidth: 14,
        data: values,
        itemStyle: { color: t.primary, borderRadius: [0, 4, 4, 0] },
        label: { show: true, position: 'right', color: t.text3, fontSize: 12 }
      }
    ]
  }
})

const satisfactionOption = computed(() => {
  const t = chartTokens()
  const axis = axisBase()
  return {
    tooltip: { ...tooltipBase(), trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 4, right: 16, top: 24, bottom: 0, containLabel: true },
    xAxis: {
      type: 'category',
      data: satisfaction.value.scores.map((score) => `${score} 分`),
      ...axis,
      splitLine: { show: false }
    },
    yAxis: { type: 'value', ...axis },
    series: [
      {
        type: 'bar',
        barWidth: 28,
        data: satisfaction.value.counts,
        itemStyle: { color: t.success, borderRadius: [4, 4, 0, 0] },
        label: { show: true, position: 'top', color: t.text3, fontSize: 12 }
      }
    ]
  }
})

const aiOption = computed(() => {
  const t = chartTokens()
  return {
    tooltip: { ...tooltipBase(), trigger: 'item' },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: t.text2, fontSize: 12 }
    },
    color: [t.ai, t.border],
    series: [
      {
        type: 'pie',
        radius: ['58%', '78%'],
        center: ['50%', '44%'],
        label: { show: false },
        labelLine: { show: false },
        itemStyle: { borderColor: t.bgCard, borderWidth: 2 },
        data: [
          { name: '自助解决', value: ai.value?.resolvedSessions ?? 0 },
          { name: '转为工单', value: ai.value?.transferSessions ?? 0 }
        ]
      }
    ]
  }
})

const { chartRef: trendRef } = useChart(trendOption)
const { chartRef: distributionRef } = useChart(distributionOption)
const { chartRef: rankingRef } = useChart(rankingOption)
const { chartRef: satisfactionRef } = useChart(satisfactionOption)
const { chartRef: aiRef } = useChart(aiOption)

// 4 个核心工单总量指标
const kpiMain = computed(() => [
  { label: '工单总量', value: overview.value?.totalOrders ?? 0, icon: 'Tickets', hint: '历史全部工单' },
  { label: '待接单', value: overview.value?.pendingOrders ?? 0, icon: 'Aim', hint: '待接单池中的工单' },
  { label: '处理中', value: overview.value?.goingOrders ?? 0, icon: 'Odometer', hint: '已接单正在处理' },
  { label: '已完工', value: overview.value?.completedOrders ?? 0, icon: 'Finished', hint: '确认完工的工单' }
])

// 响应效率与服务质量指标
const kpiSub = computed(() => [
  { label: '平均响应', value: formatMinutes(overview.value?.avgResponseMinutes), icon: 'Stopwatch', tone: 'default', hint: '从提交到接单' },
  { label: '平均处理', value: formatMinutes(overview.value?.avgHandleMinutes), icon: 'TrendCharts', tone: 'primary', hint: '从接单到完工' },
  { label: '平均评分', value: overview.value?.avgScore ?? 0, unit: '分', icon: 'Stamp', tone: 'success', hint: '来自业主评价' },
  { label: 'SLA 超时', value: overview.value?.slaBreachedCount ?? 0, icon: 'Warning', tone: 'danger', hint: '已触发升级的工单' }
])
</script>

<template>
  <div class="layout-page admin-dashboard">
    <PageHeader title="统计看板">
      <el-radio-group v-model="trendDays" size="default" @change="onDaysChange">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="14">近 14 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
      </el-radio-group>
      <el-button :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
    </PageHeader>

    <SectionCard v-if="failed && !loading">
      <EmptyState
        icon="Warning"
        title="看板数据加载失败"
        description="请稍后重试，或确认后端服务状态。"
        action-text="重新加载"
        @action="loadAll"
      />
    </SectionCard>

    <template v-else>
      <!-- KPI 共用一块表面，避免浅色页面出现不一致的深色嵌套卡片。 -->
      <AppSkeleton v-if="loading" variant="card" :rows="4" :columns="4" />
      <div v-else class="kpi-panel">
        <div class="grid grid-4">
          <StatCard
            v-for="item in kpiMain"
            :key="item.label"
            :label="item.label"
            :value="item.value"
            :icon="item.icon"
            :hint="item.hint"
          />
        </div>
      </div>

      <AppSkeleton v-if="loading" variant="card" :rows="4" :columns="4" />
      <div v-else class="grid grid-4 dashboard-efficiency">
        <StatCard
          v-for="item in kpiSub"
          :key="item.label"
          :label="item.label"
          :value="item.value"
          :unit="item.unit"
          :icon="item.icon"
          :tone="item.tone"
          :hint="item.hint"
        />
      </div>

      <!-- 趋势 + 类型分布 -->
      <div class="grid dashboard-charts">
        <SectionCard title="工单趋势" :description="`近 ${trendDays} 天每日新增工单`">
          <AppSkeleton v-if="loading" variant="chart" :height="260" />
          <div v-else ref="trendRef" class="chart chart--md" />
        </SectionCard>

        <SectionCard title="工单类型分布" description="按一级报修类型统计">
          <AppSkeleton v-if="loading" variant="chart" :height="260" />
          <div v-else ref="distributionRef" class="chart chart--md" />
        </SectionCard>
      </div>

      <!-- 排行 + 满意度 -->
      <div class="grid grid-2">
        <SectionCard title="师傅接单排行" description="按完工量取前 5 名">
          <AppSkeleton v-if="loading" variant="chart" :height="260" />
          <div v-else ref="rankingRef" class="chart chart--md" />
        </SectionCard>

        <SectionCard title="满意度分布" :description="`共 ${satisfaction.total} 条评价，平均 ${satisfaction.avgScore} 分`">
          <AppSkeleton v-if="loading" variant="chart" :height="260" />
          <div v-else ref="satisfactionRef" class="chart chart--md" />
        </SectionCard>
      </div>

      <!-- AI 自助解决率 -->
      <SectionCard title="AI 自助解决率" description="智能助手会话的自助解决与转工单占比">
        <AppSkeleton v-if="loading" variant="chart" :height="240" />
        <div v-else class="ai-block">
          <div ref="aiRef" class="chart chart--sm" />
          <dl class="ai-block__stats">
            <div>
              <dt>会话总数</dt>
              <dd class="text-mono">{{ formatNumber(ai?.totalSessions ?? 0) }}</dd>
            </div>
            <div>
              <dt>自助解决</dt>
              <dd class="text-mono">{{ formatNumber(ai?.resolvedSessions ?? 0) }}</dd>
            </div>
            <div>
              <dt>转为工单</dt>
              <dd class="text-mono">{{ formatNumber(ai?.transferSessions ?? 0) }}</dd>
            </div>
            <div>
              <dt>解决率</dt>
              <dd class="text-mono ai-block__rate">{{ formatPercent(ai?.resolutionRate) }}</dd>
            </div>
            <div>
              <dt>Token 消耗</dt>
              <dd class="text-mono">
                {{ formatNumber((ai?.totalPromptTokens ?? 0) + (ai?.totalCompletionTokens ?? 0)) }}
              </dd>
            </div>
          </dl>
        </div>
      </SectionCard>
    </template>
  </div>
</template>

<style scoped>
/* 核心指标分栏展示，与整页使用同一主题。 */
.kpi-panel {
  border-radius: var(--radius-xl);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
}

.kpi-panel > .grid {
  gap: 0;
}

.kpi-panel :deep(.stat-card) {
  background: transparent;
  border: none;
  border-radius: 0;
  box-shadow: none;
}

.kpi-panel :deep(.stat-card + .stat-card) {
  border-left: 1px solid var(--color-divider);
}

@media (max-width: 1280px) {
  .kpi-panel :deep(.stat-card:nth-child(3)) {
    border-left: none;
  }

  .kpi-panel :deep(.stat-card:nth-child(n + 3)) {
    border-top: 1px solid var(--color-divider);
  }
}

@media (max-width: 768px) {
  .kpi-panel > .grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .kpi-panel :deep(.stat-card) {
    padding: var(--space-4);
  }
}

.chart {
  width: 100%;
}

.chart--md {
  height: 260px;
}

.chart--sm {
  height: 240px;
}

.ai-block {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: var(--space-5);
  align-items: center;
}

@media (max-width: 1024px) {
  .ai-block {
    grid-template-columns: minmax(0, 1fr);
  }
}

.ai-block__stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4) var(--space-5);
}

.ai-block__stats dt {
  margin-bottom: var(--space-1);
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.ai-block__stats dd {
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  color: var(--color-text-1);
}

.ai-block__rate {
  color: var(--color-ai-strong);
}
</style>
