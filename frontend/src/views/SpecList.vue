<template>
  <el-main class="spec-list">
    <div class="toolbar">
      <h2>{{ kindMeta.label }} 목록</h2>
      <div class="actions">
        <el-radio-group v-model="kind" data-test="kind-filter" @change="load">
          <el-radio-button v-for="(meta, key) in SPEC_KIND" :key="key" :label="key">{{ meta.label }}</el-radio-button>
        </el-radio-group>
        <el-checkbox v-if="kind === 'PRD'" v-model="allVersions" data-test="all-versions" @change="load">이전 버전 포함</el-checkbox>
        <el-select v-model="status" placeholder="전체 상태" clearable style="width: 160px" @change="load">
          <el-option v-for="(meta, key) in SPEC_STATUS" :key="key" :label="meta.label" :value="key" />
        </el-select>
        <el-button v-if="planner && kind === 'PRD'" type="primary" @click="$router.push('/specs/new')">새 PRD</el-button>
      </div>
    </div>
    <el-table v-loading="loading" :data="specs" empty-text="문서가 없습니다." @row-click="open" row-class-name="clickable">
      <el-table-column prop="title" label="제목" min-width="260">
        <template #default="{ row }">
          {{ row.title }}
          <el-tag v-if="row.revision" size="small" effect="plain" class="revision">v{{ row.revision }}</el-tag>
          <el-tag v-if="row.prdChanged" size="small" type="danger" data-test="prd-changed">PRD 변경</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="상태" width="140">
        <template #default="{ row }">
          <el-tag :type="SPEC_STATUS[row.status]?.tag">{{ SPEC_STATUS[row.status]?.label || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="author" label="작성자" width="140" />
      <el-table-column label="수정일" width="200">
        <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
      </el-table-column>
    </el-table>
  </el-main>
</template>

<script setup>
import { SPEC_KIND, SPEC_STATUS, canPlan, specApiMethods } from '@/api/specs'
import { formatTime } from '@/utils/format'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()
const planner = canPlan()
const specs = ref([])
const kind = ref(SPEC_KIND[route.query.kind] ? route.query.kind : 'PRD')
const kindMeta = computed(() => SPEC_KIND[kind.value])
const status = ref('')
const allVersions = ref(false)
const loading = ref(false)

const load = async () => {
  loading.value = true
  try {
    router.replace({ query: kind.value === 'PRD' ? {} : { kind: kind.value } })
    specs.value = await specApiMethods.list({ kind: kind.value, status: status.value, allVersions: kind.value === 'PRD' && allVersions.value })
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

const open = (row) => router.push(row.kind === 'PRD' ? `/specs/${row.id}` : `/tech-docs/${row.id}`)

onMounted(load)
</script>

<style scoped>
.spec-list { padding: 40px; overflow: auto; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.actions { display: flex; gap: 12px; }
:deep(.clickable) { cursor: pointer; }
.revision { margin-left: 6px; }
</style>
