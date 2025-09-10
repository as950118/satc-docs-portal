package com.okestro.docs.format.request;

/**
 * 문서 위치 변경 요청 DTO
 * 같은 폴더 내에서 문서 순서를 변경하면서 다른 문서들의 인덱스를 자동 조정
 */
public class DocumentPositionRequest {
    
    private String documentId; // 순서를 변경할 문서 ID
    private String targetParentId; // 문서가 속한 부모 폴더 ID (null이면 루트, 반드시 문서의 현재 부모와 같아야 함)
    private Integer targetPosition; // 변경할 위치 (0부터 시작)
    
    public DocumentPositionRequest() {
    }
    
    public DocumentPositionRequest(String documentId, String targetParentId, Integer targetPosition) {
        this.documentId = documentId;
        this.targetParentId = targetParentId;
        this.targetPosition = targetPosition;
    }
    
    public String getDocumentId() {
        return documentId;
    }
    
    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }
    
    public String getTargetParentId() {
        return targetParentId;
    }
    
    public void setTargetParentId(String targetParentId) {
        this.targetParentId = targetParentId;
    }
    
    public Integer getTargetPosition() {
        return targetPosition;
    }
    
    public void setTargetPosition(Integer targetPosition) {
        this.targetPosition = targetPosition;
    }
}
