<template>
  <el-main class="spec-editor" v-loading="loading">
    <SpecToolbar :label="isNew ? '새 PRD' : 'PRD'" :spec="spec" :approved="approved" back-to="/specs">
      <el-button v-if="canRevise" :loading="saving" data-test="new-version" @click="newVersion">새 버전 작성</el-button>
      <el-button v-if="editable" :type="canApprove ? 'default' : 'primary'" :loading="saving" @click="save">저장</el-button>
      <el-button v-if="canApprove" type="primary" :loading="saving" @click="approve">승인</el-button>
    </SpecToolbar>

    <el-alert v-if="staleNotice" type="warning" :closable="false" show-icon class="notice" data-test="stale-banner">
      <template #title>
        다른 사용자가 이 PRD를 변경했습니다. 편집 중인 내용은 그대로 두었습니다.
        <el-button link type="primary" data-test="stale-refresh" @click="refreshSpec">새로고침</el-button>
      </template>
    </el-alert>

    <el-alert v-if="spec?.status === 'SUPERSEDED'" type="info" :closable="false" show-icon class="notice" data-test="superseded">
      <template #title>
        v{{ spec.revision }}은 이후 버전으로 대체된 읽기 전용 이력입니다.
        <router-link :to="`/specs/${spec.supersededById}`">다음 버전 보기</router-link>
      </template>
    </el-alert>
    <el-alert v-else-if="spec?.supersededById" type="warning" :closable="false" show-icon class="notice" data-test="pending-version">
      <template #title>
        새 버전 v{{ spec.revision + 1 }} 초안이 작성 중입니다. 승인되면 이 버전을 대체하고 기술문서가 새 버전으로 옮겨집니다.
        <router-link :to="`/specs/${spec.supersededById}`">초안 보기</router-link>
      </template>
    </el-alert>
    <el-alert v-if="spec?.supersedesId" type="info" :closable="false" show-icon class="notice" data-test="previous-version">
      <template #title>
        v{{ spec.revision - 1 }}의 새 버전입니다.
        <span v-if="spec.status === 'DRAFT'">승인하면 구현 전 기술문서는 재검토로 돌아가고, 구현 중인 기술문서에는 "PRD 변경" 표시가 붙습니다.</span>
        <router-link :to="`/specs/${spec.supersedesId}`">이전 버전 보기</router-link>
      </template>
    </el-alert>

    <el-alert v-if="!planner" type="info" :closable="false" show-icon class="notice"
      title="PLANNER 역할만 작성, 수정, 승인할 수 있습니다. 읽기 전용으로 표시됩니다." />
    <el-alert v-else-if="approved" type="success" :closable="false" show-icon class="notice"
      title="승인된 PRD는 수정할 수 없습니다." />

    <el-input v-model="title" placeholder="제목" :readonly="!editable" class="title-input" />

    <div class="panes" ref="panesRef">
      <el-input v-if="editable" v-model="body" type="textarea" :rows="28" resize="none" class="pane" placeholder="Markdown" />
      <div class="pane preview markdown-body" data-test="preview" v-html="rendered"></div>
    </div>

    <TechDocsPanel v-if="approved" :spec="spec" @updated="apply" />

    <template v-if="spec">
      <h3>이력</h3>
      <el-timeline data-test="events">
        <el-timeline-item v-for="(ev, i) in spec.events" :key="i" :timestamp="formatTime(ev.at)" placement="top">
          <strong>{{ actionLabel(ev.action) }}</strong>
          <span v-if="ev.toStatus" class="transition">{{ ev.fromStatus ? `${statusLabel(ev.fromStatus)} → ` : '' }}{{ statusLabel(ev.toStatus) }}</span>
          <div class="meta">
            <template v-if="ev.action === 'AGGREGATE'">시스템 (PRD 자동 집계)</template>
            <template v-else>{{ ev.actor }}<span v-if="ev.actorRole"> ({{ ev.actorRole }})</span></template>
          </div>
          <div v-if="ev.comment" class="meta">{{ ev.comment }}</div>
        </el-timeline-item>
      </el-timeline>
    </template>
  </el-main>
</template>

