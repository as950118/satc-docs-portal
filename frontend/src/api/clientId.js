// 탭마다 하나의 랜덤 ID. 새로고침해도 유지되도록 sessionStorage 에 둔다.
// 변경 요청에 X-Client-Id 로 실어 보내고, SSE 이벤트의 originId 와 비교해 내가 일으킨 변경인지 판별한다.
const KEY = 'clientId'

const randomId = () =>
  globalThis.crypto?.randomUUID?.() ||
  'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })

let memoryId = null

export const getClientId = (storage) => {
  try {
    const store = storage || globalThis.sessionStorage
    let id = store.getItem(KEY)
    if (!id) {
      id = randomId()
      store.setItem(KEY, id)
    }
    return id
  } catch {
    // sessionStorage 를 못 쓰는 환경에서는 메모리에만 유지한다.
    if (!memoryId) memoryId = randomId()
    return memoryId
  }
}

export const withClientId = (config) => {
  config.headers = config.headers || {}
  if (typeof config.headers.set === 'function') {
    config.headers.set('X-Client-Id', getClientId())
  } else {
    config.headers['X-Client-Id'] = getClientId()
  }
  return config
}
