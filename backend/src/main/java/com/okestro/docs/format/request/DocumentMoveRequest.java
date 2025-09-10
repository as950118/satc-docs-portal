package com.okestro.docs.format.request;

import java.util.List;

/**
 * 문서/폴더 이동 요청 DTO
 */
public class DocumentMoveRequest {
    private List<String> documentIds; // 이동할 문서/폴더 ID 목록
    private String targetParentId;     // 이동할 대상 부모 폴더 ID (null이면 루트로 이동)

    public DocumentMoveRequest() {
    }

    public DocumentMoveRequest(List<String> documentIds, String targetParentId) {
        this.documentIds = documentIds;
        this.targetParentId = targetParentId;
    }

    public List<String> getDocumentIds() {
        return documentIds;
    }

    public void setDocumentIds(List<String> documentIds) {
        this.documentIds = documentIds;
    }

    public String getTargetParentId() {
        return targetParentId;
    }

    public void setTargetParentId(String targetParentId) {
        this.targetParentId = targetParentId;
    }
}

