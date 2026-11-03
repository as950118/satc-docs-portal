import { describe, expect, it, vi } from 'vitest'

import { getClientId, withClientId } from '../../api/clientId'
import {
  BATCH_LIMIT,
  backoffDelay,
  createLiveClient,
  createToastBatcher,
  toastMessage,
} from '../liveEventsCore'

class FakeES {
  static instances = []
  constructor(url) {
    this.url = url
    this.readyState = 0
    this.listeners = {}
    this.closed = false
    FakeES.instances.push(this)
  }
  addEventListener(type, fn) {
    this.listeners[type] = fn
  }
  close() {
    this.closed = true
    this.readyState = 2
  }
  emit(type, data) {
    this.listeners[type]?.({ data: typeof data === 'string' ? data : JSON.stringify(data) })
  }
}

// 수동 타이머
const makeTimers = () => {
  let now = 0
  let seq = 0
  const tasks = new Map()
  return {
    setTimer: (fn, ms) => {
      tasks.set(++seq, { fn, at: now + ms })
      return seq
    },
    clearTimer: (id) => tasks.delete(id),
    advance: async (ms) => {
      now += ms
      for (const [id, t] of [...tasks]) {
        if (t.at <= now) {
          tasks.delete(id)
          t.fn()
        }
      }
      await Promise.resolve()
    },
    now: () => now,
  }
}

const setup = (over = {}) => {
  FakeES.instances = []
  const timers = makeTimers()
  const calls = { events: [], hello: 0, status: [] }
  const client = createLiveClient({
    url: 'http://x/api/v1/events',
    clientId: 'me',
    EventSourceCtor: FakeES,
    onEvent: (evt, own) => calls.events.push([evt, own]),
    onHello: () => calls.hello++,
    onStatus: (d) => calls.status.push(d),
    probe: async () => 200,
    setTimer: timers.setTimer,
    clearTimer: timers.clearTimer,
    ...over,
  })
  return { client, timers, calls }
}

const flushMicro = async () => {
  for (let i = 0; i < 5; i++) await Promise.resolve()
}

describe('backoffDelay', () => {
  it('1, 2, 4초 ... 최대 30초', () => {
    expect([0, 1, 2, 3, 4, 5, 6, 10].map(backoffDelay)).toEqual([1000, 2000, 4000, 8000, 16000, 30000, 30000, 30000])
  })
})

describe('createLiveClient', () => {
  it('이벤트를 분기하고 본인 originId 를 판별한다', () => {
    const { client, calls } = setup()
    client.start()
    const es = FakeES.instances[0]
    es.emit('document.updated', { type: 'document.updated', id: 'd1', originId: 'me' })
    es.emit('spec.changed', { type: 'spec.changed', id: 's1', originId: 'other' })
    expect(calls.events.map(([e, own]) => [e.type, own])).toEqual([
      ['document.updated', true],
      ['spec.changed', false],
    ])
  })

  it('잘못된 JSON 은 연결을 유지한 채 무시한다', () => {
    const { client, calls } = setup()
    const err = vi.spyOn(console, 'error').mockImplementation(() => {})
    client.start()
    FakeES.instances[0].emit('document.created', 'not-json')
    expect(calls.events).toEqual([])
    expect(FakeES.instances[0].closed).toBe(false)
    err.mockRestore()
  })

  it('끊기면 백오프로 재연결하고 hello 에서 재조회를 호출한다', async () => {
    const { client, timers, calls } = setup()
    client.start()
    FakeES.instances[0].readyState = 2
    FakeES.instances[0].onerror()
    await flushMicro()
    expect(FakeES.instances).toHaveLength(1)
    await timers.advance(999)
    expect(FakeES.instances).toHaveLength(1)
    await timers.advance(1)
    expect(FakeES.instances).toHaveLength(2)

    // 두 번째 실패는 2초
    FakeES.instances[1].readyState = 2
    FakeES.instances[1].onerror()
    await flushMicro()
    await timers.advance(1999)
    expect(FakeES.instances).toHaveLength(2)
    await timers.advance(1)
    expect(FakeES.instances).toHaveLength(3)

    FakeES.instances[2].emit('hello', {})
    expect(calls.hello).toBe(1)
  })

  it('5초 이상 끊기면 표시하고 hello 로 지운다', async () => {
    const { client, timers, calls } = setup()
    client.start()
    FakeES.instances[0].onerror() // readyState 0: 브라우저 자체 재시도
    await timers.advance(4999)
    expect(calls.status).toEqual([])
    await timers.advance(1)
    expect(calls.status).toEqual([true])
    FakeES.instances[0].emit('hello', {})
    expect(calls.status).toEqual([true, false])
  })

  it('5초 안에 복구되면 표시하지 않는다', async () => {
    const { client, timers, calls } = setup()
    client.start()
    FakeES.instances[0].onerror()
    await timers.advance(2000)
    FakeES.instances[0].emit('hello', {})
    await timers.advance(10000)
    expect(calls.status).toEqual([])
  })

  it('503 이면 30초 뒤에 재시도한다', async () => {
    const { client, timers } = setup({ probe: async () => 503 })
    client.start()
    FakeES.instances[0].readyState = 2
    FakeES.instances[0].onerror()
    await flushMicro()
    await timers.advance(29999)
    expect(FakeES.instances).toHaveLength(1)
    await timers.advance(1)
    expect(FakeES.instances).toHaveLength(2)
  })

  it('stop 하면 연결을 닫고 재시도하지 않는다', async () => {
    const { client, timers } = setup()
    client.start()
    FakeES.instances[0].readyState = 2
    FakeES.instances[0].onerror()
    await flushMicro()
    client.stop()
    await timers.advance(60000)
    expect(FakeES.instances).toHaveLength(1)
  })

  it('EventSource 가 없으면 아무 일도 하지 않는다', () => {
    const { client } = setup({ EventSourceCtor: undefined })
    expect(() => client.start()).not.toThrow()
  })
})

