<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Close } from '@element-plus/icons-vue'
import { uploadFile, publicFileUrl } from '@/api/file'
import { formatFileSize } from '@/utils/format'

// 多图上传：直连后端 /api/file/upload，本地预览 + 上传进度 + 删除
// 对外只暴露 v-model（已上传成功的 URL 数组），上传中的临时态只在组件内部维护
const props = defineProps({
  /** v-model：已上传成功的图片 URL 数组 */
  modelValue: { type: Array, default: () => [] },
  /** 最多张数 */
  limit: { type: Number, default: 3 },
  /** 业务类型：order / evaluate / avatar / knowledge，后端按它分目录 */
  bizType: { type: String, default: 'order' },
  accept: { type: String, default: 'image/*' },
  /** 单张大小上限（MB），与后端 10MB 限制保持一致 */
  maxSize: { type: Number, default: 10 },
  disabled: { type: Boolean, default: false },
  /** 只读模式：详情页展示已传图片，不显示删除与上传入口 */
  readonly: { type: Boolean, default: false },
  /** 自定义提示语，不传则用默认文案 */
  tip: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue'])

// 已上传成功的图片：直接由 v-model 派生，保证父子状态一致
const uploaded = computed(() =>
  (props.modelValue || []).map((url, index) => ({ key: `ok-${index}-${url}`, url }))
)

// 上传中的图片：本地临时态，含进度百分比
const uploading = ref([])

const total = computed(() => uploaded.value.length + uploading.value.length)
const canAdd = computed(() => !props.readonly && !props.disabled && total.value < props.limit)
const previewList = computed(() => uploaded.value.map((item) => publicFileUrl(item.url)))

const tipText = computed(
  () => props.tip || `支持 jpg / png / webp，单张不超过 ${props.maxSize}MB，最多 ${props.limit} 张`
)

/** 本地校验：类型与大小不合格的文件直接拦下，不进上传队列 */
function beforeUpload(file) {
  if (!file.type.startsWith('image/')) {
    ElMessage.error('只能上传图片文件')
    return false
  }
  if (file.size > props.maxSize * 1024 * 1024) {
    ElMessage.error(`图片不能超过 ${props.maxSize}MB`)
    return false
  }
  if (total.value >= props.limit) {
    ElMessage.warning(`最多上传 ${props.limit} 张图片`)
    return false
  }
  return true
}

/** 自定义上传：走 src/api，进度写进本地临时态，成功后把 URL 追加进 v-model */
async function customUpload(options) {
  const { file } = options
  const key = `up-${Date.now()}-${file.uid || Math.random().toString(36).slice(2, 8)}`
  uploading.value = [...uploading.value, { key, name: file.name, size: file.size, percent: 0 }]

  try {
    const res = await uploadFile(file, props.bizType, (event) => {
      if (!event.total) return
      const percent = Math.round((event.loaded * 100) / event.total)
      uploading.value = uploading.value.map((item) => (item.key === key ? { ...item, percent } : item))
    })
    // 后端约定返回 { url, name }
    const url = res?.url || res
    if (url) {
      emit('update:modelValue', [...(props.modelValue || []), url])
    }
    options.onSuccess?.(res)
  } catch (error) {
    options.onError?.(error)
    // 拦截器已做过统一提示，这里只兜底网络层异常
    if (error?.code === 'NETWORK' || error?.code === 'TIMEOUT') {
      ElMessage.error('图片上传失败，请稍后重试')
    }
  } finally {
    uploading.value = uploading.value.filter((item) => item.key !== key)
  }
}

/** 删除第 index 张已上传图片 */
function removeAt(index) {
  const next = (props.modelValue || []).filter((_, i) => i !== index)
  emit('update:modelValue', next)
}
</script>

<template>
  <div class="image-upload">
    <ul class="image-upload__grid">
      <!-- 已上传图片：点击可放大预览 -->
      <li v-for="(item, index) in uploaded" :key="item.key" class="image-upload__item">
        <el-image
          class="image-upload__image"
          :src="publicFileUrl(item.url)"
          :preview-src-list="previewList"
          :initial-index="index"
          fit="cover"
          preview-teleported
          hide-on-click-modal
        />
        <button
          v-if="!props.readonly && !props.disabled"
          class="image-upload__remove"
          type="button"
          aria-label="删除图片"
          @click="removeAt(index)"
        >
          <el-icon><Close /></el-icon>
        </button>
      </li>

      <!-- 上传中的图片：展示文件名与环形进度 -->
      <li v-for="item in uploading" :key="item.key" class="image-upload__item is-uploading">
        <el-progress type="circle" :percentage="item.percent" :width="52" :stroke-width="4" />
        <span class="image-upload__pending truncate">{{ item.name }}</span>
        <span class="image-upload__pending text-mono">{{ formatFileSize(item.size) }}</span>
      </li>

      <!-- 上传入口：达到上限后自动隐藏 -->
      <li v-if="canAdd" class="image-upload__item is-trigger">
        <el-upload
          class="image-upload__uploader"
          action="#"
          :show-file-list="false"
          :multiple="props.limit > 1"
          :accept="props.accept"
          :disabled="props.disabled"
          :before-upload="beforeUpload"
          :http-request="customUpload"
        >
          <slot name="trigger">
            <div class="image-upload__add">
              <el-icon><Plus /></el-icon>
              <span>上传图片</span>
            </div>
          </slot>
        </el-upload>
      </li>
    </ul>

    <p v-if="!props.readonly && tipText" class="image-upload__tip">{{ tipText }}</p>
  </div>
</template>

<style scoped>
.image-upload__grid {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.image-upload__item {
  position: relative;
  width: 96px;
  height: 96px;
  border-radius: var(--radius-md);
  overflow: hidden;
}

.image-upload__image {
  width: 100%;
  height: 100%;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-sunken);
}

.image-upload__remove {
  position: absolute;
  top: 4px;
  right: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: none;
  border-radius: var(--radius-full);
  background: var(--color-bg-mask);
  color: var(--color-text-inverse);
  font-size: 12px;
  cursor: pointer;
  transition: background var(--dur-fast) var(--ease-out);
}

.image-upload__remove:hover {
  background: var(--color-danger);
}

.image-upload__item.is-uploading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  padding: var(--space-2);
  border: 1px dashed var(--color-border-strong);
  background: var(--color-bg-sunken);
}

.image-upload__pending {
  max-width: 100%;
  font-size: 11px;
  color: var(--color-text-3);
}

.image-upload__item.is-trigger {
  border: 1px dashed var(--color-border-strong);
  background: var(--color-bg-sunken);
}

.image-upload__uploader {
  width: 100%;
  height: 100%;
}

.image-upload__uploader :deep(.el-upload) {
  width: 100%;
  height: 100%;
}

.image-upload__add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  width: 100%;
  height: 100%;
  color: var(--color-text-3);
  font-size: var(--fs-xs);
  transition: color var(--dur-fast) var(--ease-out);
}

.image-upload__add .el-icon {
  font-size: 20px;
}

.image-upload__uploader:hover .image-upload__add {
  color: var(--color-primary);
}

.image-upload__tip {
  margin-top: var(--space-2);
  font-size: var(--fs-xs);
  color: var(--color-text-3);
  line-height: var(--lh-base);
}
</style>
