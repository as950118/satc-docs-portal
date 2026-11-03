import { SPECS_CHANGED, specApiMethods, specPath } from '@/api/specs'
import { getClientId } from '@/api/clientId'
import { apiBaseUrl } from '@/config'
import { ElNotification } from 'element-plus'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import {
  DOCUMENTS_CHANGED,
  LIVE_EVENT,
  createLiveClient,
  createToastBatcher,
  isDocumentEvent,
} from './liveEventsCore'

// 헤더가 구독하는 연결 끊김 표시 (5초 이상 끊겼을 때만 true)
export const liveDisconnected = ref(false)

const DEBOUNCE_MS = 500

// Home 에서 한 번 호출한다. openDocument(evt) 는 문서 토스트 클릭 시 해당 문서를 여는 콜백.
export function useLiveEvents({ router, openDocument } = {}) {
  let client = null
  let debounceTimer = null

  const signalDocuments = () => window.dispatchEvent(new Event(DOCUMENTS_CHANGED))
  const debouncedSignal = () => {
    clearTimeout(debounceTimer)
    debounceTimer = setTimeout(signalDocuments, DEBOUNCE_MS)
  }

  const openFromToast = (evt) => {
    if (evt.type === 'spec.changed') {
      // 이벤트에는 kind 가 없으므로 스펙을 조회해 PRD/기술문서 경로를 정한다.
      specApiMethods.get(evt.id).then((spec) => router?.push(specPath(spec))).catch((e) => console.error(e))
    } else if (evt.type !== 'document.deleted') {
      openDocument?.(evt)
    }
  }

  const batcher = createToastBatcher({
    show: (evt, message) =>
      ElNotification({ message, duration: 4000, onClick: () => openFromToast(evt) }),
    showSummary: (count) => ElNotification({ message: `${count}건의 변경이 있었습니다`, duration: 4000 }),
  })

  const onEvent = (evt, isOwn) => {
    window.dispatchEvent(new CustomEvent(LIVE_EVENT, { detail: evt }))
    if (isDocumentEvent(evt.type)) debouncedSignal()
    else if (evt.type === 'spec.changed') window.dispatchEvent(new Event(SPECS_CHANGED))
    if (!isOwn) batcher.push(evt)
  }

  onMounted(() => {
    client = createLiveClient({
      url: `${apiBaseUrl}/api/v1/events`,
      clientId: getClientId(),
      onEvent,
      onHello: () => {
        signalDocuments()
        window.dispatchEvent(new Event(SPECS_CHANGED))
      },
      onStatus: (disconnected) => {
        liveDisconnected.value = disconnected
      },
    })
    client.start()
  })

  onBeforeUnmount(() => {
    clearTimeout(debounceTimer)
    client?.stop()
  })
}
