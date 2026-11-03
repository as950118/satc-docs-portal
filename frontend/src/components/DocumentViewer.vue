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
              <span class="title-line highlight">문서 관리 시스템</span>
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

      <el-alert
        v-if="staleNotice"
        type="warning"
        :closable="false"
        show-icon
        class="live-notice"
        data-test="stale-banner"
      >
        <template #title>
          문서가 수정되었습니다.
          <el-button link type="primary" data-test="stale-refresh" @click="refreshContent">새로고침</el-button>
        </template>
      </el-alert>

      <!-- 문서 헤더 -->
      <div class="document-header">
        <div class="document-title">
          <el-icon style="margin-right: 8px; color: var(--mt-ink);">
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
                 document.status === 'COMPLETE' ? '완료' : 
                 document.status === 'FAILED' ? '인덱싱 실패' : document.status }}
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
        <!-- <div class="info-row"> -->
          <!-- <span class="info-label">마지막 수정:</span> -->
          <!-- <span>{{ formatDate(document.lastModified || new Date()) }}</span> -->
            <!-- </div> -->
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
import { getClientId } from '@/api/clientId'
import { LIVE_EVENT } from '@/composables/liveEventsCore'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { specPath } from '@/api/specs'
import { goBack } from '@/utils/nav'
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
    const router = useRouter()
    const route = useRoute()
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
    let lastSearchedQuery = null
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
      if (!props.document?.filePath && !props.document?.indexOnly) {
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
        let res
        let content
        if (props.document.indexOnly) {
          // MinIO 파일이 없는 색인 전용 문서는 색인 서비스에서 본문을 받는다
          res = await documentApi.getIndexedDocument(props.document.id)
          content = res.data?.data?.content
        } else {
          res = await documentApi.getDocumentContent(props.document.id)
          content = res.data?.data
        }
        if (typeof content !== 'string') {
          throw new Error(res.data?.errorMessage || '문서 본문이 없습니다.')
        }
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
    // 검색어는 URL(?q=)에 담아 히스토리에 남긴다. 그래서 문서를 열었다가 뒤로가기 하면 검색 결과로 돌아온다.
    const performSearch = async () => {
      const q = (searchQuery.value || '').trim()
      if (!q) {
        ElMessage.warning('검색어를 입력해주세요.')
        return
      }
      if (route.path === '/' && route.query.q === q && !route.query.d) {
        await runSearch(q) // 같은 검색어 재검색: 주소가 같아 이동이 일어나지 않으므로 직접 실행
      } else {
        router.push({ path: '/', query: { q } })
      }
    }

    const runSearch = async (query) => {
      searchQuery.value = query
      lastSearchedQuery = query
      searchLoading.value = true
      searchError.value = null
      searchResults.value = null
      showSearchResults.value = true

      try {
        console.log('검색 실행:', query)
        const response = await documentApi.searchDocuments(query)
        
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

    // 검색 화면 상태만 초기화 (주소는 건드리지 않음)
    const resetSearch = () => {
      showSearchResults.value = false
      searchQuery.value = ''
      searchResults.value = null
      searchError.value = null
      fromSearchResults.value = false
      lastSearchedQuery = null
    }

    // 검색 결과 닫기 (X/뒤로 버튼): 주소의 검색어도 함께 지운다
    const closeSearchResults = () => {
      resetSearch()
      if (route.query.q) router.push({ path: '/' })
    }

    // 주소(?q=, ?d=)를 따라 검색 결과 화면을 복원한다 (뒤로/앞으로 가기, 새로고침 포함)
    watch(
      () => [route.path, route.query.q, route.query.d],
      ([path, q, d]) => {
        if (path !== '/') return
        if (q && !d) {
          showSearchResults.value = true
          fromSearchResults.value = false
          if (q !== lastSearchedQuery || (!searchResults.value && !searchLoading.value)) runSearch(String(q))
        } else if (q && d) {
          showSearchResults.value = false
          fromSearchResults.value = true
        } else if (showSearchResults.value) {
          resetSearch()
        }
      },
      { immediate: true }
    )

    // 검색 결과로 돌아가기
    const backToSearchResults = () => {
      goBack(router, { path: '/', query: { q: route.query.q || searchQuery.value } })
    }

    // 검색창 포커스 처리
    const handleSearchFocus = () => {
      // 검색창에 포커스가 되면 특별한 동작 없음
    }

    // 검색 결과에서 문서 열기
    const handleOpenDocument = (result) => {
      // PRD·기술문서는 업로드 문서가 아니라 스펙 화면으로 이동한다
      if (result.specKind) {
        router.push(specPath({ id: result.documentId, kind: result.specKind }))
        return
      }
      console.log('검색 결과에서 문서 열기:', result)
      
      // 검색 결과 정보로 문서 객체 생성 (DocumentViewer에 필요한 최소 정보)
      const documentObject = {
        id: result.documentId,
        title: result.title,
        filePath: result.objectName, // MinIO 파일 경로 (색인 전용 문서는 null)
        indexOnly: !!result.indexOnly,
        extension: 'md', // 대부분 마크다운 파일
        isFolder: false,
        status: 'COMPLETE', // 검색된 문서는 완료된 상태
        fromSearch: true // 검색에서 온 문서임을 표시하는 플래그 추가
      }
      
      console.log('생성된 문서 객체:', documentObject)
      console.log('fromSearchResults 설정 전:', fromSearchResults.value)
      
      // 검색 결과에서 온 문서임을 표시
      fromSearchResults.value = true

      emit('open-document', documentObject)

      // 히스토리에 항목을 추가해서 뒤로가기 하면 검색 결과로 돌아오게 한다. 문서 정보는 history.state 에 담는다.
      router.push({ path: '/', query: { q: route.query.q || searchQuery.value, d: '1' }, state: { doc: documentObject } })

      // 검색 결과 화면은 닫지 않고 숨김 (돌아가기 위해 유지)
      showSearchResults.value = false
    }


    // 실시간 이벤트: 열어 둔 문서가 다른 사용자에게 수정되면 본문은 그대로 두고 배너만, 삭제되면 안내 후 선택 해제
    const staleNotice = ref(false)
    const refreshContent = async () => {
      await loadFileContent()
      staleNotice.value = false
    }
    const onLiveEvent = (e) => {
      const evt = e.detail
      const current = props.document
      if (!current || !evt || evt.id !== current.id) return
      if (evt.type === 'document.updated' && evt.originId !== getClientId()) {
        staleNotice.value = true
      } else if (evt.type === 'document.deleted') {
        ElMessage.warning('문서가 삭제되었습니다')
        staleNotice.value = false
        emit('document-update', null)
      }
    }
    onMounted(() => window.addEventListener(LIVE_EVENT, onLiveEvent))
    onBeforeUnmount(() => window.removeEventListener(LIVE_EVENT, onLiveEvent))

    // 문서가 변경될 때 내용 로드
    watch(() => props.document, (newDoc, oldDoc) => {
      if (newDoc?.id !== oldDoc?.id) staleNotice.value = false
      console.log('문서 변경 감지:', newDoc)
      console.log('현재 fromSearchResults:', fromSearchResults.value)
      
      if (newDoc) {
        loadFileContent()
        
        // 검색에서 온 문서가 아닌 경우 (트리에서 선택된 경우)
        if (!newDoc.fromSearch) {
          console.log('트리에서 선택된 문서 - 검색 결과 닫기')
          if (showSearchResults.value) {
            resetSearch()
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
      staleNotice,
      refreshContent,
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
  background: var(--mt-surface);
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
  border-bottom: 1px solid rgba(10,10,10, 0.08);
  background: #ffffff;
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
  background: rgba(0,180,138, 0.2);
}

.document-title {
  display: flex;
  align-items: center;
}

.back-to-search-btn {
  transition: all 0.2s ease;
}

.back-to-search-btn:hover {
  background-color: rgba(0,180,138, 0.1) !important;
  border-radius: 6px;
}

.back-to-search-btn-floating {
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 10;
  color: var(--mt-green-deep) !important;
  font-weight: 500;
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid rgba(0,180,138, 0.2);
  border-radius: 8px;
  padding: 8px 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: all 0.2s ease;
  backdrop-filter: blur(4px);
}

.back-to-search-btn-floating:hover {
  background: rgba(0,180,138, 0.05) !important;
  border-color: rgba(0,180,138, 0.4);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transform: translateY(-1px);
}

.document-title h2 {
  margin: 0;
  color: var(--mt-ink);
  font-weight: 600;
  font-size: 24px;
  text-shadow: none;
}

.file-extension {
  color: var(--mt-steel);
  font-size: 14px;
  margin-left: 12px;
  font-weight: 500;
  padding: 4px 12px;
  background: var(--mt-hairline);
  border-radius: 20px;
  border: 1px solid rgba(10,10,10, 0.08);
  font-family: var(--mt-font-mono);
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
  background: rgba(195,125,13, 0.15);
  color: var(--mt-error);
  border: 1px solid rgba(195,125,13, 0.3);
}

.status-uploading .status-icon {
  color: var(--mt-error);
  animation: spin 1s linear infinite;
}

/* INDEXING 상태 - 파란색 */
.status-indexing {
  background: rgba(0,180,138, 0.15);
  color: var(--mt-ink);
  border: 1px solid rgba(0,180,138, 0.3);
}

.status-indexing .status-icon {
  color: var(--mt-ink);
}

/* COMPLETE 상태 - 초록색 */
.status-complete {
  background: rgba(0,180,138, 0.15);
  color: var(--mt-green-deep);
  border: 1px solid rgba(0,180,138, 0.3);
}

.status-complete .status-icon {
  color: var(--mt-green-deep);
}

/* FAILED 상태 - 인덱싱 실패 */
.status-failed {
  background: transparent !important;
  color: var(--mt-error) !important;
  border-color: var(--mt-error) !important;
}

.status-failed .status-text {
  color: var(--mt-error) !important;
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
  background: #ffffff;
  position: relative;
}

/* 스크롤바 스타일 */
.document-body::-webkit-scrollbar {
  width: 8px;
}

.document-body::-webkit-scrollbar-track {
  background: rgba(10,10,10, 0.08);
}

.document-body::-webkit-scrollbar-thumb {
  background: rgba(10,10,10, 0.08);
  border-radius: 4px;
}

.document-body::-webkit-scrollbar-thumb:hover {
  background: rgba(10,10,10, 0.08);
}

.content-container {
  padding: 32px;
}

.content-display {
  color: var(--mt-ink);
  line-height: 1.6;
}

/* 마크다운 전용 스타일 */
.markdown-viewer {
  font-family: var(--mt-font);
  font-size: 16px;
  line-height: 1.6;
  color: var(--mt-ink);
  background: #ffffff;
  padding: 32px;
  border-radius: 12px;
  box-shadow: none;
  border: 1px solid var(--mt-hairline);
  max-width: none;
  word-wrap: break-word;
  overflow-wrap: break-word;
}

/* 일반 텍스트 파일 스타일 */
.text-viewer {
  font-family: var(--mt-font-mono);
  line-height: 1.6;
  background: var(--mt-surface);
  padding: 32px;
  border-radius: 12px;
  white-space: pre-wrap;
  color: var(--mt-ink);
  box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.05);
  border: 1px solid rgba(10,10,10, 0.08);
  font-size: 14px;
  word-wrap: break-word;
  overflow-wrap: break-word;
}

/* GitHub 스타일의 마크다운 스타일링 */
.markdown-viewer :deep(h1) {
  color: var(--mt-ink);
  letter-spacing: -0.02em;
  line-height: 1.2;
  border-bottom: 1px solid var(--mt-hairline);
  padding-bottom: 10px;
  margin: 32px 0 16px 0;
  font-size: 32px;
  font-weight: 600;
}

.markdown-viewer :deep(h2) {
  color: var(--mt-ink);
  letter-spacing: -0.02em;
  line-height: 1.2;
  border-bottom: 1px solid var(--mt-hairline);
  padding-bottom: 8px;
  margin: 24px 0 12px 0;
  font-size: 24px;
  font-weight: 600;
}

.markdown-viewer :deep(h3) {
  color: var(--mt-ink);
  letter-spacing: -0.02em;
  line-height: 1.2;
  margin: 20px 0 10px 0;
  font-size: 20px;
  font-weight: 600;
}

.markdown-viewer :deep(h4) {
  color: var(--mt-ink);
  margin: 16px 0 8px 0;
  font-size: 16px;
  font-weight: 600;
}

.markdown-viewer :deep(h5) {
  color: var(--mt-ink);
  margin: 12px 0 6px 0;
  font-size: 14px;
  font-weight: 600;
}

.markdown-viewer :deep(h6) {
  color: var(--mt-ink);
  margin: 10px 0 5px 0;
  font-size: 12px;
  font-weight: 600;
}

.markdown-viewer :deep(p) {
  margin: 16px 0;
  line-height: 1.6;
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
  border-left: 4px solid var(--mt-hairline);
  padding: 0 16px;
  color: var(--mt-steel);
  margin: 16px 0;
  background: var(--mt-surface);
  border-radius: 0 6px 6px 0;
}

.markdown-viewer :deep(code) {
  background: var(--mt-surface);
  border-radius: 6px;
  font-size: 13px;
  margin: 0 2px;
  padding: 2px 6px;
  border: 1px solid var(--mt-hairline);
  font-family: var(--mt-font-mono);
  color: var(--mt-ink);
}

.markdown-viewer :deep(pre) {
  background: var(--mt-surface-code);
  border-radius: 6px;
  font-size: 85%;
  line-height: 1.45;
  overflow: auto;
  padding: 16px;
  margin: 16px 0;
  border: 1px solid var(--mt-surface-code);
}

.markdown-viewer :deep(pre code) {
  background: transparent;
  border: 0;
  font-size: 13px;
  display: inline;
  line-height: inherit;
  margin: 0;
  overflow: visible;
  padding: 0;
  word-wrap: normal;
  color: #f7f7f7;
}

.markdown-viewer :deep(table) {
  border-collapse: collapse;
  border-spacing: 0;
  margin: 16px 0;
  width: 100%;
  border: 1px solid var(--mt-hairline);
  border-radius: 6px;
  overflow: hidden;
}

.markdown-viewer :deep(th),
.markdown-viewer :deep(td) {
  border: 1px solid var(--mt-hairline);
  padding: 8px 12px;
  text-align: left;
}

.markdown-viewer :deep(th) {
  background: var(--mt-surface);
  font-weight: 600;
}

.markdown-viewer :deep(tr:nth-child(even)) {
  background: var(--mt-surface);
}

.markdown-viewer :deep(a) {
  color: var(--mt-ink);
  text-decoration: underline;
  text-decoration-color: var(--mt-green);
  text-underline-offset: 3px;
}

.markdown-viewer :deep(a:hover) {
  text-decoration-color: var(--mt-green-deep);
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
  background-color: var(--mt-hairline);
  border: 0;
  border-radius: 2px;
}

.error-message {
  padding: 40px 20px;
  text-align: center;
  background: #fbeeee;
  border-radius: 12px;
  margin: 20px;
  box-shadow: 0 4px 16px rgba(212,86,86, 0.3);
  border: 1px solid rgba(212,86,86, 0.2);
}

.loading-placeholder {
  padding: 32px;
}

.document-info {
  margin-top: 40px;
  padding: 24px 0 0 0;
  border-top: 1px solid rgba(10,10,10, 0.08);
  background: rgba(10,10,10, 0.08);
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
  color: var(--mt-slate);
}

:deep(.el-button--primary) {
  background-color: var(--mt-green-deep);
  border-color: var(--mt-green-deep);
}

:deep(.el-button--primary:hover) {
  background-color: var(--mt-green-deep);
  border-color: var(--mt-green-deep);
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
  background-color: rgba(195,125,13, 0.1) !important;
  border-color: rgba(195,125,13, 0.3) !important;
}

.status-uploading .status-icon {
  color: var(--mt-warn) !important;
  animation: spin 2s linear infinite;
}

.status-uploading .status-text {
  color: var(--mt-warn) !important;
}

.status-indexing {
  background-color: rgba(0,180,138, 0.1) !important;
  border-color: rgba(0,180,138, 0.3) !important;
}

.status-indexing .status-icon {
  color: var(--mt-ink) !important;
}

.status-indexing .status-text {
  color: var(--mt-ink) !important;
}

.status-completed,
.status-complete {
  background-color: rgba(0,180,138, 0.1) !important;
  border-color: rgba(0,180,138, 0.3) !important;
}

.status-completed .status-icon,
.status-complete .status-icon {
  color: var(--mt-green-deep) !important;
}

.status-completed .status-text,
.status-complete .status-text {
  color: var(--mt-green-deep) !important;
}

/* 더 구체적인 선택자로 완료 상태 강제 적용 */
:deep(.status-completed),
:deep(.status-complete),
:deep(.document-header .status-completed),
:deep(.document-header .status-complete) {
  background-color: rgba(0,180,138, 0.1) !important;
  border-color: rgba(0,180,138, 0.3) !important;
}

:deep(.status-completed .status-icon),
:deep(.status-complete .status-icon),
:deep(.status-completed .status-text),
:deep(.status-complete .status-text) {
  color: var(--mt-green-deep) !important;
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
  color: var(--mt-green-deep) !important;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 최종 보험 - 모든 가능한 완료 상태 선택자 */
* .status-complete,
* .status-completed {
  color: var(--mt-green-deep) !important;
}

* .status-complete .status-icon,
* .status-completed .status-icon,
* .status-complete .status-text,
* .status-completed .status-text {
  color: var(--mt-green-deep) !important;
}

/* Vue 컴포넌트 스코프 무시하고 전역 적용 */
.status-complete >>> .status-icon,
.status-complete >>> .status-text,
.status-completed >>> .status-icon,
.status-completed >>> .status-text {
  color: var(--mt-green-deep) !important;
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
  color: var(--mt-green-deep);
  animation: spin 1s linear infinite;
}

.loading-text {
  color: var(--mt-steel);
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
  display: none;
}

.video-fallback {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: var(--mt-canvas);
  z-index: -1;
  display: none;
}

/* 비디오 오버레이 */
.video-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: transparent;
  z-index: -1;
  display: none;
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
  color: var(--mt-ink);
  max-width: 700px;
  font-family: var(--mt-font);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-rendering: optimizeLegibility;
}

.main-title {
  font-size: 4.5rem;
  font-weight: 200;
  line-height: 1.1;
  margin-bottom: 2rem;
  text-shadow: none;
  letter-spacing: -0.02em;
  font-family: var(--mt-font);
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
  font-weight: 600;
  letter-spacing: -0.02em;
  opacity: 1;
}

.highlight {
  color: var(--mt-ink);
  font-weight: 600;
  position: relative;
}

.highlight::after {
  content: '';
  position: absolute;
  bottom: -8px;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--mt-green);
  border-radius: 1px;
  opacity: 1;
}

.subtitle {
  font-size: 1.1rem;
  font-weight: 400;
  line-height: 1.8;
  margin-bottom: 3rem;
  color: var(--mt-steel);
  opacity: 1;
  text-shadow: none;
  letter-spacing: 0.005em;
  font-family: var(--mt-font);
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
  background: var(--mt-canvas);
  border: 1px solid var(--mt-hairline);
  border-radius: 12px;
  padding: 0;
  transition: all 0.3s ease;
}

.search-bar:hover {
  background: var(--mt-canvas);
  border-color: var(--mt-stone);
  box-shadow: var(--mt-shadow-sm);
}

.search-icon {
  padding: 16px;
  color: var(--mt-stone);
  font-size: 18px;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  color: var(--mt-ink);
  font-size: 16px;
  font-weight: 400;
  padding: 16px 0;
  font-family: var(--mt-font);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-rendering: optimizeLegibility;
}

.search-input::placeholder {
  color: var(--mt-muted);
}

.search-shortcut {
  padding: 16px;
  flex-shrink: 0;
}

.search-shortcut span {
  display: inline-block;
  background: var(--mt-surface);
  color: var(--mt-steel);
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  font-family: var(--mt-font-mono);
  border: 1px solid var(--mt-hairline);
}

.search-hint {
  font-size: 14px;
  color: var(--mt-steel);
  text-align: center;
  margin: 0;
  font-weight: 400;
  letter-spacing: 0.005em;
  font-family: var(--mt-font);
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
.live-notice { margin: 0 0 12px; }
</style>