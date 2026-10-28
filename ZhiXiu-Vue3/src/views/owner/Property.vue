<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, OfficeBuilding, Plus, Star } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import SectionCard from '@/components/SectionCard.vue'
import AppSkeleton from '@/components/AppSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import { illustrations } from '@/assets'
import {
  createProperty,
  deleteProperty,
  listMyProperties,
  setDefaultProperty,
  updateProperty
} from '@/api/property'

// 我的房产：增删改 + 设为默认；默认房产会作为报修时的预选项
const loading = ref(true)
const failed = ref(false)
const submitting = ref(false)
const properties = ref([])

const dialogVisible = ref(false)
const editingId = ref('')
const formRef = ref(null)
const form = reactive({
  community: '',
  building: '',
  unit: '',
  room: '',
  areaSize: null,
  isDefault: 0
})

const rules = {
  community: [{ required: true, message: '请填写小区名称', trigger: 'blur' }],
  building: [{ required: true, message: '请填写栋号', trigger: 'blur' }],
  unit: [{ required: true, message: '请填写单元', trigger: 'blur' }],
  room: [{ required: true, message: '请填写房号', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  failed.value = false
  try {
    properties.value = (await listMyProperties()) || []
  } catch {
    properties.value = []
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openCreate() {
  editingId.value = ''
  Object.assign(form, {
    community: '',
    building: '',
    unit: '',
    room: '',
    areaSize: null,
    // 第一条房产直接设为默认，省一步操作
    isDefault: properties.value.length ? 0 : 1
  })
  dialogVisible.value = true
}

function openEdit(item) {
  editingId.value = item.id
  Object.assign(form, {
    community: item.community || '',
    building: item.building || '',
    unit: item.unit || '',
    room: item.room || '',
    areaSize: item.areaSize ?? null,
    isDefault: item.isDefault ?? 0
  })
  dialogVisible.value = true
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  const payload = {
    community: form.community.trim(),
    building: form.building.trim(),
    unit: form.unit.trim(),
    room: form.room.trim(),
    isDefault: Number(form.isDefault)
  }
  if (form.areaSize !== null && form.areaSize !== '') payload.areaSize = Number(form.areaSize)

  submitting.value = true
  try {
    if (editingId.value) {
      await updateProperty(editingId.value, payload)
      ElMessage.success('房产已更新')
    } else {
      await createProperty(payload)
      ElMessage.success('房产已添加')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 业务失败已由拦截器提示
  } finally {
    submitting.value = false
  }
}

async function onSetDefault(item) {
  if (item.isDefault === 1) return
  try {
    await setDefaultProperty(item.id)
    ElMessage.success('已设为默认房产')
    await load()
  } catch {
    // 忽略
  }
}

async function onDelete(item) {
  try {
    await ElMessageBox.confirm(`确认删除「${item.fullAddress}」？删除后不可恢复。`, '删除房产', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  try {
    await deleteProperty(item.id)
    ElMessage.success('房产已删除')
    await load()
  } catch {
    // 忽略
  }
}
</script>

<template>
  <div class="layout-page">
    <PageHeader title="我的房产" description="报修时需要指定房屋，默认房产会在提交报修时自动选中">
      <el-button type="primary" :icon="Plus" @click="openCreate">添加房产</el-button>
    </PageHeader>

    <SectionCard v-if="loading">
      <AppSkeleton variant="card" :rows="3" :columns="3" />
    </SectionCard>

    <SectionCard v-else-if="failed">
      <EmptyState
        icon="Warning"
        title="房产加载失败"
        description="请确认后端服务已启动后重试。"
        action-text="重新加载"
        @action="load"
      />
    </SectionCard>

    <SectionCard v-else-if="!properties.length">
      <EmptyState
        :illustration="illustrations.emptyProperty"
        title="还没有登记房产"
        description="把房屋地址登记进来，报修时就能直接选择，师傅也能快速找到位置。"
        action-text="添加房产"
        @action="openCreate"
      />
    </SectionCard>

    <div v-else class="property-grid">
      <article
        v-for="item in properties"
        :key="item.id"
        class="property-card surface"
        :class="{ 'is-default': item.isDefault === 1 }"
        :aria-label="item.fullAddress"
      >
        <header class="property-card__head">
          <div class="property-card__community">
            <span class="property-card__icon" aria-hidden="true"><el-icon><OfficeBuilding /></el-icon></span>
            <h2>{{ item.community }}</h2>
          </div>
          <span v-if="item.isDefault === 1" class="property-card__default">
            <el-icon><Star /></el-icon>
            默认房产
          </span>
        </header>

        <div class="property-card__home">
          <p class="property-card__room"><strong class="text-mono">{{ item.room }}</strong><span>室</span></p>
          <p class="property-card__location">{{ item.building }} 栋<span aria-hidden="true"> / </span>{{ item.unit }} 单元</p>
        </div>

        <dl class="property-card__meta">
          <div>
            <dt>建筑面积</dt>
            <dd><span class="text-mono">{{ item.areaSize ?? '未填写' }}</span><span v-if="item.areaSize != null"> ㎡</span></dd>
          </div>
        </dl>

        <footer class="property-card__foot">
          <el-button text type="primary" :icon="Edit" @click="openEdit(item)">编辑</el-button>
          <el-button v-if="item.isDefault !== 1" text @click="onSetDefault(item)">
            设为默认
          </el-button>
          <el-button class="property-card__delete" text type="danger" @click="onDelete(item)">删除</el-button>
        </footer>
      </article>
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑房产' : '添加房产'"
      width="520px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="小区" prop="community">
          <el-input v-model="form.community" placeholder="例如「阳光嘉园」" />
        </el-form-item>

        <div class="grid grid-3">
          <el-form-item label="栋号" prop="building">
            <el-input v-model="form.building" placeholder="3" />
          </el-form-item>
          <el-form-item label="单元" prop="unit">
            <el-input v-model="form.unit" placeholder="2" />
          </el-form-item>
          <el-form-item label="房号" prop="room">
            <el-input v-model="form.room" placeholder="501" />
          </el-form-item>
        </div>

        <el-form-item label="面积">
          <el-input-number v-model="form.areaSize" :min="0" :max="2000" :precision="2" :step="1" />
          <span class="form-suffix">㎡（选填）</span>
        </el-form-item>

        <el-form-item label="默认">
          <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
          <span class="form-suffix">开启后提交报修默认选中该房产</span>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.property-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 320px), 1fr));
  gap: var(--space-5);
}

.property-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  overflow-wrap: anywhere;
}

.property-card.is-default {
  border-color: var(--color-primary-border);
}

.property-card.is-default .property-card__home {
  background: var(--color-primary-soft);
}

.property-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-5) var(--space-5) 0;
}

