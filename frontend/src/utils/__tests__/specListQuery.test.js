import { describe, expect, it } from 'vitest'

import { buildListQuery, clampPage, parseListQuery } from '../specListQuery'

const opts = { kinds: { PRD: {}, TECH_BE: {} }, statuses: { DRAFT: {}, APPROVED: {} } }

describe('parseListQuery', () => {
  it('restores kind, status, q and page from the URL', () => {
    expect(parseListQuery({ kind: 'TECH_BE', status: 'APPROVED', q: 'abc', page: '3' }, opts))
      .toEqual({ kind: 'TECH_BE', status: 'APPROVED', q: 'abc', page: 3 })
  })

  it('falls back to defaults for missing or invalid values', () => {
    expect(parseListQuery({ kind: 'NOPE', status: 'NOPE', page: '-2' }, opts))
      .toEqual({ kind: 'PRD', status: '', q: '', page: 1 })
    expect(parseListQuery({ page: 'x' }, opts).page).toBe(1)
  })
})

describe('buildListQuery', () => {
  it('omits defaults', () => {
    expect(buildListQuery({ kind: 'PRD', status: '', q: '  ', page: 1 })).toEqual({})
  })

  it('includes non-default values and trims q', () => {
    expect(buildListQuery({ kind: 'TECH_BE', status: 'DRAFT', q: ' x ', page: 2 }))
      .toEqual({ kind: 'TECH_BE', status: 'DRAFT', q: 'x', page: '2' })
  })
})

describe('clampPage', () => {
  it('keeps a page inside the range', () => {
    expect(clampPage(2, 41, 20)).toBe(2)
    expect(clampPage(3, 41, 20)).toBe(3)
  })

  it('pulls a page beyond the last page back to the last page', () => {
    expect(clampPage(9, 41, 20)).toBe(3)
    expect(clampPage(2, 20, 20)).toBe(1)
  })

  it('returns 1 when there are no results', () => {
    expect(clampPage(5, 0, 20)).toBe(1)
  })
})
