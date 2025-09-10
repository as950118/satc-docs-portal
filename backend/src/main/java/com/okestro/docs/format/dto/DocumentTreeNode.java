package com.okestro.docs.format.dto;

import com.okestro.docs.persistence.DocumentEntity;
import com.okestro.docs.persistence.DocumentStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class DocumentTreeNode {
    
    private String id;
    private String parentId;
    private String title;
    private String extension;
    private String filePath;
    private boolean isFolder;
    private DocumentStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private List<DocumentTreeNode> children;
    private Integer childrenCount; // 별도로 설정된 자식 수
    
    public DocumentTreeNode() {
        this.children = new ArrayList<>();
    }
    
    public DocumentTreeNode(DocumentEntity entity) {
        this.id = entity.getId();
        this.parentId = entity.getParentId();
        this.title = entity.getTitle();
        this.extension = entity.getExtension();
        this.filePath = entity.getFilePath();
        this.isFolder = (entity.getExtension() == null || entity.getExtension().isEmpty());
        this.status = entity.getStatus();
        this.createdAt = entity.getCreatedAt();
        this.updatedAt = entity.getUpdatedAt();
        this.children = new ArrayList<>();
    }
    
    // Getters and Setters
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
    
    public String getExtension() {
        return extension;
    }
    
    public void setExtension(String extension) {
        this.extension = extension;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public boolean getIsFolder() {
        return isFolder;
    }
    
    public void setIsFolder(boolean folder) {
        this.isFolder = folder;
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
    
    public List<DocumentTreeNode> getChildren() {
        return children;
    }
    
    public void setChildren(List<DocumentTreeNode> children) {
        this.children = children;
    }
    
    public void addChild(DocumentTreeNode child) {
        this.children.add(child);
    }
    
    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }
    
    public int getChildrenCount() {
        // 별도로 설정된 childrenCount가 있다면 그것을 사용, 없다면 실제 children 배열 크기
        return childrenCount != null ? childrenCount : (children != null ? children.size() : 0);
    }
    
    public void setChildrenCount(int childrenCount) {
        this.childrenCount = childrenCount;
    }
}
