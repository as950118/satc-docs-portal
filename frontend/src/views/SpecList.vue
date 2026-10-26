<template>
  <el-main class="spec-list">
    <div class="toolbar">
      <h2>PRD 목록</h2>
      <div class="actions">
        <el-select v-model="status" placeholder="전체 상태" clearable style="width: 160px" @change="load">
          <el-option v-for="(meta, key) in SPEC_STATUS" :key="key" :label="meta.label" :value="key" />
        </el-select>
        <el-button v-if="planner" type="primary" @click="$router.push('/specs/new')">새 PRD</el-button>
      </div>
    </div>
    <el-table v-loading="loading" :data="specs" empty-text="PRD가 없습니다." @row-click="open" row-class-name="clickable">
      <el-table-column prop="title" label="제목" min-width="260" />
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
import { SPEC_STATUS, canPlan, specApiMethods } from '@/api/specs'
import { formatTime } from '@/utils/format'
import { ElMessage } from 'element-plus'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const planner = canPlan()
const specs = ref([])
const status = ref('')
const loading = ref(false)

const load = async () => {
  loading.value = true
  try {
    specs.value = await specApiMethods.list({ status: status.value })
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

const open = (row) => router.push(`/specs/${row.id}`)

onMounted(load)
</script>

<style scoped>
.spec-list { padding: 40px; overflow: auto; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.actions { display: flex; gap: 12px; }
:deep(.clickable) { cursor: pointer; }
</style>
