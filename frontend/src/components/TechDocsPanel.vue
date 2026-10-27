<template>
  <section class="tech-docs" data-test="tech-docs">
    <h3>기술문서</h3>

    <el-steps :active="STEP_ACTIVE[spec.status] || 1" finish-status="success" class="steps" data-test="prd-steps">
      <el-step title="PRD 승인" description="PRD_APPROVED" />
      <el-step title="명세 승인" description="SPEC_APPROVED (필요 영역 기술문서가 모두 승인되면 자동)" />
      <el-step title="구현 완료" description="IMPLEMENTED (모든 영역의 구현이 승인되면 자동)" />
    </el-steps>

    <div v-if="planner && spec.status === 'PRD_APPROVED'" class="declare" data-test="declare">
      <span>필요 영역</span>
      <el-checkbox-group v-model="selected">
        <el-checkbox v-for="a in TECH_AREAS" :key="a.area" :label="a.area" :data-test="`area-${a.area}`">{{ a.area }}</el-checkbox>
      </el-checkbox-group>
      <el-button type="primary" :loading="saving" :disabled="!selected.length" data-test="declare-save" @click="declare">영역 저장</el-button>
    </div>

    <el-empty v-if="!spec.techAreas.length" description="아직 필요 영역이 선언되지 않았습니다." :image-size="60" />

    <div class="cards">
      <el-card v-for="a in declared" :key="a.area" shadow="never" :data-test="`card-${a.area}`">
        <template #header>
          <strong>{{ a.label }}</strong>
          <el-tag v-if="!docs" type="info" class="status" data-test="docs-loading">불러오는 중</el-tag>
          <el-tag v-else-if="docs[a.area]" :type="SPEC_STATUS[docs[a.area].status]?.tag" class="status">
            {{ SPEC_STATUS[docs[a.area].status]?.label || docs[a.area].status }}
          </el-tag>
          <el-tag v-else type="info" class="status">미작성</el-tag>
        </template>
        <router-link v-if="docs?.[a.area]" :to="`/tech-docs/${docs[a.area].id}`">{{ docs[a.area].title }}</router-link>
        <el-button v-else-if="docs && canAuthorTech(a.kind) && spec.status === 'PRD_APPROVED'" size="small" type="primary"
          :data-test="`write-${a.area}`" @click="$router.push(`/specs/${spec.id}/tech/new?area=${a.area}`)">작성</el-button>
        <span v-else-if="docs" class="meta">작성된 문서가 없습니다.</span>
      </el-card>
    </div>
  </section>
</template>

<script setup>
import { SPEC_STATUS, TECH_AREAS, canAuthorTech, canPlan, specApiMethods } from '@/api/specs'
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'

const props = defineProps({ spec: { type: Object, required: true } })
const emit = defineEmits(['updated'])

const STEP_ACTIVE = { PRD_APPROVED: 1, SPEC_APPROVED: 2, IMPLEMENTED: 3 }

const planner = canPlan()
const selected = ref([...props.spec.techAreas])
const docs = ref(null)
const saving = ref(false)

const declared = computed(() => TECH_AREAS.filter((a) => props.spec.techAreas.includes(a.area)))

const loadDocs = async () => {
  docs.value = null
  try {
    const list = await specApiMethods.listTechDocs(props.spec.id)
    docs.value = Object.fromEntries(list.map((d) => [TECH_AREAS.find((a) => a.kind === d.kind)?.area, d]))
  } catch (e) {
    ElMessage.error(e.message)
  }
}

const declare = async () => {
  saving.value = true
  try {
    emit('updated', await specApiMethods.setTechAreas(props.spec.id, selected.value))
    ElMessage.success('필요 영역을 저장했습니다.')
  } catch (e) {
    ElMessage.error(e.message)
    if (e.status === 409) {
      emit('updated', await specApiMethods.get(props.spec.id))
    }
  } finally {
    saving.value = false
  }
}

watch(() => props.spec.techAreas, (areas) => { selected.value = [...areas] })
watch(() => props.spec.id, loadDocs, { immediate: true })
</script>

<style scoped>
.tech-docs { margin: 24px 0; flex-shrink: 0; }
.steps { margin: 16px 0 24px; }
.declare { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 16px; }
.status { margin-left: 8px; }
.meta { color: #909399; font-size: 13px; }
</style>
