package com.docsportal.docs.format.response;

import java.time.Instant;
import java.util.List;

import com.docsportal.docs.persistence.SpecDocument;
import com.docsportal.docs.persistence.SpecKind;
import com.docsportal.docs.persistence.SpecStatus;
import com.docsportal.docs.persistence.TechArea;

public record SpecSummaryResponse(
        String id, SpecKind kind, String prdId, String title, SpecStatus status, List<TechArea> techAreas, Integer version,
        Integer revision, String supersedesId, boolean prdChanged, String author, Instant createdAt, Instant updatedAt) {

    public static SpecSummaryResponse from(SpecDocument d, boolean prdChanged) {
        return new SpecSummaryResponse(d.getId(), d.getKind(), d.getPrdId(), d.getTitle(), d.getStatus(),
            List.copyOf(d.getTechAreas()), d.getVersion(), d.getRevision(), d.getSupersedesId(), prdChanged,
            d.getAuthor(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
