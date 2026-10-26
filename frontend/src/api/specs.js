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
  list: ({ kind = 'PRD', status } = {}) => call(specApi.get('', { params: { kind, status: status || undefined } })),
  get: (id) => call(specApi.get(`/${id}`)),
  create: (title, body) => call(specApi.post('', { title, body })),
  update: (id, title, body) => call(specApi.put(`/${id}`, { title, body })),
  approve: (id, comment) => call(specApi.post(`/${id}/approve`, comment ? { comment } : {})),
}

export const SPEC_STATUS = {
  DRAFT: { label: '초안', tag: 'info' },
  PRD_APPROVED: { label: 'PRD 승인', tag: 'success' },
}

export const canPlan = () => authUtils.hasRole('PLANNER')
