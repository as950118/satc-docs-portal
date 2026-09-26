package com.docsportal.docs.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

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

    // === 코드 → 문서 자동 동기화 메타데이터 (ARCH-0001). 수동 업로드 문서는 모두 null ===
    @Column(name = "source_key", unique = true)
    private String sourceKey;
    @Column(name = "source_repo")
    private String sourceRepo;
    @Column(name = "source_path")
    private String sourcePath;
    @Column(name = "source_commit", length = 40)
    private String sourceCommit;
    @Column(name = "content_hash", length = 64)
    private String contentHash;
    @Column(name = "synced_at")
    private Instant syncedAt;

    // === 문서-코드 드리프트 검사 결과 (ARCH-0003) ===
    @Enumerated(EnumType.STRING)
    @Column(name = "drift_status")
    private DriftStatus driftStatus;
    @Column(name = "drift_checked_at")
    private Instant driftCheckedAt;
    @Column(name = "drift_source_key")
    private String driftSourceKey;
    @Column(name = "drift_commit", length = 40)
    private String driftCommit;
    @Column(name = "drift_detail", columnDefinition = "text")
    private String driftDetail;

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
     * 동기화 소스 연결 (최초 생성 시)
     */
    public void attachSource(String sourceKey, String sourceRepo, String sourcePath) {
        this.sourceKey = sourceKey;
        this.sourceRepo = sourceRepo;
        this.sourcePath = sourcePath;
    }

    /**
     * 동기화 결과 반영 (커밋/해시/시각)
     */
    public void markSynced(String sourceCommit, String contentHash, Instant syncedAt) {
        this.sourceCommit = sourceCommit;
        this.contentHash = contentHash;
        this.syncedAt = syncedAt;
    }

    /**
     * 삭제된 동기화 문서의 sourceKey 를 해제해 같은 소스로 새 문서를 만들 수 있게 한다 (unique 제약)
     */
    public void releaseSourceKey() {
        this.sourceKey = null;
    }

    /**
     * 드리프트 검사 결과 반영
     */
    public void recordDrift(DriftStatus status, Instant checkedAt, String sourceKey, String commit, String detailJson) {
        this.driftStatus = status;
        this.driftCheckedAt = checkedAt;
        this.driftSourceKey = sourceKey;
        this.driftCommit = commit;
        this.driftDetail = detailJson;
    }

    public DriftStatus getDriftStatus() {
        return driftStatus;
    }

    public Instant getDriftCheckedAt() {
        return driftCheckedAt;
    }

    public String getDriftSourceKey() {
        return driftSourceKey;
    }

    public String getDriftCommit() {
        return driftCommit;
    }

    public String getDriftDetail() {
        return driftDetail;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public String getSourceRepo() {
        return sourceRepo;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getSourceCommit() {
        return sourceCommit;
    }

    public String getContentHash() {
        return contentHash;
    }

    public Instant getSyncedAt() {
        return syncedAt;
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
