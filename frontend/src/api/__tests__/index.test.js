import { AxiosError } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const memoryStorage = () => {
  const items = new Map()
  return {
    getItem: (key) => (items.has(key) ? items.get(key) : null),
    setItem: (key, value) => items.set(key, String(value)),
    removeItem: (key) => items.delete(key),
  }
}

vi.stubGlobal('localStorage', memoryStorage())
vi.stubGlobal('sessionStorage', memoryStorage())
vi.stubGlobal('window', { location: { pathname: '/', href: '/' }, dispatchEvent: () => true })

const { default: api, documentApi } = await import('../index.js')

let sent
const respondWith = (status) => {
  api.defaults.adapter = async (config) => {
    sent = config
    const response = { data: { status }, status, statusText: String(status), headers: {}, config }
    if (!config.validateStatus(status)) {
      throw new AxiosError(String(status), AxiosError.ERR_BAD_REQUEST, config, null, response)
    }
    return response
  }
}

describe('documentApi', () => {
  beforeEach(() => {
    sent = null
    window.location.pathname = '/'
    window.location.href = '/'
    sessionStorage.setItem('sessionId', 'session-token')
  })

  it('로그인 세션을 Authorization 헤더로 보낸다', async () => {
    respondWith(200)
    await documentApi.deleteDocument('d1')
    expect(sent.headers.Authorization).toBe('Bearer session-token')
  })

  it('세션이 없으면 Authorization 헤더를 싣지 않는다', async () => {
    sessionStorage.removeItem('sessionId')
    respondWith(200)
    await documentApi.getDocumentTree()
    expect(sent.headers.Authorization).toBeUndefined()
  })

  it('401 이면 세션을 지우고 로그인 화면으로 보낸다', async () => {
    respondWith(401)
    await expect(documentApi.getDocumentTree()).rejects.toMatchObject({ response: { status: 401 } })
    expect(sessionStorage.getItem('sessionId')).toBeNull()
    expect(window.location.href).toBe('/login')
  })

  it('403 은 세션을 유지한다', async () => {
    respondWith(403)
    await expect(documentApi.deleteDocument('d1')).rejects.toMatchObject({ response: { status: 403 } })
    expect(sessionStorage.getItem('sessionId')).toBe('session-token')
    expect(window.location.href).toBe('/')
  })
})
