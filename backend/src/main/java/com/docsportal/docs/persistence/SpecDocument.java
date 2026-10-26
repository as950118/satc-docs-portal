package com.docsportal.docs.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 스펙 문서 한 건. kind 만 다르고 같은 전이 엔진을 쓴다 (ARCH-0004).
 * 상태 변경은 항상 SpecService 가 전이 표를 통과시킨 뒤에만 호출한다.
 */
@Entity
@Table(name = "spec_document")
public class SpecDocument extends BaseEntity {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpecKind kind;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text", nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpecStatus status;

    @Column(nullable = false)
    private String author;

    protected SpecDocument() {
    }

    public SpecDocument(String id, SpecKind kind, String title, String body, SpecStatus status, String author) {
        this.id = id;
        this.kind = kind;
        this.title = title;
        this.body = body;
        this.status = status;
        this.author = author;
    }

    public String getId() {
        return id;
    }

    public SpecKind getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public SpecStatus getStatus() {
        return status;
    }

    public String getAuthor() {
        return author;
    }

    public void edit(String title, String body) {
        this.title = title;
        this.body = body;
    }

    public void moveTo(SpecStatus status) {
        this.status = status;
    }
}
