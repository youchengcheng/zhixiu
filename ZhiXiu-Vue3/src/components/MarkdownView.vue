<script setup>
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/core'

// 代码高亮语言按需注册：highlight.js 全量包很大，只引项目实际会用到的语言
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import json from 'highlight.js/lib/languages/json'
import bash from 'highlight.js/lib/languages/bash'
import sql from 'highlight.js/lib/languages/sql'
import java from 'highlight.js/lib/languages/java'
import xml from 'highlight.js/lib/languages/xml'
import yaml from 'highlight.js/lib/languages/yaml'
import plaintext from 'highlight.js/lib/languages/plaintext'

// 高亮主题用库自带样式表（含固定色值，属于第三方资源，不占用组件样式块）
import 'highlight.js/styles/github.css'

hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('json', json)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('shell', bash)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('java', java)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('plaintext', plaintext)

/**
 * HTML 转义：把原始 HTML 当成纯文本输出。
 * AI 返回内容里若混入 <script> / <img onerror> 等，会在第一道防线就被转义掉。
 */
function escapeHtml(text) {
  return String(text ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

// 第一道防线：改写 marked 渲染器 —— 关闭原始 HTML、代码块交给 hljs 高亮
marked.use({
  gfm: true,
  breaks: true,
  renderer: {
    // 原始 HTML 直接转义，杜绝直通注入
    html(token) {
      return escapeHtml(token.text ?? token.raw ?? '')
    },
    // 代码块：识别语言后高亮；识别不到就按纯文本转义展示
    code(token) {
      const lang = String(token.lang || '')
        .trim()
        .split(/\s+/)[0]
        .toLowerCase()
      const raw = token.code ?? ''
      const body =
        lang && hljs.getLanguage(lang)
          ? hljs.highlight(raw, { language: lang, ignoreIllegals: true }).value
          : escapeHtml(raw)
      const cls = lang ? ` class="hljs language-${escapeHtml(lang)}"` : ' class="hljs"'
      return `<pre class="md-pre"><code${cls}>${body}</code></pre>`
    }
  }
})

// 第二道防线：DOMPurify 白名单过滤，只放行 Markdown 实际会用到的标签与属性
DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  // 外链一律新窗口打开，并切断 opener 引用
  if (node.tagName === 'A') {
    node.setAttribute('target', '_blank')
    node.setAttribute('rel', 'noopener noreferrer')
  }
})

const ALLOWED_TAGS = [
  'p', 'br', 'hr', 'strong', 'em', 'del', 's',
  'code', 'pre', 'blockquote', 'span',
  'ul', 'ol', 'li',
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'a', 'img',
  'table', 'thead', 'tbody', 'tr', 'th', 'td'
]

const ALLOWED_ATTR = ['href', 'title', 'target', 'rel', 'class', 'src', 'alt']

const props = defineProps({
  /** Markdown 原文（AI 回答或知识库正文） */
  content: { type: String, default: '' },
  /** 流式输出中：末尾展示打字光标 */
  streaming: { type: Boolean, default: false }
})

// AI 回答必须经这里渲染，页面禁止直接 v-html 原文
const html = computed(() => {
  if (!props.content) return ''
  const rendered = marked.parse(props.content)
  return DOMPurify.sanitize(rendered, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
    // 禁止 data: / javascript: 之类的协议，只允许 http(s) 与站内相对路径
    ALLOWED_URI_REGEXP: /^(?:https?:|mailto:|#|\/)/
  })
})
</script>

<template>
  <div class="markdown-view" :class="{ 'is-streaming': props.streaming }" v-html="html" />
</template>

<style scoped>
.markdown-view {
  font-size: var(--fs-body);
  line-height: var(--lh-loose);
  color: var(--color-text-1);
  word-break: break-word;
}

.markdown-view :deep(> *:first-child) {
  margin-top: 0;
}

.markdown-view :deep(> *:last-child) {
  margin-bottom: 0;
}

.markdown-view :deep(p) {
  margin: 0 0 var(--space-3);
}

.markdown-view :deep(h1),
.markdown-view :deep(h2),
.markdown-view :deep(h3),
.markdown-view :deep(h4) {
  margin: var(--space-4) 0 var(--space-2);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-tight);
}

.markdown-view :deep(h1) {
  font-size: var(--fs-h1);
}

.markdown-view :deep(h2) {
  font-size: var(--fs-h2);
}

.markdown-view :deep(h3),
.markdown-view :deep(h4) {
  font-size: var(--fs-h3);
}

.markdown-view :deep(ul),
.markdown-view :deep(ol) {
  margin: 0 0 var(--space-3);
  padding-left: var(--space-5);
}

.markdown-view :deep(li) {
  margin-bottom: var(--space-1);
}

.markdown-view :deep(li::marker) {
  color: var(--color-text-3);
}

.markdown-view :deep(strong) {
  font-weight: var(--fw-semibold);
}

.markdown-view :deep(a) {
  color: var(--color-link);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.markdown-view :deep(blockquote) {
  margin: 0 0 var(--space-3);
  padding: var(--space-2) var(--space-4);
  border-left: 3px solid var(--color-ai-border);
  background: var(--color-ai-soft);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  color: var(--color-text-2);
}

/* 行内代码 */
.markdown-view :deep(code) {
  padding: 1px 5px;
  border-radius: var(--radius-xs);
  background: var(--color-bg-sunken);
  font-family: var(--font-mono);
  font-size: 0.92em;
}

/* 代码块：外层容器统一留白与滚动，内层由 highlight.js 主题接管配色 */
.markdown-view :deep(.md-pre) {
  margin: 0 0 var(--space-3);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-sunken);
  overflow-x: auto;
}

.markdown-view :deep(.md-pre code) {
  padding: 0;
  background: transparent;
  font-size: var(--fs-sm);
  line-height: var(--lh-base);
}

.markdown-view :deep(table) {
  width: 100%;
  margin: 0 0 var(--space-3);
  border-collapse: collapse;
  font-size: var(--fs-sm);
}

.markdown-view :deep(th),
.markdown-view :deep(td) {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  text-align: left;
}

.markdown-view :deep(th) {
  background: var(--color-bg-sunken);
  font-weight: var(--fw-medium);
}

.markdown-view :deep(img) {
  max-width: 100%;
  border-radius: var(--radius-md);
}

.markdown-view :deep(hr) {
  margin: var(--space-4) 0;
  border: none;
  border-top: 1px solid var(--color-divider);
}

/* 流式输出光标：有 content 时贴在最后一个字符后面 */
.markdown-view.is-streaming :deep(> *:last-child::after) {
  content: '';
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: var(--color-ai);
  animation: md-caret 1s step-end infinite;
}

@keyframes md-caret {
  50% {
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .markdown-view.is-streaming :deep(> *:last-child::after) {
    animation: none;
  }
}
</style>
