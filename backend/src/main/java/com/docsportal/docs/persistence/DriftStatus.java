package com.docsportal.docs.persistence;

/**
 * 문서-코드 드리프트 검사 결과 (ARCH-0003)
 */
public enum DriftStatus {
    /**
     * 문서에 언급된 API가 모두 코드에 존재 (자동 동기화 문서 포함)
     */
    IN_SYNC,

    /**
     * 문서에 언급된 API 중 코드에 없는 것이 있음
     */
    DRIFTED,

    /**
     * 문서 본문에서 API 언급을 찾지 못해 비교 불가
     */
    NO_API_REFERENCES
}
