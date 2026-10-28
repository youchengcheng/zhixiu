<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, Iphone, Lock, Postcard, User } from '@element-plus/icons-vue'
import { registerOwner } from '@/api/auth'

const router = useRouter()
const formRef = ref(null)
const submitting = ref(false)
const errorMessage = ref('')

const form = reactive({
  username: '',
  realName: '',
  phone: '',
  password: '',
  confirmPassword: ''
})

function validateConfirm(rule, value, callback) {
  if (!value) return callback(new Error('请再次输入密码'))
  if (value !== form.password) return callback(new Error('两次输入的密码不一致'))
  return callback()
}

const rules = {
  username: [
    { required: true, message: '请输入登录名', trigger: 'blur' },
    { min: 4, max: 20, message: '登录名需为 4-20 位', trigger: 'blur' }
  ],
  realName: [{ max: 20, message: '姓名不超过 20 个字', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码需为 6-20 位', trigger: 'blur' }
  ],
  confirmPassword: [{ required: true, validator: validateConfirm, trigger: 'blur' }]
}

async function onSubmit() {
  if (submitting.value) return
  errorMessage.value = ''
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || submitting.value) return

  submitting.value = true
  try {
    await registerOwner({ ...form, username: form.username.trim() })
    ElMessage.success('注册成功，请使用新账号登录')
    router.replace({ name: 'Login' })
  } catch (error) {
    errorMessage.value = error.message || '提交失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-form-view">
    <h2 id="auth-form-title" class="auth-panel__title">创建你的账号</h2>
    <p class="auth-panel__description">添加房产后，即可报修并查看处理进度。</p>
    <p v-if="errorMessage" class="auth-form__error" role="alert">{{ errorMessage }}</p>
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      :aria-busy="submitting"
      @submit.prevent="onSubmit"
    >
      <div class="auth-form__grid">
        <el-form-item label="登录名" prop="username" class="auth-form__span">
          <el-input
            v-model="form.username"
            name="username"
            autocomplete="username"
            placeholder="设置 4-20 位登录名"
            :prefix-icon="User"
            clearable
          />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input
            v-model="form.realName"
            name="realName"
            autocomplete="name"
            placeholder="选填，输入真实姓名"
            :prefix-icon="Postcard"
            clearable
          />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input
            v-model="form.phone"
            name="phone"
            type="tel"
            inputmode="numeric"
            autocomplete="tel"
            placeholder="请输入 11 位手机号"
            :prefix-icon="Iphone"
            maxlength="11"
            clearable
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            name="password"
            type="password"
            autocomplete="new-password"
            placeholder="设置 6-20 位密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            name="confirmPassword"
            type="password"
            autocomplete="new-password"
            placeholder="请再次输入密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
      </div>
      <el-button
        type="primary"
        native-type="submit"
        class="auth-form__submit"
        :disabled="submitting"
      >
        {{ submitting ? '正在注册…' : '注册' }}
        <el-icon v-if="!submitting" aria-hidden="true"><ArrowRight /></el-icon>
      </el-button>
    </el-form>
    <div class="auth-panel__foot">
      <span>已有账号？</span>
      <router-link to="/login">返回登录</router-link>
    </div>
  </div>
</template>
