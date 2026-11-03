<template>
  <el-container style="height: 100%">
    <!-- 사이드바 (문서 트리) -->
    <el-aside width="480px" style="background: var(--mt-canvas); padding: 0; border-right: 1px solid var(--mt-hairline); box-shadow: none; position: relative; overflow: hidden;">
      <DocumentTree
        @document-select="handleDocumentSelect"
        @document-add="handleDocumentAdd"
        @document-delete="handleDocumentDelete"
        @tree-data-updated="handleTreeDataUpdated"
      />
    </el-aside>

    <!-- 메인 컨텐츠 (스펙 화면 또는 문서 뷰어) -->
    <router-view v-slot="{ Component }">
      <component :is="Component" v-if="Component" />
      <el-main v-else style="padding: 40px; background: linear-gradient(135deg, #fafbfc 0%, #f0f4f8 100%); position: relative; overflow: hidden;">
        <div class="main-background-pattern"></div>
        <DocumentViewer
          :document="selectedDocument"
          @document-update="handleDocumentUpdate"
          @open-document="handleOpenDocumentById"
        />
      </el-main>
    </router-view>
  </el-container>
</template>

<script>
import DocumentTree from '@/components/DocumentTree.vue'
import DocumentViewer from '@/components/DocumentViewer.vue'
import { ElMessage } from 'element-plus'
import { ref, toRaw, watch } from 'vue'
import { useLiveEvents } from '@/composables/useLiveEvents'
import { useRoute, useRouter } from 'vue-router'

