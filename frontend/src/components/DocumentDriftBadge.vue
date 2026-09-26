<template>
  <div v-if="drift" class="drift-badge">
    <!-- 문서에 적힌 API 중 코드에 없는 것이 있음 (ARCH-0003) -->
    <el-alert
      v-if="drift.driftStatus === 'DRIFTED'"
      type="warning"
      show-icon
      :closable="false"
      title="이 문서는 실제 코드와 다를 수 있습니다"
    >
      <template #default>
        <p class="drift-summary">
          문서에 적힌 API 중 <strong>{{ missingInCode.length }}개</strong>가 코드에서 확인되지 않습니다.
          <span class="drift-meta">({{ sourceLabel }} · {{ formatDate(drift.checkedAt) }} 검사)</span>
        </p>
        <el-collapse class="drift-collapse">
          <el-collapse-item title="코드에 없는 API 보기" name="missingInCode">
            <ul class="drift-list">
              <li v-for="op in missingInCode" :key="op"><code>{{ op }}</code></li>
            </ul>
            <p v-if="drift.detail?.truncated" class="drift-note">목록이 길어 일부만 표시합니다.</p>
          </el-collapse-item>
          <el-collapse-item v-if="missingInDoc.length" title="문서에 없는 새 API 보기" name="missingInDoc">
            <ul class="drift-list">
              <li v-for="op in missingInDoc" :key="op"><code>{{ op }}</code></li>
            </ul>
          </el-collapse-item>
        </el-collapse>
        <p v-if="unresolvedPaths > 0" class="drift-note">
          코드에서 경로를 해석하지 못한 API가 {{ unresolvedPaths }}개 있어 일부는 오탐일 수 있습니다.
        </p>
      </template>
    </el-alert>

    <el-tag v-else-if="drift.synced" type="success" effect="plain" class="drift-tag">
      코드에서 자동 생성 · {{ shortCommit(drift.sourceCommit) }} · {{ formatDate(drift.syncedAt) }}
    </el-tag>

    <el-tag v-else-if="drift.driftStatus === 'IN_SYNC'" type="success" effect="plain" class="drift-tag">
      코드와 일치 확인 · {{ sourceLabel }} · {{ formatDate(drift.checkedAt) }}
    </el-tag>
  </div>
</template>

<script>
import { documentApi } from '@/api'
import { computed, ref, watch } from 'vue'

export default {
  name: 'DocumentDriftBadge',
  props: {
    documentId: {
      type: String,
      default: null
    }
  },
  setup(props) {
    const drift = ref(null)

    const load = async (documentId) => {
      drift.value = null
      if (!documentId) return
      try {
        const response = await documentApi.getDocumentDrift(documentId)
        // 문서가 바뀐 사이 응답이 늦게 도착한 경우 무시
        if (documentId === props.documentId) {
          drift.value = response.data?.data || null
        }
      } catch (error) {
        // 배지는 보조 정보이므로 실패해도 문서 표시를 막지 않는다
        console.warn('드리프트 정보 조회 실패:', error.message)
      }
    }

    watch(() => props.documentId, load, { immediate: true })

    const shortCommit = (commit) => (commit ? commit.substring(0, 7) : '')

    const sourceLabel = computed(() => {
      if (!drift.value?.driftSourceKey) return ''
      const commit = shortCommit(drift.value.driftCommit)
      return commit ? `${drift.value.driftSourceKey}@${commit}` : drift.value.driftSourceKey
    })

    const formatDate = (value) => (value ? new Date(value).toLocaleString('ko-KR') : '')

    return {
      drift,
      missingInCode: computed(() => drift.value?.detail?.missingInCode || []),
      missingInDoc: computed(() => drift.value?.detail?.missingInDoc || []),
      unresolvedPaths: computed(() => drift.value?.detail?.unresolvedPaths || 0),
      sourceLabel,
      shortCommit,
      formatDate
    }
  }
}
</script>

<style scoped>
.drift-badge {
  margin: 8px 0 12px;
}

.drift-summary {
  margin: 0 0 4px;
}

.drift-meta,
.drift-note {
  color: #909399;
  font-size: 12px;
}

.drift-note {
  margin: 4px 0 0;
}

.drift-collapse {
  --el-collapse-header-bg-color: transparent;
  --el-collapse-content-bg-color: transparent;
  border: none;
}

.drift-list {
  margin: 0;
  padding-left: 18px;
}

.drift-tag {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
