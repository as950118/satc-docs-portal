package com.docsportal.docs.format.response;

import java.time.Instant;
import java.util.List;

import com.docsportal.docs.persistence.SpecDocument;
import com.docsportal.docs.persistence.SpecEvent;
import com.docsportal.docs.persistence.SpecKind;
import com.docsportal.docs.persistence.SpecStatus;
import com.docsportal.docs.persistence.TechArea;

/**
 * prdId 는 기술문서에만, techAreas 는 PRD 에만, prLinks 는 기술문서에만 값이 있다(그 외 null, 빈 배열).
 * version 은 낙관적 잠금 카운터, revision 은 사람이 보는 PRD 버전 번호다. revision, supersedesId, supersededById 는 PRD 에만,
 * prdRevision 은 기술문서에만 있다. prdChanged 는 기술문서가 부모 PRD 의 현재 버전을 아직 승인하거나 확인하지 않았다는 표시다.
 */
public record SpecDetailResponse(
        String id, SpecKind kind, String prdId, String title, String body, SpecStatus status, List<TechArea> techAreas,
        List<SpecPrLinkResponse> prLinks, Integer version, Integer revision, String supersedesId, String supersededById,
        Integer prdRevision, boolean prdChanged, String author, Instant createdAt, Instant updatedAt,
        List<SpecEventResponse> events) {

    public static SpecDetailResponse from(SpecDocument d, List<SpecEvent> events, String supersededById, boolean prdChanged) {
        return new SpecDetailResponse(d.getId(), d.getKind(), d.getPrdId(), d.getTitle(), d.getBody(), d.getStatus(),
            List.copyOf(d.getTechAreas()), d.getPrLinks().stream().map(SpecPrLinkResponse::from).toList(),
            d.getVersion(), d.getRevision(), d.getSupersedesId(), supersededById, d.getPrdRevision(), prdChanged,
            d.getAuthor(), d.getCreatedAt(), d.getUpdatedAt(), events.stream().map(SpecEventResponse::from).toList());
    }
}
