import { SPECS_CHANGED, SPEC_STATUS, TECH_AREAS, canPlan, specApiMethods } from '@/api/specs'
import { ElMessage } from 'element-plus'
import { computed, onMounted, onUnmounted, ref } from 'vue'

// 스펙은 문서 테이블에 복제하지 않고, 목록 API 결과를 문서 트리 노드 모양의 가상 폴더로 묶는다.
// 가상 노드 id는 실제 문서 id와 겹치지 않도록 접두사를 붙이고, 스펙 노드는 spec 필드로 구분한다.
export const PRD_FOLDER = 'spec-folder:PRD'
export const TECH_FOLDER = 'spec-folder:TECH'

const specNode = (spec) => ({ id: spec.id, title: spec.title, isFolder: false, spec })

const folderNode = (id, title, children, extra = {}) => ({
  id,
  title,
  isFolder: true,
  children,
  childrenCount: children.reduce((sum, child) => sum + (child.isFolder ? child.childrenCount : 1), 0),
  ...extra,
})

export const buildSpecTree = (specs, planner) => {
  const byKind = (kind) => specs.filter((s) => s.kind === kind).map(specNode)
  return [
    folderNode(PRD_FOLDER, 'PRD', byKind('PRD'), {
      canCreate: planner,
      emptyHint: planner ? '새 PRD로 시작하세요' : '아직 PRD가 없습니다',
    }),
    folderNode(TECH_FOLDER, '기술문서',
      TECH_AREAS.map((a) => folderNode(`spec-folder:${a.kind}`, a.area, byKind(a.kind)))),
  ]
}

export const folderKeyOf = (spec) => (spec.kind === 'PRD' ? [PRD_FOLDER] : [TECH_FOLDER, `spec-folder:${spec.kind}`])

export function useSpecTree() {
  const specs = ref([])
  const loaded = ref(false)
  const planner = canPlan()

  let latest = 0

  // 연달아 바뀌면 응답 순서가 뒤집힐 수 있으므로 마지막 요청의 결과만 반영한다.
  const reload = async () => {
    const request = ++latest
    try {
      const result = await specApiMethods.listAll()
      if (request !== latest) return
      specs.value = result
      loaded.value = true
    } catch (e) {
      ElMessage.error(e.message)
    }
  }

  const tree = computed(() => buildSpecTree(specs.value, planner))

  const statusCounts = computed(() =>
    Object.keys(SPEC_STATUS)
      .map((status) => ({ status, count: specs.value.filter((s) => s.status === status).length }))
      .filter((c) => c.count > 0))

  onMounted(() => {
    reload()
    window.addEventListener(SPECS_CHANGED, reload)
  })
  onUnmounted(() => window.removeEventListener(SPECS_CHANGED, reload))

  return { specs, loaded, tree, statusCounts }
}
