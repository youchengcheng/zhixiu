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
import { GENDER, ROLE, dictOptions, dictText } from '@/utils/dict'
import { formatDateTime } from '@/utils/format'

// 个人中心（管理端）：资料维护 + 修改密码 + 账号信息
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
    <PageHeader title="个人中心" description="维护管理员资料与登录密码" />

    <SectionCard v-if="loading">
      <AppSkeleton variant="detail" :rows="5" />
    </SectionCard>

    <div v-else class="aprofile-layout">
      <!-- 账号信息：账号体系统一维护的只读字段，通栏展示便于一眼看完 -->
      <SectionCard title="账号信息" description="由账号体系维护，如需变更请联系系统管理员">
        <dl class="aprofile-grid">
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

      <!-- 资料与安全：两栏并排，卡片高度接近，避免一侧留下大片空白 -->
      <div class="aprofile-columns">
        <!-- 基本资料 -->
        <SectionCard title="基本资料" description="姓名会出现在操作日志与派单备注中">
          <el-form ref="profileFormRef" :model="form" :rules="profileRules" label-position="top">
            <el-form-item label="头像">
              <div class="aprofile-avatar">
                <!-- 未上传头像时用姓名首字占位，上传后只展示图片，不再重复显示首字 -->
                <LetterAvatar
                  v-if="!avatar.length"
                  :name="form.realName || userStore.displayName"
                  :size="64"
                />
                <div class="aprofile-avatar__uploader">
                  <ImageUpload v-model="avatar" :limit="1" biz-type="avatar" tip="支持 jpg / png，不超过 10MB" />
                </div>
              </div>
            </el-form-item>

            <!-- 姓名与邮箱并排，压缩表单纵向长度 -->
            <div class="aprofile-fields">
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
        <SectionCard title="修改密码" description="修改后当前登录仍然有效">
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
  </div>
</template>

<style scoped>
.aprofile-layout {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

/* 资料与密码两栏：宽屏并排、窄屏自动堆叠 */
.aprofile-columns {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
  gap: var(--space-5);
  align-items: start;
}

@media (max-width: 1024px) {
  .aprofile-columns {
    grid-template-columns: minmax(0, 1fr);
  }
}

.aprofile-avatar {
  display: flex;
  align-items: flex-start;
  gap: var(--space-4);
}

/* 上传控件占据剩余宽度，提示语不会把头像挤变形 */
.aprofile-avatar__uploader {
  flex: 1 1 auto;
  min-width: 0;
}

/* 表单两列：姓名与邮箱同行，窄屏回落单列 */
.aprofile-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: var(--space-4);
}

@media (max-width: 640px) {
  .aprofile-fields {
    grid-template-columns: minmax(0, 1fr);
  }
}

.aprofile-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: var(--space-4) var(--space-5);
}

.aprofile-grid dt {
  margin-bottom: var(--space-1);
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}

.aprofile-grid dd {
  font-size: var(--fs-body);
  color: var(--color-text-1);
}
</style>
