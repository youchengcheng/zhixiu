<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AuthLayout from '@/components/AuthLayout.vue'
import Register from './Register.vue'
import RegisterWorker from './RegisterWorker.vue'

const route = useRoute()
const isWorker = computed(() => route.name === 'RegisterWorker')
const view = computed(() => (isWorker.value ? 'worker' : 'owner'))

function leaveForm(element) {
  // 退场表单只参与视觉过渡，避免被键盘或屏幕阅读器再次访问。
  element.inert = true
  element.setAttribute('aria-hidden', 'true')
}

function enterForm(element) {
  element.inert = false
  element.removeAttribute('aria-hidden')
}
</script>

<template>
  <AuthLayout
    :view="view"
    class="auth-page--register"
    :style="{ '--auth-switch-offset': isWorker ? '16px' : '-16px' }"
  >
    <nav class="auth-role-tabs" :class="{ 'is-worker': isWorker }" aria-label="注册身份">
      <span class="auth-role-tabs__indicator" aria-hidden="true" />
      <router-link to="/register" :aria-current="!isWorker ? 'page' : undefined">
        业主注册
      </router-link>
      <router-link to="/register/worker" :aria-current="isWorker ? 'page' : undefined">
        师傅注册
      </router-link>
    </nav>
    <div class="auth-form-stage">
      <Transition
        name="auth-form-switch"
        @before-leave="leaveForm"
        @before-enter="enterForm"
        @leave-cancelled="enterForm"
      >
        <KeepAlive :max="2">
          <component :is="isWorker ? RegisterWorker : Register" :key="view" />
        </KeepAlive>
      </Transition>
    </div>
  </AuthLayout>
</template>
