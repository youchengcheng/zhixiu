<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleClose, Delete, Document, Plus, Promotion } from '@element-plus/icons-vue'
import ChatBubble from '@/components/ChatBubble.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import {
  confirmDraft,
  createSession,
  deleteSession,
  getSessionDraft,
  listSessionMessages,
  listSessions
} from '@/api/ai'
import { listMyProperties } from '@/api/property'
import { getCategoryTree } from '@/api/category'
import { chatStream } from '@/utils/sse'
import { useUserStore } from '@/stores/user'
import { AFFECT_SCOPE, SAFETY_FLAGS, URGENCY, dictOptions } from '@/utils/dict'
import { formatDateTime } from '@/utils/format'

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()

const sessions = ref([])
const historyOpen = ref(false)
const sessionsLoading = ref(true)
const activeSessionId = ref('')
const sessionResolved = ref(0)
const submittedOrder = ref(null)
const messages = ref([])
const messagesLoading = ref(false)
const creatingSession = ref(false)
const deletingSession = ref(false)
const confirming = ref(false)

const streaming = ref(false)
const streamAction = ref('')
const streamText = ref('')
const streamIntent = ref('')
const streamCitations = ref([])
const streamSlots = ref({ filled: [], missing: [] })
let abortStream = null
let streamVersion = 0
let disposed = false

const input = ref('')
const listRef = ref(null)
const properties = ref([])
const categoryTree = ref([])
const draft = ref(null)
// 草稿数据与展开状态分开，收起不会丢失草稿或已编辑的字段。
const draftOpen = ref(false)
const draftRef = ref(null)
const draftEntryRef = ref(null)
const draftDeadline = ref(0)
const clockNow = ref(Date.now())
let clockTimer = null

const draftForm = reactive({
  categoryId: '',
  title: '',
  description: '',
  urgency: 2,
  faultDuration: '',
  affectScope: 1,
  safetyFlag: '',
  deviceInfo: '',
  propertyId: '',
  expectedVisitTime: '',
  contactPhone: '',
  images: []
})

const urgencyOptions = dictOptions(URGENCY)
const scopeOptions = dictOptions(AFFECT_SCOPE)
const slotLabels = {
  categoryId: '报修类型',
  description: '故障描述',
  urgency: '紧急程度',
  affectScope: '影响范围',
  faultDuration: '故障时长',
  deviceInfo: '设备型号',
  safetyFlag: '安全隐患'
}
const missingSlotLabels = computed(() => streamSlots.value.missing.map((slot) => slotLabels[slot] || '补充信息'))
const cascaderProps = { value: 'id', label: 'name', children: 'children', emitPath: false }
const activeSession = computed(
  () => sessions.value.find((item) => String(item.sessionId) === activeSessionId.value) || null
)
const busy = computed(
  () => streaming.value || creatingSession.value || messagesLoading.value || confirming.value || deletingSession.value
)
const canSend = computed(
  () => input.value.trim().length > 0 && !busy.value && sessionResolved.value === 0
)
const canStop = computed(() => streaming.value && ['CHAT', 'UNRESOLVED'].includes(streamAction.value))
const streamHint = computed(() => {
  if (streamAction.value === 'RESOLVED') return '正在记录反馈…'
  if (streamAction.value === 'GENERATE_DRAFT') return '正在整理草稿…'
  return '正在生成…'
})
const latestAnswer = computed(() => {
  const last = messages.value.at(-1)
  return last?.sender === 'AI' && !last.incomplete && !String(last.id).startsWith('local-') ? last : null
})
const hasProperty = computed(() => properties.value.length > 0)
const orderId = computed(() => submittedOrder.value?.orderId || activeSession.value?.orderId)
const draftSeconds = computed(() =>
  draft.value ? Math.max(0, Math.ceil((draftDeadline.value - clockNow.value) / 1000)) : 0
)
const hasValidDraft = computed(() => Boolean(draft.value) && draftSeconds.value > 0 && sessionResolved.value === 0)
const draftExpired = computed(() => Boolean(draft.value) && draftSeconds.value === 0)
const draftCountdown = computed(() => {
  const minutes = Math.floor(draftSeconds.value / 60)
  const seconds = draftSeconds.value % 60
  return String(minutes).padStart(2, '0') + ':' + String(seconds).padStart(2, '0')
})

async function scrollToBottom() {
  await nextTick()
  const el = listRef.value
  if (el) el.scrollTop = el.scrollHeight
}

function resetStreamState() {
  streaming.value = false
  streamAction.value = ''
  streamText.value = ''
  streamIntent.value = ''
  streamCitations.value = []
  streamSlots.value = { filled: [], missing: [] }
  abortStream = null
}

