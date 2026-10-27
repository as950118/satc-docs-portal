<template>
  <el-main class="spec-editor" v-loading="loading">
    <div class="toolbar">
      <div class="title-line">
        <el-button link @click="$router.push(`/specs?kind=${kind || 'PRD'}`)">&larr; 목록</el-button>
        <h2>{{ isNew ? `새 ${kindMeta?.label || '기술문서'}` : kindMeta?.label }}</h2>
        <el-tag v-if="spec" :type="SPEC_STATUS[spec.status]?.tag" effect="dark" data-test="status-tag">
          {{ SPEC_STATUS[spec.status]?.label || spec.status }}
        </el-tag>
        <el-tag v-if="spec?.status === 'APPROVED'" type="success" effect="dark" data-test="approved-badge">승인됨</el-tag>
      </div>
      <div class="actions">
        <el-button v-if="editable" type="primary" :loading="saving" data-test="save" @click="save">저장</el-button>
        <el-button v-if="editable && !isNew" type="warning" :loading="saving" data-test="submit" @click="submit">검토 요청</el-button>
        <el-button v-if="canDecide" type="success" :loading="saving" data-test="approve" @click="approve">승인</el-button>
        <el-button v-if="canDecide" type="danger" :loading="saving" data-test="request-changes" @click="requestChanges">변경 요청</el-button>
      </div>
    </div>

    <div v-if="prd" class="prd-link" data-test="prd-link">
      상위 PRD:
      <router-link :to="`/specs/${prd.id}`">{{ prd.title }}</router-link>
      <el-tag size="small" :type="SPEC_STATUS[prd.status]?.tag">{{ SPEC_STATUS[prd.status]?.label || prd.status }}</el-tag>
    </div>

    <el-alert v-if="spec && !authorRole && !inImpl && spec.status !== 'IN_REVIEW'" type="info" :closable="false" show-icon class="notice"
      title="PLANNER 또는 해당 영역 역할만 작성할 수 있습니다. 읽기 전용으로 표시됩니다." />
    <el-alert v-else-if="spec?.status === 'IN_REVIEW' && !reviewer" type="info" :closable="false" show-icon class="notice"
      :title="`검토 중입니다. ${kindMeta?.label} 영역 역할만 승인하거나 변경을 요청할 수 있습니다.`" />
    <el-alert v-else-if="spec?.status === 'APPROVED'" type="success" :closable="false" show-icon class="notice"
      title="승인된 기술문서는 수정할 수 없습니다." />
    <el-alert v-else-if="spec?.status === 'CHANGES_REQUESTED'" type="warning" :closable="false" show-icon class="notice"
      title="변경이 요청되었습니다. 이력의 코멘트를 확인하고 수정한 뒤 다시 검토를 요청하세요." />

    <el-input v-model="title" placeholder="제목" :readonly="!editable" class="title-input" />

    <div class="panes">
      <el-input v-if="editable" v-model="body" type="textarea" :rows="28" resize="none" class="pane" placeholder="Markdown" />
      <div class="pane preview markdown-body" data-test="preview" v-html="rendered"></div>
    </div>

    <div v-if="canDecide" class="decision" data-test="decision">
      <el-input v-model="comment" type="textarea" :rows="3" placeholder="코멘트 (변경 요청 시 필수)" />
      <div v-if="commentError" class="error" data-test="comment-error">{{ commentError }}</div>
    </div>

    <ImplementationPanel v-if="spec && (spec.status === 'APPROVED' || inImpl)" :spec="spec" :prd-status="prd?.status"
      @updated="onImplUpdated" @reload="load" />

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
import { AREA_META, SPEC_KIND, SPEC_STATUS, actionLabel, canAuthorTech, canReview, specApiMethods, statusLabel } from '@/api/specs'
import ImplementationPanel from '@/components/ImplementationPanel.vue'
import { formatTime } from '@/utils/format'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const md = new MarkdownIt({ html: false, linkify: true })
const route = useRoute()
const router = useRouter()

// 새 문서는 /specs/:prdId/tech/new?area=BE, 기존 문서는 /tech-docs/:id
const isNew = computed(() => !route.params.id)
const spec = ref(null)
const prd = ref(null)
const title = ref('')
const body = ref('')
const comment = ref('')
const commentError = ref('')
const loading = ref(false)
const saving = ref(false)

const kind = computed(() => (isNew.value ? AREA_META[String(route.query.area).toUpperCase()]?.kind : spec.value?.kind))
const kindMeta = computed(() => SPEC_KIND[kind.value])
const authorRole = computed(() => canAuthorTech(kind.value))
const reviewer = computed(() => canReview(kind.value))
const editable = computed(() =>
  authorRole.value && (isNew.value || ['DRAFT', 'CHANGES_REQUESTED'].includes(spec.value?.status)))
