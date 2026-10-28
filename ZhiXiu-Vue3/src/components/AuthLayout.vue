<script setup>
import { computed, ref } from 'vue'
import { ChatDotRound, CircleCheck, House, List, Tools } from '@element-plus/icons-vue'
import communityPhoto from '@/assets/auth/community-courtyard.webp'

const props = defineProps({ view: { type: String, default: 'login' } })
const imageFailed = ref(false)
const content = computed(() => {
  if (props.view === 'worker') {
    return {
      title: ['用你的手艺，', '守护每一份安心。'],
      description: '接单、上门、完工记录，让每一次维修都有据可查。',
      features: [
        { icon: Tools, label: '便捷接单' },
        { icon: List, label: '工序留痕' },
        { icon: CircleCheck, label: '服务评价' }
      ]
    }
  }
  return {
    title:
      props.view === 'owner'
        ? ['从此，家的小问题', '有人在意。']
        : ['让家的小问题，', '都有妥善的回应。'],
    description: '提交报修、跟踪进度、评价服务，在这里完成。',
    features: [
      { icon: ChatDotRound, label: '智能报修' },
      { icon: List, label: '进度可查' },
      { icon: CircleCheck, label: '服务评价' }
    ]
  }
})
</script>

<template>
  <div class="auth-page" :class="`auth-page--${view}`">
    <header class="auth-page__header">
      <div class="auth-brand">
        <span class="auth-brand__mark" aria-hidden="true">修</span>
        <span class="auth-brand__name">智修云</span>
        <span class="auth-brand__description">物业报修系统</span>
      </div>
      <span class="auth-page__header-note">
        <el-icon aria-hidden="true"><House /></el-icon>
        让社区生活更安心
      </span>
    </header>
    <main class="auth-page__body">
      <section class="auth-story" aria-labelledby="auth-story-title">
        <div class="auth-story__copy">
          <span class="auth-story__label">连接业主、师傅与物业</span>
          <div class="auth-story__message-stack">
            <Transition name="auth-story-switch">
              <div :key="view" class="auth-story__message">
                <h1 id="auth-story-title" class="auth-story__title">
                  <span v-for="line in content.title" :key="line">{{ line }}</span>
                </h1>
                <p class="auth-story__description">{{ content.description }}</p>
              </div>
            </Transition>
          </div>
        </div>
        <figure class="auth-story__visual">
          <img
            v-if="!imageFailed"
            :src="communityPhoto"
            alt="绿树环绕、亮着温暖灯光的住宅"
            width="1000"
            height="720"
            fetchpriority="high"
            @error="imageFailed = true"
          />
          <div v-else class="auth-story__image-fallback" role="status">社区图片暂时无法加载</div>
        </figure>
        <ul class="auth-story__features" aria-label="平台服务">
          <li v-for="feature in content.features" :key="feature.label">
            <el-icon aria-hidden="true"><component :is="feature.icon" /></el-icon>
            <span>{{ feature.label }}</span>
          </li>
        </ul>
      </section>
      <section class="auth-panel" aria-labelledby="auth-form-title">
        <div class="auth-panel__inner"><slot /></div>
      </section>
    </main>
    <footer class="auth-page__footer">智修云，让每一次报修都有回应。</footer>
  </div>
</template>