function clearCurrentDraft() {
  draft.value = null
  draftDeadline.value = 0
  draftOpen.value = false
}

function resetConversation() {
  messages.value = []
  sessionResolved.value = 0
  submittedOrder.value = null
  clearCurrentDraft()
  resetStreamState()
}

async function rememberSession(sessionId) {
  const query = { ...route.query }
  if (sessionId) query.sessionId = sessionId
  else delete query.sessionId
  await router.replace({ query })
}

async function loadSessions() {
  sessionsLoading.value = true
  try {
    const list = (await listSessions()) || []
    if (!disposed) sessions.value = list.filter((item) => (item.messageCount ?? 0) > 0)
  } catch {
    // 刷新失败时保留已有列表，具体错误由请求拦截器提示。
  } finally {
    sessionsLoading.value = false
  }
}

async function loadConversation(sessionId) {
  messagesLoading.value = true
  try {
    const [messageResult, draftResult] = await Promise.allSettled([
      listSessionMessages(sessionId),
      getSessionDraft(sessionId, { silentCodes: [3002] })
    ])
    if (disposed || sessionId !== activeSessionId.value) return
    if (messageResult.status === 'fulfilled') messages.value = messageResult.value || []
    if (draftResult.status === 'fulfilled') applyDraft(draftResult.value, false)
    else if (draftResult.reason?.code === 3002 && draft.value) draftDeadline.value = 0
    if (sessionResolved.value !== 0) clearCurrentDraft()
  } finally {
    if (sessionId === activeSessionId.value) messagesLoading.value = false
    scrollToBottom()
  }
}

async function selectSession(sessionId) {
  if (busy.value || String(sessionId) === activeSessionId.value) return
  activeSessionId.value = String(sessionId)
  resetConversation()
  sessionResolved.value = Number(activeSession.value?.resolved || 0)
  submittedOrder.value = activeSession.value?.orderId ? activeSession.value : null
  historyOpen.value = false
  input.value = ''
  messagesLoading.value = true
  await rememberSession(activeSessionId.value)
  await loadConversation(activeSessionId.value)
}

// 失败后恢复服务端记录，避免未完成的本地消息变成可操作回答。
async function recoverConversation(sessionId) {
  messagesLoading.value = true
  await loadSessions()
  if (disposed || sessionId !== activeSessionId.value) return
  sessionResolved.value = Number(activeSession.value?.resolved ?? sessionResolved.value)
  submittedOrder.value = activeSession.value?.orderId ? activeSession.value : submittedOrder.value
  await loadConversation(sessionId)
}

async function createNewSession() {
  if (busy.value || (!activeSessionId.value && !messages.value.length)) return
  activeSessionId.value = ''
  resetConversation()
  input.value = ''
  historyOpen.value = false
  await rememberSession('')
}