export default {
  name: 'Home',
  components: {
    DocumentTree,
    DocumentViewer
  },
  setup() {
    const router = useRouter()
    const route = useRoute()
    const selectedDocument = ref(null)
    const treeData = ref([])

    // history.state 는 구조화 복제만 가능하므로 트리 노드(반응형 + children)를 평범한 객체로 줄여 담는다.
    const toStateDoc = (node) => {
      const { children, ...rest } = toRaw(node)
      return JSON.parse(JSON.stringify(rest))
    }

    // 트리에서 고른 문서도 검색 흐름처럼 히스토리 항목으로 남긴다. 선택 복원은 아래 watch 가 route 를 보고 한다.
    const handleDocumentSelect = (document) => {
      router.push({ path: '/', query: { doc: document.id }, state: { doc: toStateDoc(document) } })
    }

    // 문서 선택을 주소(?doc=, ?d=1)와 history.state 에 맞춘다. 뒤로/앞으로 가기와 새로고침 모두 여기로 복원된다.
    // 검색 결과 화면(?q)에서 X 로 닫아 빈 주소로 오면, 그 전에 열려 있던 트리 문서는 그대로 둔다.
    let prevWasSearch = false
    const syncSelection = () => {
      if (route.path !== '/') return
      const { doc: docId, d, q } = route.query
      const stateDoc = window.history.state?.doc
      if (d && stateDoc) {
        selectedDocument.value = stateDoc
      } else if (docId) {
        selectedDocument.value = String(stateDoc?.id) === docId ? stateDoc : findNodeInTree(treeData.value, docId)
      } else if (!d) {
        const keep = selectedDocument.value && !selectedDocument.value.fromSearch && (q || prevWasSearch)
        if (!keep) selectedDocument.value = null
      }
      prevWasSearch = !!q && !d
    }
    // 현재 선택을 비운다. 트리에서 연 문서의 주소(?doc=)는 함께 지워 뒤로가기로 지운 문서가 되살아나지 않게 한다.
    const clearSelection = () => {
      selectedDocument.value = null
      if (route.query.doc) router.replace('/')
    }

    // 문서 토스트 클릭: 트리에 읽혀 있는 노드면 그 문서를 연다. 아직 읽히지 않은 폴더 안이면 홈으로만 간다.
    const handleToastOpenDocument = (evt) => {
      const node = findNodeInTree(treeData.value, evt.id)
      if (node && !node.isFolder) {
        handleDocumentSelect(node)
      } else {
        router.push('/')
      }
    }

    const findNodeInTree = (tree, id) => {
      for (const node of tree) {
        if (String(node.id) === String(id)) return node
        const found = node.children?.length ? findNodeInTree(node.children, id) : null
        if (found) return found
      }
      return null
    }

    watch(() => route.fullPath, syncSelection, { immediate: true })

    useLiveEvents({ router, openDocument: handleToastOpenDocument })

    const handleTreeDataUpdated = (newTreeData) => {
      treeData.value = newTreeData
      if (route.query.doc && !selectedDocument.value) syncSelection()
      console.log('트리 데이터 업데이트됨:', newTreeData.length, '개 항목')
    }

    const handleDocumentAdd = (document) => {
      ElMessage.success(`"${document.title}" 문서가 추가되었습니다.`)
      // 파일 업로드 후 자동 선택 기능 제거 - 사용자가 수동으로 선택하도록 함
    }

    const handleDocumentDelete = (document) => {
      // 현재 선택된 문서가 있는지 확인
      if (!selectedDocument.value) {
        return
      }

      // 1. 현재 선택된 문서 자체가 삭제되는 경우
      if (selectedDocument.value.id === document.id) {
        clearSelection()
        return
      }

      // 2. 삭제되는 것이 폴더인 경우, 현재 선택된 문서가 그 폴더 내부에 있는지 확인
      if (document.isFolder) {
        const isDocumentInDeletedFolder = checkIfDocumentInFolder(selectedDocument.value, document.id)
        if (isDocumentInDeletedFolder) {
          console.log(`현재 선택된 문서 "${selectedDocument.value.title}"가 삭제되는 폴더 "${document.title}" 내부에 있어서 홈 화면으로 돌아갑니다.`)
          clearSelection()
          ElMessage.info(`상위 폴더가 삭제되었습니다. 홈 화면으로 돌아갑니다.`)
        }
      }
    }

    // 문서가 특정 폴더 내부에 있는지 확인하는 함수 (재귀적으로 부모 관계 확인)
    const checkIfDocumentInFolder = (doc, folderId) => {
      if (!doc || !doc.parentId) {
        return false
      }

      // 직접적인 부모가 삭제되는 폴더인 경우
      if (doc.parentId === folderId) {
        return true
      }

      // 재귀적으로 상위 폴더들을 확인
      // 현재 문서의 부모 폴더를 트리에서 찾아서 그 부모의 부모도 확인
      const parentFolder = findFolderInTree(treeData.value, doc.parentId)
      if (parentFolder && parentFolder.parentId) {
        return checkIfDocumentInFolder(parentFolder, folderId)
      }

      return false
    }

    // 트리에서 특정 ID의 폴더를 찾는 함수
    const findFolderInTree = (tree, folderId) => {
      for (const node of tree) {
        if (node.id === folderId && node.isFolder) {
          return node
        }
        if (node.children && node.children.length > 0) {
          const found = findFolderInTree(node.children, folderId)
          if (found) return found
        }
      }
      return null
    }

    const handleDocumentUpdate = (updatedDocument) => {
      // 문서 업데이트 시 현재 선택된 문서도 업데이트
      if (updatedDocument) selectedDocument.value = updatedDocument
      else clearSelection()

      // 실제 애플리케이션에서는 여기서 백엔드 API 호출
      console.log('문서 업데이트:', updatedDocument)
    }

    // 검색 결과에서 문서 열기
    const handleOpenDocumentById = (document) => {
      console.log('검색 결과에서 문서 열기:', document)
      // 문서 객체를 직접 선택
      selectedDocument.value = document
    }

    return {
      selectedDocument,
      treeData,
      handleDocumentSelect,
      handleTreeDataUpdated,
      handleDocumentAdd,
      handleDocumentDelete,
      handleDocumentUpdate,
      handleOpenDocumentById,
      checkIfDocumentInFolder,
      findFolderInTree
    }
  }
}
</script>

<style scoped>
.main-background-pattern { display: none; }
:deep(.el-aside) { position: relative; background: var(--mt-canvas); border-right: 1px solid var(--mt-hairline); }
:deep(.el-main) { position: relative; z-index: 1; }
:deep(.el-container) { background: var(--mt-canvas); border-radius: 0; overflow: hidden; }
</style>

