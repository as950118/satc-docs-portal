package com.okestro.docs.format.response;

/**
 * Breadcrumb 경로의 개별 항목 DTO
 * 문서의 계층 구조를 나타내기 위한 클래스
 */
public class BreadcrumbItem {
    
    private String id;
    private String title;
    private String type; // "root" 또는 "folder"
    
    public BreadcrumbItem() {
    }
    
    public BreadcrumbItem(String id, String title, String type) {
        this.id = id;
        this.title = title;
        this.type = type;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    @Override
    public String toString() {
        return "BreadcrumbItem{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