async function onDeleteSession(session) {
  if (busy.value) return
  deletingSession.value = true
  let deleted = false
  try {
    await ElMessageBox.confirm('确认删除会话「' + session.title + '」？聊天记录将一并清除。', '删除会话', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteSession(session.sessionId)
    deleted = true
    ElMessage.success('会话已删除')
    await loadSessions()
  } catch {
    // 取消不提示；接口错误由请求拦截器提示。
  } finally {
    deletingSession.value = false
  }
  if (deleted && String(session.sessionId) === activeSessionId.value) {
    await createNewSession()
    if (sessions.value.length) await selectSession(sessions.value[0].sessionId)
  }
}

function onStreamEvent(eventName, data, sessionId) {
  switch (eventName) {
    case 'message':
      streamText.value += data?.delta || ''
      scrollToBottom()
      break
    case 'intent':
      streamIntent.value = data?.intent || ''
      break
    case 'citations':
      streamCitations.value = data?.ids || []
      break
    case 'slot':
      streamSlots.value = { filled: data?.filled || [], missing: data?.missing || [] }
      break
    case 'followup':
      if (data?.question) streamText.value += '\n\n' + data.question
      scrollToBottom()
      break
    case 'draft':
      applyDraft(data)
      break
    case 'done':
      finalizeStream(data, sessionId)
      break
    case 'error':
      failStream(data, sessionId)
      break
    default:
      break
  }
}

function finalizeStream(data, sessionId) {
  if (!data?.messageId) {
    failStream({ message: '回答未完整结束，请重试。' }, sessionId)
    return
  }
  const content = streamText.value.trim()
  if (content) {
    const record = {
      id: String(data.messageId),
      sender: 'AI',
      content,
      intent: streamIntent.value || null,
      citations: streamCitations.value.length ? streamCitations.value.join(',') : null,
      createTime: formatDateTime(new Date())
    }
    const existing = messages.value.find((item) => String(item.id) === record.id)
    if (existing) Object.assign(existing, record)
    else messages.value.push(record)
  }
  sessionResolved.value = Number(data.sessionResolved ?? (data.resolved ? 1 : 0))
  if (sessionResolved.value !== 0) clearCurrentDraft()
  else if (data.hasDraft === false && draft.value) draftDeadline.value = 0
  resetStreamState()
  scrollToBottom()
  loadSessions()
}

async function failStream(error, sessionId) {
  if (!streaming.value) return
  streamVersion += 1
  abortStream?.()
  resetStreamState()
  ElMessage.error(error?.message || '智能助手暂时不可用，请稍后重试')
  await recoverConversation(sessionId)
}

function startStream(action, content = '', messageId) {
  const sessionId = activeSessionId.value
  const version = ++streamVersion
  resetStreamState()
  streaming.value = true
  streamAction.value = action
  if (action === 'CHAT' || action === 'UNRESOLVED') {
    messages.value.push({
      id: 'local-' + Date.now() + '-u',
      sender: 'USER',
      content,
      createTime: formatDateTime(new Date())
    })
    if (action === 'CHAT') input.value = ''
  }
  scrollToBottom()
  const abort = chatStream({
    sessionId,
    content,
    action,
    messageId,
    onEvent: (eventName, data) => {
      if (version === streamVersion && streaming.value) onStreamEvent(eventName, data, sessionId)
    },
    onDone: () => {
      if (version === streamVersion && streaming.value) {
        failStream({ message: '回答未完整结束，请重试。' }, sessionId)
      }
    },
    onError: (error) => {
      if (version === streamVersion && streaming.value) failStream(error, sessionId)
    }
  })
  if (version === streamVersion && streaming.value) abortStream = abort
  else abort()
}

async function sendMessage() {
  if (!canSend.value) return
  const content = input.value.trim()
  if (!activeSessionId.value) {
    creatingSession.value = true
    try {
      const result = await createSession({})
      if (!result?.sessionId) {
        ElMessage.error('创建会话失败，请重试')
        return
      }
      activeSessionId.value = String(result.sessionId)
      await rememberSession(activeSessionId.value)
    } catch {
      return
    } finally {
      creatingSession.value = false
    }
  }
  if (!disposed) startStream('CHAT', content)
}

function onAnswerAction(action) {
  if (busy.value || sessionResolved.value !== 0 || !latestAnswer.value) return
  const content = action === 'UNRESOLVED'
    ? '问题还没有解决，请提供其他排查方案，并询问还需要哪些详细信息。'
    : ''
  startStream(action, content, latestAnswer.value.id)
}

function stopStream() {
  if (!canStop.value) return
  streamVersion += 1
  abortStream?.()
  const content = streamText.value.trim()
  if (content) {
    messages.value.push({
      id: 'local-' + Date.now() + '-partial',
      sender: 'AI',
      content,
      incomplete: true,
      createTime: formatDateTime(new Date())
    })
  }
  resetStreamState()
  scrollToBottom()
  loadSessions()
}

function onComposerKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    sendMessage()
  }
}

function applyDraft(payload, open = true) {
  if (!payload?.draftId) return
  const sameDraft = String(draft.value?.draftId) === String(payload.draftId)
  const deadline = Date.now() + Number(payload.expiresIn || 0) * 1000
  draftDeadline.value = sameDraft ? Math.min(draftDeadline.value, deadline) : deadline
  clockNow.value = Date.now()
  draft.value = payload
  if (!sameDraft) {
    const form = payload.form || {}
    Object.assign(draftForm, {
      categoryId: form.categoryId != null ? String(form.categoryId) : '',
      title: form.title || '',
      description: form.description || '',
      urgency: form.urgency ?? 2,
      faultDuration: form.faultDuration || '',
      affectScope: form.affectScope ?? 1,
      safetyFlag: form.safetyFlag || '',
      deviceInfo: form.deviceInfo || '',
      propertyId: properties.value.find((item) => item.isDefault === 1)?.id || properties.value[0]?.id || '',
      expectedVisitTime: '一个工作日内',
      contactPhone: userStore.userInfo?.phone || '',
      images: []
    })
  }
  if (open) openDraft()
}

