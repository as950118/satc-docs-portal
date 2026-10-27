<template>
  <section class="impl" data-test="impl-panel">
    <h3>구현</h3>

    <el-alert v-if="spec.status === 'APPROVED' && prdStatus !== 'SPEC_APPROVED'" type="info" :closable="false" show-icon
      title="모든 영역의 기술문서가 승인되면 구현을 시작할 수 있습니다." data-test="impl-wait-hint" />

    <div v-if="spec.status === 'APPROVED' && prdStatus === 'SPEC_APPROVED'" class="row">
      <el-button v-if="implementer" type="primary" :loading="busy" data-test="start-impl" @click="start">구현 시작</el-button>
      <span v-else class="meta">해당 영역 역할만 구현을 시작할 수 있습니다.</span>
    </div>

    <template v-if="inProgress || submitted || done">
      <el-tag v-if="done" type="success" effect="dark" size="large" data-test="implemented-badge">구현 완료</el-tag>

      <h4>연결된 PR</h4>
      <el-empty v-if="!spec.prLinks.length" description="연결된 PR이 없습니다." :image-size="40" data-test="pr-empty" />
      <ul v-else class="pr-list" data-test="pr-list">
        <li v-for="p in spec.prLinks" :key="p.url">
          <a :href="p.url" target="_blank" rel="noopener noreferrer">{{ p.url }}</a>
          <span class="meta">{{ p.linkedBy }} · {{ formatTime(p.linkedAt) }}</span>
          <el-button v-if="inProgress && implementer" link type="danger" size="small" :loading="busy"
            data-test="pr-remove" @click="removePr(p.url)">삭제</el-button>
        </li>
      </ul>

      <div v-if="inProgress && implementer" class="row">
        <el-input v-model="prUrl" placeholder="https://github.com/org/repo/pull/1" class="pr-input" data-test="pr-input"
          @keyup.enter="addPr" />
        <el-button :loading="busy" data-test="pr-add" @click="addPr">PR 추가</el-button>
      </div>
      <div v-if="prError" class="error" data-test="pr-error">{{ prError }}</div>

      <div v-if="inProgress && implementer" class="row">
        <el-button type="warning" :loading="busy" :disabled="!spec.prLinks.length" data-test="submit-impl" @click="submit">구현 검토 요청</el-button>
        <span v-if="!spec.prLinks.length" class="meta" data-test="submit-hint">PR을 하나 이상 연결해야 검토를 요청할 수 있습니다.</span>
      </div>
      <div v-else-if="inProgress" class="meta">해당 영역 역할만 PR을 연결하고 검토를 요청할 수 있습니다.</div>

      <div v-if="submitted && reviewer" class="decision" data-test="impl-decision">
        <el-input v-model="comment" type="textarea" :rows="3" placeholder="코멘트 (변경 요청 시 필수)" />
        <div v-if="commentError" class="error" data-test="impl-comment-error">{{ commentError }}</div>
        <div class="row">
          <el-button type="success" :loading="busy" data-test="approve-impl" @click="approve">구현 승인</el-button>
          <el-button type="danger" :loading="busy" data-test="request-impl-changes" @click="requestChanges">변경 요청</el-button>
        </div>
      </div>
      <div v-else-if="submitted" class="meta" data-test="impl-readonly">구현 검토 중입니다. REVIEWER 역할만 승인하거나 변경을 요청할 수 있습니다.</div>
    </template>
  </section>
</template>

<script setup>
import { canImplement, canReviewImplementation, specApiMethods } from '@/api/specs'
import { formatTime } from '@/utils/format'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, ref } from 'vue'

const props = defineProps({
  spec: { type: Object, required: true },
  prdStatus: { type: String, default: '' },
})
const emit = defineEmits(['updated', 'reload'])

const prUrl = ref('')
const prError = ref('')
const comment = ref('')
const commentError = ref('')
const busy = ref(false)

const implementer = computed(() => canImplement(props.spec.kind))
const reviewer = canReviewImplementation()
const inProgress = computed(() => props.spec.status === 'IN_IMPLEMENTATION')
const submitted = computed(() => props.spec.status === 'IMPL_IN_REVIEW')
const done = computed(() => props.spec.status === 'IMPLEMENTED')

// 409는 서버 상태가 바뀐 것이므로 부모가 최신 상태로 다시 불러온다.
const run = async (action, okMessage) => {
  busy.value = true
  try {
    emit('updated', await action())
    if (okMessage) ElMessage.success(okMessage)
    return true
  } catch (e) {
    ElMessage.error(e.message)
    if (e.status === 409) emit('reload')
    return false
  } finally {
    busy.value = false
  }
}

const start = () => run(() => specApiMethods.startImplementation(props.spec.id), '구현을 시작했습니다.')

const addPr = async () => {
  prError.value = ''
  if (!prUrl.value.trim()) {
    prError.value = 'PR URL을 입력하세요.'
    return
  }
  busy.value = true
  try {
    emit('updated', await specApiMethods.linkPr(props.spec.id, prUrl.value.trim()))
    prUrl.value = ''
  } catch (e) {
    prError.value = e.message
    if (e.status === 409) emit('reload')
  } finally {
    busy.value = false
  }
}

const removePr = async (url) => {
  try {
    await ElMessageBox.confirm(`${url}\n연결을 해제할까요?`, 'PR 연결 해제', { type: 'warning', confirmButtonText: '해제', cancelButtonText: '취소' })
  } catch {
    return
  }
  await run(() => specApiMethods.unlinkPr(props.spec.id, url), 'PR 연결을 해제했습니다.')
}

const submit = () => run(() => specApiMethods.submitImplementation(props.spec.id), '구현 검토를 요청했습니다.')

const approve = async () => {
  commentError.value = ''
  if (await run(() => specApiMethods.approveImplementation(props.spec.id, comment.value.trim()), '구현을 승인했습니다.')) {
    comment.value = ''
  }
}

const requestChanges = async () => {
  if (!comment.value.trim()) {
    commentError.value = '변경 요청에는 코멘트가 필요합니다.'
    return
  }
  commentError.value = ''
  if (await run(() => specApiMethods.requestImplementationChanges(props.spec.id, comment.value.trim()), '변경을 요청했습니다.')) {
    comment.value = ''
  }
}
</script>

<style scoped>
.impl { margin: 24px 0; }
.row { display: flex; align-items: center; gap: 12px; margin: 12px 0; }
.pr-input { max-width: 480px; }
.pr-list { padding-left: 20px; }
.pr-list li { margin-bottom: 6px; }
.pr-list .meta { margin-left: 12px; }
.decision { margin-top: 16px; }
.error { color: #f56c6c; font-size: 13px; margin-top: 4px; }
.meta { color: #909399; font-size: 13px; }
</style>
