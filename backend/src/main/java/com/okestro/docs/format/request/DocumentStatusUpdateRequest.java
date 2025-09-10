package com.okestro.docs.format.request;

import com.okestro.docs.persistence.DocumentStatus;

/**
 * 문서 상태 업데이트 요청 DTO
 */
public class DocumentStatusUpdateRequest {
    
    private String documentId;   // 상태를 변경할 문서 ID
    private DocumentStatus status; // 새로운 상태
    
    public DocumentStatusUpdateRequest() {
    }
    
    public DocumentStatusUpdateRequest(String documentId, DocumentStatus status) {
        this.documentId = documentId;
        this.status = status;
    }
    
    public String getDocumentId() {
        return documentId;
    }
    
    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }
    
    public DocumentStatus getStatus() {
        return status;
    }
    
    public void setStatus(DocumentStatus status) {
        this.status = status;
    }
    
    @Override
    public String toString() {
        return "DocumentStatusUpdateRequest{" +
                "documentId='" + documentId + '\'' +
                ", status=" + status +
                '}';
    }
}
