import axios from 'axios'
import { authUtils } from './auth'

const specApi = axios.create({
  baseURL: 'http://localhost:8080/api/v1/specs', // Local (docker-compose)
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
    throw new SpecApiError(status, message)
  }
}

export const specApiMethods = {
  getPrdTemplate: () => call(specApi.get('/templates/prd')),
  list: ({ kind = 'PRD', status, prdId } = {}) =>
    call(specApi.get('', { params: { kind: kind || undefined, status: status || undefined, prdId: prdId || undefined } })),
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
}

export const SPEC_STATUS = {
  DRAFT: { label: '초안', tag: 'info' },
  PRD_APPROVED: { label: 'PRD 승인', tag: 'warning' },
  SPEC_APPROVED: { label: '명세 승인', tag: 'success' },
  IN_REVIEW: { label: '검토 중', tag: 'primary' },
  CHANGES_REQUESTED: { label: '변경 요청', tag: 'danger' },
  APPROVED: { label: '승인', tag: 'success' },
}

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