<script setup>
import { actionLabel, canPlan, specApiMethods, statusLabel } from '@/api/specs'
import SpecToolbar from '@/components/SpecToolbar.vue'
import TechDocsPanel from '@/components/TechDocsPanel.vue'
import { formatTime } from '@/utils/format'
import { ElMessage, ElMessageBox } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { useSyncScroll } from '@/composables/useSyncScroll'
import { getClientId } from '@/api/clientId'
import { LIVE_EVENT } from '@/composables/liveEventsCore'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
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

const approved = computed(() => ['PRD_APPROVED', 'SPEC_APPROVED', 'IMPLEMENTED'].includes(spec.value?.status))
const editable = computed(() => planner && (isNew.value || spec.value?.status === 'DRAFT'))
const canApprove = computed(() => planner && spec.value?.status === 'DRAFT')
const canRevise = computed(() => planner && approved.value && !spec.value?.supersededById)
const rendered = computed(() => md.render(body.value || ''))

const panesRef = ref(null)
useSyncScroll(panesRef, editable)

const apply = (data) => {
  spec.value = data
  title.value = data.title
  body.value = data.body
}

const load = async () => {
  if (isNew.value && !planner) {
    router.replace('/specs')
    return
  }
  loading.value = true
  try {
    if (isNew.value) {
      const tpl = await specApiMethods.getPrdTemplate()
      body.value = tpl.markdown
    } else {
      const data = await specApiMethods.get(route.params.id)
      if (data.kind !== 'PRD') {
        router.replace(`/tech-docs/${data.id}`)
        return
      }
      apply(data)
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
  ElMessage.success(spec.value.supersedesId ? '승인했습니다. 이전 버전을 대체하고 기술문서를 이 버전으로 옮겼습니다.' : '승인했습니다.')
})

const newVersion = async () => {
  try {
    await ElMessageBox.confirm('현재 본문으로 새 버전 초안을 만듭니다. 승인 전까지 지금 버전이 유효합니다.', '새 버전 작성',
      { confirmButtonText: '작성', cancelButtonText: '취소' })
  } catch {
    return
  }
  await run(async () => {
    const created = await specApiMethods.newVersion(spec.value.id)
    router.push(`/specs/${created.id}`)
  })
}

// 실시간 이벤트: 열어 둔 PRD 가 다른 사용자에게 바뀌면 편집 중이 아닐 때만 다시 읽고, 편집 중이면 배너만 띄운다.
const staleNotice = ref(false)
const isEditing = () => editable.value && !!spec.value && (title.value !== spec.value.title || body.value !== spec.value.body)
const refreshSpec = async () => {
  await load()
  staleNotice.value = false
}
const onLiveEvent = async (e) => {
  const evt = e.detail
  if (evt?.type !== 'spec.changed' || isNew.value || evt.id !== route.params.id) return
  if (evt.originId === getClientId()) return
  if (isEditing()) {
    staleNotice.value = true
    return
  }
  try {
    const data = await specApiMethods.get(route.params.id)
    if (!isEditing()) apply(data)
    else staleNotice.value = true
  } catch (err) {
    console.error('스펙 재조회 실패:', err)
  }
}
watch(() => route.params.id, () => { staleNotice.value = false })
onMounted(() => window.addEventListener(LIVE_EVENT, onLiveEvent))
onBeforeUnmount(() => window.removeEventListener(LIVE_EVENT, onLiveEvent))

// /specs/new 에서 저장 후 /specs/:id 로 replace 하면 같은 컴포넌트가 재사용되므로 경로 변화에 맞춰 다시 불러온다.
watch(() => route.params.id, load, { immediate: true })
</script>

<style scoped>
.spec-editor { padding: 40px; overflow: auto; }
.transition { margin-left: 6px; }
.notice, .title-input { margin-bottom: 16px; }
.panes { display: flex; gap: 16px; }
.pane { flex: 1; min-width: 0; }
.preview { background: var(--mt-canvas); border: 1px solid var(--mt-hairline); border-radius: var(--mt-r-sm); padding: 12px 16px; max-height: 640px; overflow: auto; }
.meta { color: var(--mt-stone); font-size: 13px; }
</style>
