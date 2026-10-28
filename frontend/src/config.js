// 컨테이너는 기동 시 public/config.js 를 환경변수로 다시 써서 이미지 재빌드 없이 주소를 바꾼다
const runtime = window.__APP_CONFIG__ || {}
const read = (key) => runtime[key] || import.meta.env[key] || ''

export const apiBaseUrl = read('VITE_API_BASE_URL') || 'http://localhost:8080'

export const toolLinks = [
  { key: 'jenkins', label: 'Jenkins', name: 'Jenkins', url: read('VITE_JENKINS_URL') },
  { key: 'argocd', label: 'ArgoCD', name: 'ArgoCD', url: read('VITE_ARGOCD_URL') },
  { key: 'common-code', label: '공통코드', name: '공통 코드 관리 시스템', url: read('VITE_COMMON_CODE_URL') },
].filter((link) => link.url)