describe('createToastBatcher', () => {
  const make = () => {
    const timers = makeTimers()
    const shown = []
    const summaries = []
    const b = createToastBatcher({
      show: (evt, msg) => shown.push(msg),
      showSummary: (n) => summaries.push(n),
      now: timers.now,
      setTimer: timers.setTimer,
    })
    return { b, timers, shown, summaries }
  }
  const evt = (i, type = 'document.updated') => ({ type, id: `d${i}`, title: `T${i}` })

  it('같은 id+type 은 2초 안에 한 번만', async () => {
    const { b, timers, shown } = make()
    b.push(evt(1))
    b.push(evt(1))
    await timers.advance(300)
    expect(shown).toHaveLength(1)
    await timers.advance(1700)
    b.push(evt(1))
    await timers.advance(300)
    expect(shown).toHaveLength(2)
  })

  it('type 이 다르면 각각 보인다', async () => {
    const { b, timers, shown } = make()
    b.push(evt(1, 'document.updated'))
    b.push(evt(1, 'document.deleted'))
    await timers.advance(300)
    expect(shown).toHaveLength(2)
  })

  it('5건까지는 개별, 5건 초과는 묶음', async () => {
    const a = make()
    for (let i = 0; i < BATCH_LIMIT; i++) a.b.push(evt(i))
    await a.timers.advance(300)
    expect(a.shown).toHaveLength(5)
    expect(a.summaries).toEqual([])

    const c = make()
    for (let i = 0; i < BATCH_LIMIT + 1; i++) c.b.push(evt(i))
    await c.timers.advance(300)
    expect(c.shown).toHaveLength(0)
    expect(c.summaries).toEqual([6])
  })

  it('알리지 않는 타입(document.status)은 건너뛴다', async () => {
    const { b, timers, shown } = make()
    b.push(evt(1, 'document.status'))
    await timers.advance(300)
    expect(shown).toEqual([])
  })
})

describe('toastMessage', () => {
  it('문서는 제목 중심, 스펙은 actor 포함', () => {
    expect(toastMessage({ type: 'document.created', title: 'a.md' })).toBe('a.md 문서가 업로드되었습니다')
    expect(toastMessage({ type: 'document.deleted', title: 'a.md' })).toBe('a.md 문서가 삭제되었습니다')
    expect(toastMessage({ type: 'spec.changed', title: 'PRD', actor: 'kim' })).toBe('kim이(가) PRD을(를) 변경했습니다')
  })
})

describe('X-Client-Id', () => {
  const memStore = () => {
    const m = new Map()
    return { getItem: (k) => m.get(k) ?? null, setItem: (k, v) => m.set(k, v) }
  }
  it('같은 저장소에서는 같은 ID 를 유지한다', () => {
    const s = memStore()
    const id = getClientId(s)
    expect(id).toMatch(/^[0-9a-f-]{36}$/)
    expect(getClientId(s)).toBe(id)
  })
  it('요청 설정에 헤더를 붙인다 (plain object / AxiosHeaders 형태)', () => {
    const cfg = withClientId({ headers: {} })
    expect(cfg.headers['X-Client-Id']).toBeTruthy()
    const set = vi.fn()
    withClientId({ headers: { set } })
    expect(set).toHaveBeenCalledWith('X-Client-Id', expect.any(String))
  })
})
