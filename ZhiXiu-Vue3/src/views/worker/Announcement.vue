<script setup>
import { onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import SectionCard from '@/components/SectionCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import MarkdownView from '@/components/MarkdownView.vue'
import RelativeTime from '@/components/RelativeTime.vue'
import Pagination from '@/components/Pagination.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import { illustrations } from '@/assets'
import { getAnnouncementDetail, listAnnouncements } from '@/api/announcement'
import { ANNOUNCEMENT_TARGET, ANNOUNCEMENT_TYPE, dictText } from '@/utils/dict'

// 物业公告（师傅端）：与业主端同一接口，后端按 target_role 过滤投放范围
// 公告模块后端尚未就绪，由 mock 兜底
const rows = ref([])
const total = ref(0)
const loading = ref(true)
const failed = ref(false)
const page = reactive({ pageNum: 1, pageSize: 10 })

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

async function load() {
  loading.value = true
  failed.value = false
  try {
    const data = await listAnnouncements({ pageNum: page.pageNum, pageSize: page.pageSize })
    rows.value = data?.records || []
    total.value = data?.total || 0
  } catch {
    rows.value = []
    total.value = 0
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function openDetail(item) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getAnnouncementDetail(item.id)
    // 详情接口会累加阅读量，列表同步刷新
    load()
  } catch {
    detail.value = item
  } finally {
    detailLoading.value = false
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
  <div class="layout-page">
    <PageHeader title="物业公告" description="与你相关的通知与规则更新" />

    <SectionCard v-if="loading">
      <AppSkeleton variant="detail" :rows="5" />
    </SectionCard>

    <SectionCard v-else-if="failed">
      <EmptyState
        icon="Warning"
        title="公告加载失败"
        description="请稍后重试。"
        action-text="重新加载"
        @action="load"
      />
    </SectionCard>

    <SectionCard v-else-if="!rows.length">
      <EmptyState
        :illustration="illustrations.emptyAnnouncement"
        title="暂时没有公告"
        description="物业发布通知后，这里会第一时间同步。"
        action-text="刷新"
        @action="load"
      />
    </SectionCard>

    <SectionCard v-else padding="none">
      <ul class="wnotice-list">
        <li v-for="item in rows" :key="item.id" class="wnotice-item">
          <button class="wnotice-item__main" type="button" @click="openDetail(item)">
            <span class="wnotice-item__head">
              <span v-if="item.isTop === 1" class="wnotice-item__top">置顶</span>
              <span class="wnotice-item__title truncate">{{ item.title }}</span>
              <StatusTag :status="item.type" :dict="ANNOUNCEMENT_TYPE" :dot="false" size="small" />
            </span>
            <span class="wnotice-item__preview clamp-2">{{ item.content }}</span>
            <span class="wnotice-item__meta">
              <RelativeTime :value="item.publishTime" fallback="尚未发布" />
              <span>· {{ dictText(ANNOUNCEMENT_TARGET, item.targetRole) }}</span>
              <span>· 阅读 {{ item.viewCount }}</span>
            </span>
          </button>
        </li>
      </ul>

      <div class="wnotice-pager page-pager">
        <Pagination
          :total="total"
          :page="page.pageNum"
          :size="page.pageSize"
          @update:page="onPageChange"
          @update:size="onSizeChange"
        />
      </div>
    </SectionCard>

    <el-dialog v-model="detailVisible" :title="detail?.title || '公告详情'" width="680px">
      <AppSkeleton v-if="detailLoading" variant="detail" :rows="4" />
      <template v-else-if="detail">
        <div class="wnotice-detail">
          <!-- 元信息固定在顶部，不参与正文滚动 -->
          <div class="wnotice-detail__meta">
            <StatusTag :status="detail.type" :dict="ANNOUNCEMENT_TYPE" :dot="false" size="small" />
            <RelativeTime :value="detail.publishTime" fallback="尚未发布" />
            <span>· {{ detail.publisherName || '物业' }}</span>
            <span>· 阅读 {{ detail.viewCount }}</span>
          </div>
          <!-- 只有正文区域滚动，标题与元信息保持可见 -->
          <div class="wnotice-detail__body">
            <MarkdownView :content="detail.content || ''" />
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.wnotice-list {
  display: flex;
  flex-direction: column;
}

.wnotice-item {
  border-bottom: 1px solid var(--color-divider);
}

.wnotice-item:last-child {
  border-bottom: none;
}

.wnotice-item__main {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  width: 100%;
  padding: var(--space-4) var(--space-5);
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background var(--dur-fast) var(--ease-out);
}

.wnotice-item__main:hover {
  background: var(--color-bg-sunken);
}

.wnotice-item__head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.wnotice-item__title {
  font-size: var(--fs-h3);
  font-weight: var(--fw-medium);
  color: var(--color-text-1);
}

.wnotice-item__top {
  flex: 0 0 auto;
  padding: 0 6px;
  border-radius: var(--radius-xs);
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: 11px;
  line-height: 18px;
}

.wnotice-item__preview {
  font-size: var(--fs-sm);
  color: var(--color-text-2);
  line-height: var(--lh-base);
}

.wnotice-item__meta {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.wnotice-pager {
  padding: 0 var(--space-5) var(--space-4);
}

.wnotice-detail__meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--color-divider);
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

/* 正文滚动容器：仅正文区域滚动，弹窗标题与元信息保持固定可见 */
.wnotice-detail__body {
  max-height: min(60vh, 520px);
  overflow-y: auto;
  padding-right: var(--space-2);
}
</style>
