package com.docsportal.docs.format.response;

import java.util.List;

/**
 * 검색 결과 개별 항목 DTO
 * document_id, title, breadcrumb, object_name, highlight content 포함
 */
public class SearchResultItem {
    
    private String documentId;
    private String title;
    private List<BreadcrumbItem> breadcrumb;
    private String objectName;
    private List<String> highlightContent;
    
    public SearchResultItem() {
    }
    
    public SearchResultItem(String documentId, String title, List<BreadcrumbItem> breadcrumb, String objectName, List<String> highlightContent) {
        this.documentId = documentId;
        this.title = title;
        this.breadcrumb = breadcrumb;
        this.objectName = objectName;
        this.highlightContent = highlightContent;
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
    
    public List<BreadcrumbItem> getBreadcrumb() {
        return breadcrumb;
    }
    
    public void setBreadcrumb(List<BreadcrumbItem> breadcrumb) {
        this.breadcrumb = breadcrumb;
    }
    
    public String getObjectName() {
        return objectName;
    }
    
    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }
    
    public List<String> getHighlightContent() {
        return highlightContent;
    }
    
    public void setHighlightContent(List<String> highlightContent) {
        this.highlightContent = highlightContent;
    }
}
