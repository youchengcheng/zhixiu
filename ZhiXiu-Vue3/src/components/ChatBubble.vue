<script setup>
import { computed } from 'vue'
import MarkdownView from './MarkdownView.vue'
import { formatTime } from '@/utils/format'

// 对话气泡：USER 靠右纯文本，AI 靠左走 MarkdownView（AI 回答必须经 MarkdownView 渲染防 XSS）
const props = defineProps({
  /** USER 用户提问；AI 智能助手回答 */
  sender: { type: String, default: 'AI' },
  content: { type: String, default: '' },
  /** AI 正在生成：无内容时显示打字动画，有内容时尾部显示光标 */
  loading: { type: Boolean, default: false },
  /** 时间，展示为 HH:mm */
  time: { type: [String, Number, Date], default: '' },
  /** 用户显示名；不传则用「我」 */
  name: { type: String, default: '' }
})

const isUser = computed(() => props.sender === 'USER')
const who = computed(() => (isUser.value ? props.name || '我' : '智能助手'))
// 头像用单字，比默认人像图标更贴近真实对话
const initial = computed(() => (isUser.value ? who.value.slice(-1) : '智'))
const timeText = computed(() => (props.time ? formatTime(props.time, '') : ''))
</script>

<template>
  <div class="chat-bubble" :class="isUser ? 'is-user' : 'is-ai'">
    <span class="chat-bubble__avatar" aria-hidden="true">{{ initial }}</span>

    <div class="chat-bubble__main">
      <div class="chat-bubble__meta">
        <span class="chat-bubble__who">{{ who }}</span>
        <span v-if="timeText" class="chat-bubble__time text-mono">{{ timeText }}</span>
      </div>

      <div class="chat-bubble__body">
        <!-- AI：Markdown 渲染 -->
        <MarkdownView v-if="!isUser && props.content" :content="props.content" :streaming="props.loading" />

        <!-- 用户：纯文本，保留换行 -->
        <p v-else-if="isUser && props.content" class="chat-bubble__plain">{{ props.content }}</p>

        <!-- 等待首字：打字动画 -->
        <span v-else-if="props.loading" class="chat-bubble__typing" role="status" aria-label="正在生成回答">
          <i />
          <i />
          <i />
        </span>
      </div>

      <!-- 引用来源 / 槽位追问 / 草稿卡等附加内容由页面通过默认插槽注入 -->
      <slot />
    </div>
  </div>
</template>

<style scoped>
.chat-bubble {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
}

.chat-bubble.is-user {
  flex-direction: row-reverse;
}

.chat-bubble__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-full);
  font-size: var(--fs-xs);
  font-weight: var(--fw-medium);
}

.is-ai .chat-bubble__avatar {
  background: var(--color-ai-soft);
  border: 1px solid var(--color-ai-border);
  color: var(--color-ai-strong);
}

.is-user .chat-bubble__avatar {
  background: var(--color-primary-soft);
  border: 1px solid var(--color-primary-border);
  color: var(--color-primary);
}

.chat-bubble__main {
  min-width: 0;
  max-width: 78%;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.is-user .chat-bubble__main {
  align-items: flex-end;
}

.chat-bubble__meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.is-user .chat-bubble__meta {
  flex-direction: row-reverse;
}

.chat-bubble__who {
  font-weight: var(--fw-medium);
  color: var(--color-text-2);
}

.chat-bubble__body {
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-lg);
  font-size: var(--fs-body);
}

.is-ai .chat-bubble__body {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-top-left-radius: var(--radius-xs);
}

@media (max-width: 768px) {
  .chat-bubble {
    gap: var(--space-2);
  }

  .chat-bubble__main {
    max-width: calc(100% - 40px);
  }
}

.is-user .chat-bubble__body {
  background: var(--color-primary-soft);
  border: 1px solid var(--color-primary-border);
  border-top-right-radius: var(--radius-xs);
  color: var(--color-text-1);
}

.chat-bubble__plain {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: var(--lh-base);
}

/* 打字动画：三点依次起落，降低动态偏好下静默 */
.chat-bubble__typing {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 20px;
}

.chat-bubble__typing i {
  width: 6px;
  height: 6px;
  border-radius: var(--radius-full);
  background: var(--color-ai-strong);
  opacity: 0.4;
  animation: bubble-typing 1.2s var(--ease-in-out) infinite;
}

.chat-bubble__typing i:nth-child(2) {
  animation-delay: 0.15s;
}

.chat-bubble__typing i:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes bubble-typing {
  0%,
  60%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }
  30% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .chat-bubble__typing i {
    animation: none;
    opacity: 0.6;
  }
}
</style>
