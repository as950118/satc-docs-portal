<template>
  <el-container style="height: 100%">
    <!-- 사이드바 (문서 트리) -->
    <el-aside width="480px" style="background: linear-gradient(135deg, #ffffff 0%, #f8f9fa 100%); padding: 0; border-right: 1px solid rgba(0, 0, 128, 0.1); box-shadow: 4px 0 20px rgba(0, 0, 128, 0.08); position: relative; overflow: hidden;">
      <DocumentTree
        @document-select="handleDocumentSelect"
        @document-add="handleDocumentAdd"
        @document-delete="handleDocumentDelete"
        @tree-data-updated="handleTreeDataUpdated"
      />
    </el-aside>

    <!-- 메인 컨텐츠 (문서 뷰어) -->
    <el-main style="padding: 40px; background: linear-gradient(135deg, #fafbfc 0%, #f0f4f8 100%); position: relative; overflow: hidden;">
      <div class="main-background-pattern"></div>
      <DocumentViewer
        :document="selectedDocument"
        @document-update="handleDocumentUpdate"
        @open-document="handleOpenDocumentById"
      />
    </el-main>
  </el-container>
</template>

<script>
import DocumentTree from '@/components/DocumentTree.vue'
import DocumentViewer from '@/components/DocumentViewer.vue'
import { ElMessage } from 'element-plus'
import { ref } from 'vue'

export default {
  name: 'Home',
  components: {
    DocumentTree,
    DocumentViewer
  },
  setup() {
    const selectedDocument = ref(null)
    const treeData = ref([])

    const handleDocumentSelect = (document) => {
      selectedDocument.value = document
    }

    const handleTreeDataUpdated = (newTreeData) => {
      treeData.value = newTreeData
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
        selectedDocument.value = null
        return
      }

      // 2. 삭제되는 것이 폴더인 경우, 현재 선택된 문서가 그 폴더 내부에 있는지 확인
      if (document.isFolder) {
        const isDocumentInDeletedFolder = checkIfDocumentInFolder(selectedDocument.value, document.id)
        if (isDocumentInDeletedFolder) {
          console.log(`현재 선택된 문서 "${selectedDocument.value.title}"가 삭제되는 폴더 "${document.title}" 내부에 있어서 홈 화면으로 돌아갑니다.`)
          selectedDocument.value = null
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
      selectedDocument.value = updatedDocument
      
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
.main-background-pattern {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  opacity: 0.03;
  background-image: 
    radial-gradient(circle at 25% 25%, #000080 2px, transparent 2px),
    radial-gradient(circle at 75% 75%, #000080 2px, transparent 2px);
  background-size: 60px 60px;
  background-position: 0 0, 30px 30px;
  pointer-events: none;
  z-index: 0;
}

:deep(.el-aside) {
  position: relative;
}

:deep(.el-aside::before) {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  width: 1px;
  height: 100%;
  background: linear-gradient(180deg, 
    transparent 0%, 
    rgba(0, 0, 128, 0.1) 25%, 
    rgba(0, 0, 128, 0.2) 50%, 
    rgba(0, 0, 128, 0.1) 75%, 
    transparent 100%
  );
}

:deep(.el-main) {
  position: relative;
  z-index: 1;
}

:deep(.el-container) {
  background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);
  border-radius: 0;
  overflow: hidden;
}
</style>

