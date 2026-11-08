<script setup>
import { nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { resetUserPassword } from '@/api/adminUser'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  userId: { type: [String, Number], default: '' },
  displayName: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'success'])

const formRef = ref(null)
const submitting = ref(false)
const form = reactive({ newPassword: '', confirmPassword: '' })

function validateConfirm(_rule, value, callback) {
  if (!value) callback(new Error('请再次输入新密码'))
  else if (value !== form.newPassword) callback(new Error('两次输入的密码不一致'))
  else callback()
}

const rules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度需为 6-20 位', trigger: 'blur' }
  ],
  confirmPassword: [{ validator: validateConfirm, trigger: ['blur', 'change'] }]
}

function close() {
  emit('update:modelValue', false)
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !props.userId) return

  submitting.value = true
  try {
    await resetUserPassword(props.userId, {
      newPassword: form.newPassword,
      confirmPassword: form.confirmPassword
    })
    ElMessage.success('密码已重置')
    emit('success')
    close()
  } finally {
    submitting.value = false
  }
}

watch(
  () => props.modelValue,
  async (visible) => {
    if (!visible) return
    Object.assign(form, { newPassword: '', confirmPassword: '' })
    await nextTick()
    formRef.value?.clearValidate()
  }
)
</script>

<template>
  <el-dialog
    :model-value="props.modelValue"
    title="重置用户密码"
    width="460px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p class="reset-password__target">
      正在为 <strong>{{ props.displayName || '该用户' }}</strong> 设置新密码。保存后旧密码立即失效。
    </p>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="submit"
    >
      <el-form-item label="新密码" prop="newPassword">
        <el-input
          v-model="form.newPassword"
          type="password"
          show-password
          autocomplete="new-password"
          placeholder="请输入 6-20 位新密码"
        />
      </el-form-item>
      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          show-password
          autocomplete="new-password"
          placeholder="请再次输入新密码"
          @keyup.enter="submit"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button :disabled="submitting" @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认重置</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.reset-password__target {
  margin-bottom: var(--space-5);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-bg-sunken);
  color: var(--color-text-2);
  font-size: var(--fs-sm);
  line-height: var(--lh-base);
}

.reset-password__target strong {
  color: var(--color-text-1);
  font-weight: var(--fw-semibold);
}
</style>
