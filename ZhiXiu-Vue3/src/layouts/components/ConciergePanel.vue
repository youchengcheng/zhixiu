<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowUp, ChatDotRound, Clock, Close, Plus } from '@element-plus/icons-vue'
import MarkdownView from '@/components/MarkdownView.vue'
import {
  createConciergeSession, fetchConciergeTable, listConciergeHistory, listConciergeSessions, streamConciergeChat
} from '@/api/concierge'

const props = defineProps({
  roleCode: { type: String, required: true },
  orderId: { type: String, default: null }
})

const router = useRouter()
const open = ref(false)
const showingHistory = ref(false)
const sessions = ref([])
const sessionPage = ref(0)
const moreSessions = ref(false)
const sessionsLoading = ref(false)
const selected = ref('')
const messages = ref([])
const messagePage = ref(0)
const moreMessages = ref(false)
const messageLoading = ref(false)
const busy = ref(false)
const issue = ref('')
const draft = ref('')
const logElement = ref(null)
const launcher = ref(null)
const viewportHeight = ref(0)
const viewportTop = ref(0)
const narrow = ref(false)
let abort = null
let activeRun = null
let disposed = false

const title = computed(() => sessions.value.find(item => item.id === selected.value)?.title || '新对话')
const drawerStyle = computed(() => narrow.value && viewportHeight.value
  ? { '--concierge-height': viewportHeight.value + 'px', '--concierge-top': viewportTop.value + 'px' }
  : {})
const choices = computed(() => {
  const roleChoice = {
    OWNER: '查看我的工单',
    WORKER: '看看我的待办',
    ADMIN: '有哪些待审核师傅？'
  }[props.roleCode]
  if (props.orderId) return ['这张工单到哪一步了？', '查看我的通知']
  return [roleChoice, '查看我的通知', '查看社区公告'].filter(Boolean)
})

function syncViewport() {
  narrow.value = window.matchMedia('(max-width: 640px)').matches
  viewportHeight.value = window.visualViewport?.height || window.innerHeight
  viewportTop.value = window.visualViewport?.offsetTop || 0
}

onMounted(() => {
  syncViewport()
  window.addEventListener('resize', syncViewport)
  window.visualViewport?.addEventListener('resize', syncViewport)
  window.visualViewport?.addEventListener('scroll', syncViewport)
})

onBeforeUnmount(() => {
  disposed = true
  activeRun = null
  abort?.()
  window.removeEventListener('resize', syncViewport)
  window.visualViewport?.removeEventListener('resize', syncViewport)
  window.visualViewport?.removeEventListener('scroll', syncViewport)
})

async function loadSessions(page = 1) {
  if (sessionsLoading.value) return false
  sessionsLoading.value = true
  try {
    const data = await listConciergeSessions(page)
    if (disposed) return false
    const restored = page === 1 ? [] : sessions.value
    const all = new Map(restored.map(item => [item.id, item]))
    data.records.forEach(item => all.set(item.id, item))
    sessions.value = [...all.values()]
    sessionPage.value = page
    moreSessions.value = page < data.pages
    return true
  } catch (error) {
    issue.value = error.message || '会话暂时无法加载'
    return false
  } finally {
    sessionsLoading.value = false
  }
}

async function show() {
  open.value = true
  if (sessionPage.value) return
  issue.value = ''
  if (await loadSessions() && !selected.value && !busy.value && sessions.value.length) {
    await choose(sessions.value[0].id)
  }
}

async function choose(id) {
  if (busy.value || messageLoading.value) return
  issue.value = ''
  messageLoading.value = true
  try {
    const data = await listConciergeHistory(id)
    if (disposed) return
    selected.value = id
    messages.value = data.records.slice().reverse()
    messagePage.value = 1
    moreMessages.value = data.pages > 1
    showingHistory.value = false
    await scrollToEnd(true)
  } catch (error) {
    issue.value = error.message || '聊天历史暂时无法加载'
  } finally {
    messageLoading.value = false
  }
}

