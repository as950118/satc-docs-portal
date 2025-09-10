package com.okestro.docs.format.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 외부 검색 API 응답의 개별 결과 항목 DTO
 */
public class ExternalSearchResultItem {
    
    @JsonProperty("document_id")
    private String documentId;
    
    private String title;
    private Double score;
    private ExternalSearchMetadata metadata;
    private ExternalSearchHighlight highlight;
    
    public ExternalSearchResultItem() {
    }
    
    public String getDocumentId() {
        return documentId;
    }
    
    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public Double getScore() {
        return score;
    }
    
    public void setScore(Double score) {
        this.score = score;
    }
    
    public ExternalSearchMetadata getMetadata() {
        return metadata;
    }
    
    public void setMetadata(ExternalSearchMetadata metadata) {
        this.metadata = metadata;
    }
    
    public ExternalSearchHighlight getHighlight() {
        return highlight;
    }
    
    public void setHighlight(ExternalSearchHighlight highlight) {
        this.highlight = highlight;
    }
}
