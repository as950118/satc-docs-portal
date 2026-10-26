package com.docsportal.docs.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docsportal.docs.format.response.SpecDetailResponse;
import com.docsportal.docs.format.response.SpecSummaryResponse;
import com.docsportal.docs.persistence.SpecAction;
import com.docsportal.docs.persistence.SpecDocument;
import com.docsportal.docs.persistence.SpecEvent;
import com.docsportal.docs.persistence.SpecKind;
import com.docsportal.docs.persistence.SpecStatus;
import com.docsportal.docs.repository.SpecDocumentRepository;
import com.docsportal.docs.repository.SpecEventRepository;
import com.docsportal.docs.service.SpecTransitions.Decision;
import com.docsportal.docs.service.SpecTransitions.Verdict;

/**
 * 스펙 문서 유스케이스. 허용 여부는 전부 SpecTransitions 표가 결정하고, 여기서는 결과를 반영하고 이력을 남긴다.
 */
@Service
public class SpecService {

    private static final String PRD_TEMPLATE_PATH = "spec-templates/prd.md";

    private final SpecDocumentRepository documents;
    private final SpecEventRepository events;

    public SpecService(SpecDocumentRepository documents, SpecEventRepository events) {
        this.documents = documents;
        this.events = events;
    }

    public String prdTemplate() {
        try {
            return new ClassPathResource(PRD_TEMPLATE_PATH).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("PRD 양식을 읽을 수 없습니다: " + PRD_TEMPLATE_PATH, e);
        }
    }

    @Transactional
    public SpecDetailResponse create(SpecActor actor, String title, String body) {
        Decision decision = authorize(SpecKind.PRD, null, SpecAction.CREATE, actor);
        requireText(title, "title");
        requireText(body, "body");

        SpecDocument doc = SpecDocument.prd(UUID.randomUUID().toString(), title, body,
            decision.transition().to(), actor.username());
        documents.saveAndFlush(doc);
        record(doc, decision, actor, null);
        return detail(doc);
    }

    @Transactional
    public SpecDetailResponse edit(SpecActor actor, String id, String title, String body) {
        SpecDocument doc = find(id);
        Decision decision = authorize(doc.getKind(), doc.getStatus(), SpecAction.EDIT, actor);
        requireText(body, "body");

        doc.edit(title == null || title.isBlank() ? doc.getTitle() : title, body);
        documents.saveAndFlush(doc);
        record(doc, decision, actor, null);
        return detail(doc);
    }

    @Transactional
    public SpecDetailResponse approve(SpecActor actor, String id, String comment) {
        SpecDocument doc = find(id);
        Decision decision = authorize(doc.getKind(), doc.getStatus(), SpecAction.APPROVE, actor);
        if (decision.verdict() == Verdict.ALLOWED) {
            doc.moveTo(decision.transition().to());
            documents.saveAndFlush(doc);
            record(doc, decision, actor, comment);
        }
        return detail(doc);
    }

    @Transactional(readOnly = true)
    public List<SpecSummaryResponse> list(SpecKind kind, SpecStatus status) {
        return documents.search(kind, status, null).stream()
            .map(SpecSummaryResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public SpecDetailResponse get(String id) {
        return detail(find(id));
    }

    private Decision authorize(SpecKind kind, SpecStatus current, SpecAction action, SpecActor actor) {
        Decision decision = SpecTransitions.decide(kind, current, action, actor.roles());
        return switch (decision.verdict()) {
            case ALLOWED, ALREADY_DONE -> decision;
            case FORBIDDEN -> throw new SpecException(HttpStatus.FORBIDDEN,
                "이 작업을 수행할 역할이 없습니다: " + action);
            case INVALID_STATE -> throw new SpecException(HttpStatus.CONFLICT,
                "현재 상태(" + current + ")에서는 " + action + " 할 수 없습니다.");
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

    private SpecDetailResponse detail(SpecDocument doc) {
        return SpecDetailResponse.from(doc, events.findBySpecIdOrderByAtAscIdAsc(doc.getId()));
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new SpecException(HttpStatus.BAD_REQUEST, field + " 이(가) 필요합니다.");
        }
    }
}
