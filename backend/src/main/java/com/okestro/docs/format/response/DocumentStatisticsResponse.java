package com.okestro.docs.format.response;

/**
 * 문서 통계 응답 DTO
 */
public class DocumentStatisticsResponse {
    private Long totalFolders;        // 전체 폴더 수
    private Long totalDocuments;      // 전체 문서 수
    private Long uploadingDocuments;  // 업로드 중인 문서 수
    private Long indexingDocuments;   // 인덱싱 중인 문서 수
    private Long completedDocuments;  // 처리 완료된 문서 수

    public DocumentStatisticsResponse() {
    }

    public DocumentStatisticsResponse(Long totalFolders, Long totalDocuments, 
                                    Long uploadingDocuments, Long indexingDocuments, Long completedDocuments) {
        this.totalFolders = totalFolders;
        this.totalDocuments = totalDocuments;
        this.uploadingDocuments = uploadingDocuments;
        this.indexingDocuments = indexingDocuments;
        this.completedDocuments = completedDocuments;
    }

    public Long getTotalFolders() {
        return totalFolders;
    }

    public void setTotalFolders(Long totalFolders) {
        this.totalFolders = totalFolders;
    }

    public Long getTotalDocuments() {
        return totalDocuments;
    }

    public void setTotalDocuments(Long totalDocuments) {
        this.totalDocuments = totalDocuments;
    }

    public Long getUploadingDocuments() {
        return uploadingDocuments;
    }

    public void setUploadingDocuments(Long uploadingDocuments) {
        this.uploadingDocuments = uploadingDocuments;
    }

    public Long getIndexingDocuments() {
        return indexingDocuments;
    }

    public void setIndexingDocuments(Long indexingDocuments) {
        this.indexingDocuments = indexingDocuments;
    }

    public Long getCompletedDocuments() {
        return completedDocuments;
    }

    public void setCompletedDocuments(Long completedDocuments) {
        this.completedDocuments = completedDocuments;
    }
}

