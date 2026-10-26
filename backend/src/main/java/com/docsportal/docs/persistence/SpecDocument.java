package com.docsportal.docs.persistence;

import java.util.EnumSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 스펙 문서 한 건. kind 만 다르고 같은 전이 엔진을 쓴다 (ARCH-0004).
 * 상태 변경은 항상 SpecService 가 전이 표를 통과시킨 뒤에만 호출한다.
 * prdId 는 기술문서에만, techAreas 는 PRD 에만 값이 있다.
 */
@Entity
@Table(name = "spec_document", indexes = @Index(name = "idx_spec_document_prd_id", columnList = "prd_id"))
public class SpecDocument extends BaseEntity {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpecKind kind;

    @Column(name = "prd_id")
    private String prdId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text", nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpecStatus status;

    @Convert(converter = TechAreasConverter.class)
    @Column(name = "tech_areas")
    private Set<TechArea> techAreas = EnumSet.noneOf(TechArea.class);

    @Column(nullable = false)
    private String author;

    protected SpecDocument() {
    }

    private SpecDocument(String id, SpecKind kind, String prdId, String title, String body, SpecStatus status, String author) {
        this.id = id;
        this.kind = kind;
        this.prdId = prdId;
        this.title = title;
        this.body = body;
        this.status = status;
        this.author = author;
    }

    public static SpecDocument prd(String id, String title, String body, SpecStatus status, String author) {
        return new SpecDocument(id, SpecKind.PRD, null, title, body, status, author);
    }

    public static SpecDocument techDoc(String id, TechArea area, String prdId, String title, String body,
                                       SpecStatus status, String author) {
        return new SpecDocument(id, area.kind(), prdId, title, body, status, author);
    }

    public String getId() {
        return id;
    }

    public SpecKind getKind() {
        return kind;
    }

    public String getPrdId() {
        return prdId;
    }

    /** 이 문서가 속한 PRD 의 id. PRD 자신이면 자기 id 다. */
    public String getRootPrdId() {
        return prdId == null ? id : prdId;
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

    public Set<TechArea> getTechAreas() {
        Set<TechArea> copy = EnumSet.noneOf(TechArea.class);
        // Hibernate 는 null 컬럼(1단계 행)에 컨버터를 부르지 않을 수 있다
        if (techAreas != null) {
            copy.addAll(techAreas);
        }
        return copy;
    }

    public String getAuthor() {
        return author;
    }

    public void edit(String title, String body) {
        this.title = title;
        this.body = body;
    }

    public void declareTechAreas(Set<TechArea> areas) {
        Set<TechArea> copy = EnumSet.noneOf(TechArea.class);
        copy.addAll(areas);
        this.techAreas = copy;
    }

    public void moveTo(SpecStatus status) {
        this.status = status;
    }
}