async function loadOlder() {
  if (messageLoading.value || busy.value) return
  messageLoading.value = true
  const oldHeight = logElement.value?.scrollHeight ?? 0
  const oldTop = logElement.value?.scrollTop ?? 0
  const page = messagePage.value + 1
  try {
    const data = await listConciergeHistory(selected.value, page)
    const rendered = new Set(messages.value.map(item => item.id))
    messages.value.unshift(...data.records.slice().reverse().filter(item => !rendered.has(item.id)))
    messagePage.value = page
    moreMessages.value = page < data.pages
    await nextTick()
    if (logElement.value) logElement.value.scrollTop = oldTop + logElement.value.scrollHeight - oldHeight
  } catch (error) {
    issue.value = error.message || '更早的消息加载失败'
  } finally {
    messageLoading.value = false
  }
}

function newConversation() {
  selected.value = ''
  messages.value = []
  messagePage.value = 0
  moreMessages.value = false
  showingHistory.value = false
  issue.value = ''
  draft.value = ''
}

async function send(text = draft.value) {
  const content = text.trim()
  if (!content || busy.value || messageLoading.value || disposed) return
  busy.value = true
  issue.value = ''
  try {
    if (!selected.value) {
      const session = await createConciergeSession()
      if (disposed) return
      selected.value = session.id
      sessions.value = [session, ...sessions.value]
    }
    const turn = reactive({
      requestId: requestId(), content, orderId: props.orderId, state: 'PENDING', answer: '', citations: []
    })
    messages.value.push(turn)
    draft.value = ''
    run(turn)
    scrollToEnd(true)
  } catch (error) {
    issue.value = error.message || '会话创建失败'
    busy.value = false
  }
}

function requestId() {
  if (globalThis.crypto.randomUUID) return globalThis.crypto.randomUUID()
  const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = [...bytes].map(value => value.toString(16).padStart(2, '0')).join('')
  return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join('-')
}

function run(turn) {
  busy.value = true
  activeRun = turn
  turn.state = 'PENDING'
  turn.answer = ''
  turn.error = ''
  let complete = false
  const finish = (error) => {
    if (activeRun !== turn) return
    if (!complete) {
      turn.state = 'FAILED'
      turn.error = error?.message || '连接中断，可以重试这条消息'
    }
    activeRun = null
    abort = null
    busy.value = false
  }
  abort = streamConciergeChat(selected.value, {
    requestId: turn.requestId, content: turn.content, orderId: turn.orderId
  }, {
    onEvent(event, data) {
      if (activeRun !== turn) return
      if (event === 'message') turn.answer += data.delta || ''
      if (event === 'table') turn.table = data
      if (event === 'citations') turn.citations = data.items || []
      if (event === 'link') turn.link = data
      if (event === 'done') {
        turn.id = data.messageId
        turn.hasTable = data.hasTable
        turn.state = 'COMPLETE'
        complete = true
        finish()
        loadSessions()
      }
      scrollToEnd()
    },
    onDone: () => finish(),
    onError: finish
  })
}

async function loadTable(turn, page = 1) {
  if (turn.tableLoading || !turn.id) return
  turn.tableLoading = true
  turn.tableError = ''
  try {
    turn.table = await fetchConciergeTable(selected.value, turn.id, page)
  } catch (error) {
    turn.table = null
    turn.tableError = error.message || '查询失败，请重试'
  } finally {
    turn.tableLoading = false
  }
}

function navigate(path) {
  if (!path?.startsWith('/' + props.roleCode.toLowerCase() + '/')) return
  open.value = false
  router.push(path)
}

async function scrollToEnd(force = false) {
  const el = logElement.value
  const nearEnd = !el || el.scrollHeight - el.scrollTop - el.clientHeight < 120
  await nextTick()
  if ((force || nearEnd) && logElement.value) logElement.value.scrollTop = logElement.value.scrollHeight
}

function onComposerKey(event) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return
  event.preventDefault()
  send()
}
</script>

