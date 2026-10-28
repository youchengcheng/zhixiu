<script setup>
import { computed } from 'vue'
import { dictOptions } from '@/utils/dict'

// 字典下拉：选项文案统一来自 dict.js，页面只传字典对象
const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  dict: { type: Object, required: true },
  placeholder: { type: String, default: '请选择' },
  clearable: { type: Boolean, default: true },
  disabled: { type: Boolean, default: false },
  size: { type: String, default: 'default' }
})

const emit = defineEmits(['update:modelValue', 'change'])

const options = computed(() => dictOptions(props.dict))

function onChange(value) {
  emit('update:modelValue', value)
  emit('change', value)
}
</script>

<template>
  <el-select
    :model-value="props.modelValue"
    :placeholder="props.placeholder"
    :clearable="props.clearable"
    :disabled="props.disabled"
    :size="props.size"
    @update:model-value="onChange"
  >
    <el-option v-for="opt in options" :key="opt.value" :label="opt.label" :value="opt.value" />
  </el-select>
</template>
