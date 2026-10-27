package com.docsportal.docs.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docsportal.docs.common.Roles;
import com.docsportal.docs.format.response.SpecDetailResponse;
import com.docsportal.docs.format.response.SpecSummaryResponse;
import com.docsportal.docs.persistence.SpecAction;
import com.docsportal.docs.persistence.SpecDocument;
import com.docsportal.docs.persistence.SpecEvent;
import com.docsportal.docs.persistence.SpecKind;
import com.docsportal.docs.persistence.SpecStatus;
import com.docsportal.docs.persistence.TechArea;
import com.docsportal.docs.repository.SpecDocumentRepository;
import com.docsportal.docs.repository.SpecEventRepository;
import com.docsportal.docs.service.SpecTransitions.Decision;
import com.docsportal.docs.service.SpecTransitions.PrdFacts;
import com.docsportal.docs.service.SpecTransitions.Verdict;

/**
 * 스펙 문서 유스케이스. 허용 여부는 전부 SpecTransitions 표가 결정하고, 여기서는 결과를 반영하고 이력을 남긴다.
 *
 * 문서를 바꾸는 모든 요청은 그 문서가 속한 PRD 행을 먼저 잠근다. 같은 PRD 아래 마지막 두 영역이 동시에 승인되면
 * 각 트랜잭션이 상대의 승인을 보지 못해 집계가 영영 일어나지 않을 수 있어서다.
 */
@Service
public class SpecService {

    private static final SpecActor SYSTEM = new SpecActor("system", List.of(Roles.SYSTEM));

    private final SpecDocumentRepository documents;
    private final SpecEventRepository events;

    public SpecService(SpecDocumentRepository documents, SpecEventRepository events) {
        this.documents = documents;
        this.events = events;
    }

    public String template(SpecKind kind) {
        try {
            return new ClassPathResource(kind.templatePath()).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("양식을 읽을 수 없습니다: " + kind.templatePath(), e);
        }
    }

    @Transactional
    public SpecDetailResponse create(SpecActor actor, String title, String body) {
        Decision decision = authorize(SpecKind.PRD, null, SpecAction.CREATE, actor, PrdFacts.NONE);
        requireText(title, "title");
        requireText(body, "body");

        SpecDocument doc = SpecDocument.prd(UUID.randomUUID().toString(), title, body,
            decision.transition().to(), actor.username());
        documents.saveAndFlush(doc);
        record(doc, decision, actor, null);
        return detail(doc);
    }

    @Transactional
    public SpecDetailResponse createTechDoc(SpecActor actor, String prdId, TechArea area, String title, String body) {
        SpecDocument prd = lockPrd(prdId);
        Decision decision = authorize(area.kind(), null, SpecAction.CREATE, actor, factsOf(prd));
        requireText(title, "title");
        requireText(body, "body");

        SpecDocument doc = SpecDocument.techDoc(UUID.randomUUID().toString(), area, prd.getId(), title, body,
            decision.transition().to(), actor.username());
        documents.saveAndFlush(doc);
        record(doc, decision, actor, null);
        aggregate(prd);
        return detail(doc);
    }

    @Transactional
    public SpecDetailResponse setTechAreas(SpecActor actor, String prdId, Set<TechArea> areas) {
        SpecDocument prd = lockPrd(prdId);
        Decision decision = authorize(prd.getKind(), prd.getStatus(), SpecAction.SET_TECH_AREAS, actor, factsOf(prd));
        if (areas.isEmpty()) {
            throw new SpecException(HttpStatus.BAD_REQUEST, "areas 에 영역이 1개 이상 필요합니다.");
        }

        prd.declareTechAreas(areas);
        documents.saveAndFlush(prd);
        record(prd, decision, actor, prd.getTechAreas().stream().map(TechArea::name).collect(Collectors.joining(",")));
        aggregate(prd);
        return detail(prd);
    }

    @Transactional
    public SpecDetailResponse edit(SpecActor actor, String id, String title, String body) {
        return transition(actor, id, SpecAction.EDIT, null, doc -> {
            requireText(body, "body");
            doc.edit(title == null || title.isBlank() ? doc.getTitle() : title, body);
        });
    }

    @Transactional
    public SpecDetailResponse submit(SpecActor actor, String id, String comment) {
        return transition(actor, id, SpecAction.SUBMIT, comment, doc -> { });
    }

    /** PRD 면 PRD 승인, 기술문서면 검토 승인. 어느 쪽인지는 전이 표가 kind 로 정한다. */
    @Transactional
    public SpecDetailResponse approve(SpecActor actor, String id, String comment) {
        return transition(actor, id, SpecAction.APPROVE, comment, doc -> { });
    }

