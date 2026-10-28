<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight } from '@element-plus/icons-vue'
import { registerWorker } from '@/api/auth'
import { WORKER_SKILL_SUGGESTIONS } from '@/utils/dict'

const router = useRouter()
const formRef = ref(null)
const submitting = ref(false)
const errorMessage = ref('')

// 技能标签为师傅自身的作业能力，与报修类型不是同一套数据；
// 注册页在登录前访问，因此使用本地预设并可自由输入，不依赖需鉴权的接口
const skillOptions = WORKER_SKILL_SUGGESTIONS

const form = reactive({
  username: '',
  realName: '',
  phone: '',
  workerNo: '',
  skillTags: [],
  idCard: '',
  password: '',
  confirmPassword: ''
})

function validateConfirm(rule, value, callback) {
  if (!value) return callback(new Error('请再次输入密码'))
  if (value !== form.password) return callback(new Error('两次输入的密码不一致'))
  return callback()
}

function validateIdCard(rule, value, callback) {
  if (!value) return callback()
  if (!/^\d{17}[\dXx]$/.test(value)) return callback(new Error('请输入 18 位身份证号'))
  return callback()
}

const rules = {
  username: [
    { required: true, message: '请输入登录名', trigger: 'blur' },
    { min: 4, max: 20, message: '登录名需为 4-20 位', trigger: 'blur' }
  ],
  realName: [{ required: true, message: '请填写真实姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  workerNo: [{ required: true, message: '请填写工号', trigger: 'blur' }],
  skillTags: [{ required: true, type: 'array', message: '请选择至少一项技能', trigger: 'change' }],
  idCard: [{ validator: validateIdCard, trigger: 'blur' }],
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
    await registerWorker({
      ...form,
      username: form.username.trim(),
      realName: form.realName.trim(),
      workerNo: form.workerNo.trim(),
      skillTags: form.skillTags.join(',')
    })
    ElMessage.success('注册成功，等待物业管理员审核')
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
    <h2 id="auth-form-title" class="auth-panel__title">加入社区维修服务</h2>
    <p class="auth-panel__description">工号由物业分配，审核通过后即可接单。</p>
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
        <el-form-item label="登录名" prop="username">
          <el-input
            v-model="form.username"
            name="username"
            autocomplete="username"
            placeholder="4-20 位登录名"
            clearable
          />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input
            v-model="form.realName"
            name="realName"
            autocomplete="name"
            placeholder="请输入真实姓名"
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
            placeholder="11 位手机号"
            maxlength="11"
            clearable
          />
        </el-form-item>
        <el-form-item label="工号" prop="workerNo">
          <el-input
            v-model="form.workerNo"
            name="workerNo"
            placeholder="请输入物业分配的工号"
            clearable
          />
        </el-form-item>
        <el-form-item label="技能标签" prop="skillTags">
          <el-select
            v-model="form.skillTags"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入技能"
            style="width: 100%"
            :teleported="false"
          >
            <el-option v-for="item in skillOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="身份证号" prop="idCard">
          <el-input
            v-model="form.idCard"
            name="idCard"
            placeholder="选填，仅用于资质审核"
            maxlength="18"
            clearable
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            name="password"
            type="password"
            autocomplete="new-password"
            placeholder="6-20 位密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            name="confirmPassword"
            type="password"
            autocomplete="new-password"
            placeholder="再次输入密码"
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
        {{ submitting ? '正在提交…' : '提交注册' }}
        <el-icon v-if="!submitting" aria-hidden="true"><ArrowRight /></el-icon>
      </el-button>
    </el-form>
    <div class="auth-panel__foot">
      <span>已有账号？</span>
      <router-link to="/login">返回登录</router-link>
    </div>
  </div>
</template>
