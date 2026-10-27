package com.docsportal.docs.persistence;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 기술문서에 연결된 PR 한 건 (ARCH-0004 3단계). 문서당 URL 은 하나만 있으므로 동등성은 url 로만 판단한다.
 */
@Embeddable
public class SpecPrLink {

    public static final int MAX_URL_LENGTH = 500;

    @Column(nullable = false, length = MAX_URL_LENGTH)
    private String url;

    @Column(name = "linked_by", nullable = false)
    private String linkedBy;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    protected SpecPrLink() {
    }

    public SpecPrLink(String url, String linkedBy, Instant linkedAt) {
        this.url = url;
        this.linkedBy = linkedBy;
        this.linkedAt = linkedAt;
    }

    public String getUrl() {
        return url;
    }

    public String getLinkedBy() {
        return linkedBy;
    }

    public Instant getLinkedAt() {
        return linkedAt;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SpecPrLink other && Objects.equals(url, other.url);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(url);
    }
}