<template>
  <button ref="launcher" type="button" class="concierge-launch" :aria-expanded="open" @click="show">
    <el-icon aria-hidden="true"><ChatDotRound /></el-icon>
    <span>AI 管家</span>
  </button>
  <el-drawer
    v-model="open"
    title="AI 管家"
    direction="rtl"
    append-to-body
    :modal="narrow"
    :modal-penetrable="!narrow"
    :lock-scroll="narrow"
    :with-header="false"
    :size="narrow ? '100%' : 'min(480px, 100vw)'"
    :style="drawerStyle"
    class="concierge-drawer"
    @opened="scrollToEnd(true)"
    @closed="launcher?.focus()"
  >
    <section class="concierge-panel" aria-label="AI 管家">
      <header class="concierge-heading">
        <div class="concierge-heading__titles">
          <h2>AI 管家</h2>
          <p v-if="selected">{{ title }}</p>
        </div>
        <button type="button" class="concierge-icon" aria-label="会话历史" title="会话历史"
          :aria-pressed="showingHistory" @click="showingHistory = !showingHistory">
          <el-icon><Clock /></el-icon>
        </button>
        <button type="button" class="concierge-icon" aria-label="新对话" title="新对话"
          :disabled="busy || messageLoading" @click="newConversation">
          <el-icon><Plus /></el-icon>
        </button>
        <button type="button" class="concierge-icon" aria-label="关闭管家" title="关闭管家" @click="open = false">
          <el-icon><Close /></el-icon>
        </button>
      </header>

      <div v-if="issue" class="concierge-alert" role="alert">
        {{ issue }}
        <button v-if="!sessionPage" type="button" class="concierge-link" @click="show">重试</button>
      </div>

      <div v-if="showingHistory" class="concierge-history" aria-label="会话历史" :aria-busy="sessionsLoading">
        <el-skeleton v-if="sessionsLoading && !sessions.length" :rows="3" animated />
        <p v-else-if="!sessions.length" class="concierge-muted">还没有会话记录。</p>
        <ul v-else>
          <li v-for="item in sessions" :key="item.id">
            <button type="button" :aria-current="item.id === selected ? 'true' : undefined"
              :disabled="busy || messageLoading" @click="choose(item.id)">
              <span>{{ item.title }}</span><small>{{ item.updateTime }}</small>
            </button>
          </li>
        </ul>
        <button v-if="moreSessions" type="button" class="concierge-link" :disabled="sessionsLoading"
          @click="loadSessions(sessionPage + 1)">更多会话</button>
      </div>

      <template v-else>
        <div ref="logElement" class="concierge-messages" aria-label="对话消息" :aria-busy="messageLoading">
          <el-skeleton v-if="messageLoading && !messages.length" :rows="3" animated />
          <div v-else-if="!messages.length" class="concierge-start">
            <h3>你想查什么？</h3>
            <button v-for="text in choices" :key="text" type="button" :disabled="busy" @click="send(text)">{{ text }}</button>
          </div>
          <button v-if="moreMessages" type="button" class="concierge-link concierge-earlier"
            :disabled="messageLoading || busy" @click="loadOlder">加载更早消息</button>
          <article v-for="turn in messages" :key="turn.requestId" class="concierge-turn">
            <p class="concierge-question">{{ turn.content }}</p>
            <div class="concierge-answer">
              <MarkdownView v-if="turn.answer && !turn.hasTable && !turn.table" :content="turn.answer" />
              <p v-if="turn.state === 'PENDING' && !turn.answer" class="concierge-muted" role="status">
                {{ busy ? '正在查询…' : '这轮回复尚未完成' }}
              </p>
              <p v-if="turn.error" class="concierge-alert" role="alert">{{ turn.error }}</p>
              <button v-if="turn.state !== 'COMPLETE' && !busy" type="button" class="concierge-link" @click="run(turn)">
                重试这条消息
              </button>
              <button v-if="turn.link" type="button" class="concierge-link" @click="navigate(turn.link.path)">
                {{ turn.link.label }}
              </button>
              <p v-if="turn.citations?.length" class="concierge-source">
                引用知识库：{{ turn.citations.map(item => item.title).filter(Boolean).join('、') || '来源标题暂不可用' }}
              </p>
              <div v-if="turn.table" class="concierge-results">
                <div class="concierge-results__heading">
                  <strong>{{ turn.table.title }}</strong>
                  <span>{{ turn.table.total }} 条</span>
                </div>
                <div v-if="turn.table.rows.length" class="concierge-table-scroll" tabindex="0" aria-label="查询结果表格">
                  <table class="concierge-table">
                    <thead><tr><th v-for="col in turn.table.columns" :key="col" scope="col">{{ col }}</th></tr></thead>
                    <tbody>
                      <tr v-for="(entry, index) in turn.table.rows" :key="index">
                        <td v-for="(cell, column) in entry.cells" :key="column">
                          <button v-if="column === 0 && entry.path" type="button" class="concierge-link"
                            @click="navigate(entry.path)">{{ cell }}</button>
                          <template v-else>{{ cell }}</template>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <p v-else class="concierge-muted">暂无符合条件的记录。</p>
                <div v-if="turn.table.page > 1 || turn.table.hasMore" class="concierge-results__pages">
                  <button type="button" class="concierge-link" :disabled="turn.tableLoading || turn.table.page <= 1 || !turn.id"
                    @click="loadTable(turn, turn.table.page - 1)">上一页</button>
                  <span>第 {{ turn.table.page }} 页</span>
                  <button type="button" class="concierge-link" :disabled="turn.tableLoading || !turn.table.hasMore || !turn.id"
                    @click="loadTable(turn, turn.table.page + 1)">下一页</button>
                </div>
              </div>
              <p v-if="turn.tableError" class="concierge-alert" role="alert">{{ turn.tableError }}</p>
              <button v-if="turn.hasTable && !turn.table" type="button" class="concierge-link" :disabled="turn.tableLoading"
                @click="loadTable(turn)">
                {{ turn.tableLoading ? '正在查询…' : '重新查询' }}
              </button>
            </div>
          </article>
        </div>
        <footer class="concierge-footer">
          <div class="concierge-context">
            <span v-if="orderId">当前工单</span>
            <button v-if="roleCode === 'OWNER'" type="button" class="concierge-link"
              @click="navigate('/owner/ai-assistant')">打开维修助手</button>
          </div>
          <form class="concierge-composer" @submit.prevent="send()">
            <textarea v-model="draft" aria-label="给 AI 管家发消息" placeholder="问工单、通知或维修知识"
              maxlength="1000" rows="2" @keydown="onComposerKey" />
            <button type="submit" class="concierge-send" aria-label="发送" :disabled="!draft.trim() || busy || messageLoading">
              <el-icon><ArrowUp /></el-icon>
            </button>
          </form>
        </footer>
      </template>
    </section>
  </el-drawer>
