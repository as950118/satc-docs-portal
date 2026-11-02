import axios from 'axios'

import { apiBaseUrl } from '@/config'
import { authUtils } from './auth'

// 포털 동의 화면 전용. 승인은 사용자의 포털 토큰(Authorization 헤더)으로 하므로 쿠키 기반 CSRF 대상이 아니다.
const oauthApi = axios.create({
  baseURL: `${apiBaseUrl}/oauth/authorize/requests`,
  timeout: 10000,
})

export class OAuthRequestError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

const call = async (request) => {
  try {
    return (await request()).data
  } catch (error) {
    const status = error.response?.status ?? 0
    throw new OAuthRequestError(status, error.response?.data?.error_description || '서버에 연결할 수 없습니다.')
  }
}

const enc = encodeURIComponent

export const oauthApiMethods = {
  describe: (requestId) => call(() => oauthApi.get(`/${enc(requestId)}`)),
  approve: (requestId) =>
    call(() => oauthApi.post(`/${enc(requestId)}/approve`, null, {
      headers: { Authorization: `Bearer ${authUtils.getSessionId()}` },
    })).then((data) => data.redirectUri),
  deny: (requestId) => call(() => oauthApi.post(`/${enc(requestId)}/deny`)).then((data) => data.redirectUri),
}
