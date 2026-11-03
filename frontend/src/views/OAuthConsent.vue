<template>
  <el-main class="consent-page">
    <div class="consent-card" v-loading="loading">
      <div class="consent-header">
        <img src="/logo.svg" alt="Logo" class="consent-logo" />
        <h1>외부 애플리케이션 접근 요청</h1>
      </div>

      <el-result v-if="failure" icon="warning" :title="failure" sub-title="클라이언트(예: Claude Code)에서 연결을 다시 시도하세요." />

      <el-result v-else-if="finished" icon="success" :title="finished" sub-title="이 창을 닫고 클라이언트로 돌아가세요." />

      <template v-else-if="request">
        <p class="client-name" data-test="client-name">{{ request.clientName }}</p>
        <p class="lead">
          이 애플리케이션이 <strong>{{ user.fullName || user.username }}</strong> 님의 문서 관리 시스템 계정으로 동작하도록 허용합니다.
          허용하면 이 계정의 권한으로 문서를 읽고 PRD·기술문서를 작성·승인할 수 있습니다.
        </p>

        <el-descriptions :column="1" border class="details">
          <el-descriptions-item label="계정">{{ user.fullName }} ({{ user.username }})</el-descriptions-item>
          <el-descriptions-item label="역할">
            <el-tag v-for="role in user.roles" :key="role" size="small" effect="plain" class="role">{{ role }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="돌아갈 주소"><code data-test="redirect-uri">{{ request.redirectUri }}</code></el-descriptions-item>
          <el-descriptions-item v-if="request.resource" label="사용할 서버"><code>{{ request.resource }}</code></el-descriptions-item>
          <el-descriptions-item label="유효 기간">{{ ttlLabel }}</el-descriptions-item>
        </el-descriptions>

        <el-alert type="warning" :closable="false" show-icon class="notice"
          title="직접 요청하지 않았거나 돌아갈 주소가 낯설면 거부하세요." />

        <div class="actions">
          <el-button size="large" :disabled="busy" data-test="deny" @click="decide(oauthApiMethods.deny, '접근을 거부했습니다.')">거부</el-button>
          <el-button size="large" type="primary" class="approve" :loading="busy" data-test="approve"
            @click="decide(oauthApiMethods.approve, '접근을 허용했습니다.')">허용</el-button>
        </div>
      </template>
    </div>
  </el-main>
</template>

<script setup>
import { authApiMethods, authUtils } from '@/api/auth'
import { oauthApiMethods } from '@/api/oauth'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const requestId = route.query.request_id
const loading = ref(true)
const busy = ref(false)
const request = ref(null)
const user = ref({})
const failure = ref('')
const finished = ref('')

const ttlLabel = computed(() => {
  const seconds = request.value?.tokenTtlSeconds ?? 0
  return seconds % 86400 === 0 ? `${seconds / 86400}일` : `${Math.round(seconds / 3600)}시간`
})

const relogin = () => {
  authUtils.logout()
  router.replace({ path: '/login', query: { redirect: route.fullPath } })
}

onMounted(async () => {
  try {
    if (!requestId) {
      failure.value = '인가 요청 정보가 없습니다.'
      return
    }
    const me = await authApiMethods.getCurrentUser()
    if (me.data.status !== 200) {
      relogin()
      return
    }
    user.value = me.data.data
    request.value = await oauthApiMethods.describe(requestId)
  } catch (e) {
    failure.value = e.message
  } finally {
    loading.value = false
  }
})

const decide = async (action, message) => {
  busy.value = true
  try {
    const redirectUri = await action(requestId)
    finished.value = message
    window.location.assign(redirectUri)
  } catch (e) {
    if (e.status === 401) {
      relogin()
    } else {
      failure.value = e.message
    }
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.consent-page { display: flex; justify-content: center; align-items: flex-start; padding: 48px 20px; background: var(--mt-canvas); font-family: var(--mt-font); }
.consent-card { width: 100%; max-width: 560px; background: var(--mt-canvas); border-radius: var(--mt-r-xl); padding: 40px; box-shadow: var(--mt-shadow-sm); border: 1px solid var(--mt-hairline); }
.consent-header { text-align: center; margin-bottom: 24px; }
.consent-logo { width: 110px; height: auto; margin-bottom: 12px; }
.consent-header h1 { color: var(--mt-ink); font-size: 22px; font-weight: 600; letter-spacing: -0.3px; margin: 0; }
.client-name { text-align: center; font-size: 20px; font-weight: 600; color: var(--mt-ink); margin: 0 0 12px; word-break: break-all; }
.lead { color: var(--mt-steel); font-size: 14px; line-height: 1.6; margin: 0 0 20px; }
.details code { font-family: var(--mt-font-mono); font-size: 13px; word-break: break-all; }
.role { margin-right: 6px; }
.notice { margin-top: 16px; }
.actions { display: flex; gap: 12px; margin-top: 24px; }
.actions .el-button { flex: 1; height: 46px; border-radius: var(--mt-r-full); font-weight: 600; }
.actions .approve { background: var(--mt-primary); color: var(--mt-on-primary); border: none; }
.actions .approve:hover { background: var(--mt-charcoal); }
</style>
