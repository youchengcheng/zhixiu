import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElLoading } from 'element-plus'
import App from './App.vue'
import router from './router'
import { USE_MOCK } from './utils/env'
import { setHttpAdapter, setUnauthorizedHandler } from './utils/request'
import { permission } from './utils/permission'
import { useUserStore } from './stores/user'

// 自托管拉丁与数字字体，零外网依赖
import '@fontsource-variable/geist'
import '@fontsource-variable/geist-mono'

import '@/styles/index.css'
// 指令式 API 的样式：组件样式由 unplugin-vue-components 按需引入
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-message-box.css'
import 'element-plus/theme-chalk/el-loading.css'

async function bootstrap() {
  // Mock 层只替换 axios 的 adapter，页面、拦截器、错误处理链路完全不变
  if (USE_MOCK) {
    const { createMockAdapter } = await import('@/mock/adapter')
    setHttpAdapter(createMockAdapter())
  }

  const app = createApp(App)

  app.use(createPinia())
  app.use(router)
  app.use(ElLoading)
  app.directive('permission', permission)

  // 401 统一出口：清登录态并回到登录页，保留回跳地址
  setUnauthorizedHandler(() => {
    useUserStore().clearAuth()
    const current = router.currentRoute.value
    if (current.name !== 'Login') {
      router.replace({ name: 'Login', query: { redirect: current.fullPath } })
    }
  })

  app.mount('#app')
}

bootstrap()