const canDecide = computed(() => reviewer.value && spec.value?.status === 'IN_REVIEW')
const inImpl = computed(() => ['IN_IMPLEMENTATION', 'IMPL_IN_REVIEW', 'IMPLEMENTED'].includes(spec.value?.status))
const rendered = computed(() => md.render(body.value || ''))

const apply = (data) => {
  spec.value = data
  title.value = data.title
  body.value = data.body
}

// 구현 승인으로 PRD가 IMPLEMENTED로 집계될 수 있으므로 상위 PRD를 다시 읽는다.
const onImplUpdated = async (data) => {
  apply(data)
  await loadPrd(data.prdId)
}

const loadPrd = async (id) => {
  try {
    prd.value = await specApiMethods.get(id)
  } catch (e) {
    prd.value = null
  }
}

const load = async () => {
  comment.value = ''
  commentError.value = ''
  loading.value = true
  try {
    if (isNew.value) {
      const meta = AREA_META[String(route.query.area).toUpperCase()]
      if (!meta || !canAuthorTech(meta.kind)) {
        router.replace(`/specs/${route.params.prdId}`)
        return
      }
      spec.value = null
      const [tpl] = await Promise.all([specApiMethods.getTechTemplate(meta.area), loadPrd(route.params.prdId)])
      body.value = tpl.markdown
      title.value = prd.value ? `${prd.value.title} ${meta.label}` : meta.label
    } else {
      apply(await specApiMethods.get(route.params.id))
      await loadPrd(spec.value.prdId)
    }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

// 409는 최신 상태로 다시 불러와 화면과 서버를 맞춘다.
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

const isDirty = () => title.value !== spec.value.title || body.value !== spec.value.body

const save = () => run(async () => {
  if (!title.value.trim()) {
    ElMessage.warning('제목을 입력하세요.')
    return
  }
  if (isNew.value) {
    const created = await specApiMethods.createTechDoc(
      route.params.prdId, String(route.query.area).toUpperCase(), title.value, body.value)
    ElMessage.success('기술문서를 저장했습니다.')
    router.replace(`/tech-docs/${created.id}`)
    return
  }
  apply(await specApiMethods.update(spec.value.id, title.value, body.value))
  ElMessage.success('저장했습니다.')
})

// 제출은 DRAFT에서만 가능하고 CHANGES_REQUESTED는 저장해야 DRAFT가 되므로, 변경 요청 후 재제출은 저장을 거친다.
const submit = () => run(async () => {
  if (spec.value.status === 'CHANGES_REQUESTED' || isDirty()) {
    apply(await specApiMethods.update(spec.value.id, title.value, body.value))
  }
  apply(await specApiMethods.submit(spec.value.id))
  ElMessage.success('검토를 요청했습니다.')
})

const approve = () => run(async () => {
  commentError.value = ''
  apply(await specApiMethods.approve(spec.value.id, comment.value.trim()))
  comment.value = ''
  ElMessage.success('승인했습니다.')
  await loadPrd(spec.value.prdId)
  if (prd.value?.status === 'SPEC_APPROVED') {
    ElMessage.success('모든 영역이 승인되어 PRD가 명세 승인 상태가 되었습니다.')
  }
})

const requestChanges = () => run(async () => {
  if (!comment.value.trim()) {
    commentError.value = '변경 요청에는 코멘트가 필요합니다.'
    return
  }
  commentError.value = ''
  apply(await specApiMethods.requestChanges(spec.value.id, comment.value.trim()))
  comment.value = ''
  ElMessage.success('변경을 요청했습니다.')
})

watch(() => [route.params.id, route.params.prdId, route.query.area], load, { immediate: true })
</script>

<style scoped>
.spec-editor { padding: 40px; overflow: auto; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.title-line { display: flex; align-items: center; gap: 12px; }
.actions { display: flex; gap: 12px; }
.transition { margin-left: 6px; }
.prd-link { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
.notice, .title-input { margin-bottom: 16px; }
.panes { display: flex; gap: 16px; }
.pane { flex: 1; min-width: 0; }
.preview { background: #fff; border: 1px solid #dcdfe6; border-radius: 4px; padding: 12px 16px; max-height: 640px; overflow: auto; }
.decision { margin-top: 16px; }
.error { color: #f56c6c; font-size: 13px; margin-top: 4px; }
.meta { color: #909399; font-size: 13px; }
</style>
