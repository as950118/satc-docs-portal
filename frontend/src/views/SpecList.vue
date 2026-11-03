<template>
  <el-main class="spec-list">
    <div class="toolbar">
      <div class="title-area">
        <h2>{{ kindMeta.label }} 목록</h2>
        <el-button v-if="planner && kind === 'PRD'" type="primary" @click="$router.push('/specs/new')">새 PRD</el-button>
      </div>
      <!-- 필터는 오른쪽 끝에 고정. PRD 전용 체크박스는 맨 왼쪽에 붙어서, 나타나도 나머지 위치가 움직이지 않는다 -->
      <div class="actions">
        <el-checkbox v-if="kind === 'PRD'" v-model="allVersions" data-test="all-versions" @change="resetAndLoad">이전 버전 포함</el-checkbox>
        <el-input
          v-model="q"
          class="search"
          clearable
          placeholder="제목·작성자 검색"
          data-test="search"
          :prefix-icon="Search"
          @input="onQueryInput"
          @clear="resetAndLoad"
          @keyup.enter="searchNow"
        />
        <el-radio-group v-model="kind" data-test="kind-filter" @change="resetAndLoad">
          <el-radio-button v-for="(meta, key) in SPEC_KIND" :key="key" :label="key">{{ meta.label }}</el-radio-button>
        </el-radio-group>
        <el-select v-model="status" placeholder="전체 상태" clearable style="width: 160px" @change="resetAndLoad">
          <el-option v-for="(meta, key) in SPEC_STATUS" :key="key" :label="meta.label" :value="key" />
        </el-select>
      </div>
    </div>
    <el-table v-loading="loading" :data="specs" :empty-text="q.trim() ? '검색 결과가 없습니다.' : '문서가 없습니다.'" @row-click="open" row-class-name="clickable">
      <el-table-column prop="title" label="제목" min-width="260">
        <template #default="{ row }">
          {{ row.title }}
          <el-tag v-if="row.revision" size="small" effect="plain" class="revision">v{{ row.revision }}</el-tag>
          <el-tag v-if="row.prdChanged" size="small" type="danger" data-test="prd-changed">{{ prdChangedLabel(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="상태" width="140">
        <template #default="{ row }">
          <el-tag :type="SPEC_STATUS[row.status]?.tag" :class="statusClass(row.status)">{{ SPEC_STATUS[row.status]?.label || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="author" label="작성자" width="140" />
      <el-table-column label="수정일" width="200">
        <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
      </el-table-column>
    </el-table>
    <div v-if="total > 0" class="pager">
      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="total"
        :page-size="PAGE_SIZE"
        :current-page="page"
        data-test="pagination"
        @current-change="onPageChange"
      />
    </div>
  </el-main>
</template>

<script setup>
import { SPECS_CHANGED, SPEC_KIND, SPEC_STATUS, canPlan, prdChangedLabel, specApiMethods, specPath, statusClass } from '@/api/specs'
import { formatTime } from '@/utils/format'
import { buildListQuery, clampPage, parseListQuery } from '@/utils/specListQuery'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const PAGE_SIZE = 20
const DEBOUNCE_MS = 300

const router = useRouter()
const route = useRoute()
const planner = canPlan()
const specs = ref([])
const total = ref(0)
const initial = parseListQuery(route.query, { kinds: SPEC_KIND, statuses: SPEC_STATUS })
const kind = ref(initial.kind)
const kindMeta = computed(() => SPEC_KIND[kind.value])
const status = ref(initial.status)
const allVersions = ref(false)
const q = ref(initial.q)
// UI 는 1부터, API 는 0부터
const page = ref(initial.page)
const loading = ref(false)

let seq = 0
let timer = null

const syncUrl = () => {
  router.replace({ query: buildListQuery({ kind: kind.value, status: status.value, q: q.value, page: page.value }) })
}

const load = async () => {
  const mine = ++seq
  loading.value = true
  try {
    syncUrl()
    const result = await specApiMethods.listPage({
      kind: kind.value,
      status: status.value,
      allVersions: kind.value === 'PRD' && allVersions.value,
      q: q.value.trim(),
      page: page.value - 1,
      size: PAGE_SIZE,
    })
    if (mine !== seq) return
    const lastPage = clampPage(page.value, result.total, PAGE_SIZE)
    if (lastPage !== page.value) {
      page.value = lastPage
      return load()
    }
    specs.value = result.items
    total.value = result.total
  } catch (e) {
    if (mine !== seq) return
    ElMessage.error(e.message)
  } finally {
    if (mine === seq) loading.value = false
  }
}

const clearTimer = () => {
  if (timer) {
    clearTimeout(timer)
    timer = null
  }
}

const resetAndLoad = () => {
  clearTimer()
  page.value = 1
  load()
}

const onQueryInput = () => {
  clearTimer()
  timer = setTimeout(resetAndLoad, DEBOUNCE_MS)
}

const searchNow = () => resetAndLoad()

const onPageChange = (p) => {
  clearTimer()
  page.value = p
  load()
}

const open = (row) => router.push(specPath(row))

// 실시간 이벤트(spec.changed, 재연결)로 현재 페이지·필터 그대로 목록을 다시 읽는다.
onMounted(() => {
  load()
  window.addEventListener(SPECS_CHANGED, load)
})
onBeforeUnmount(() => {
  clearTimer()
  window.removeEventListener(SPECS_CHANGED, load)
})
</script>

<style scoped>
.spec-list { padding: 40px; overflow: auto; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.actions { display: flex; align-items: center; gap: 12px; }
.search { width: 220px; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
.title-area { display: flex; align-items: center; gap: 16px; }
.title-area h2 { margin: 0; }
:deep(.clickable) { cursor: pointer; }
.revision { margin-left: 6px; }
</style>
