// 문서 목록의 검색·필터·페이지 상태와 URL 쿼리를 오가는 순수 함수들. 기본값은 URL 에 싣지 않는다.

export const parseListQuery = (query, { kinds, statuses }) => {
  const kind = typeof query.kind === 'string' && kinds[query.kind] ? query.kind : 'PRD'
  const status = typeof query.status === 'string' && statuses[query.status] ? query.status : ''
  const q = typeof query.q === 'string' ? query.q : ''
  const page = Math.max(parseInt(query.page, 10) || 1, 1)
  return { kind, status, q, page }
}

export const buildListQuery = ({ kind, status, q, page }) => {
  const query = {}
  if (kind !== 'PRD') query.kind = kind
  if (status) query.status = status
  if (q.trim()) query.q = q.trim()
  if (page > 1) query.page = String(page)
  return query
}

// 주소의 page 가 마지막 페이지를 넘으면(삭제·필터 변경 뒤 등) 마지막 페이지로 당긴다. 결과가 없으면 1.
export const clampPage = (page, total, size) => Math.min(page, Math.max(Math.ceil(total / size), 1))
