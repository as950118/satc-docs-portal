package com.docsportal.docs.format.response;

import com.docsportal.docs.persistence.DocumentEntity;
import com.docsportal.docs.persistence.DocumentStatus;
import java.time.Instant;

public class DocumentResponse {
    
    private String id;
    private String parentId;
    private String title;
    private String bucket;
    private String filePath;
    private String extension;
    private Boolean isFolder;
    private DocumentStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    
    public DocumentResponse() {}
    
    public DocumentResponse(DocumentEntity entity) {
        this.id = entity.getId();
        this.parentId = entity.getParentId();
        this.title = entity.getTitle();
        this.bucket = entity.getBucket();
        this.filePath = entity.getFilePath();
        this.extension = entity.getExtension();
        // folder 여부 설정 (DB의 isFolder 필드 또는 확장자 없음으로 판단)
        this.isFolder = (entity.getIsFolder() != null && entity.getIsFolder()) || 
                       (entity.getExtension() == null || entity.getExtension().isEmpty());
        this.status = entity.getStatus();
        this.createdAt = entity.getCreatedAt();
        this.updatedAt = entity.getUpdatedAt();
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getParentId() {
        return parentId;
    }
    
    public void setParentId(String parentId) {
        this.parentId = parentId;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getBucket() {
        return bucket;
    }
    
    public void setBucket(String bucket) {
        this.bucket = bucket;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public String getExtension() {
        return extension;
    }
    
    public void setExtension(String extension) {
        this.extension = extension;
    }
    
    public Boolean getIsFolder() {
        return isFolder;
    }
    
    public void setIsFolder(Boolean isFolder) {
        this.isFolder = isFolder;
    }
    
    public DocumentStatus getStatus() {
        return status;
    }
    
    public void setStatus(DocumentStatus status) {
        this.status = status;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
