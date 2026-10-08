package com.docsportal.docs.format.response;

import com.docsportal.docs.persistence.DocumentStatus;

/**
 * 동기화 결과 응답 DTO
 */
public class DocumentSyncResponse {

    public enum Result {
        CREATED, UPDATED, UNCHANGED
    }

    private final String documentId;
    private final Result result;
    private final DocumentStatus status;

    public DocumentSyncResponse(String documentId, Result result, DocumentStatus status) {
        this.documentId = documentId;
        this.result = result;
        this.status = status;
    }

    public String getDocumentId() {
        return documentId;
    }

    /**
     * CLI 계약에 맞춰 소문자(created/updated/unchanged)로 직렬화
     */
    public String getResult() {
        return result.name().toLowerCase();
    }

    public DocumentStatus getStatus() {
        return status;
    }
}
