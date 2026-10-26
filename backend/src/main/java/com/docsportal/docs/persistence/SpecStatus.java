package com.docsportal.docs.persistence;

/**
 * 스펙 문서 상태. PRD 는 DRAFT → PRD_APPROVED → SPEC_APPROVED, 기술문서는 DRAFT → IN_REVIEW → APPROVED 또는
 * CHANGES_REQUESTED 를 쓴다 (ARCH-0004). 허용 전이는 SpecTransitions 표에만 있다.
 */
public enum SpecStatus {
    DRAFT,
    PRD_APPROVED,
    SPEC_APPROVED,
    IN_REVIEW,
    APPROVED,
    CHANGES_REQUESTED
}
