<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell, Check } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SectionCard from '@/components/SectionCard.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import Pagination from '@/components/Pagination.vue'
import {
  listNotifications,
  readAllNotifications,
  readNotification
} from '@/api/notification'
import { useNotificationStore } from '@/stores/notification'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()
const notificationStore = useNotificationStore()

const rows = ref([])
const loading = ref(false)
const failed = ref(false)
const actingId = ref('')
const readingAll = ref(false)
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const hasUnread = computed(() => rows.value.some((item) => item.isRead !== 1))

async function load() {
  loading.value = true
  failed.value = false
  try {
    const data = await listNotifications({ pageNum: page.pageNum, pageSize: page.pageSize })
    rows.value = data?.records || []
    page.total = data?.total || 0
  } catch {
    rows.value = []
    page.total = 0
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)

function orderPath(orderId) {
  const prefix = { OWNER: '/owner', WORKER: '/worker', ADMIN: '/admin' }[userStore.roleCode]
  return prefix && orderId ? `${prefix}/order/${orderId}` : ''
}

async function openNotification(item) {
  if (actingId.value) return
  actingId.value = item.id
  try {
    if (item.isRead !== 1) {
      await readNotification(item.id)
      item.isRead = 1
      item.readTime = new Date().toISOString()
      notificationStore.markOneRead()
    }
    const path = orderPath(item.orderId)
    if (path) await router.push(path)
  } finally {
    actingId.value = ''
  }
}

async function markAllRead() {
  if (!hasUnread.value || readingAll.value) return
  readingAll.value = true
  try {
    await readAllNotifications()
    rows.value.forEach((item) => {
      item.isRead = 1
    })
    notificationStore.clearUnread()
    ElMessage.success('全部消息已标记为已读')
  } finally {
    readingAll.value = false
  }
}

function onPageChange(value) {
  page.pageNum = value
  load()
}

function onSizeChange(value) {
  page.pageSize = value
  page.pageNum = 1
  load()
}
</script>

<template>
  <div class="layout-page notification-page">
    <PageHeader title="消息中心" description="查看工单进展与 SLA 提醒，未读消息会在顶部显示数量">
      <el-button
        :icon="Check"
        :loading="readingAll"
        :disabled="!hasUnread"
        @click="markAllRead"
      >
        全部已读
      </el-button>
    </PageHeader>

    <SectionCard padding="none">
      <AppSkeleton v-if="loading" variant="table" :rows="6" />

      <EmptyState
        v-else-if="failed"
        icon="Warning"
        title="消息加载失败"
        description="请确认网络或后端服务状态后重试。"
        action-text="重新加载"
        @action="load"
      />

      <EmptyState
        v-else-if="!rows.length"
        icon="Bell"
        title="暂时没有消息"
        description="工单状态变化和 SLA 提醒会出现在这里。"
      />

      <template v-else>
        <ul class="notification-list" aria-label="消息列表">
          <li v-for="item in rows" :key="item.id" class="notification-list__item">
            <button
              class="notification-item"
              :class="{ 'is-unread': item.isRead !== 1 }"
              type="button"
              :disabled="actingId === item.id"
              @click="openNotification(item)"
            >
              <span class="notification-item__icon" aria-hidden="true">
                <el-icon><Bell /></el-icon>
              </span>
              <span class="notification-item__content">
                <span class="notification-item__heading">
                  <span class="notification-item__title">{{ item.title }}</span>
                  <span v-if="item.isRead !== 1" class="notification-item__unread">未读</span>
                </span>
                <span class="notification-item__body">{{ item.content }}</span>
                <span class="notification-item__meta text-mono">
                  {{ formatDateTime(item.createTime) }}
                </span>
              </span>
              <span v-if="item.orderId" class="notification-item__action">查看工单</span>
            </button>
          </li>
        </ul>

        <div class="notification-page__pager page-pager">
          <Pagination
            :total="page.total"
            :page="page.pageNum"
            :size="page.pageSize"
            @update:page="onPageChange"
            @update:size="onSizeChange"
          />
        </div>
      </template>
    </SectionCard>
  </div>
</template>

<style scoped>
.notification-list {
  list-style: none;
}

.notification-list__item + .notification-list__item {
  border-top: 1px solid var(--color-divider);
}

.notification-item {
  display: flex;
  align-items: flex-start;
  width: 100%;
  gap: var(--space-4);
  padding: var(--space-5);
  border: 0;
  background: var(--color-bg-card);
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: background var(--dur-fast) var(--ease-out);
}

.notification-item:hover {
  background: var(--color-bg-sunken);
}

.notification-item:focus-visible {
  position: relative;
  z-index: 1;
  outline: 2px solid var(--color-primary);
  outline-offset: -3px;
}

.notification-item:disabled {
  cursor: wait;
}

.notification-item.is-unread {
  background: var(--color-primary-soft);
}

.notification-item.is-unread:hover {
  background: var(--color-bg-sunken);
}

.notification-item__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg-card);
  color: var(--color-text-2);
}

.notification-item.is-unread .notification-item__icon {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.notification-item__content {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.notification-item__heading {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.notification-item__title {
  overflow: hidden;
  color: var(--color-text-1);
  font-size: var(--fs-body);
  font-weight: var(--fw-semibold);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-item__unread {
  flex: 0 0 auto;
  padding: 1px 6px;
  border-radius: var(--radius-xs);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-size: 11px;
  line-height: 18px;
}

.notification-item__body {
  color: var(--color-text-2);
  font-size: var(--fs-sm);
  line-height: var(--lh-base);
}

.notification-item__meta {
  color: var(--color-text-3);
  font-size: var(--fs-xs);
}

.notification-item__action {
  align-self: center;
  flex: 0 0 auto;
  color: var(--color-primary);
  font-size: var(--fs-sm);
  font-weight: var(--fw-medium);
}

.notification-page__pager {
  padding: var(--space-4) var(--space-5);
}

@media (max-width: 768px) {
  .notification-item {
    gap: var(--space-3);
    padding: var(--space-4);
  }

  .notification-item__icon {
    width: 34px;
    height: 34px;
  }

  .notification-item__action {
    display: none;
  }

  .notification-page__pager {
    padding: var(--space-4);
  }
}
</style>
