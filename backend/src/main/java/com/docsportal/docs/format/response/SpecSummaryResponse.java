package com.docsportal.docs.format.response;

import java.time.Instant;

import com.docsportal.docs.persistence.SpecDocument;
import com.docsportal.docs.persistence.SpecKind;
import com.docsportal.docs.persistence.SpecStatus;

public record SpecSummaryResponse(
        String id, SpecKind kind, String title, SpecStatus status, Integer version,
        String author, Instant createdAt, Instant updatedAt) {

    public static SpecSummaryResponse from(SpecDocument d) {
        return new SpecSummaryResponse(d.getId(), d.getKind(), d.getTitle(), d.getStatus(), d.getVersion(),
            d.getAuthor(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
