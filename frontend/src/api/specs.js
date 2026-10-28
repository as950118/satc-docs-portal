import axios from 'axios'
import { authUtils } from './auth'

const specApi = axios.create({
  baseURL: `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'}/api/v1/specs`,
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' },
})

specApi.interceptors.request.use((config) => {
  const token = authUtils.getSessionId()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 백엔드 오류 메시지에 섞인 상태·행동 enum 이름을 한글 라벨로 바꾼다.
const localize = (message) =>
  message.replace(/\b[A-Z][A-Z_]+\b/g, (token) => ACTION_LABEL[token] || SPEC_STATUS[token]?.label || token)

export class SpecApiError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

// 백엔드는 실제 HTTP 상태 코드와 RestResponse 봉투를 함께 돌려준다.
const call = async (request) => {
  try {
    const { data } = await request
    return data.data
  } catch (error) {
    const status = error.response?.status ?? 0
    const message = error.response?.data?.errorMessage || error.message || '요청에 실패했습니다.'
    if (status === 401) {
      authUtils.logout()
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    throw new SpecApiError(status, localize(message))
  }
}

export const specApiMethods = {
  getPrdTemplate: () => call(specApi.get('/templates/prd')),
  list: ({ kind = 'PRD', status, prdId, allVersions } = {}) =>
    call(specApi.get('', {
      params: { kind: kind || undefined, status: status || undefined, prdId: prdId || undefined, allVersions: allVersions || undefined },
    })),
  get: (id) => call(specApi.get(`/${id}`)),
  create: (title, body) => call(specApi.post('', { title, body })),
  update: (id, title, body) => call(specApi.put(`/${id}`, { title, body })),
  approve: (id, comment) => call(specApi.post(`/${id}/approve`, comment ? { comment } : {})),
  submit: (id) => call(specApi.post(`/${id}/submit`, {})),
  requestChanges: (id, comment) => call(specApi.post(`/${id}/request-changes`, { comment })),
  setTechAreas: (prdId, areas) => call(specApi.put(`/${prdId}/tech-areas`, { areas })),
  createTechDoc: (prdId, area, title, body) => call(specApi.post(`/${prdId}/tech-docs`, { area, title, body })),
  listTechDocs: (prdId) => call(specApi.get(`/${prdId}/tech-docs`)),
  getTechTemplate: (area) => call(specApi.get(`/templates/tech/${area}`)),
  startImplementation: (id, comment) => call(specApi.post(`/${id}/start-implementation`, comment ? { comment } : {})),
  linkPr: (id, url) => call(specApi.post(`/${id}/pr-links`, { url })),
  unlinkPr: (id, url) => call(specApi.delete(`/${id}/pr-links`, { params: { url } })),
  submitImplementation: (id, comment) => call(specApi.post(`/${id}/submit-implementation`, comment ? { comment } : {})),
  approveImplementation: (id, comment) => call(specApi.post(`/${id}/approve-implementation`, comment ? { comment } : {})),
  requestImplementationChanges: (id, comment) =>
    call(specApi.post(`/${id}/request-implementation-changes`, { comment })),
  newVersion: (prdId) => call(specApi.post(`/${prdId}/new-version`, {})),
  acknowledgePrdChange: (id, comment) =>
    call(specApi.post(`/${id}/acknowledge-prd-change`, comment ? { comment } : {})),
}

export const SPEC_STATUS = {
  DRAFT: { label: '초안', tag: 'info' },
  PRD_APPROVED: { label: 'PRD 승인', tag: 'warning' },
  SPEC_APPROVED: { label: '명세 승인', tag: 'success' },
  IN_REVIEW: { label: '검토 중', tag: 'primary' },
  CHANGES_REQUESTED: { label: '변경 요청', tag: 'danger' },
  APPROVED: { label: '승인', tag: 'success' },
  IN_IMPLEMENTATION: { label: '구현 중', tag: 'primary' },
  IMPL_IN_REVIEW: { label: '구현 검토 중', tag: 'warning' },
  IMPLEMENTED: { label: '구현 완료', tag: 'success' },
  SUPERSEDED: { label: '이전 버전', tag: 'info' },
}

export const ACTION_LABEL = {
  CREATE: '생성',
  EDIT: '수정',
  SUBMIT: '검토 요청',
  APPROVE: '승인',
  REQUEST_CHANGES: '변경 요청',
  SET_TECH_AREAS: '필요 영역 선언',
  AGGREGATE: '자동 집계',
  START_IMPLEMENTATION: '구현 시작',
  LINK_PR: 'PR 연결',
  UNLINK_PR: 'PR 연결 해제',
  SUBMIT_IMPLEMENTATION: '구현 검토 요청',
  APPROVE_IMPLEMENTATION: '구현 승인',
  REQUEST_IMPL_CHANGES: '구현 변경 요청',
  NEW_VERSION: '새 버전 작성',
  SUPERSEDE: '새 버전으로 대체',
  PRD_REVISED: 'PRD 새 버전 반영',
  ACKNOWLEDGE_PRD_CHANGE: 'PRD 변경 영향 확인',
}

export const actionLabel = (action) => ACTION_LABEL[action] || action

export const statusLabel = (status) => SPEC_STATUS[status]?.label || status

export const SPEC_KIND = {
  PRD: { label: 'PRD' },
  TECH_BE: { label: 'BE 기술문서', area: 'BE', role: 'BACKEND' },
  TECH_FE: { label: 'FE 기술문서', area: 'FE', role: 'FRONTEND' },
  TECH_DATA: { label: 'DATA 기술문서', area: 'DATA', role: 'DATA' },
}

export const TECH_AREAS = Object.entries(SPEC_KIND)
  .filter(([, meta]) => meta.area)
  .map(([kind, meta]) => ({ area: meta.area, kind, role: meta.role, label: meta.label }))

export const AREA_META = Object.fromEntries(TECH_AREAS.map((a) => [a.area, a]))

export const canPlan = () => authUtils.hasRole('PLANNER')

export const canReview = (kind) => {
  const role = SPEC_KIND[kind]?.role
  return !!role && authUtils.hasRole(role)
}

// 작성과 제출은 PLANNER 또는 영역 역할, 검토(승인/변경 요청)는 영역 역할만 가능하다.
export const canAuthorTech = (kind) => canPlan() || canReview(kind)

// 구현 승인과 변경 요청은 영역과 무관하게 REVIEWER 역할만 가능하다.
export const canReviewImplementation = () => authUtils.hasRole('REVIEWER')

// 구현 시작, PR 연결과 해제, 구현 검토 요청은 해당 영역 역할만 가능하다.
export const canImplement = (kind) => canReview(kind)
