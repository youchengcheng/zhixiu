<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { PAGE_SIZE_OPTIONS } from '@/utils/env'

// 全站统一分页：布局固定 total/sizes/prev/pager/next/jumper，避免各页样式漂移
const props = defineProps({
  total: { type: Number, default: 0 },
  page: { type: Number, default: 1 },
  size: { type: Number, default: 10 },
  pageSizes: { type: Array, default: () => PAGE_SIZE_OPTIONS },
  layout: { type: String, default: 'total, sizes, prev, pager, next, jumper' },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:page', 'update:size', 'change'])

const compact = ref(false)
let media
function syncCompact() {
  compact.value = media.matches
}
onMounted(() => {
  media = window.matchMedia('(max-width: 640px)')
  syncCompact()
  media.addEventListener('change', syncCompact)
})
onBeforeUnmount(() => media?.removeEventListener('change', syncCompact))
// 使用 Element Plus 的 slot 布局项，为每页条数选择器提供明确的辅助名称。
const visibleLayout = computed(() => props.layout.split(',')
  .map(item => item.trim())
  .filter(item => !compact.value || !['sizes', 'jumper'].includes(item))
  .map(item => item === 'sizes' ? 'slot' : item)
  .join(', '))

function onCurrentChange(value) {
  emit('update:page', value)
  emit('change', { page: value, size: props.size })
}

function onSizeChange(value) {
  emit('update:size', value)
  emit('update:page', 1)
  emit('change', { page: 1, size: value })
}
</script>

<template>
  <div class="pagination">
    <el-pagination
      :current-page="props.page"
      :page-size="props.size"
      :page-sizes="props.pageSizes"
      :total="props.total"
      :layout="visibleLayout"
      :pager-count="compact ? 5 : 7"
      :disabled="props.disabled"
      @current-change="onCurrentChange"
      @size-change="onSizeChange"
    >
      <el-select
        v-if="!compact && props.layout.split(',').some(item => item.trim() === 'sizes')"
        :model-value="props.size"
        :disabled="props.disabled"
        aria-label="每页条数"
        class="pagination__size"
        @change="onSizeChange"
      >
        <el-option v-for="size in props.pageSizes" :key="size" :value="size" :label="`${size} 条/页`" />
      </el-select>
    </el-pagination>
  </div>
</template>

<style scoped>
.pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: var(--space-4);
  min-width: 0;
}

.pagination__size {
  width: 128px;
}

@media (max-width: 640px) {
  .pagination {
    justify-content: center;
  }

  .pagination :deep(.el-pagination) {
    gap: var(--space-1);
    flex-wrap: wrap;
    justify-content: center;
  }

  .pagination :deep(.el-pagination__total) {
    flex-basis: 100%;
    text-align: center;
  }
}
</style>
