import axios from 'axios'

import { apiBaseUrl } from '@/config'

// 인증 API 클라이언트 설정
const authApi = axios.create({
  baseURL: `${apiBaseUrl}/api/v1/auth`,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 요청 인터셉터 - 세션 ID 자동 추가
authApi.interceptors.request.use(
  (config) => {
    // 저장된 세션 ID 가져오기
    const sessionId = localStorage.getItem('sessionId') || sessionStorage.getItem('sessionId')
    
    if (sessionId) {
      config.headers.Authorization = `Bearer ${sessionId}`
    }
    
    console.log('인증 API 요청:', config.method?.toUpperCase(), config.url)
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 응답 인터셉터 - 인증 실패 처리
authApi.interceptors.response.use(
  (response) => {
    console.log('인증 API 응답:', response.status, response.config.url)
    return response
  },
  (error) => {
    console.error('인증 API 에러:', error.response?.status, error.config?.url, error.message)
    
    // 인증 실패 시 로그아웃 처리
    if (error.response?.status === 401) {
      // 세션 정보 제거
      localStorage.removeItem('sessionId')
      localStorage.removeItem('userInfo')
      sessionStorage.removeItem('sessionId')
      sessionStorage.removeItem('userInfo')
      
      // 로그인 페이지로 리다이렉트 (현재 페이지가 로그인 페이지가 아닌 경우에만)
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    
    return Promise.reject(error)
  }
)

// 인증 관련 API 함수들
export const authApiMethods = {
  // 로그인
  login: (credentials) => {
    return authApi.post('/login', {
      username: credentials.username,
      password: credentials.password,
      rememberMe: credentials.rememberMe || false
    })
  },

  // 로그아웃
  logout: () => {
    return authApi.post('/logout')
  },


  // 현재 사용자 정보 조회
  getCurrentUser: () => {
    return authApi.get('/me')
  },


  // 세션 유효성 검증
  validateSession: () => {
    return authApi.get('/validate')
  }
}

// 인증 상태 변경 이벤트
const authStateChangeEvent = new CustomEvent('authStateChange')

// 인증 상태 관리 유틸리티
export const authUtils = {
  // 로그인 상태 확인
  isAuthenticated: () => {
    const sessionId = localStorage.getItem('sessionId') || sessionStorage.getItem('sessionId')
    return !!sessionId
  },

  // 저장된 사용자 정보 가져오기
  getUserInfo: () => {
    const userInfoStr = localStorage.getItem('userInfo') || sessionStorage.getItem('userInfo')
    return userInfoStr ? JSON.parse(userInfoStr) : null
  },

  // 세션 ID 가져오기
  getSessionId: () => {
    return localStorage.getItem('sessionId') || sessionStorage.getItem('sessionId')
  },

  // 로그아웃 처리
  logout: () => {
    localStorage.removeItem('sessionId')
    localStorage.removeItem('userInfo')
    sessionStorage.removeItem('sessionId')
    sessionStorage.removeItem('userInfo')
    // 인증 상태 변경 이벤트 발생
    window.dispatchEvent(authStateChangeEvent)
  },

  // 로그인 처리 (세션 ID 저장)
  setAuth: (sessionId, userInfo, rememberMe = false) => {
    if (rememberMe) {
      localStorage.setItem('sessionId', sessionId)
      localStorage.setItem('userInfo', JSON.stringify(userInfo))
    } else {
      sessionStorage.setItem('sessionId', sessionId)
      sessionStorage.setItem('userInfo', JSON.stringify(userInfo))
    }
    // 인증 상태 변경 이벤트 발생
    window.dispatchEvent(authStateChangeEvent)
  },

  // 사용자 권한 확인
  hasRole: (role) => {
    const userInfo = authUtils.getUserInfo()
    return userInfo?.roles?.includes(role) || false
  },

  // 사용자 권한 목록 확인
  hasAnyRole: (roles) => {
    const userInfo = authUtils.getUserInfo()
    if (!userInfo?.roles) return false
    return roles.some(role => userInfo.roles.includes(role))
  },

  // 관리자 권한 확인
  isAdmin: () => {
    return authUtils.hasRole('ADMIN')
  },

  // 일반 사용자 권한 확인 (ADMIN이 아닌 경우)
  isUser: () => {
    const userInfo = authUtils.getUserInfo()
    if (!userInfo?.roles) return false
    return userInfo.roles.includes('USER') && !userInfo.roles.includes('ADMIN')
  }
}

// 로그인 뒤 돌아갈 경로. 같은 앱 안의 경로만 받아 열린 리다이렉트를 막는다
export const safeRedirectPath = (value) =>
  typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') && !value.startsWith('/\\') ? value : '/'

// 기본 export를 authApiMethods로 변경
export { authApiMethods as authApi }
export default authApi