.property-card__community {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}

.property-card__community h2 {
  font-size: var(--fs-h3);
  font-weight: var(--fw-semibold);
  color: var(--color-text-1);
}

.property-card__icon {
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  width: 36px;
  height: 36px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text-2);
  font-size: 20px;
}

.property-card__default {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex: 0 0 auto;
  padding: 4px 8px;
  border-radius: var(--radius-full);
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: var(--fs-xs);
  white-space: nowrap;
}

.property-card__home {
  margin: var(--space-5) var(--space-5) 0;
  padding: var(--space-5);
  border-radius: var(--radius-md);
  background: var(--color-bg-sunken);
}

.property-card__room {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  color: var(--color-text-1);
}

.property-card__room strong {
  min-width: 0;
  font-size: 36px;
  font-weight: var(--fw-semibold);
  line-height: 1.2;
  letter-spacing: -0.04em;
}

.property-card__room > span {
  flex-shrink: 0;
  font-size: var(--fs-sm);
  color: var(--color-text-2);
}

.property-card__location {
  margin-top: var(--space-2);
  font-size: var(--fs-sm);
  color: var(--color-text-2);
}

.property-card__location > span {
  padding-inline: var(--space-2);
  color: var(--color-text-3);
}

.property-card__meta {
  padding: var(--space-4) var(--space-5);
}

.property-card__meta > div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
}

.property-card__meta dt {
  margin-bottom: 2px;
  font-size: var(--fs-sm);
  color: var(--color-text-3);
}

.property-card__meta dd {
  font-size: var(--fs-sm);
  color: var(--color-text-1);
}

.property-card__foot {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: auto;
  padding: var(--space-3) var(--space-5);
  border-top: 1px solid var(--color-divider);
}

.property-card__foot .el-button {
  margin: 0;
  padding-inline: var(--space-2);
}

.property-card__foot .property-card__delete {
  margin-left: auto;
}

@media (max-width: 767px) {
  .property-card__head { padding: var(--space-4) var(--space-4) 0; }
  .property-card__home { margin: var(--space-4) var(--space-4) 0; }
  .property-card__meta { padding-inline: var(--space-4); }
  .property-card__foot { padding-inline: var(--space-4); }
}

.form-suffix {
  margin-left: var(--space-2);
  font-size: var(--fs-xs);
  color: var(--color-text-3);
}
</style>
