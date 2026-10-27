package com.docsportal.docs.persistence;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 스펙 문서 한 건. kind 만 다르고 같은 전이 엔진을 쓴다 (ARCH-0004).
 * 상태 변경은 항상 SpecService 가 전이 표를 통과시킨 뒤에만 호출한다.
 * prdId 는 기술문서에만, techAreas 는 PRD 에만, prLinks 는 구현 단계 기술문서에만 값이 있다.
 * PRD 는 버전마다 행이 따로 있고 supersedesId 로 직전 버전을 가리킨다. revision 과 supersedesId 는 PRD 에만,
 * prdRevision 은 기술문서에만 값이 있다.
 */
@Entity
@Table(name = "spec_document", indexes = @Index(name = "idx_spec_document_prd_id", columnList = "prd_id"),
    uniqueConstraints = @UniqueConstraint(name = "uk_spec_document_supersedes_id", columnNames = "supersedes_id"))
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

    @Column(name = "revision")
    private Integer revision;

    @Column(name = "supersedes_id")
    private String supersedesId;

    // 이 기술문서를 마지막으로 승인하거나 영향 확인한 PRD revision. 부모보다 작으면 "PRD 변경" 표시다
    @Column(name = "prd_revision")
    private Integer prdRevision;

    // 문서가 소유한 컬렉션이라 링크를 더하면 문서 version 이 오른다. 오래된 문서 상태로 링크를 붙이는 동시 요청이 그래서 409 가 된다
    @ElementCollection
    @CollectionTable(name = "spec_pr_link", joinColumns = @JoinColumn(name = "spec_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_spec_pr_link_spec_url", columnNames = {"spec_id", "url"}))
    @OrderBy("linkedAt asc")
    private Set<SpecPrLink> prLinks = new LinkedHashSet<>();

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
        SpecDocument prd = new SpecDocument(id, SpecKind.PRD, null, title, body, status, author);
        prd.revision = 1;
        return prd;
    }

    /** prior 의 현재 본문으로 시작하는 다음 버전. prior 는 이 버전이 승인될 때까지 승인본으로 남는다. */
    public static SpecDocument nextVersionOf(SpecDocument prior, String id, SpecStatus status, String author) {
        SpecDocument next = new SpecDocument(id, SpecKind.PRD, null, prior.title, prior.body, status, author);
        next.revision = prior.revision + 1;
        next.supersedesId = prior.id;
        return next;
    }

    public static SpecDocument techDoc(String id, TechArea area, SpecDocument prd, String title, String body,
                                       SpecStatus status, String author) {
        SpecDocument doc = new SpecDocument(id, area.kind(), prd.getId(), title, body, status, author);
        doc.prdRevision = prd.getRevision();
        return doc;
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

    public Integer getRevision() {
        return revision;
    }

    public String getSupersedesId() {
        return supersedesId;
    }

    public Integer getPrdRevision() {
        return prdRevision;
    }

    /** 기술문서가 부모 PRD 의 현재 버전을 아직 승인하거나 확인하지 않았다. */
    public boolean isBehind(SpecDocument prd) {
        return prdRevision != null && prdRevision < prd.getRevision();
    }

    /** 기술문서를 부모 PRD 의 현재 버전 기준으로 확인했다고 기록한다. PRD 자신에게는 의미가 없어 무시한다. */
    public void confirmAgainst(SpecDocument prd) {
        if (prdId != null) {
            prdRevision = prd.getRevision();
        }
    }

    public void moveUnder(SpecDocument prd) {
        this.prdId = prd.getId();
    }

    public List<SpecPrLink> getPrLinks() {
        return List.copyOf(prLinks);
    }

    public boolean hasPrLink(SpecPrLink link) {
        return prLinks.contains(link);
    }

    public void linkPr(SpecPrLink link) {
        prLinks.add(link);
    }

    public void unlinkPr(SpecPrLink link) {
        prLinks.remove(link);
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
