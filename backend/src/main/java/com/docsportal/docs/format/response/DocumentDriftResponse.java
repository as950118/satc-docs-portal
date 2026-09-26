package com.docsportal.docs.format.response;

import java.time.Instant;
import java.util.Map;

import com.docsportal.docs.persistence.DriftStatus;

/**
 * 문서 드리프트 상태 + 자동 동기화 메타데이터 (ARCH-0003)
 *
 * @param driftStatus 검사 이력이 없으면 null
 * @param detail      {missingInCode, missingInDoc, referenced, unresolvedPaths, truncated}
 * @param synced      자동 동기화 문서 여부
 */
public record DocumentDriftResponse(
        String documentId,
        DriftStatus driftStatus,
        Instant checkedAt,
        String driftSourceKey,
        String driftCommit,
        Map<String, Object> detail,
        boolean synced,
        String sourceKey,
        String sourceCommit,
        Instant syncedAt) {
}
