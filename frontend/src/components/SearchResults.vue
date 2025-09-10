<template>
  <div class="search-results">
    <!-- 홈 화면으로 돌아가기 버튼 (플로팅) -->
    <el-button @click="$emit('back')" class="back-button-floating">
      <el-icon><ArrowLeft /></el-icon>
      홈 화면으로 돌아가기
    </el-button>

    <!-- 검색 헤더 -->
    <div class="search-header">
      <div class="search-info">
        <h2 class="search-title" v-if="searchData">
          <el-icon><Search /></el-icon>
          <span class="search-query">"{{ searchData.query }}"</span>
          <span class="search-text">검색 결과</span>
          <span class="search-count">{{ searchData.totalResults }}개 문서 발견</span>
        </h2>
        <h2 class="search-title" v-else>
          <el-icon><Search /></el-icon>
          검색 결과
        </h2>
      </div>
    </div>

    <!-- 로딩 상태 -->
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="5" animated />
      <div class="loading-text">문서를 검색하고 있습니다...</div>
    </div>

    <!-- 오류 상태 -->
    <div v-else-if="error" class="error-container">
      <el-alert
        :title="error"
        type="error"
        show-icon
        :closable="false"
      />
    </div>

    <!-- 검색 결과 없음 -->
    <div v-else-if="searchData && searchData.totalResults === 0" class="no-results">
      <el-empty description="검색 결과가 없습니다">
        <template #image>
          <el-icon size="64" color="#909399"><Search /></el-icon>
        </template>
        <div class="no-results-text">
          <p>"{{ searchData.query }}"에 대한 검색 결과가 없습니다.</p>
          <p>다른 키워드로 검색해보세요.</p>
        </div>
      </el-empty>
    </div>

    <!-- 검색 결과 목록 -->
    <div v-else-if="searchData && searchData.results" class="results-container">
      <div class="results-stats">
        <span class="stats-text">* 연관도가 높은 문서를 최대 10개까지 표시합니다.</span>
      </div>

      <div class="results-list">
        <div 
          v-for="(result, index) in searchData.results" 
          :key="result.documentId"
          class="result-item"
          @click="openDocument(result)"
        >
          <div class="result-header">
            <div class="result-title">
              <el-icon><Document /></el-icon>
              <span class="document-title">{{ (result.title || `문서 #${index + 1}`) + '.md' }}</span>
            </div>
            <div class="result-id">{{ result.documentId }}</div>
          </div>
          
          
          <div class="result-content">
            <div 
              v-for="(highlight, hIndex) in result.highlightContent" 
              :key="hIndex"
              class="highlight-snippet"
              v-html="renderMarkdown(highlight)"
            ></div>
          </div>
          
          <div class="result-footer">
            <div class="result-path">
              <el-icon><Folder /></el-icon>
              <template v-if="result.breadcrumb && Array.isArray(result.breadcrumb) && result.breadcrumb.length > 0">
                <span v-for="(crumb, crumbIndex) in result.breadcrumb" :key="crumb.id || crumbIndex">
                  {{ crumb.title }}<span v-if="crumbIndex < result.breadcrumb.length - 1">/</span>
                </span>
                <span>/{{ result.title }}.md</span>
              </template>
              <template v-else>
                {{ result.objectName }}
              </template>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ArrowLeft, Document, Folder, Search } from '@element-plus/icons-vue'
import { ElAlert, ElButton, ElEmpty, ElIcon, ElSkeleton } from 'element-plus'
import MarkdownIt from 'markdown-it'

// Props
const props = defineProps({
  searchData: {
    type: Object,
    default: null
  },
  loading: {
    type: Boolean,
    default: false
  },
  error: {
    type: String,
    default: null
  },
  responseTime: {
    type: String,
    default: null
  }
})

// Emits
const emit = defineEmits(['back', 'openDocument'])

// Markdown 렌더러 설정
const md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true,
  breaks: true
})

// Methods
const formatResponseTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  return `${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}:${date.getSeconds().toString().padStart(2, '0')}`
}

// Markdown 렌더링 함수
const renderMarkdown = (content) => {
  if (!content) return ''
  try {
    return md.render(content)
  } catch (error) {
    console.error('Markdown 렌더링 오류:', error)
    return content // 오류 시 원본 반환
  }
}

const openDocument = (result) => {
  // 부모 컴포넌트로 문서 정보 전달
  emit('openDocument', result)
}

// 파일명 추출 함수 (확장자 포함)
const getFileName = (objectName) => {
  if (!objectName) return ''
  const parts = objectName.split('/')
  return parts[parts.length - 1] // 마지막 부분이 파일명 (확장자 포함)
}
</script>

<style scoped>
.search-results {
  padding: 20px;
  width: 100%;
  height: 100vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  position: relative;
}

