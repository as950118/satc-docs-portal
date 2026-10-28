import axios from 'axios'

import { apiBaseUrl } from '@/config'

// API 클라이언트 설정
const api = axios.create({
  baseURL: `${apiBaseUrl}/api/v1`,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 요청 인터셉터
api.interceptors.request.use(
  (config) => {
    // 요청 전 처리 (예: 토큰 추가)
    console.log('API 요청:', config.method?.toUpperCase(), config.url)
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 응답 인터셉터
api.interceptors.response.use(
  (response) => {
    console.log('API 응답:', response.status, response.config.url)
    return response
  },
  (error) => {
    console.error('API 에러:', error.response?.status, error.config?.url, error.message)
    return Promise.reject(error)
  }
)

// API 함수들
export const documentApi = {
  // 문서 트리 조회
  getDocumentTree: () => api.get('/docs/tree'),
  
  // 폴더 자식 요소 조회
  getFolderChildren: (folderId) => api.get(`/docs/tree/${folderId}`),

  // 단일 파일 업로드
  uploadFile: (file, parentId = null) => {
    const formData = new FormData()
    formData.append('file', file)
    if (parentId) {
      formData.append('parentId', parentId)
    }
    
    return api.post('/docs', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },
  
  // 새 폴더 생성
  createFolder: (title, parentId = null) => {
    const params = new URLSearchParams()
    params.append('title', title)
    if (parentId) {
      params.append('parentId', parentId)
    }
    
    return api.post('/docs/folder', params, {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded'
      }
    })
  },
  
  // 다중 파일 업로드
  uploadMultipleFiles: (files, parentId = null) => {
    const formData = new FormData()
    files.forEach(file => {
      formData.append('files', file)
    })
    if (parentId) {
      formData.append('parentId', parentId)
    }
    
    return api.post('/docs/multiple', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },
  
  // 문서 삭제
  deleteDocument: (docId) => api.delete(`/docs/${docId}`),
  
  // 문서/폴더 이동
  moveDocuments: (documentIds, targetParentId = null) => {
    return api.post('/docs/move', {
      documentIds,
      targetParentId
    })
  },
  
  // 모든 폴더 목록 조회
  getAllFolders: () => api.get('/docs/folders'),
  
  // 문서 통계 조회
  getDocumentStatistics: () => api.get('/docs/statistics'),
  
  // 문서 검색
  searchDocuments: (query) => {
    if (!query || query.trim() === '') {
      return Promise.reject(new Error('검색 쿼리가 필요합니다.'))
    }
    return api.get('/docs/search', {
      params: { q: query.trim() }
    })
  }
}

export default api
