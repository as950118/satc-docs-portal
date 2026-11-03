import { nextTick, onBeforeUnmount, watch } from 'vue'

// 마크다운 입력창(textarea)과 미리보기를 스크롤 비율로 서로 동기화한다.
// panesRef: 두 영역을 감싸는 요소의 ref, trigger: 입력창이 나타나거나 사라질 때 바뀌는 반응형 값.
export function useSyncScroll(panesRef, trigger) {
  let editor = null
  let preview = null
  let source = null // 지금 사용자가 스크롤 중인 쪽 (되먹임 루프 방지)
  let timer = null

  const ratio = (el) => {
    const max = el.scrollHeight - el.clientHeight
    return max > 0 ? el.scrollTop / max : 0
  }
  const apply = (el, r) => {
    el.scrollTop = r * Math.max(el.scrollHeight - el.clientHeight, 0)
  }
  const makeHandler = (from, to) => () => {
    if (source && source !== from) return // 반대쪽이 만든 스크롤 이벤트는 무시
    source = from
    apply(to, ratio(from))
    clearTimeout(timer)
    timer = setTimeout(() => { source = null }, 80)
  }

  let onEditor = null
  let onPreview = null
  const detach = () => {
    editor?.removeEventListener('scroll', onEditor)
    preview?.removeEventListener('scroll', onPreview)
    editor = preview = null
  }
  const attach = async () => {
    detach()
    await nextTick()
    const root = panesRef.value
    editor = root?.querySelector('.el-textarea__inner') || null
    preview = root?.querySelector('.preview') || null
    if (!editor || !preview) return
    onEditor = makeHandler(editor, preview)
    onPreview = makeHandler(preview, editor)
    editor.addEventListener('scroll', onEditor, { passive: true })
    preview.addEventListener('scroll', onPreview, { passive: true })
  }

  watch([panesRef, trigger], attach, { immediate: true, flush: 'post' })
  onBeforeUnmount(() => { clearTimeout(timer); detach() })
}