    @Transactional
    public SpecDetailResponse requestChanges(SpecActor actor, String id, String comment) {
        return transition(actor, id, SpecAction.REQUEST_CHANGES, comment, doc -> { });
    }

    @Transactional(readOnly = true)
    public List<SpecSummaryResponse> list(SpecKind kind, SpecStatus status, String prdId) {
        return documents.search(kind, status, prdId).stream()
            .map(SpecSummaryResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<SpecSummaryResponse> techDocs(String prdId) {
        SpecDocument prd = find(prdId);
        requirePrd(prd);
        return documents.findByPrdIdOrderByCreatedAtAsc(prd.getId()).stream()
            .map(SpecSummaryResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public SpecDetailResponse get(String id) {
        return detail(find(id));
    }

    private SpecDetailResponse transition(SpecActor actor, String id, SpecAction action, String comment,
                                          Consumer<SpecDocument> change) {
        SpecDocument doc = find(id);
        SpecDocument prd = lockPrd(doc.getRootPrdId());
        Decision decision = authorize(doc.getKind(), doc.getStatus(), action, actor, factsOf(prd));
        if (action.isCommentRequired()) {
            requireText(comment, "comment");
        }

        if (decision.verdict() == Verdict.ALLOWED) {
            change.accept(doc);
            doc.moveTo(decision.transition().to());
            documents.saveAndFlush(doc);
            record(doc, decision, actor, comment);
        }
        aggregate(prd);
        return detail(doc);
    }

    /**
     * PRD 집계의 유일한 실행 지점 (ARCH-0004). 조건은 표의 AGGREGATE 행이 갖고, 통과할 때만 시스템 이력과 함께 올린다.
     * 이미 올랐거나 조건이 안 맞으면 아무것도 하지 않으므로 몇 번 불러도 결과가 같다.
     */
    private void aggregate(SpecDocument prd) {
        Decision decision = SpecTransitions.decide(prd.getKind(), prd.getStatus(), SpecAction.AGGREGATE,
            SYSTEM.roles(), factsOf(prd));
        if (decision.verdict() != Verdict.ALLOWED) {
            return;
        }
        prd.moveTo(decision.transition().to());
        documents.saveAndFlush(prd);
        record(prd, decision, SYSTEM, null);
    }

    private PrdFacts factsOf(SpecDocument prd) {
        Map<SpecKind, SpecStatus> techDocs = new EnumMap<>(SpecKind.class);
        documents.findByPrdIdOrderByCreatedAtAsc(prd.getId()).forEach(d -> techDocs.put(d.getKind(), d.getStatus()));
        return new PrdFacts(prd.getStatus(), prd.getTechAreas(), techDocs);
    }

    private Decision authorize(SpecKind kind, SpecStatus current, SpecAction action, SpecActor actor, PrdFacts prd) {
        Decision decision = SpecTransitions.decide(kind, current, action, actor.roles(), prd);
        return switch (decision.verdict()) {
            case ALLOWED, ALREADY_DONE -> decision;
            case FORBIDDEN -> throw new SpecException(HttpStatus.FORBIDDEN,
                "이 작업을 수행할 역할이 없습니다: " + action);
            case INVALID_STATE -> throw new SpecException(HttpStatus.CONFLICT,
                "현재 상태(" + current + ")에서는 " + action + " 할 수 없습니다.");
            case PRECONDITION_FAILED -> throw new SpecException(HttpStatus.CONFLICT, decision.failedGuard().failure());
        };
    }

    private void record(SpecDocument doc, Decision decision, SpecActor actor, String comment) {
        events.save(new SpecEvent(doc.getId(), decision.transition().action(), decision.transition().from(), decision.transition().to(),
            actor.username(), decision.actorRole(), comment, Instant.now()));
    }

    private SpecDocument find(String id) {
        return documents.findById(id)
            .orElseThrow(() -> new SpecException(HttpStatus.NOT_FOUND, "스펙 문서를 찾을 수 없습니다: " + id));
    }

    private SpecDocument lockPrd(String prdId) {
        SpecDocument prd = documents.findForUpdate(prdId)
            .orElseThrow(() -> new SpecException(HttpStatus.NOT_FOUND, "스펙 문서를 찾을 수 없습니다: " + prdId));
        requirePrd(prd);
        return prd;
    }

    private static void requirePrd(SpecDocument doc) {
        if (doc.getKind() != SpecKind.PRD) {
            throw new SpecException(HttpStatus.NOT_FOUND, "PRD 를 찾을 수 없습니다: " + doc.getId());
        }
    }

    private SpecDetailResponse detail(SpecDocument doc) {
        return SpecDetailResponse.from(doc, events.findBySpecIdOrderByAtAscIdAsc(doc.getId()));
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new SpecException(HttpStatus.BAD_REQUEST, field + " 이(가) 필요합니다.");
        }
    }
}
