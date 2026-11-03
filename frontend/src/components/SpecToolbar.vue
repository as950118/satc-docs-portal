<template>
  <div class="toolbar">
    <div class="title-line">
      <el-button link @click="goBack(router, backTo)">&larr; 목록</el-button>
      <h2>{{ label }}</h2>
      <template v-if="spec">
        <el-tag :type="SPEC_STATUS[spec.status]?.tag" :class="statusClass(spec.status)" effect="dark" data-test="status-tag">
          {{ SPEC_STATUS[spec.status]?.label || spec.status }}
        </el-tag>
        <el-tag v-if="approved" type="success" class="st-approved" effect="dark" data-test="approved-badge">승인됨</el-tag>
        <el-tag v-if="spec.revision != null" effect="plain" data-test="revision">v{{ spec.revision }}</el-tag>
      </template>
    </div>
    <!-- 버튼 순서 규칙: 보조(저장 등), 위험(변경 요청, plain danger), 마지막에 앞으로 나아가는 primary -->
    <div class="actions"><slot /></div>
  </div>
</template>

<script setup>
import { SPEC_STATUS, statusClass } from '@/api/specs'
import { goBack } from '@/utils/nav'
import { useRouter } from 'vue-router'

defineProps({
  label: { type: String, default: '' },
  spec: { type: Object, default: null },
  approved: { type: Boolean, default: false },
  backTo: { type: String, default: '/specs' },
})

const router = useRouter()
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.title-line { display: flex; align-items: center; gap: 12px; }
.title-line h2 { margin: 0; }
.actions { display: flex; align-items: center; gap: 12px; }
</style>
