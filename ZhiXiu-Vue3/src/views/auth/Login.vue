<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, Lock, User } from '@element-plus/icons-vue'
import AuthLayout from '@/components/AuthLayout.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref(null)
const submitting = ref(false)
const errorMessage = ref('')

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入登录名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为 6-20 位', trigger: 'blur' }
  ]
}

const rolePrefix = { OWNER: '/owner', WORKER: '/worker', ADMIN: '/admin' }

async function onSubmit() {
  if (submitting.value) return
  errorMessage.value = ''
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || submitting.value) return

  submitting.value = true
  try {
    await userStore.login({ username: form.username.trim(), password: form.password })
    ElMessage.success(`欢迎回来，${userStore.displayName}`)

    // 只接受属于当前角色的回跳地址，其他情况回到本角色首页
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const prefix = rolePrefix[userStore.roleCode] || ''
    const target = redirect && prefix && redirect.startsWith(prefix) ? redirect : userStore.homePath

    router.replace(target)
  } catch (error) {
    errorMessage.value = error.message || '提交失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthLayout view="login">
    <h2 id="auth-form-title" class="auth-panel__title">欢迎回来</h2>
    <p class="auth-panel__description">登录智修云，查看报修进度与待办事项。</p>
    <p v-if="errorMessage" class="auth-form__error" role="alert">{{ errorMessage }}</p>
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      size="large"
      :aria-busy="submitting"
      @submit.prevent="onSubmit"
    >
      <el-form-item label="登录名" prop="username">
        <el-input
          v-model="form.username"
          name="username"
          autocomplete="username"
          placeholder="请输入登录名"
          :prefix-icon="User"
          clearable
        />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          name="password"
          type="password"
          autocomplete="current-password"
          placeholder="请输入密码"
          :prefix-icon="Lock"
          show-password
        />
      </el-form-item>
      <p class="auth-form__helper auth-form__login-hint">
        业主、维修师傅与物业管理员使用同一入口登录。
      </p>
      <el-button
        type="primary"
        native-type="submit"
        class="auth-form__submit"
        :disabled="submitting"
      >
        {{ submitting ? '正在登录…' : '登录' }}
        <el-icon v-if="!submitting" aria-hidden="true"><ArrowRight /></el-icon>
      </el-button>
    </el-form>
    <div class="auth-panel__foot">
      <span>还没有账号？</span>
      <router-link to="/register">业主注册</router-link>
      <span class="auth-panel__separator" aria-hidden="true" />
      <router-link to="/register/worker">师傅注册</router-link>
    </div>
  </AuthLayout>
</template>
