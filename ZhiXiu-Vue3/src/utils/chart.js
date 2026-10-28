import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import echarts from './echarts'

// 图表与设计令牌同源：canvas 不认 var()，所以在运行时把 CSS 自定义属性解析成具体颜色，
// 避免在图表配置里硬编码十六进制色值（令牌改动时图表会一并跟随）

/** 读取语义令牌值 */
export function token(name, fallback = '') {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name)
  return value ? value.trim() : fallback
}

/** 看板用到的颜色集合 */
export function chartTokens() {
  return {
    text1: token('--color-text-1'),
    text2: token('--color-text-2'),
    text3: token('--color-text-3'),
    border: token('--color-border'),
    divider: token('--color-divider'),
    bgCard: token('--color-bg-card'),
    bgSunken: token('--color-bg-sunken'),
    primary: token('--color-primary'),
    ai: token('--color-ai'),
    success: token('--color-success'),
    warning: token('--color-warning'),
    danger: token('--color-danger'),
    info: token('--color-info'),
    kpiText1: token('--kpi-text-1'),
    kpiText2: token('--kpi-text-2'),
    kpiBorder: token('--kpi-panel-border')
  }
}

/** 分类色板：主色点缀 + 状态色，灰度承担主要视觉重量 */
export function chartPalette() {
  const c = chartTokens()
  return [c.primary, c.ai, c.warning, c.success, c.danger, c.info]
}

/** 统一的坐标轴与网格样式：细线、三级文字色、无多余装饰 */
export function axisBase() {
  const c = chartTokens()
  return {
    axisLine: { lineStyle: { color: c.border } },
    axisTick: { show: false },
    axisLabel: { color: c.text3, fontSize: 12 },
    splitLine: { lineStyle: { color: c.divider, type: 'dashed' } }
  }
}

/** 统一的 tooltip 样式 */
export function tooltipBase() {
  const c = chartTokens()
  return {
    backgroundColor: c.bgCard,
    borderColor: c.border,
    borderWidth: 1,
    padding: [8, 12],
    textStyle: { color: c.text1, fontSize: 12 },
    extraCssText: 'box-shadow: var(--shadow-md); border-radius: var(--radius-md);'
  }
}

/**
 * 图表挂载组合式函数：负责初始化、选项更新、容器尺寸变化重绘与销毁
 * @param {import('vue').Ref<object>} optionRef 响应式图表配置
 * @returns {{ chartRef: import('vue').Ref, render: Function }}
 */
export function useChart(optionRef) {
  const chartRef = ref(null)
  let instance = null
  let observer = null

  function resize() {
    instance?.resize()
  }

  function render() {
    const el = chartRef.value
    if (!el) return
    if (!instance) instance = echarts.init(el)
    instance.setOption(optionRef.value || {}, true)
  }

  onMounted(async () => {
    await nextTick()
    render()
    // 侧栏折叠、窗口缩放都会改变容器宽度，交给 ResizeObserver 处理
    if (typeof ResizeObserver !== 'undefined') {
      observer = new ResizeObserver(resize)
      if (chartRef.value) observer.observe(chartRef.value)
    } else {
      window.addEventListener('resize', resize)
    }
  })

  // flush: 'post' 保证图表容器已渲染到 DOM 后再初始化
  watch(optionRef, render, { deep: true, flush: 'post' })

  onBeforeUnmount(() => {
    observer?.disconnect()
    window.removeEventListener('resize', resize)
    instance?.dispose()
    instance = null
  })

  return { chartRef, render }
}
