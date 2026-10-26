package com.docsportal.docs.format.response;

import java.time.Instant;
import java.util.List;

import com.docsportal.docs.persistence.SpecDocument;
import com.docsportal.docs.persistence.SpecEvent;
import com.docsportal.docs.persistence.SpecKind;
import com.docsportal.docs.persistence.SpecStatus;

public record SpecDetailResponse(
        String id, SpecKind kind, String title, String body, SpecStatus status, Integer version,
        String author, Instant createdAt, Instant updatedAt, List<SpecEventResponse> events) {

    public static SpecDetailResponse from(SpecDocument d, List<SpecEvent> events) {
        return new SpecDetailResponse(d.getId(), d.getKind(), d.getTitle(), d.getBody(), d.getStatus(),
            d.getVersion(), d.getAuthor(), d.getCreatedAt(), d.getUpdatedAt(),
            events.stream().map(SpecEventResponse::from).toList());
    }
}
