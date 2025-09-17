<template>
  <div class="document-viewer">
    <!-- 검색 결과 화면 -->
    <SearchResults
      v-if="showSearchResults"
      :searchData="searchResults"
      :loading="searchLoading"
      :error="searchError"
      :responseTime="searchResponseTime"
      @back="closeSearchResults"
      @openDocument="handleOpenDocument"
    />
    
    <div v-else-if="!document" class="no-document">
      <!-- 배경 비디오 -->
      <video 
        class="background-video" 
        autoplay 
        muted 
        loop 
        playsinline
      >
        <source src="/galaxy.mp4" type="video/mp4">
        <!-- 비디오를 지원하지 않는 브라우저를 위한 폴백 -->
        <div class="video-fallback"></div>
      </video>
      
      <!-- 오버레이 -->
      <div class="video-overlay"></div>
      
      <!-- 메인 콘텐츠 -->
      <div class="landing-content">
        <div class="hero-section">
          <div class="hero-text">
            <h1 class="main-title">
              <span class="title-line highlight">CMP 문서 관리 시스템</span>
            </h1>
            <p class="subtitle">
              필요한 개발 문서를 빠르고, 편리하게 찾아보세요.<br>
              등록된 문서는 MCP 서버와 연동하여 관리합니다.
            </p>
            <div class="cta-section">
              <div class="search-container">
                <div class="search-bar">
                  <el-icon class="search-icon"><Search /></el-icon>
                  <input 
                    type="text" 
                    class="search-input" 
                    placeholder="문서 제목, 키워드, 내용 등을 입력하세요."
                    v-model="searchQuery"
                    @keyup.enter="performSearch"
                    @focus="handleSearchFocus"
                  />
                  <div class="search-shortcut">
                    <span>⌘K</span>
                  </div>
                </div>
                <p class="search-hint">문서를 검색하거나, 왼쪽 문서 트리에서 확인해보세요.</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <div v-else class="document-content">
      <!-- 검색 결과로 돌아가기 버튼 (컴포넌트 오른쪽 상단) -->
      <el-button 
        v-if="fromSearchResults" 
        @click="backToSearchResults"
        type="text" 
        class="back-to-search-btn-floating"
      >
        <el-icon style="margin-right: 4px;"><ArrowLeft /></el-icon>
        검색 결과로 돌아가기
      </el-button>

      <!-- 문서 헤더 -->
      <div class="document-header">
        <div class="document-title">
          <el-icon style="margin-right: 8px; color: #000080;">
            <Document />
          </el-icon>
          <h2>{{ document.title }}</h2>
          <span v-if="document.extension" class="file-extension">
            .{{ document.extension }}
          </span>
          <!-- 상태 태그 (관리자만) -->
          <span v-if="document.status && isAdmin" class="status-tag" :class="`status-${document.status.toLowerCase()}`">
            <el-icon v-if="document.status === 'UPLOADING'" class="status-icon"><Loading /></el-icon>
            <el-icon v-else-if="document.status === 'INDEXING'" class="status-icon"><View /></el-icon>
            <el-icon v-else-if="document.status === 'COMPLETE'" class="status-icon"><Check /></el-icon>
            <span class="status-text">
              {{ document.status === 'UPLOADING' ? '업로드 중' : 
                 document.status === 'INDEXING' ? '인덱싱 중' : 
                 document.status === 'COMPLETE' ? '완료' : document.status }}
            </span>
          </span>
        </div>
      </div>

      <!-- 문서 내용 - 스크롤 영역 -->
      <div class="document-body">
        <!-- 에러 메시지는 더 이상 표시하지 않음 - 모든 상황을 컨텐츠로 처리 -->
        
        <div v-if="fileContent !== null" class="content-container">
          <div 
            :class="['content-display', { 'markdown-viewer': isMarkdownFile, 'text-viewer': !isMarkdownFile }]" 
            v-html="renderedContent"
          ></div>

      <!-- 문서 정보 -->
      <div class="document-info">
        <el-divider />
        <div class="info-row">
          <span class="info-label">문서 ID:</span>
          <span>{{ document.id }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">마지막 수정:</span>
          <span>{{ formatDate(document.lastModified || new Date()) }}</span>
            </div>
          </div>
        </div>
        
        <div v-else class="loading-placeholder">
          <div class="loading-content">
            <el-icon class="loading-spinner" size="32">
              <Loading />
            </el-icon>
            <p class="loading-text">
              {{ (document?.status === 'UPLOADING' && isAdmin) ? '파일을 업로드하고 있습니다...' : '파일을 불러오는 중입니다...' }}
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { documentApi } from '@/api'
import { authUtils } from '@/api/auth'
import { ArrowLeft, Check, Loading, Search, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { computed, ref, watch } from 'vue'
import SearchResults from './SearchResults.vue'

export default {
  name: 'DocumentViewer',
  components: {
    SearchResults
  },
  props: {
    document: {
      type: Object,
      default: null
    }
  },
  emits: ['document-update', 'open-document'],
  setup(props, { emit }) {
    const fileContent = ref(null)
    const contentLoading = ref(false)
    const contentError = ref('')
    
    // 권한 확인
    const isAdmin = computed(() => authUtils.isAdmin())

    // 검색 관련 상태
    const searchQuery = ref('')
    const showSearchResults = ref(false)
    const searchResults = ref(null)
    const searchLoading = ref(false)
    const searchError = ref(null)
    const searchResponseTime = ref(null)
    const fromSearchResults = ref(false) // 검색 결과에서 온 문서인지 추적

    // Markdown 렌더러 설정
    const md = new MarkdownIt({
      html: true,
      linkify: true,
      typographer: true,
      breaks: true
    })

    const isMarkdownFile = computed(() => {
      return props.document?.extension?.toLowerCase() === 'md'
    })

    const renderedContent = computed(() => {
      if (!fileContent.value) return ''
      
      if (isMarkdownFile.value) {
        return md.render(fileContent.value)
      } else {
        return fileContent.value
      }
    })

    const formatDate = (date) => {
      if (!date) return '-'
      const d = new Date(date)
      return d.toLocaleDateString('ko-KR') + ' ' + d.toLocaleTimeString('ko-KR')
    }

    const loadFileContent = async () => {
      if (!props.document?.filePath) {
        contentError.value = '파일 경로가 없습니다.'
        return
      }

      // 업로드 중인 파일에 대한 안내
      if (props.document.status === 'UPLOADING') {
        contentError.value = null
        contentLoading.value = true
        fileContent.value = null
        return
      }

      contentLoading.value = true
      contentError.value = null
      fileContent.value = null

      try {
        console.log('파일 로딩 시작:', props.document.filePath)
        
        // MinIO에서 직접 파일 내용 가져오기
        const content = await documentApi.downloadFromMinioDirectly(props.document.filePath)
        fileContent.value = content
        console.log('파일 로딩 성공:', content.length, '바이트')

      } catch (error) {
        console.error('파일 로딩 에러:', error)
        
        // 모든 에러 상황에서 로딩 상태 유지 (스피너 표시)
        contentLoading.value = true
        contentError.value = null
        fileContent.value = null
        
        // 샘플 데이터로 폴백
        if (isMarkdownFile.value) {
          fileContent.value = `# ${props.document.title}

이 문서의 실제 내용을 불러올 수 없습니다.

## 오류 정보
- **파일 경로**: ${props.document.filePath}
- **오류 메시지**: ${error.message}

## 샘플 마크다운 내용

### 제목 3
일반 텍스트 내용입니다.

- 목록 항목 1
- 목록 항목 2
- 목록 항목 3

\`\`\`javascript
// 코드 예시
function hello() {
  console.log('Hello World!');
}
\`\`\`

**굵은 글씨**와 *기울임 글씨*를 사용할 수 있습니다.

> 인용문 예시입니다.

### 더 많은 내용

Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.

Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.

#### 하위 제목

더 많은 텍스트 내용이 여기에 들어갑니다. 스크롤 테스트를 위해 충분한 양의 텍스트를 추가합니다.

1. 번호 목록 1
2. 번호 목록 2
3. 번호 목록 3

##### 더 작은 제목

계속해서 더 많은 내용을 추가하여 스크롤이 필요한 상황을 만들어 봅시다.

- 추가 목록 항목 A
- 추가 목록 항목 B
- 추가 목록 항목 C

###### 가장 작은 제목

마지막으로 더 많은 텍스트를 추가합니다. 이 내용들이 화면을 넘어가면서 스크롤이 생성되어야 합니다.

테이블 예시:

| 컬럼 1 | 컬럼 2 | 컬럼 3 |
|--------|--------|--------|
| 데이터 1 | 데이터 2 | 데이터 3 |
| 데이터 4 | 데이터 5 | 데이터 6 |

더 많은 내용이 계속 이어집니다...`
        } else {
          fileContent.value = `파일을 불러올 수 없습니다.

파일 경로: ${props.document.filePath}
오류: ${error.message}

샘플 텍스트 내용:
이것은 샘플 텍스트 파일 내용입니다.
여러 줄에 걸쳐 내용이 표시됩니다.

줄 1: 첫 번째 줄입니다.
줄 2: 두 번째 줄입니다.
줄 3: 세 번째 줄입니다.
줄 4: 네 번째 줄입니다.
줄 5: 다섯 번째 줄입니다.

더 많은 내용을 추가하여 스크롤 테스트를 합니다.
이 텍스트가 화면을 넘어가면 스크롤이 생성되어야 합니다.

추가 내용 1
추가 내용 2
추가 내용 3
추가 내용 4
추가 내용 5
추가 내용 6
추가 내용 7
추가 내용 8
추가 내용 9
추가 내용 10

마지막 줄입니다.`
        }
      } finally {
        contentLoading.value = false
      }
    }

    // 검색 실행
    const performSearch = async () => {
      if (!searchQuery.value || searchQuery.value.trim() === '') {
        ElMessage.warning('검색어를 입력해주세요.')
        return
      }

      searchLoading.value = true
      searchError.value = null
      searchResults.value = null
      showSearchResults.value = true

      try {
        console.log('검색 실행:', searchQuery.value)
        const response = await documentApi.searchDocuments(searchQuery.value)
        
        searchResults.value = response.data.data
        searchResponseTime.value = response.data.responseTime
        
        console.log('검색 결과:', searchResults.value)
        console.log('첫 번째 결과의 breadcrumb:', searchResults.value.results?.[0]?.breadcrumb)

      } catch (error) {
        console.error('검색 오류:', error)
        searchError.value = error.response?.data?.errorMessage || error.message || '검색 중 오류가 발생했습니다.'
        ElMessage.error(searchError.value)
      } finally {
        searchLoading.value = false
      }
    }

    // 검색 결과 닫기
    const closeSearchResults = () => {
      showSearchResults.value = false
      searchQuery.value = ''
      searchResults.value = null
      searchError.value = null
      fromSearchResults.value = false
    }

    // 검색 결과로 돌아가기
    const backToSearchResults = () => {
      if (searchResults.value) {
        showSearchResults.value = true
        fromSearchResults.value = false
        emit('open-document', null) // 문서 선택 해제
      }
    }

    // 검색창 포커스 처리
    const handleSearchFocus = () => {
      // 검색창에 포커스가 되면 특별한 동작 없음
    }

    // 검색 결과에서 문서 열기
    const handleOpenDocument = (result) => {
      console.log('검색 결과에서 문서 열기:', result)
      
      // 검색 결과 정보로 문서 객체 생성 (DocumentViewer에 필요한 최소 정보)
      const documentObject = {
        id: result.documentId,
        title: result.title,
        filePath: result.objectName, // MinIO 파일 경로
        extension: 'md', // 대부분 마크다운 파일
        isFolder: false,
        status: 'COMPLETE', // 검색된 문서는 완료된 상태
        fromSearch: true // 검색에서 온 문서임을 표시하는 플래그 추가
      }
      
      console.log('생성된 문서 객체:', documentObject)
      console.log('fromSearchResults 설정 전:', fromSearchResults.value)
      
      // 검색 결과에서 온 문서임을 표시
      fromSearchResults.value = true
      
      console.log('fromSearchResults 설정 후:', fromSearchResults.value)
      
      emit('open-document', documentObject)
      
      // 검색 결과 화면은 닫지 않고 숨김 (돌아가기 위해 유지)
      showSearchResults.value = false
    }


    // 문서가 변경될 때 내용 로드
    watch(() => props.document, (newDoc) => {
      console.log('문서 변경 감지:', newDoc)
      console.log('현재 fromSearchResults:', fromSearchResults.value)
      
      if (newDoc) {
        loadFileContent()
        
        // 검색에서 온 문서가 아닌 경우 (트리에서 선택된 경우)
        if (!newDoc.fromSearch) {
          console.log('트리에서 선택된 문서 - 검색 결과 닫기')
          if (showSearchResults.value) {
            closeSearchResults()
          }
          fromSearchResults.value = false
        } else {
          console.log('검색에서 온 문서 - fromSearchResults 유지')
          // 검색에서 온 문서는 fromSearchResults 상태를 유지
        }
      } else {
        fileContent.value = null
        contentError.value = ''
        // 문서가 null인 경우에만 fromSearchResults 해제
        if (!showSearchResults.value) {
          fromSearchResults.value = false
        }
      }
    }, { immediate: true })

    return {
      document: computed(() => props.document),
      fileContent,
      contentLoading,
      contentError,
      isMarkdownFile,
      renderedContent,
      formatDate,
      loadFileContent,
      isAdmin,
      // 검색 관련
      searchQuery,
      showSearchResults,
      searchResults,
      searchLoading,
      searchError,
      searchResponseTime,
      performSearch,
      closeSearchResults,
      backToSearchResults,
      handleSearchFocus,
      handleOpenDocument,
      fromSearchResults
    }
  }
}
</script>

<style scoped>
.document-viewer {
  height: 100%;
  background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(10px);
  display: flex;
  flex-direction: column;
}

.no-document {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.document-content {
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
}

.document-header {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 32px;
  border-bottom: 1px solid rgba(228, 231, 237, 0.6);
  background: linear-gradient(135deg, #ffffff 0%, #f8f9fa 100%);
  backdrop-filter: blur(10px);
  position: relative;
}

.document-header::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent 0%, rgba(0, 0, 128, 0.2) 50%, transparent 100%);
}

.document-title {
  display: flex;
  align-items: center;
}

.back-to-search-btn {
  transition: all 0.2s ease;
}

.back-to-search-btn:hover {
  background-color: rgba(0, 0, 128, 0.1) !important;
  border-radius: 6px;
}

.back-to-search-btn-floating {
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 10;
  color: #000080 !important;
  font-weight: 500;
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid rgba(0, 0, 128, 0.2);
  border-radius: 8px;
  padding: 8px 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: all 0.2s ease;
  backdrop-filter: blur(4px);
}

.back-to-search-btn-floating:hover {
  background: rgba(0, 0, 128, 0.05) !important;
  border-color: rgba(0, 0, 128, 0.4);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transform: translateY(-1px);
}

.document-title h2 {
  margin: 0;
  color: #1a202c;
  font-weight: 600;
  font-size: 24px;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.file-extension {
  color: #718096;
  font-size: 14px;
  margin-left: 12px;
  font-weight: 500;
  padding: 4px 12px;
  background: linear-gradient(135deg, #e2e8f0 0%, #f7fafc 100%);
  border-radius: 20px;
  border: 1px solid rgba(113, 128, 150, 0.2);
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
}

.status-tag {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 500;
  padding: 4px 10px;
  border-radius: 16px;
  margin-left: 12px;
  gap: 4px;
}

.status-icon {
  font-size: 12px;
}

.status-text {
  font-size: 12px;
}

/* UPLOADING 상태 - 주황색 */
.status-uploading {
  background: rgba(251, 146, 60, 0.15);
  color: #ea580c;
  border: 1px solid rgba(251, 146, 60, 0.3);
}

.status-uploading .status-icon {
  color: #ea580c;
  animation: spin 1s linear infinite;
}

/* INDEXING 상태 - 파란색 */
.status-indexing {
  background: rgba(59, 130, 246, 0.15);
  color: #2563eb;
  border: 1px solid rgba(59, 130, 246, 0.3);
}

.status-indexing .status-icon {
  color: #2563eb;
}

/* COMPLETE 상태 - 초록색 */
.status-complete {
  background: rgba(34, 197, 94, 0.15);
  color: #16a34a;
  border: 1px solid rgba(34, 197, 94, 0.3);
}

.status-complete .status-icon {
  color: #16a34a;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.document-body {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  background: linear-gradient(135deg, #ffffff 0%, #fafbfc 100%);
  position: relative;
}

/* 스크롤바 스타일 */
.document-body::-webkit-scrollbar {
  width: 8px;
}

.document-body::-webkit-scrollbar-track {
  background: rgba(248, 250, 252, 0.8);
}

.document-body::-webkit-scrollbar-thumb {
  background: rgba(203, 213, 225, 0.8);
  border-radius: 4px;
}

.document-body::-webkit-scrollbar-thumb:hover {
  background: rgba(148, 163, 184, 0.8);
}

.content-container {
  padding: 32px;
}

.content-display {
  color: #303133;
  line-height: 1.6;
}

/* 마크다운 전용 스타일 */
.markdown-viewer {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Helvetica Neue', Arial, sans-serif;
  line-height: 1.7;
  color: #2d3748;
  background: linear-gradient(135deg, #ffffff 0%, #f7fafc 100%);
  padding: 32px;
  border-radius: 12px;
  box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.05);
  border: 1px solid rgba(226, 232, 240, 0.8);
  max-width: none;
  word-wrap: break-word;
  overflow-wrap: break-word;
}

/* 일반 텍스트 파일 스타일 */
.text-viewer {
  font-family: 'SFMono-Regular', 'Monaco', 'Consolas', 'Liberation Mono', 'Menlo', monospace;
  line-height: 1.6;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  padding: 32px;
  border-radius: 12px;
  white-space: pre-wrap;
  color: #334155;
  box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.05);
  border: 1px solid rgba(203, 213, 225, 0.8);
  font-size: 14px;
  word-wrap: break-word;
  overflow-wrap: break-word;
}

/* GitHub 스타일의 마크다운 스타일링 */
.markdown-viewer :deep(h1) {
  color: #24292e;
  border-bottom: 1px solid #e1e4e8;
  padding-bottom: 10px;
  margin: 32px 0 16px 0;
  font-size: 32px;
  font-weight: 600;
}

.markdown-viewer :deep(h2) {
  color: #24292e;
  border-bottom: 1px solid #e1e4e8;
  padding-bottom: 8px;
  margin: 24px 0 12px 0;
  font-size: 24px;
  font-weight: 600;
}

.markdown-viewer :deep(h3) {
  color: #24292e;
  margin: 20px 0 10px 0;
  font-size: 20px;
  font-weight: 600;
}

.markdown-viewer :deep(h4) {
  color: #24292e;
  margin: 16px 0 8px 0;
  font-size: 16px;
  font-weight: 600;
}

.markdown-viewer :deep(h5) {
  color: #24292e;
  margin: 12px 0 6px 0;
  font-size: 14px;
  font-weight: 600;
}

.markdown-viewer :deep(h6) {
  color: #24292e;
  margin: 10px 0 5px 0;
  font-size: 12px;
  font-weight: 600;
}

.markdown-viewer :deep(p) {
  margin: 16px 0;
  line-height: 1.7;
}

.markdown-viewer :deep(ul),
.markdown-viewer :deep(ol) {
  margin: 16px 0;
  padding-left: 24px;
}

.markdown-viewer :deep(li) {
  margin: 4px 0;
  line-height: 1.6;
}

.markdown-viewer :deep(blockquote) {
  border-left: 4px solid #dfe2e5;
  padding: 0 16px;
  color: #6a737d;
  margin: 16px 0;
  background: #f6f8fa;
  border-radius: 0 6px 6px 0;
}

.markdown-viewer :deep(code) {
  background: #f6f8fa;
  border-radius: 6px;
  font-size: 85%;
  margin: 0 2px;
  padding: 2px 6px;
  font-family: 'SFMono-Regular', 'Consolas', 'Liberation Mono', 'Menlo', monospace;
  color: #e83e8c;
}

.markdown-viewer :deep(pre) {
  background: #f6f8fa;
  border-radius: 6px;
  font-size: 85%;
  line-height: 1.45;
  overflow: auto;
  padding: 16px;
  margin: 16px 0;
  border: 1px solid #e1e4e8;
}

.markdown-viewer :deep(pre code) {
  background: transparent;
  border: 0;
  display: inline;
  line-height: inherit;
  margin: 0;
  overflow: visible;
  padding: 0;
  word-wrap: normal;
  color: #24292e;
}

.markdown-viewer :deep(table) {
  border-collapse: collapse;
  border-spacing: 0;
  margin: 16px 0;
  width: 100%;
  border: 1px solid #e1e4e8;
  border-radius: 6px;
  overflow: hidden;
}

.markdown-viewer :deep(th),
.markdown-viewer :deep(td) {
  border: 1px solid #e1e4e8;
  padding: 8px 12px;
  text-align: left;
}

.markdown-viewer :deep(th) {
  background: #f6f8fa;
  font-weight: 600;
}

.markdown-viewer :deep(tr:nth-child(even)) {
  background: #f6f8fa;
}

.markdown-viewer :deep(a) {
  color: #0366d6;
  text-decoration: none;
}

.markdown-viewer :deep(a:hover) {
  text-decoration: underline;
}

.markdown-viewer :deep(strong) {
  font-weight: 600;
}

.markdown-viewer :deep(em) {
  font-style: italic;
}

.markdown-viewer :deep(hr) {
  height: 4px;
  padding: 0;
  margin: 24px 0;
  background-color: #e1e4e8;
  border: 0;
  border-radius: 2px;
}

.error-message {
  padding: 40px 20px;
  text-align: center;
  background: linear-gradient(135deg, #fed7d7 0%, #feb2b2 100%);
  border-radius: 12px;
  margin: 20px;
  box-shadow: 0 4px 16px rgba(254, 178, 178, 0.3);
  border: 1px solid rgba(245, 101, 101, 0.2);
}

.loading-placeholder {
  padding: 32px;
}

.document-info {
  margin-top: 40px;
  padding: 24px 0 0 0;
  border-top: 1px solid rgba(228, 231, 237, 0.6);
  background: rgba(248, 250, 252, 0.3);
  border-radius: 8px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  padding: 0 16px;
}

.info-label {
  font-weight: 600;
  color: #4a5568;
}

:deep(.el-button--primary) {
  background-color: #000080;
  border-color: #000080;
}

:deep(.el-button--primary:hover) {
  background-color: #000066;
  border-color: #000066;
}

/* 상태 태그 스타일 - DocumentViewer용 */
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border-radius: 8px;
  font-size: 11px;
  font-weight: 600;
  margin-left: 8px;
  border: 1px solid;
  white-space: nowrap;
}

.status-uploading {
  background-color: rgba(245, 158, 11, 0.1) !important;
  border-color: rgba(245, 158, 11, 0.3) !important;
}

.status-uploading .status-icon {
  color: #92400e !important;
  animation: spin 2s linear infinite;
}

.status-uploading .status-text {
  color: #92400e !important;
}

.status-indexing {
  background-color: rgba(59, 130, 246, 0.1) !important;
  border-color: rgba(59, 130, 246, 0.3) !important;
}

.status-indexing .status-icon {
  color: #1e40af !important;
}

.status-indexing .status-text {
  color: #1e40af !important;
}

.status-completed,
.status-complete {
  background-color: rgba(16, 185, 129, 0.1) !important;
  border-color: rgba(16, 185, 129, 0.3) !important;
}

.status-completed .status-icon,
.status-complete .status-icon {
  color: #065f46 !important;
}

.status-completed .status-text,
.status-complete .status-text {
  color: #065f46 !important;
}

/* 더 구체적인 선택자로 완료 상태 강제 적용 */
:deep(.status-completed),
:deep(.status-complete),
:deep(.document-header .status-completed),
:deep(.document-header .status-complete) {
  background-color: rgba(16, 185, 129, 0.1) !important;
  border-color: rgba(16, 185, 129, 0.3) !important;
}

:deep(.status-completed .status-icon),
:deep(.status-complete .status-icon),
:deep(.status-completed .status-text),
:deep(.status-complete .status-text) {
  color: #065f46 !important;
}

/* Element Plus 컴포넌트 내부까지 침투 */
:deep(.el-icon.status-icon) {
  color: inherit !important;
}

/* 완료 상태 전용 매우 구체적인 선택자 */
.document-header .status-tag.status-completed .status-icon,
.document-header .status-tag.status-complete .status-icon,
.document-header .status-tag.status-completed .status-text,
.document-header .status-tag.status-complete .status-text {
  color: #065f46 !important;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 최종 보험 - 모든 가능한 완료 상태 선택자 */
* .status-complete,
* .status-completed {
  color: #065f46 !important;
}

* .status-complete .status-icon,
* .status-completed .status-icon,
* .status-complete .status-text,
* .status-completed .status-text {
  color: #065f46 !important;
}

/* Vue 컴포넌트 스코프 무시하고 전역 적용 */
.status-complete >>> .status-icon,
.status-complete >>> .status-text,
.status-completed >>> .status-icon,
.status-completed >>> .status-text {
  color: #065f46 !important;
}

/* 로딩 스피너 스타일 */
.loading-placeholder {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 300px;
}

.loading-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.loading-spinner {
  color: #000080;
  animation: spin 1s linear infinite;
}

.loading-text {
  color: #666;
  font-size: 14px;
  margin: 0;
}

/* 영상 배경 랜딩 화면 스타일 */
.no-document {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* 배경 비디오 */
.background-video {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  z-index: -2;
}

.video-fallback {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #1f2937 0%, #374151 50%, #111827 100%);
  z-index: -1;
}

/* 비디오 오버레이 */
.video-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(
    135deg,
    rgba(17, 24, 39, 0.8) 0%,
    rgba(31, 41, 55, 0.7) 25%,
    rgba(55, 65, 81, 0.6) 50%,
    rgba(75, 85, 99, 0.7) 75%,
    rgba(17, 24, 39, 0.8) 100%
  );
  z-index: -1;
}

/* 메인 콘텐츠 */
.landing-content {
  position: relative;
  z-index: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 0 40px;
  text-align: center;
}

/* 히어로 섹션 */
.hero-section {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  max-width: 800px;
  width: 100%;
}

.hero-text {
  color: white;
  max-width: 700px;
  font-family: 'SF Pro Display', 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-rendering: optimizeLegibility;
}

.main-title {
  font-size: 4.5rem;
  font-weight: 200;
  line-height: 1.1;
  margin-bottom: 2rem;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.3), 0 0 0 1px rgba(255, 255, 255, 0.1);
  letter-spacing: -0.02em;
  font-family: 'SF Pro Display', 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-rendering: optimizeLegibility;
}

.title-line {
  display: block;
  margin-bottom: 0.2rem;
}

.title-line:first-child,
.title-line:last-child {
  font-weight: 300;
  opacity: 0.95;
}

.highlight {
  color: white;
  font-weight: 300;
  position: relative;
}

.highlight::after {
  content: '';
  position: absolute;
  bottom: -8px;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(45deg, #f59e0b, #10b981, #ef4444);
  border-radius: 1px;
  opacity: 0.7;
}

.subtitle {
  font-size: 1.1rem;
  font-weight: 400;
  line-height: 1.8;
  margin-bottom: 3rem;
  opacity: 0.9;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.4), 0 0 0 1px rgba(255, 255, 255, 0.05);
  letter-spacing: 0.005em;
  font-family: 'SF Pro Text', 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-rendering: optimizeLegibility;
}

/* CTA 섹션 */
.cta-section {
  margin-bottom: 2rem;
}

.search-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  max-width: 500px;
  width: 100%;
  margin: 0 auto;
}

.search-bar {
  position: relative;
  width: 100%;
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 12px;
  padding: 0;
  transition: all 0.3s ease;
}

.search-bar:hover {
  background: rgba(255, 255, 255, 0.15);
  border-color: rgba(255, 255, 255, 0.3);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}

.search-icon {
  padding: 16px;
  color: rgba(255, 255, 255, 0.6);
  font-size: 18px;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  color: white;
  font-size: 16px;
  font-weight: 400;
  padding: 16px 0;
  font-family: 'SF Pro Text', 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-rendering: optimizeLegibility;
}

.search-input::placeholder {
  color: rgba(255, 255, 255, 0.5);
}

.search-shortcut {
  padding: 16px;
  flex-shrink: 0;
}

.search-shortcut span {
  display: inline-block;
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.7);
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', monospace;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.search-hint {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.7);
  text-align: center;
  margin: 0;
  font-weight: 400;
  letter-spacing: 0.005em;
  font-family: 'SF Pro Text', 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-rendering: optimizeLegibility;
}



/* 애니메이션 */
@keyframes shimmer {
  0%, 100% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
}

/* 반응형 디자인 */
@media (max-width: 768px) {
  .landing-content {
    padding: 0 20px;
  }
  
  .main-title {
    font-size: 3rem;
    margin-bottom: 1.5rem;
  }
  
  .subtitle {
    font-size: 1rem;
    margin-bottom: 2rem;
  }
  
  .search-container {
    max-width: 400px;
  }
  
  .search-input {
    font-size: 15px;
  }
  
  .search-hint {
    font-size: 13px;
  }
}

@media (max-width: 480px) {
  .main-title {
    font-size: 2.2rem;
    letter-spacing: -0.01em;
  }
  
  .subtitle {
    font-size: 0.95rem;
    line-height: 1.7;
  }
  
  .search-container {
    max-width: 320px;
    gap: 12px;
  }
  
  .search-bar {
    border-radius: 10px;
  }
  
  .search-icon {
    padding: 14px;
    font-size: 16px;
  }
  
  .search-input {
    font-size: 14px;
    padding: 14px 0;
  }
  
  .search-shortcut {
    padding: 14px;
  }
  
  .search-shortcut span {
    padding: 4px 8px;
    font-size: 11px;
  }
  
  .search-hint {
    font-size: 12px;
  }
  
  .highlight::after {
    height: 1px;
    bottom: -6px;
  }
}
</style>