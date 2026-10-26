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
 * 전이 이력. append-only 이므로 setter 와 수정 경로를 두지 않는다.
 * 생성 이벤트는 fromStatus 가 null 이다.
 */
@Entity
@Table(name = "spec_event", indexes = @Index(name = "idx_spec_event_spec_id", columnList = "spec_id"))
public class SpecEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spec_id", nullable = false)
    private String specId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpecAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private SpecStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private SpecStatus toStatus;

    @Column(nullable = false)
    private String actor;

    @Column(name = "actor_role", nullable = false)
    private String actorRole;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(nullable = false)
    private Instant at;

    protected SpecEvent() {
    }

    public SpecEvent(String specId, SpecAction action, SpecStatus fromStatus, SpecStatus toStatus,
                     String actor, String actorRole, String comment, Instant at) {
        this.specId = specId;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.actorRole = actorRole;
        this.comment = comment;
        this.at = at;
    }

    public Long getId() {
        return id;
    }

    public String getSpecId() {
        return specId;
    }

    public SpecAction getAction() {
        return action;
    }

    public SpecStatus getFromStatus() {
        return fromStatus;
    }

    public SpecStatus getToStatus() {
        return toStatus;
    }

    public String getActor() {
        return actor;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getComment() {
        return comment;
    }

    public Instant getAt() {
        return at;
    }
}
