<template>
  <el-main class="spec-editor" v-loading="loading">
    <div class="toolbar">
      <div class="title-line">
        <el-button link @click="$router.push('/specs')">&larr; 목록</el-button>
        <h2>{{ isNew ? '새 PRD' : '' }}</h2>
        <template v-if="spec">
          <el-tag :type="SPEC_STATUS[spec.status]?.tag" data-test="status-tag">
            {{ SPEC_STATUS[spec.status]?.label || spec.status }}
          </el-tag>
          <el-tag v-if="approved" type="success" effect="dark" data-test="approved-badge">승인됨</el-tag>
        </template>
      </div>
      <div class="actions">
        <el-button v-if="editable" type="primary" :loading="saving" @click="save">저장</el-button>
        <el-button v-if="canApprove" type="success" :loading="saving" @click="approve">승인</el-button>
      </div>
    </div>

    <el-alert v-if="!planner" type="info" :closable="false" show-icon class="notice"
      title="PLANNER 역할만 작성, 수정, 승인할 수 있습니다. 읽기 전용으로 표시됩니다." />
    <el-alert v-else-if="approved" type="success" :closable="false" show-icon class="notice"
      title="승인된 PRD는 수정할 수 없습니다." />

    <el-input v-model="title" placeholder="제목" :readonly="!editable" class="title-input" />

    <div class="panes">
      <el-input v-if="editable" v-model="body" type="textarea" :rows="28" resize="none" class="pane" placeholder="Markdown" />
      <div class="pane preview markdown-body" data-test="preview" v-html="rendered"></div>
    </div>

    <template v-if="spec">
      <h3>이력</h3>
      <el-timeline data-test="events">
        <el-timeline-item v-for="(ev, i) in spec.events" :key="i" :timestamp="formatTime(ev.at)" placement="top">
          <strong>{{ ev.action }}</strong>
          <span v-if="ev.toStatus"> {{ ev.fromStatus ? `${ev.fromStatus} → ` : '' }}{{ ev.toStatus }}</span>
          <div class="meta">{{ ev.actor }}<span v-if="ev.actorRole"> ({{ ev.actorRole }})</span></div>
          <div v-if="ev.comment" class="meta">{{ ev.comment }}</div>
        </el-timeline-item>
      </el-timeline>
    </template>
  </el-main>
</template>

<script setup>
import { SPEC_STATUS, canPlan, specApiMethods } from '@/api/specs'
import { formatTime } from '@/utils/format'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const md = new MarkdownIt({ html: false, linkify: true })
const route = useRoute()
const router = useRouter()

const planner = canPlan()
const isNew = computed(() => !route.params.id)
const spec = ref(null)
const title = ref('')
const body = ref('')
const loading = ref(false)
const saving = ref(false)

const approved = computed(() => spec.value?.status === 'PRD_APPROVED')
const editable = computed(() => planner && (isNew.value || spec.value?.status === 'DRAFT'))
const canApprove = computed(() => planner && spec.value?.status === 'DRAFT')
const rendered = computed(() => md.render(body.value || ''))

const apply = (data) => {
  spec.value = data
  title.value = data.title
  body.value = data.body
}

const load = async () => {
  loading.value = true
  try {
    if (isNew.value) {
      const tpl = await specApiMethods.getPrdTemplate()
      body.value = tpl.markdown
    } else {
      apply(await specApiMethods.get(route.params.id))
    }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

// 403은 메시지만, 409는 최신 상태로 다시 불러와 화면과 서버를 맞춘다.
const run = async (action) => {
  saving.value = true
  try {
    await action()
  } catch (e) {
    ElMessage.error(e.message)
    if (e.status === 409 && !isNew.value) {
      await load()
    }
  } finally {
    saving.value = false
  }
}

const save = () => run(async () => {
  if (!title.value.trim()) {
    ElMessage.warning('제목을 입력하세요.')
    return
  }
  if (isNew.value) {
    const created = await specApiMethods.create(title.value, body.value)
    ElMessage.success('PRD를 저장했습니다.')
    router.replace(`/specs/${created.id}`)
    return
  }
  apply(await specApiMethods.update(spec.value.id, title.value, body.value))
  ElMessage.success('저장했습니다.')
})

const approve = () => run(async () => {
  // 화면의 미저장 편집이 승인 본문과 어긋나지 않도록 먼저 저장한다.
  if (title.value !== spec.value.title || body.value !== spec.value.body) {
    apply(await specApiMethods.update(spec.value.id, title.value, body.value))
  }
  apply(await specApiMethods.approve(spec.value.id))
  ElMessage.success('승인했습니다.')
})

onMounted(load)
</script>

<style scoped>
.spec-editor { padding: 40px; overflow: auto; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.title-line { display: flex; align-items: center; gap: 12px; }
.actions { display: flex; gap: 12px; }
.notice, .title-input { margin-bottom: 16px; }
.panes { display: flex; gap: 16px; }
.pane { flex: 1; min-width: 0; }
.preview { background: #fff; border: 1px solid #dcdfe6; border-radius: 4px; padding: 12px 16px; max-height: 640px; overflow: auto; }
.meta { color: #909399; font-size: 13px; }
</style>
