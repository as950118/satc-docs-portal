<template>
  <div class="document-view">
    <el-container style="height: calc(100vh - 60px)">
      <el-main style="padding: 20px;">
        <div v-if="document" class="standalone-document">
          <DocumentViewer
            :document="document"
            @document-update="handleDocumentUpdate"
          />
        </div>
        <div v-else-if="loading" class="loading">
          <el-skeleton :rows="10" animated />
          <div style="text-align: center; margin-top: 20px;">
            <span>문서를 불러오는 중...</span>
          </div>
        </div>
        <div v-else class="not-found">
          <el-empty description="문서를 찾을 수 없습니다">
            <el-button type="primary" @click="$router.push('/')">
              홈으로 돌아가기
            </el-button>
          </el-empty>
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<script>
import { documentApi } from '@/api'
import DocumentViewer from '@/components/DocumentViewer.vue'
import { ElMessage, ElSkeleton } from 'element-plus'
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

export default {
  name: 'DocumentView',
  components: {
    DocumentViewer,
    ElSkeleton
  },
  props: {
    id: {
      type: String,
      required: true
    }
  },
  setup(props) {
    const route = useRoute()
    const document = ref(null)
    const loading = ref(false)

    onMounted(() => {
      loadDocument(props.id)
    })

    const loadDocument = async (documentId) => {
      loading.value = true
      try {
        console.log('문서 ID로 문서 정보 조회:', documentId)
        // DocumentTree에서 문서 정보를 찾아야 함
        const treeResponse = await documentApi.getDocumentTree()
        const foundDocument = findDocumentInTree(treeResponse.data, documentId)
        
        if (foundDocument) {
          document.value = foundDocument
          console.log('문서 정보 찾음:', foundDocument)
        } else {
          console.error('문서를 찾을 수 없음:', documentId)
          document.value = null
        }
      } catch (error) {
        console.error('문서 로딩 실패:', error)
        ElMessage.error(`문서를 불러올 수 없습니다: ${error.message}`)
        document.value = null
      } finally {
        loading.value = false
      }
    }

    // 트리에서 문서 ID로 문서 찾기
    const findDocumentInTree = (tree, targetId) => {
      for (const node of tree) {
        if (!node.isFolder && node.id === targetId) {
          return node
        }
        if (node.isFolder && node.children) {
          const found = findDocumentInTree(node.children, targetId)
          if (found) return found
        }
      }
      return null
    }

    const handleDocumentUpdate = (updatedDocument) => {
      document.value = updatedDocument
      console.log('문서 업데이트:', updatedDocument)
    }

    return {
      document,
      loading,
      handleDocumentUpdate,
      findDocumentInTree
    }
  }
}
</script>

<style scoped>
.standalone-document {
  height: 100%;
}
</style>

