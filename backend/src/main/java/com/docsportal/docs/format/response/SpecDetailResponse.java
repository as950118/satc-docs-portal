package com.docsportal.docs.format.response;

import java.time.Instant;
import java.util.List;

import com.docsportal.docs.persistence.SpecDocument;
import com.docsportal.docs.persistence.SpecEvent;
import com.docsportal.docs.persistence.SpecKind;
import com.docsportal.docs.persistence.SpecStatus;
import com.docsportal.docs.persistence.TechArea;

/** prdId 는 기술문서에만, techAreas 는 PRD 에만, prLinks 는 기술문서에만 값이 있다(그 외 null, 빈 배열). */
public record SpecDetailResponse(
        String id, SpecKind kind, String prdId, String title, String body, SpecStatus status, List<TechArea> techAreas,
        List<SpecPrLinkResponse> prLinks, Integer version, String author, Instant createdAt, Instant updatedAt,
        List<SpecEventResponse> events) {

    public static SpecDetailResponse from(SpecDocument d, List<SpecEvent> events) {
        return new SpecDetailResponse(d.getId(), d.getKind(), d.getPrdId(), d.getTitle(), d.getBody(), d.getStatus(),
            List.copyOf(d.getTechAreas()), d.getPrLinks().stream().map(SpecPrLinkResponse::from).toList(),
            d.getVersion(), d.getAuthor(), d.getCreatedAt(), d.getUpdatedAt(),
            events.stream().map(SpecEventResponse::from).toList());
    }
}
