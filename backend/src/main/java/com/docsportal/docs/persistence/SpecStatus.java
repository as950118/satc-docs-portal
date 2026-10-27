package com.docsportal.docs.persistence;

/**
 * 스펙 문서 상태 (ARCH-0004). PRD 는 DRAFT → PRD_APPROVED → SPEC_APPROVED → IMPLEMENTED,
 * 기술문서는 DRAFT → IN_REVIEW → APPROVED(또는 CHANGES_REQUESTED) → IN_IMPLEMENTATION → IMPL_IN_REVIEW → IMPLEMENTED 를 쓴다.
 * 허용 전이는 SpecTransitions 표에만 있다.
 */
public enum SpecStatus {
    DRAFT,
    PRD_APPROVED,
    SPEC_APPROVED,
    IN_REVIEW,
    APPROVED,
    CHANGES_REQUESTED,
    IN_IMPLEMENTATION,
    IMPL_IN_REVIEW,
    IMPLEMENTED
}
