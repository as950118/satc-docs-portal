package com.okestro.docs.persistence;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @CreatedDate
    @Column(name = "created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Version
    @Column(name = "version")
    private Integer version;

    public BaseEntity() {
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Boolean getDeleted() {
        return isDeleted;
    }
    
    /**
     * 불변성을 위한 새로운 인스턴스 생성 메서드
     * 삭제 상태를 변경한 새로운 엔티티를 반환합니다.
     */
    protected void markAsDeleted() {
        // JPA의 경우 dirty checking을 위해 필요한 최소한의 변경
        // 실제로는 새로운 인스턴스를 생성하는 것이 이상적이지만,
        // JPA 특성상 managed entity의 상태 변경이 필요
        this.isDeleted = true;
    }

    public Integer getVersion() {
        return version;
    }
}
