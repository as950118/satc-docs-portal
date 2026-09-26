package com.docsportal.docs.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "document")
public class DocumentEntity extends BaseEntity {

    @Id
    private String id;
    private String parentId;
    private String title;
    private String bucket;
    private String filePath;
    private String extension;
    private Boolean isFolder;
    
    @Enumerated(EnumType.STRING)
    private DocumentStatus status;

    public DocumentEntity() {
    }

    public DocumentEntity(String id, String parentId, String title, String bucket, String filePath, String extension, Boolean isFolder, DocumentStatus status) {
        this.id = id;
        this.parentId = parentId;
        this.title = title;
        this.bucket = bucket;
        this.filePath = filePath;
        this.extension = extension;
        this.isFolder = isFolder;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getParentId() {
        return parentId;
    }

    public String getTitle() {
        return title;
    }

    public String getBucket() {
        return bucket;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getExtension() {
        return extension;
    }
    
    public Boolean getIsFolder() {
        return isFolder;
    }
    
    public DocumentStatus getStatus() {
        return status;
    }
    
    /**
     * 불변성을 위한 파일 경로 업데이트 메서드
     * JPA managed entity의 특성상 필드 변경을 통한 dirty checking 사용
     */
    public void updateFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    /**
     * 불변성을 위한 부모 ID 업데이트 메서드
     * JPA managed entity의 특성상 필드 변경을 통한 dirty checking 사용
     */
    public void updateParentId(String parentId) {
        this.parentId = parentId;
    }
    
    /**
     * 불변성을 위한 삭제 상태 업데이트 메서드
     */
    public void delete() {
        this.markAsDeleted();
    }
    
    /**
     * 문서 상태 업데이트 메서드
     */
    public void updateStatus(DocumentStatus status) {
        this.status = status;
    }
    
    /**
     * Builder 패턴을 위한 정적 메서드
     */
    public static DocumentEntityBuilder builder() {
        return new DocumentEntityBuilder();
    }
    
    /**
     * Builder 클래스
     */
    public static class DocumentEntityBuilder {
        private String id;
        private String parentId;
        private String title;
        private String bucket;
        private String filePath;
        private String extension;
        private Boolean isFolder;
        private DocumentStatus status;
        
        public DocumentEntityBuilder id(String id) {
            this.id = id;
            return this;
        }
        
        public DocumentEntityBuilder parentId(String parentId) {
            this.parentId = parentId;
            return this;
        }
        
        public DocumentEntityBuilder title(String title) {
            this.title = title;
            return this;
        }
        
        public DocumentEntityBuilder bucket(String bucket) {
            this.bucket = bucket;
            return this;
        }
        
        public DocumentEntityBuilder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }
        
        public DocumentEntityBuilder extension(String extension) {
            this.extension = extension;
            return this;
        }
        
        public DocumentEntityBuilder isFolder(Boolean isFolder) {
            this.isFolder = isFolder;
            return this;
        }
        
        public DocumentEntityBuilder status(DocumentStatus status) {
            this.status = status;
            return this;
        }
        
        public DocumentEntity build() {
            return new DocumentEntity(id, parentId, title, bucket, filePath, extension, isFolder, status);
        }
    }
}