async function openDraft() {
  if (!hasValidDraft.value) return
  draftOpen.value = true
  await nextTick()
  if (disposed) return
  draftRef.value?.focus({ preventScroll: true })
  if (window.matchMedia('(max-width: 1280px)').matches) {
    draftRef.value?.scrollIntoView({
      block: 'start',
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth'
    })
  }
}

async function closeDraft() {
  draftOpen.value = false
  await nextTick()
  draftEntryRef.value?.focus()
}

function openOrder() {
  if (orderId.value) router.push({ name: 'OwnerOrderDetail', params: { id: orderId.value } })
}

async function submitDraft() {
  if (busy.value) return
  clockNow.value = Date.now()
  if (!hasValidDraft.value) {
    ElMessage.warning('草稿已过期，请在最新回答下重新生成')
    return
  }
  if (!draftForm.propertyId) return ElMessage.warning('请选择报修房产')
  if (!/^1[3-9]\d{9}$/.test(draftForm.contactPhone.trim())) return ElMessage.warning('请填写正确的联系电话')
  if (!draftForm.categoryId) return ElMessage.warning('请选择报修类型')
  if (!draftForm.title.trim() || !draftForm.description.trim()) return ElMessage.warning('请补充工单标题和故障描述')

  const sessionId = activeSessionId.value
  const payload = {
    draftId: draft.value.draftId,
    propertyId: draftForm.propertyId,
    categoryId: draftForm.categoryId,
    title: draftForm.title.trim(),
    description: draftForm.description.trim(),
    urgency: Number(draftForm.urgency),
    affectScope: Number(draftForm.affectScope),
    contactPhone: draftForm.contactPhone.trim()
  }
  if (draftForm.faultDuration.trim()) payload.faultDuration = draftForm.faultDuration.trim()
  if (draftForm.safetyFlag) payload.safetyFlag = draftForm.safetyFlag
  if (draftForm.deviceInfo.trim()) payload.deviceInfo = draftForm.deviceInfo.trim()
  if (draftForm.expectedVisitTime) payload.expectedVisitTime = draftForm.expectedVisitTime
  if (draftForm.images.length) payload.images = draftForm.images.join(',')

  confirming.value = true
  try {
    const result = await confirmDraft(sessionId, payload)
    sessionResolved.value = 2
    submittedOrder.value = result
    clearCurrentDraft()
    ElMessage.success('工单已提交，工单号 ' + (result?.orderNo || ''))
    messages.value = (await listSessionMessages(sessionId)) || messages.value
    loadSessions()
    scrollToBottom()
  } catch {
    await recoverConversation(sessionId)
  } finally {
    confirming.value = false
  }
}

async function loadReferenceData() {
  const [propertyResult, categoryResult] = await Promise.allSettled([
    listMyProperties(),
    getCategoryTree()
  ])
  if (propertyResult.status === 'fulfilled') properties.value = propertyResult.value || []
  if (categoryResult.status === 'fulfilled') categoryTree.value = categoryResult.value || []
}

onMounted(async () => {
  clockTimer = setInterval(() => { clockNow.value = Date.now() }, 1000)
  await Promise.allSettled([loadSessions(), loadReferenceData()])
  if (disposed) return
  const requested = typeof route.query.sessionId === 'string' ? route.query.sessionId : ''
  const session = sessions.value.find((item) => String(item.sessionId) === requested) || sessions.value[0]
  if (session) await selectSession(session.sessionId)
})

onBeforeUnmount(() => {
  disposed = true
  streamVersion += 1
  abortStream?.()
  clearInterval(clockTimer)
})
</script>

