<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import SectionCard from '@/components/SectionCard.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import { illustrations } from '@/assets'
import { createOrder } from '@/api/ownerOrder'
import { listMyProperties } from '@/api/property'
import { getCategoryTree } from '@/api/category'
import { useUserStore } from '@/stores/user'
import { AFFECT_SCOPE, SAFETY_FLAGS, URGENCY, dictOptions } from '@/utils/dict'

// 手动提交报修：字段与文档 7.1 一一对应，接口走真实后端 POST /api/owner/order
const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const submitting = ref(false)
const properties = ref([])
const categoryTree = ref([])
const formRef = ref(null)

const urgencyOptions = dictOptions(URGENCY)
const scopeOptions = dictOptions(AFFECT_SCOPE)

const form = reactive({
  propertyId: '',
  categoryId: '',
  title: '',
  description: '',
  urgency: 2,
  faultDuration: '',
  affectScope: 1,
  safetyFlag: '',
  deviceInfo: '',
  // 期望上门时段默认一个工作日内，与 AI 助手报修保持一致
  expectedVisitTime: '一个工作日内',
  contactPhone: '',
  images: []
})

const rules = {
  propertyId: [{ required: true, message: '请选择报修房产', trigger: 'change' }],
  categoryId: [{ required: true, message: '请选择报修类型', trigger: 'change' }],
  title: [
    { required: true, message: '请填写标题', trigger: 'blur' },
    { max: 100, message: '标题不超过 100 个字', trigger: 'blur' }
  ],
  contactPhone: [
    { required: true, message: '请填写联系电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ]
}

// 级联选择器：类型树取 id 作值、name 作文案，emitPath=false 保证只提交二级类型 ID
const cascaderProps = { value: 'id', label: 'name', children: 'children', emitPath: false }

const hasProperty = computed(() => properties.value.length > 0)

onMounted(async () => {
  loading.value = true
  const [propertyResult, categoryResult] = await Promise.allSettled([
    listMyProperties(),
    getCategoryTree()
  ])

  if (propertyResult.status === 'fulfilled') {
    properties.value = propertyResult.value || []
    // 默认选中默认房产，省一次选择
    const preset = properties.value.find((item) => item.isDefault === 1) || properties.value[0]
    if (preset) form.propertyId = preset.id
  }

  if (categoryResult.status === 'fulfilled') {
    categoryTree.value = categoryResult.value || []
  }

  // 联系电话默认取个人资料，减少重复输入
  form.contactPhone = userStore.userInfo?.phone || ''
  loading.value = false
})

/** 提交前组装载荷：可选字段为空时不发送，避免后端把空串当成有效值 */
function buildPayload() {
  const payload = {
    propertyId: form.propertyId,
    categoryId: form.categoryId,
    title: form.title.trim(),
    urgency: Number(form.urgency),
    contactPhone: form.contactPhone.trim()
  }

  const optional = {
    description: form.description.trim(),
    faultDuration: form.faultDuration.trim(),
    safetyFlag: form.safetyFlag,
    deviceInfo: form.deviceInfo.trim(),
    expectedVisitTime: form.expectedVisitTime,
    images: form.images.join(',')
  }

  if (form.affectScope !== null && form.affectScope !== '') {
    payload.affectScope = Number(form.affectScope)
  }

  Object.entries(optional).forEach(([key, value]) => {
    if (value) payload[key] = value
  })

  return payload
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const result = await createOrder(buildPayload())
    ElMessage.success(`提交成功，工单号 ${result?.orderNo || ''}`)
    // 提交后直接进入详情页，方便用户立刻看到流转状态
    if (result?.orderId) {
      router.replace({ name: 'OwnerOrderDetail', params: { id: result.orderId } })
    } else {
      router.replace({ name: 'OwnerOrderList' })
    }
  } catch {
    // 业务失败已由拦截器统一提示
  } finally {
    submitting.value = false
  }
}

function onReset() {
  formRef.value?.resetFields()
  form.images = []
}
</script>

<template>
  <div class="layout-page order-create-page">
    <div class="repair-layout">
      <aside class="repair-guide" aria-label="报修说明">
        <PageHeader title="提交报修" description="描述现场情况，物业会安排合适的师傅处理。" :back="true" />
        <div v-if="!loading && hasProperty" class="repair-guide__steps">
          <h2>把问题交给我们</h2>
          <p>提供清楚的现场信息，方便师傅判断问题、准备工具。</p>
          <ol>
            <li><strong>确认房屋与类型</strong><span>选择需要维修的房产和设施。</span></li>
            <li><strong>描述现场情况</strong><span>说明故障表现，补充照片和联系信息。</span></li>
            <li><strong>跟踪处理进度</strong><span>提交后可在「我的工单」查看进展。</span></li>
          </ol>
        </div>
      </aside>

      <SectionCard v-if="loading">
        <AppSkeleton variant="detail" :rows="5" />
      </SectionCard>

      <!-- 没有房产时无法报修，先引导去登记 -->
      <SectionCard v-else-if="!hasProperty">
        <EmptyState
          :illustration="illustrations.emptyProperty"
          title="还没有登记房产"
          description="报修需要指定具体房屋，先把你的房产登记进来吧。"
          action-text="去登记房产"
          @action="router.push({ name: 'OwnerProperty' })"
        />
      </SectionCard>

      <SectionCard v-else title="报修信息" description="带 * 的为必填项">
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="96px"
          label-position="top"
          class="order-form"
        >
          <div class="grid grid-2">
            <el-form-item label="报修房产" prop="propertyId">
              <el-select v-model="form.propertyId" placeholder="请选择房产">
                <el-option
                  v-for="item in properties"
                  :key="item.id"
                  :label="item.fullAddress"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="报修类型" prop="categoryId">
              <el-cascader
                v-model="form.categoryId"
                :options="categoryTree"
                :props="cascaderProps"
                placeholder="请选择报修类型"
                clearable
              />
            </el-form-item>
          </div>

          <el-form-item label="标题" prop="title">
            <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="一句话说明问题，例如「卧室空调不制冷」" />
          </el-form-item>

          <el-form-item label="故障现象" prop="description">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="4"
              maxlength="500"
              show-word-limit
              placeholder="描述发生时间、具体表现、已经尝试过的处理方式"
            />
          </el-form-item>

          <div class="grid grid-2">
            <el-form-item label="紧急程度" prop="urgency">
              <el-radio-group v-model="form.urgency">
                <el-radio-button v-for="item in urgencyOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="影响范围" prop="affectScope">
              <el-radio-group v-model="form.affectScope">
                <el-radio-button v-for="item in scopeOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </el-radio-button>
              </el-radio-group>
            </el-form-item>
          </div>

          <div class="grid grid-2">
            <el-form-item label="安全隐患" prop="safetyFlag">
              <el-select v-model="form.safetyFlag" placeholder="无则不选" clearable>
                <el-option v-for="item in SAFETY_FLAGS" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>

            <el-form-item label="故障持续时长" prop="faultDuration">
              <el-input v-model="form.faultDuration" placeholder="例如「约 1 天」「3 小时」" />
            </el-form-item>
          </div>

          <div class="grid grid-2">
            <el-form-item label="设备品牌型号" prop="deviceInfo">
              <el-input v-model="form.deviceInfo" placeholder="例如「格力 KFR-35GW」，有助于师傅备件" />
            </el-form-item>

            <el-form-item label="期望上门时段" prop="expectedVisitTime">
              <!-- 后端字段是文本（如「今天下午」），默认一个工作日内，可按需改写 -->
              <el-input v-model="form.expectedVisitTime" placeholder="如：一个工作日内、今天下午" />
            </el-form-item>
          </div>

          <el-form-item label="联系电话" prop="contactPhone">
            <el-input v-model="form.contactPhone" maxlength="11" placeholder="师傅上门前会先与你联系" />
          </el-form-item>

          <el-form-item label="现场照片">
            <ImageUpload v-model="form.images" :limit="3" biz-type="order" />
          </el-form-item>

          <div class="order-form__footer">
            <el-button type="primary" :loading="submitting" @click="onSubmit">提交报修</el-button>
            <el-button @click="onReset">重置</el-button>
          </div>
        </el-form>
      </SectionCard>
    </div>
  </div>
</template>

<style scoped>
.order-create-page {
  max-width: 1280px;
}

.order-form {
  width: 100%;
}

.order-form__footer {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-2);
}
</style>
