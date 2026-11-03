package com.docsportal.docs.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 사용자가 등록한 표준문서의 등록·수정 이력. append-only 이므로 setter 와 수정 경로를 두지 않는다 (SpecEvent 와 같은 방식).
 */
@Entity
@Table(name = "document_event", indexes = @Index(name = "idx_document_event_document_id", columnList = "document_id"))
public class DocumentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentEventAction action;

    @Column(nullable = false)
    private String actor;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "source_commit", length = 40)
    private String sourceCommit;

    @Column(nullable = false)
    private Instant at;

    protected DocumentEvent() {
    }

    public DocumentEvent(String documentId, DocumentEventAction action, Long userId, String actor,
                         String contentHash, String sourceCommit, Instant at) {
        this.documentId = documentId;
        this.action = action;
        this.userId = userId;
        this.actor = actor;
        this.contentHash = contentHash;
        this.sourceCommit = sourceCommit;
        this.at = at;
    }

    public Long getId() { return id; }
    public String getDocumentId() { return documentId; }
    public DocumentEventAction getAction() { return action; }
    public String getActor() { return actor; }
    public Long getUserId() { return userId; }
    public String getContentHash() { return contentHash; }
    public String getSourceCommit() { return sourceCommit; }
    public Instant getAt() { return at; }
}