<template>
  <div class="layout-page ai-page">
    <div class="ai-layout" :class="{ 'has-draft': draftOpen && Boolean(draft) }">
      <!-- 会话列表：助手标题 + 新建会话 + 历史会话 -->
      <aside class="ai-sessions surface" :class="{ 'is-history-open': historyOpen }">
        <!-- 助手标识区（原位于页面顶部，现并入左栏，节省首屏纵向空间） -->
        <div class="ai-brand">
          <h1 class="ai-brand__title">智能维修助手</h1>
          <p class="ai-brand__desc">先获取排查方案，反馈是否解决；需要报修时再生成工单草稿</p>
          <el-button type="primary" :icon="Plus" class="ai-brand__new" :disabled="busy" @click="createNewSession">
            新建会话
          </el-button>
        </div>

        <header class="ai-panel__head">
          <span class="ai-panel__title">会话</span>
          <span class="ai-panel__count text-mono">{{ sessions.length }}</span>
          <button class="ai-history-toggle" type="button" :aria-expanded="historyOpen" aria-controls="ai-history" @click="historyOpen = !historyOpen">
            {{ historyOpen ? '收起会话' : '展开会话' }}
          </button>
        </header>

        <div id="ai-history" class="ai-sessions__body">
          <AppSkeleton v-if="sessionsLoading" variant="detail" :rows="3" />
          <EmptyState
            v-else-if="!sessions.length"
            icon="ChatDotRound"
            title="还没有会话"
            description="点击「新建会话」即可开始咨询。"
            size="compact"
          />
          <ul v-else class="ai-session-list">
            <li
              v-for="item in sessions"
              :key="item.sessionId"
              class="ai-session"
              :class="{ 'is-active': item.sessionId === activeSessionId }"
            >
              <button class="ai-session__main" type="button" :disabled="busy" @click="selectSession(item.sessionId)">
                <span class="ai-session__title truncate">{{ item.title }}</span>
                <span class="ai-session__meta">
                  {{ item.messageCount }} 条 · {{ item.updateTime?.slice(5, 16) || '' }}
                </span>
              </button>
              <button
                class="ai-session__delete"
                type="button"
                aria-label="删除会话"
                :disabled="busy"
                @click.stop="onDeleteSession(item)"
              >
                <el-icon><Delete /></el-icon>
              </button>
            </li>
          </ul>
        </div>
      </aside>

      <!-- 对话区 -->
      <section class="ai-chat surface">
        <header class="ai-panel__head">
          <span class="ai-panel__title truncate">{{ activeSession?.title || '智能维修助手' }}</span>
          <div class="ai-panel__tools">
            <!-- 会话已生成工单时提供直达入口，重新进入会话也能查看 -->
            <el-button
              v-if="orderId"
              text
              type="primary"
              size="small"
              :icon="Document"
              @click="openOrder"
            >
              查看工单
            </el-button>
            <span v-if="streaming" class="ai-panel__hint" role="status">{{ streamHint }}</span>
          </div>
        </header>

        <div v-if="hasValidDraft" class="ai-draft-entry">
          <button
            ref="draftEntryRef"
            class="ai-draft-entry__button"
            type="button"
            :disabled="busy"
            :aria-expanded="draftOpen"
            aria-controls="ai-draft-panel"
            @click="openDraft"
          >
            <el-icon><Document /></el-icon>
            <span>查看工单草稿</span>
            <span class="ai-draft-entry__time text-mono">剩余 {{ draftCountdown }}</span>
          </button>
        </div>
        <p v-else-if="draftExpired && sessionResolved === 0" class="ai-draft-expired" role="status">
          草稿已过期，请在最新回答下重新生成。
        </p>

        <div ref="listRef" class="ai-chat__list" role="region" aria-label="对话消息" tabindex="0">
          <AppSkeleton v-if="messagesLoading" variant="detail" :rows="4" />

          <EmptyState
            v-else-if="!messages.length && !streaming"
            icon="ChatDotRound"
            title="说清楚遇到的问题"
            description="例如「卧室空调不制冷，外机声音很大」。助手会先给出排查方案，你可以反馈是否解决，或选择生成工单草稿。"
          />

          <template v-else>
            <ChatBubble
              v-for="message in messages"
              :key="message.id"
              :sender="message.sender"
              :content="message.content"
              :time="message.createTime"
              :name="userStore.displayName"
            >
              <div v-if="message.citations" class="ai-citations">
                引用知识库：{{ message.citations }}
              </div>
              <p v-if="message.incomplete" class="ai-answer-actions__label">回答已停止，补充信息后可继续咨询。</p>
              <div v-if="sessionResolved === 0 && latestAnswer?.id === message.id" class="ai-answer-actions">
                <p class="ai-answer-actions__label">问题是否已解决？</p>
                <div class="ai-answer-actions__buttons">
                  <el-button size="small" :disabled="busy" @click="onAnswerAction('RESOLVED')">
                    已解决
                  </el-button>
                  <el-button size="small" :disabled="busy" @click="onAnswerAction('UNRESOLVED')">
                    未解决，继续排查
                  </el-button>
                  <el-button size="small" type="primary" plain :icon="Document" :disabled="busy" @click="onAnswerAction('GENERATE_DRAFT')">
                    生成工单草稿
                  </el-button>
                </div>
              </div>
            </ChatBubble>

            <!-- 正在生成的回答：无内容时显示打字动画，有内容时尾部带光标 -->
            <ChatBubble
              v-if="streaming && streamAction !== 'RESOLVED'"
              sender="AI"
              :content="streamText"
              :loading="true"
              :name="userStore.displayName"
            >
              <div v-if="streamCitations.length" class="ai-citations">
                引用知识库：{{ streamCitations.join('、') }}
              </div>
              <div v-if="streamSlots.missing.length" class="ai-citations">
                待补充：{{ missingSlotLabels.join('、') }}
              </div>
            </ChatBubble>
          </template>

          <!-- 免责声明作为对话尾注：跟随消息滚动，只在对话结束后显示 -->
          <p v-if="!streaming && messages.length" class="ai-chat__disclaimer">
            助手回答仅供参考，重要操作请与物业确认
          </p>
        </div>

        <footer class="ai-composer">
          <div v-if="sessionResolved !== 0" class="ai-composer__resolved" role="status">
            <p>{{ sessionResolved === 1 ? '问题已解决，感谢你的反馈。' : '工单已提交，可查看处理进度。' }}</p>
            <el-button :icon="Plus" @click="createNewSession">新问题，开始新会话</el-button>
          </div>
          <template v-else>
            <div class="ai-composer__row">
              <el-input
                v-model="input"
                class="ai-composer__input"
                aria-label="描述故障现象"
                type="textarea"
                :autosize="{ minRows: 1, maxRows: 4 }"
                resize="none"
                maxlength="2000"
                :disabled="busy"
                placeholder="描述故障现象，Enter 发送，Shift + Enter 换行"
                @keydown="onComposerKeydown"
              />
              <el-button v-if="canStop" class="ai-composer__action" :icon="CircleClose" @click="stopStream">
                停止
              </el-button>
              <el-button
                v-else
                class="ai-composer__action"
                type="primary"
                :icon="Promotion"
                :loading="streaming || creatingSession"
                :disabled="!canSend"
                @click="sendMessage"
              >
                {{ streaming ? '处理中' : '发送' }}
              </el-button>
            </div>
            <span v-if="canStop" class="ai-composer__tip">生成中可随时停止</span>
          </template>
        </footer>
      </section>

      <!-- 工单草稿卡 -->
      <aside v-if="draftOpen && draft" id="ai-draft-panel" ref="draftRef" class="ai-draft surface" tabindex="-1" aria-label="工单草稿">
        <header class="ai-panel__head">
          <span class="ai-panel__title">工单草稿</span>
          <button class="ai-session__delete" type="button" aria-label="收起草稿" @click="closeDraft">
            <el-icon><CircleClose /></el-icon>
          </button>
        </header>

        <div class="ai-draft__body">
          <p class="ai-draft__tip" :class="{ 'is-expired': draftExpired }">
            {{ draftExpired ? '草稿已过期，请在最新回答下重新生成。' : '草稿保留 15 分钟，剩余 ' + draftCountdown + '。请核对信息并补充必填项，确认后才会正式报修。' }}
          </p>

          <el-form label-position="top" size="default" :disabled="busy || !hasValidDraft">
            <el-form-item label="AI 识别类型">
              <span class="ai-draft__readonly">{{ draft.form?.categoryPath || '-' }}</span>
            </el-form-item>

            <el-form-item label="报修类型 *">
              <el-cascader
                v-model="draftForm.categoryId"
                :options="categoryTree"
                :props="cascaderProps"
                placeholder="请选择报修类型"
                clearable
              />
            </el-form-item>

            <el-form-item label="标题 *">
              <el-input v-model="draftForm.title" maxlength="100" />
            </el-form-item>

            <el-form-item label="故障描述 *">
              <el-input v-model="draftForm.description" type="textarea" :rows="3" maxlength="1000" />
            </el-form-item>

            <el-form-item label="紧急程度">
              <el-radio-group v-model="draftForm.urgency">
                <el-radio-button v-for="item in urgencyOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="影响范围">
              <el-radio-group v-model="draftForm.affectScope">
                <el-radio-button v-for="item in scopeOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="安全隐患">
              <el-select v-model="draftForm.safetyFlag" placeholder="无则不选" clearable>
                <el-option v-for="item in SAFETY_FLAGS" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>

            <div class="grid grid-2">
              <el-form-item label="故障时长">
                <el-input v-model="draftForm.faultDuration" maxlength="50" />
              </el-form-item>
              <el-form-item label="设备型号">
                <el-input v-model="draftForm.deviceInfo" maxlength="100" />
              </el-form-item>
            </div>

            <el-form-item label="报修房产 *">
              <el-select v-model="draftForm.propertyId" placeholder="请选择房产">
                <el-option
                  v-for="item in properties"
                  :key="item.id"
                  :label="item.fullAddress"
                  :value="item.id"
                />
              </el-select>
              <p v-if="!hasProperty" class="ai-draft__warn">
                还没有登记房产，请先到「我的房产」添加一处。
              </p>
            </el-form-item>

            <el-form-item label="期望上门时段">
              <!-- 后端字段是文本（如「今天下午」），这里用输入框并给出默认值 -->
              <el-input v-model="draftForm.expectedVisitTime" maxlength="50" placeholder="如：一个工作日内、今天下午" />
            </el-form-item>

            <el-form-item label="联系电话 *">
              <el-input v-model="draftForm.contactPhone" maxlength="11" />
            </el-form-item>

            <el-form-item label="现场照片">
              <ImageUpload v-model="draftForm.images" :limit="3" :disabled="busy || !hasValidDraft" biz-type="order" />
            </el-form-item>
          </el-form>
        </div>

        <footer class="ai-draft__foot">
          <el-button @click="closeDraft">收起草稿</el-button>
          <el-button type="primary" :loading="confirming" :disabled="!hasProperty || !hasValidDraft || busy" @click="submitDraft">
            确认报修
          </el-button>
        </footer>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.ai-page {
  max-width: var(--layout-content-max);
}

