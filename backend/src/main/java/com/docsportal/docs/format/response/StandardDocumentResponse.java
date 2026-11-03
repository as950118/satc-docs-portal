package com.docsportal.docs.format.response;

import java.time.Instant;
import java.util.List;

import com.docsportal.docs.persistence.DocumentStatus;

/**
 * 표준문서 등록 결과와 이력 조회 응답.
 */
public final class StandardDocumentResponse {

    private StandardDocumentResponse() {
    }

    public record Registered(String documentId, String result, DocumentStatus status,
                             Long createdByUserId, String createdBy, Long updatedByUserId, String updatedBy,
                             Instant updatedAt) {
    }

    public record Event(String action, Long userId, String actor, Instant at, String contentHash, String sourceCommit) {
    }

    public record History(String documentId, String title, String sourceKey, Long createdByUserId, String createdBy, Instant createdAt,
                          Long updatedByUserId, String updatedBy, Instant updatedAt, List<Event> events) {
    }
}
