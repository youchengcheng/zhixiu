<script setup>
import { computed, ref, watch } from 'vue'
import { nameInitials } from '@/utils/format'
import { publicFileUrl } from '@/api/file'

const props = defineProps({
  name: { type: String, default: '' },
  /** 头像图片地址：有值时优先显示图片，为空或加载失败才回退姓名首字 */
  src: { type: String, default: '' },
  size: { type: Number, default: 32 },
  /** square 用于列表，circle 用于用户区 */
  shape: { type: String, default: 'circle' }
})

// 图片加载失败标记：失败后回退首字，避免出现破图
const failed = ref(false)

// 图片地址变化（如重新上传头像）时重置失败标记，允许再次尝试加载
watch(
  () => props.src,
  () => {
    failed.value = false
  }
)

// 只有存在地址且未失败时才走图片分支
const imageUrl = computed(() => (props.src && !failed.value ? publicFileUrl(props.src) : ''))

const initials = computed(() => nameInitials(props.name))
const style = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  fontSize: `${Math.max(11, Math.round(props.size * 0.38))}px`,
  borderRadius: props.shape === 'square' ? 'var(--radius-md)' : '50%'
}))
</script>

<template>
  <span class="letter-avatar" :class="{ 'is-image': imageUrl }" :style="style" aria-hidden="true">
    <!-- 有头像图片时渲染图片，加载失败由 @error 回退到首字 -->
    <img v-if="imageUrl" class="letter-avatar__img" :src="imageUrl" alt="" @error="failed = true" />
    <template v-else>{{ initials }}</template>
  </span>
</template>

<style scoped>
.letter-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-weight: var(--fw-medium);
  letter-spacing: 0.02em;
  user-select: none;
  border: 1px solid var(--color-primary-border);
}

/* 图片模式：去掉文字排版的留白，裁切铺满整个头像框 */
.letter-avatar.is-image {
  overflow: hidden;
  background: var(--color-bg-sunken);
  border-color: var(--color-border);
}

.letter-avatar__img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
