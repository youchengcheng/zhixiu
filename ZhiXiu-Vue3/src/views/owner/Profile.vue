<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import SectionCard from '@/components/SectionCard.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import LetterAvatar from '@/components/LetterAvatar.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import { changePassword, getProfile, updateProfile } from '@/api/user'
import { useUserStore } from '@/stores/user'
import { GENDER, ROLE, dictText, dictOptions } from '@/utils/dict'
import { formatDateTime } from '@/utils/format'

// 个人中心：资料维护 + 修改密码 + 账号信息（用户名/手机号由账号体系维护，此处只读）
const userStore = useUserStore()

const loading = ref(true)
const savingProfile = ref(false)
const savingPassword = ref(false)

const profile = ref(null)
const profileFormRef = ref(null)
const passwordFormRef = ref(null)

const genderOptions = dictOptions(GENDER)

// ImageUpload 以数组作为 v-model，头像只允许一张
const avatar = ref([])

const form = reactive({ realName: '', email: '', gender: 0 })
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const profileRules = {
  realName: [{ required: true, message: '请填写真实姓名', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '新密码长度为 6-20 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) callback(new Error('两次输入的新密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

const roleText = computed(() => dictText(ROLE, profile.value?.roleCode, '-'))

async function load() {
  loading.value = true
  try {
    profile.value = await getProfile()
    form.realName = profile.value?.realName || ''
    form.email = profile.value?.email || ''
    form.gender = profile.value?.gender ?? 0
    avatar.value = profile.value?.avatar ? [profile.value.avatar] : []
  } catch {
    profile.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function saveProfile() {
  const valid = await profileFormRef.value?.validate().catch(() => false)
  if (!valid) return

  savingProfile.value = true
  try {
    await updateProfile({
      realName: form.realName.trim(),
      email: form.email.trim(),
      gender: Number(form.gender),
      avatar: avatar.value[0] || ''
    })
    ElMessage.success('资料已保存')

    // 刷新本地展示与登录态中的用户信息，顶栏头像/姓名同步更新
    await load()
    userStore.setUserInfo({
      ...(userStore.userInfo || {}),
      realName: form.realName.trim(),
      email: form.email.trim(),
      gender: Number(form.gender),
      avatar: avatar.value[0] || ''
    })
  } catch {
    // 拦截器已统一提示
  } finally {
    savingProfile.value = false
  }
}

async function savePassword() {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return

  savingPassword.value = true
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
      confirmPassword: passwordForm.confirmPassword
    })
    ElMessage.success('密码已修改')
    passwordFormRef.value?.resetFields()
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
  } catch {
    // 业务码 1003 等已由拦截器提示
  } finally {
    savingPassword.value = false
  }
}
</script>

<template>
  <div class="layout-page profile-page">
    <PageHeader title="个人中心" description="维护联系方式与头像，修改登录密码" />

    <SectionCard v-if="loading">
      <AppSkeleton variant="detail" :rows="5" />
    </SectionCard>

    <div v-else class="profile-layout">
      <SectionCard class="profile-account" title="账号信息" description="如需变更账号信息，请联系物业">
        <dl class="account-grid">
          <div>
            <dt>登录名</dt>
            <dd class="text-mono">{{ profile?.username || '-' }}</dd>
          </div>
          <div>
            <dt>角色</dt>
            <dd>{{ roleText }}</dd>
          </div>
          <div>
            <dt>手机号</dt>
            <dd class="text-mono">{{ profile?.phone || '-' }}</dd>
          </div>
          <div>
            <dt>注册时间</dt>
            <dd class="text-mono">{{ formatDateTime(profile?.createTime) }}</dd>
          </div>
          <div>
            <dt>上次登录</dt>
            <dd class="text-mono">{{ formatDateTime(profile?.lastLoginTime) }}</dd>
          </div>
        </dl>
      </SectionCard>

      <SectionCard class="profile-details" title="基本资料" description="姓名与电话会提供给上门师傅">
        <el-form ref="profileFormRef" :model="form" :rules="profileRules" label-position="top">
          <el-form-item label="头像">
            <ImageUpload class="profile-avatar" v-model="avatar" :limit="1" biz-type="avatar" tip="支持 jpg / png，不超过 10MB">
              <template #trigger>
                <div class="profile-avatar__trigger">
                  <LetterAvatar :name="form.realName || userStore.displayName" :size="48" />
                  <span>上传头像</span>
                </div>
              </template>
            </ImageUpload>
          </el-form-item>

          <div class="profile-fields">
            <el-form-item label="真实姓名" prop="realName">
              <el-input v-model="form.realName" maxlength="20" placeholder="请填写真实姓名" />
            </el-form-item>

            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" placeholder="选填，用于接收通知" />
            </el-form-item>
          </div>

          <el-form-item label="性别" prop="gender">
            <el-radio-group v-model="form.gender">
              <el-radio v-for="item in genderOptions" :key="item.value" :value="item.value">
                {{ item.label }}
              </el-radio>
            </el-radio-group>
          </el-form-item>

          <el-button type="primary" :loading="savingProfile" @click="saveProfile">保存资料</el-button>
        </el-form>
      </SectionCard>

      <!-- 修改密码 -->
      <SectionCard class="profile-security" title="修改密码" description="修改后当前登录仍然有效">
        <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-position="top">
          <el-form-item label="原密码" prop="oldPassword">
            <el-input v-model="passwordForm.oldPassword" type="password" show-password autocomplete="current-password" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" />
          </el-form-item>
          <el-button type="primary" :loading="savingPassword" @click="savePassword">修改密码</el-button>
        </el-form>
      </SectionCard>
    </div>
  </div>
</template>

<style scoped>
.profile-layout {
  display: grid;
  grid-template-columns: minmax(0, 0.85fr) minmax(0, 1.2fr) minmax(0, 1fr);
  gap: var(--space-5);
  align-items: stretch;
}

.profile-layout > :deep(.section-card) {
  overflow-wrap: anywhere;
}

.profile-layout :deep(.section-card__body) {
  display: flex;
  flex: 1;
  flex-direction: column;
}

.profile-layout :deep(.el-form) {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.profile-layout :deep(.el-form > .el-button) {
  align-self: flex-start;
  margin-top: auto;
}

.profile-avatar {
  width: 100%;
}

.profile-avatar__trigger {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  width: 100%;
  height: 100%;
  color: var(--color-text-2);
  font-size: var(--fs-xs);
}

.profile-avatar :deep(.is-trigger) {
  border-style: solid;
}

.profile-avatar :deep(.el-upload:hover) .profile-avatar__trigger {
  color: var(--color-primary);
}

.profile-fields {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
}

@media (max-width: 1023px) {
  .profile-layout {
    grid-template-columns: minmax(0, 1fr);
  }
}

.account-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-5);
}

.account-grid dt {
  margin-bottom: var(--space-1);
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.account-grid dd {
  font-size: var(--fs-body);
  color: var(--color-text-1);
}

@media (min-width: 768px) and (max-width: 1023px) {
  .account-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