</template>

<style scoped>
.concierge-launch {
  position: fixed;
  z-index: 30;
  inset-inline-end: max(20px, env(safe-area-inset-right));
  inset-block-end: max(20px, env(safe-area-inset-bottom));
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 0 15px;
  border: 1px solid var(--color-ai);
  border-radius: var(--radius-full);
  background: var(--color-ai);
  color: var(--color-on-primary);
  box-shadow: var(--shadow-md);
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}

.concierge-launch:hover, .concierge-send:hover:not(:disabled) { background: var(--color-ai-hover); }

:global(.concierge-drawer.el-drawer) {
  height: var(--concierge-height, 100%);
  top: var(--concierge-top, 0px);
  max-height: none;
}

:global(.concierge-drawer .el-drawer__body) { padding: 0; overflow: hidden; }

.concierge-panel { display: flex; flex-direction: column; height: 100%; color: var(--color-text-1); }
.concierge-heading { display: flex; align-items: center; gap: 5px; padding: 16px; border-bottom: 1px solid var(--color-border); }
.concierge-heading__titles { flex: 1; min-width: 0; }
.concierge-heading h2 { margin: 0; font-size: 1rem; font-weight: 650; }
.concierge-heading p { overflow: hidden; margin: 4px 0 0; color: var(--color-text-2); font-size: .8125rem; text-overflow: ellipsis; white-space: nowrap; }
.concierge-icon { display: grid; place-items: center; min-width: 38px; min-height: 38px; border: 0; border-radius: var(--radius-md); background: transparent; color: var(--color-text-2); font-size: 1.125rem; cursor: pointer; }
.concierge-icon:hover:not(:disabled), .concierge-icon[aria-pressed="true"] { background: var(--color-bg-hover); color: var(--color-text-1); }
.concierge-messages, .concierge-history { flex: 1; min-height: 0; overflow-y: auto; overscroll-behavior: contain; padding: 16px; }
.concierge-start { display: grid; justify-items: start; gap: 8px; padding: 18px 0; }
.concierge-start h3 { margin: 0 0 4px; font-size: 1.0625rem; font-weight: 600; }
.concierge-start button { padding: 8px 11px; min-height: 36px; border: 1px solid var(--color-border); border-radius: var(--radius-md); background: var(--color-bg-card); color: var(--color-text-1); font: inherit; cursor: pointer; text-align: start; }
.concierge-start button:hover:not(:disabled) { background: var(--color-bg-hover); }
.concierge-history ul { list-style: none; padding: 0; margin: 0 0 10px; }
.concierge-history li + li { border-top: 1px solid var(--color-border); }
.concierge-history li button { display: flex; flex-wrap: wrap; gap: 4px 12px; width: 100%; justify-content: space-between; padding: 11px 8px; border: 0; background: transparent; text-align: start; color: var(--color-text-1); cursor: pointer; }
.concierge-history li button:hover, .concierge-history li button[aria-current="true"] { background: var(--color-bg-hover); }
.concierge-history small, .concierge-muted { color: var(--color-text-2); }
.concierge-muted { margin: 6px 0; font-size: .875rem; }
.concierge-turn { display: grid; gap: 9px; margin: 0 0 18px; }
.concierge-question { justify-self: end; max-width: 86%; margin: 0; padding: 8px 11px; border-radius: var(--radius-lg); background: var(--color-ai-soft); white-space: pre-wrap; overflow-wrap: anywhere; }
.concierge-answer { min-width: 0; overflow-wrap: anywhere; }
.concierge-source { margin: 6px 0; color: var(--color-text-2); font-size: .8125rem; }
.concierge-alert { margin: 0; padding: 7px 12px; background: var(--color-danger-soft); color: var(--color-danger-strong); font-size: .875rem; }
.concierge-link { min-height: 28px; padding: 0 1px; border: 0; background: transparent; color: var(--color-link); text-align: start; font: inherit; cursor: pointer; }
.concierge-link:hover:not(:disabled) { text-decoration: underline; text-underline-offset: 3px; }
.concierge-earlier { margin: 0 0 14px; }
.concierge-results { margin-top: 8px; border: 1px solid var(--color-border); border-radius: var(--radius-md); overflow: hidden; }
.concierge-results__heading, .concierge-results__pages { display: flex; align-items: center; gap: 8px; justify-content: space-between; padding: 8px 10px; background: var(--color-bg-sunken); font-size: .8125rem; }
.concierge-results__heading > span { flex: none; white-space: nowrap; }
.concierge-results > .concierge-muted { padding-inline: 10px; }
.concierge-table-scroll { overflow-x: auto; }
.concierge-table { width: 100%; border-collapse: collapse; font-size: .8125rem; font-variant-numeric: tabular-nums; }
.concierge-table th, .concierge-table td { padding: 6px 8px; border-top: 1px solid var(--color-border); text-align: start; vertical-align: top; }
.concierge-table th { color: var(--color-text-2); font-weight: 550; white-space: nowrap; }
.concierge-table td:first-child { white-space: nowrap; }
.concierge-footer { padding: 8px 16px max(12px, env(safe-area-inset-bottom)); border-top: 1px solid var(--color-border); }
.concierge-context { display: flex; justify-content: space-between; align-items: center; color: var(--color-text-2); font-size: .8125rem; }
.concierge-context .concierge-link { margin-inline-start: auto; }
.concierge-composer { display: flex; align-items: center; gap: 7px; border: 1px solid var(--color-border); border-radius: var(--radius-lg); padding: 6px; }
.concierge-composer:focus-within { border-color: var(--color-ai); }
.concierge-composer textarea { flex: 1; min-width: 0; min-height: 42px; resize: none; border: 0; padding: 5px; outline: none; background: transparent; color: var(--color-text-1); font: inherit; }
.concierge-composer textarea::placeholder { color: var(--color-text-2); }
.concierge-send { display: grid; place-items: center; flex: 0 0 36px; height: 36px; border: 0; border-radius: var(--radius-md); background: var(--color-ai); color: var(--color-on-primary); cursor: pointer; }
.concierge-panel button:disabled { opacity: .55; cursor: default; }
.concierge-panel button:focus-visible, .concierge-launch:focus-visible, .concierge-table-scroll:focus-visible { outline: 2px solid var(--color-ai); outline-offset: 2px; }

@media (max-width: 640px) {
  .concierge-launch { inset-inline-end: max(12px, env(safe-area-inset-right)); inset-block-end: max(12px, env(safe-area-inset-bottom)); }
  .concierge-heading, .concierge-messages, .concierge-history { padding-inline: 12px; }
  .concierge-heading { padding-top: max(12px, env(safe-area-inset-top)); padding-bottom: 9px; }
  .concierge-footer { padding-inline: 12px; }
}
</style>
