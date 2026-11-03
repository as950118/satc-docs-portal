// 실시간 이벤트(SSE) 순수 로직. Vue/Element Plus/axios 에 의존하지 않아 단독으로 테스트한다.

export const LIVE_EVENT = 'live-event' // window CustomEvent: detail = 서버 이벤트 data
export const DOCUMENTS_CHANGED = 'documents-changed' // 문서 트리·통계를 다시 읽으라는 신호

export const EVENT_TYPES = [
  'document.created',
  'document.updated',
  'document.status',
  'document.deleted',
  'document.moved',
  'spec.changed',
]

export const isDocumentEvent = (type) => type.startsWith('document.')

export const MAX_DELAY = 30000
export const SERVER_BUSY_DELAY = 30000
export const DISCONNECT_NOTICE_MS = 5000
export const DEDUPE_MS = 2000
export const BATCH_WINDOW_MS = 300
export const BATCH_LIMIT = 5

// 1, 2, 4초 ... 최대 30초
export const backoffDelay = (attempt) => Math.min(1000 * 2 ** attempt, MAX_DELAY)

const DOC_VERB = {
  'document.created': '업로드',
  'document.updated': '수정',
  'document.deleted': '삭제',
  'document.moved': '이동',
}

// 토스트 문구. 알리지 않을 이벤트(document.status 등)는 null.
export const toastMessage = (evt) => {
  if (evt.type === 'spec.changed') {
    return `${evt.actor || '누군가'}이(가) ${evt.title || '스펙'}을(를) 변경했습니다`
  }
  const verb = DOC_VERB[evt.type]
  return verb ? `${evt.title || '문서'} 문서가 ${verb}되었습니다` : null
}

// 같은 id+type 은 2초에 한 번, 한 번에 5건을 넘으면 한 줄로 묶는다.
// 짧은 창(300ms) 동안 모았다가 한꺼번에 처리한다.
export function createToastBatcher({ show, showSummary, now = Date.now, setTimer = setTimeout }) {
  const lastShown = new Map()
  let pending = []
  let timer = null

  const flush = () => {
    timer = null
    const items = pending
    pending = []
    if (items.length === 0) return
    if (items.length > BATCH_LIMIT) {
      showSummary(items.length, items)
    } else {
      items.forEach((evt) => show(evt, toastMessage(evt)))
    }
  }

  return {
    push(evt) {
      if (!toastMessage(evt)) return
      const key = `${evt.type}:${evt.id}`
      const t = now()
      if (lastShown.has(key) && t - lastShown.get(key) < DEDUPE_MS) return
      lastShown.set(key, t)
      // 오래된 키 정리
      if (lastShown.size > 200) {
        for (const [k, ts] of lastShown) if (t - ts >= DEDUPE_MS) lastShown.delete(k)
      }
      pending.push(evt)
      if (!timer) timer = setTimer(flush, BATCH_WINDOW_MS)
    },
    flush,
  }
}

const defaultProbe = async (url) => {
  const controller = new AbortController()
  try {
    const res = await fetch(url, { signal: controller.signal, headers: { Accept: 'text/event-stream' } })
    return res.status
  } finally {
    controller.abort()
  }
}

// EventSource 연결 수명 관리: 이벤트 분기, 지수 백오프 재연결, 끊김 표시, 503 지연.
export function createLiveClient({
  url,
  clientId,
  onEvent, // (evt, isOwn)
  onHello, // ()
  onStatus = () => {}, // (disconnected: boolean)
  EventSourceCtor = globalThis.EventSource,
  probe = defaultProbe,
  setTimer = setTimeout,
  clearTimer = clearTimeout,
}) {
  let es = null
  let stopped = false
  let attempt = 0
  let retryTimer = null
  let noticeTimer = null
  let disconnected = false

  const setDisconnected = (value) => {
    if (disconnected === value) return
    disconnected = value
    onStatus(value)
  }

  const startNoticeTimer = () => {
    if (noticeTimer || disconnected) return
    noticeTimer = setTimer(() => {
      noticeTimer = null
      setDisconnected(true)
    }, DISCONNECT_NOTICE_MS)
  }

  const clearNoticeTimer = () => {
    if (noticeTimer) clearTimer(noticeTimer)
    noticeTimer = null
  }

  const handle = (message) => {
    try {
      const evt = JSON.parse(message.data)
      onEvent(evt, !!clientId && evt.originId === clientId)
    } catch (e) {
      console.error('실시간 이벤트 처리 실패:', e)
    }
  }

  const scheduleRetry = (delay) => {
    if (stopped || retryTimer) return
    retryTimer = setTimer(() => {
      retryTimer = null
      connect()
    }, delay)
  }

  const onError = async () => {
    if (stopped) return
    startNoticeTimer()
    // 브라우저가 스스로 재시도 중(CONNECTING)이면 그대로 둔다. 닫혔으면 우리가 백오프로 다시 연다.
    if (es && es.readyState === 2) {
      es.close()
      es = null
      let delay = backoffDelay(attempt++)
      try {
        if ((await probe(url)) === 503) delay = SERVER_BUSY_DELAY
      } catch {
        // 서버가 아예 응답하지 않으면 일반 백오프
      }
      scheduleRetry(delay)
    }
  }

  function connect() {
    if (stopped || !EventSourceCtor) return
    try {
      es = new EventSourceCtor(url)
    } catch (e) {
      console.error('EventSource 생성 실패:', e)
      scheduleRetry(backoffDelay(attempt++))
      return
    }
    es.addEventListener('hello', () => {
      attempt = 0
      clearNoticeTimer()
      setDisconnected(false)
      try {
        onHello()
      } catch (e) {
        console.error('hello 처리 실패:', e)
      }
    })
    EVENT_TYPES.forEach((type) => es.addEventListener(type, handle))
    es.onerror = onError
  }

  return {
    start() {
      stopped = false
      connect()
    },
    stop() {
      stopped = true
      if (es) es.close()
      es = null
      if (retryTimer) clearTimer(retryTimer)
      retryTimer = null
      clearNoticeTimer()
      setDisconnected(false)
    },
  }
}