.search-header {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.back-button {
  background-color: white !important;
  color: #606266 !important;
  border: 1px solid #dcdfe6 !important;
  transition: all 0.2s ease;
}

.back-button:hover {
  background-color: #f5f7fa !important;
  color: #409eff !important;
  border-color: #c6e2ff !important;
}

.back-button-floating {
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

.back-button-floating:hover {
  background: rgba(0, 0, 128, 0.05) !important;
  border-color: rgba(0, 0, 128, 0.4);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transform: translateY(-1px);
}

.search-info {
  flex: 1;
}

.search-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 8px 0;
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

.search-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 14px;
  color: #606266;
}

.search-query {
  font-weight: 700;
  color: #409eff;
}

.search-text {
  font-weight: 600;
  color: #303133;
}

.search-count {
  font-weight: 400;
  font-size: 16px;
  color: #606266;
  margin-top: 5px;
  margin-left: 5px;
}

.search-time {
  color: #c0c4cc;
}

.loading-container {
  text-align: center;
  padding: 40px 20px;
}

.loading-text {
  margin-top: 16px;
  color: #909399;
  font-size: 14px;
}

.error-container {
  margin: 20px 0;
}

.no-results {
  text-align: center;
  padding: 60px 20px;
}

.no-results-text p {
  margin: 8px 0;
  color: #606266;
}

.results-container {
  margin-top: 20px;
  flex: 1;
  overflow-y: auto;
}

.results-stats {
  margin-bottom: 20px;
  text-align: left;
}

.stats-text {
  font-size: 15px;
  color: #909399;
  font-weight: 400;
  letter-spacing: 0.2px;
}

.max-score {
  font-weight: 600;
  color: #409eff;
}

.results-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.result-item {
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  padding: 20px;
  background: #ffffff;
  cursor: pointer;
  transition: all 0.3s ease;
}

.result-item:hover {
  border-color: #409eff;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.1);
  transform: translateY(-2px);
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.result-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 22px;
  color: #303133;
}

.document-title {
  color: rgb(0, 0, 128);
  font-weight: 600;
}

.result-id {
  font-size: 12px;
  color: #c0c4cc;
  font-family: 'Monaco', 'Menlo', monospace;
}


.result-content {
  margin: 16px 0;
}

.highlight-snippet {
  margin: 8px 0;
  padding: 12px;
  background: #f8f9fa;
  border-left: 3px solid rgba(0, 0, 128, 0.3);
  border-radius: 4px;
  line-height: 1.6;
  color: #606266;
}

.highlight-snippet :deep(em) {
  background: #fff3cd;
  color: #856404;
  font-style: normal;
  font-weight: 600;
  padding: 2px 4px;
  border-radius: 3px;
}

/* Markdown 렌더링 스타일 */
.highlight-snippet :deep(h1),
.highlight-snippet :deep(h2),
.highlight-snippet :deep(h3),
.highlight-snippet :deep(h4),
.highlight-snippet :deep(h5),
.highlight-snippet :deep(h6) {
  margin: 8px 0 4px 0;
  color: #303133;
  font-weight: 600;
}

.highlight-snippet :deep(h1) { font-size: 18px; }
.highlight-snippet :deep(h2) { font-size: 16px; }
.highlight-snippet :deep(h3) { font-size: 14px; }
.highlight-snippet :deep(h4) { font-size: 13px; }

.highlight-snippet :deep(p) {
  margin: 4px 0;
  line-height: 1.6;
}

.highlight-snippet :deep(strong) {
  font-weight: 600;
  color: #303133;
}

.highlight-snippet :deep(code) {
  background: #f1f2f6;
  padding: 2px 4px;
  border-radius: 3px;
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 12px;
  color: #e96900;
}

.highlight-snippet :deep(ul),
.highlight-snippet :deep(ol) {
  margin: 4px 0;
  padding-left: 20px;
}

.highlight-snippet :deep(li) {
  margin: 2px 0;
}

.highlight-snippet :deep(blockquote) {
  margin: 8px 0;
  padding: 8px 12px;
  background: #f8f9fa;
  border-left: 3px solid rgba(0, 0, 128, 0.3);
  font-style: italic;
}

.highlight-snippet :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
}

.highlight-snippet :deep(th),
.highlight-snippet :deep(td) {
  border: 1px solid #e4e7ed;
  padding: 6px 8px;
  text-align: left;
}

.highlight-snippet :deep(th) {
  background: #f5f7fa;
  font-weight: 600;
  color: #303133;
}


.result-footer {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #f0f2f5;
}

.result-path {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
  font-family: 'Monaco', 'Menlo', monospace;
}

/* 반응형 디자인 */
@media (max-width: 768px) {
  .search-results {
    padding: 16px;
  }
  
  
  .search-meta {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  
  .results-stats {
    text-align: left;
  }
  
  .result-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  
  
  .document-title {
    font-size: 15px;
  }
}
</style>