.ai-layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: var(--space-4);
  height: calc(100dvh - var(--layout-top-height) - 2 * var(--layout-page-padding));
  min-height: 480px;
}

/* 草稿卡展开时多占一列 */
.ai-layout.has-draft {
  grid-template-columns: 220px minmax(0, 1fr) 320px;
}

.ai-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex: 0 0 auto;
  min-height: 56px;
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--color-divider);
}

.ai-panel__title {
  font-size: var(--fs-sm);
  font-weight: var(--fw-semibold);
  color: var(--color-text-1);
}

.ai-panel__count,
.ai-panel__hint {
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

/* 对话区头部右侧：查看工单入口与生成中提示并排 */
.ai-panel__tools {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  gap: var(--space-2);
}

/* ---------- 会话列表 ---------- */
.ai-sessions {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 助手标识区：窄栏内使用次级标题字号，避免压过右栏对话内容 */
.ai-brand {
  flex: 0 0 auto;
  padding: var(--space-4);
  border-bottom: 1px solid var(--color-divider);
  background: var(--color-primary-soft);
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
}

.ai-brand__title {
  font-size: var(--fs-h2);
  font-weight: var(--fw-semibold);
  line-height: var(--lh-tight);
  color: var(--color-text-1);
}

.ai-brand__desc {
  margin-top: var(--space-2);
  font-size: var(--fs-xs);
  line-height: var(--lh-base);
  color: var(--color-text-3);
}

/* 新建会话是左栏主操作，撑满宽度便于点击 */
.ai-brand__new {
  width: 100%;
  margin-top: var(--space-3);
}

.ai-sessions__body {
  flex: 1 1 auto;
  overflow-y: auto;
  padding: var(--space-2);
}

.ai-session-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ai-session {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  border-radius: var(--radius-sm);
  transition: background var(--dur-fast) var(--ease-out);
}

.ai-session:hover {
  background: var(--color-bg-sunken);
}

.ai-session.is-active {
  background: var(--color-primary-soft);
}

.ai-session__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1 1 auto;
  min-width: 0;
  padding: var(--space-2) var(--space-3);
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.ai-session__title {
  font-size: var(--fs-sm);
  font-weight: var(--fw-medium);
  color: var(--color-text-1);
}

.ai-session__meta {
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.ai-session__delete {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 32px;
  height: 32px;
  margin-right: var(--space-2);
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-2);
  cursor: pointer;
  transition: color var(--dur-fast) var(--ease-out);
}

.ai-session__delete:hover {
  color: var(--color-danger-strong);
}

/* ---------- 对话区 ---------- */
.ai-chat {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.ai-chat__list {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  overflow-y: auto;
  padding: var(--space-5);
  background: var(--color-ai-canvas);
}

/* 对话尾注：跟随最后一条消息，始终贴在内容末尾 */
.ai-chat__disclaimer {
  flex: 0 0 auto;
  margin-top: calc(-1 * var(--space-2));
  text-align: center;
  font-size: var(--fs-xs);
  line-height: var(--lh-base);
  color: var(--color-text-3);
}

.ai-citations {
  margin-top: var(--space-2);
  padding: 2px var(--space-2);
  border-radius: var(--radius-xs);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.ai-composer {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  padding: var(--space-3) var(--space-4) var(--space-4);
  border-top: 1px solid var(--color-divider);
}

/* 输入框与按钮同一行；底部对齐，输入框自动增高时按钮跟随下移 */
.ai-composer__row {
  display: flex;
  align-items: flex-end;
  gap: var(--space-2);
}

/* 输入框占满剩余宽度，长文本在框内自动增高到 4 行 */
.ai-composer__input {
  flex: 1 1 auto;
  min-width: 0;
}

.ai-composer__input :deep(.el-textarea__inner) {
  min-height: 40px;
  padding-block: 9px;
}

/* 操作按钮固定宽度，与单行输入框等高，不被文本压缩 */
.ai-composer__action {
  flex: 0 0 auto;
  min-width: 84px;
  height: 40px;
}

.ai-history-toggle {
  display: none;
  border: none;
  padding: var(--space-2);
  background: transparent;
  color: var(--color-primary);
  cursor: pointer;
  font-size: var(--fs-sm);
}

.ai-composer__tip {
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

/* 最新回答的反馈与草稿入口 */
.ai-answer-actions {
  margin-top: var(--space-2);
}

.ai-answer-actions__label {
  margin-block: var(--space-2) var(--space-1);
  font-size: var(--fs-xs);
  color: var(--color-text-2);
}

.ai-answer-actions__buttons {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.ai-answer-actions__buttons :deep(.el-button + .el-button) {
  margin-left: 0;
}

.ai-draft-entry {
  flex: 0 0 auto;
  padding: var(--space-2) var(--space-4);
  border-bottom: 1px solid var(--color-divider);
  background: var(--color-primary-soft);
}

.ai-draft-entry__button {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
  width: 100%;
  min-height: 36px;
  border: 0;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--color-primary);
  text-align: left;
  font-size: var(--fs-sm);
  cursor: pointer;
}

.ai-draft-entry__button:disabled,
.ai-session__main:disabled,
.ai-session__delete:disabled {
  cursor: default;
  opacity: 0.6;
}

.ai-draft-entry__time {
  margin-left: auto;
  font-size: var(--fs-xs);
}

.ai-draft-expired {
  flex: 0 0 auto;
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--color-divider);
  font-size: var(--fs-xs);
  color: var(--color-text-2);
}

.ai-composer__resolved {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--space-3);
  font-size: var(--fs-sm);
  color: var(--color-text-2);
}

.ai-draft__tip.is-expired {
  background: var(--color-bg-sunken);
  color: var(--color-text-2);
}

/* ---------- 草稿卡 ---------- */
.ai-draft {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.ai-draft__body {
  flex: 1 1 auto;
  overflow-y: auto;
  padding: var(--space-4);
}

.ai-draft__tip {
  margin-bottom: var(--space-4);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  background: var(--color-ai-soft);
  color: var(--color-ai-strong);
  font-size: var(--fs-xs);
  line-height: var(--lh-base);
}

.ai-draft__readonly {
  font-size: var(--fs-sm);
  color: var(--color-text-2);
}

.ai-draft__warn {
  margin-top: var(--space-1);
  font-size: var(--fs-xs);
  color: var(--color-danger-strong);
}

.ai-draft__foot {
  flex: 0 0 auto;
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border-top: 1px solid var(--color-divider);
}

@media (max-width: 1280px) {
  .ai-layout,
  .ai-layout.has-draft {
    grid-template-columns: 200px minmax(0, 1fr);
  }

  .ai-layout.has-draft {
    height: auto;
    grid-template-rows: minmax(480px, calc(100dvh - var(--layout-top-height) - 2 * var(--layout-page-padding))) auto;
  }

  .ai-draft {
    grid-column: 1 / -1;
    height: 420px;
  }
}

@media (max-width: 1024px) {
  .ai-layout,
  .ai-layout.has-draft {
    grid-template-columns: minmax(0, 1fr);
    height: auto;
    min-height: 0;
    grid-template-rows: auto;
  }

  .ai-brand {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: center;
    gap: var(--space-3);
  }

  .ai-brand__desc {
    display: none;
  }

  .ai-brand__new {
    margin-top: 0;
  }

  .ai-history-toggle {
    display: inline-flex;
    margin-left: auto;
  }

  .ai-sessions__body {
    display: none;
    max-height: 220px;
  }

  .is-history-open .ai-sessions__body {
    display: block;
  }

  .ai-chat {
    height: calc(100dvh - var(--layout-top-height) - 2 * var(--layout-page-padding) - 144px);
    min-height: 420px;
  }

  .ai-chat__list {
    padding: var(--space-4);
  }

  .ai-draft {
    height: auto;
  }
}

@media (max-width: 640px) {
  .ai-answer-actions__buttons :deep(.el-button),
  .ai-draft-entry__button {
    min-height: 44px;
  }

  .ai-draft__body .grid-2 {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
