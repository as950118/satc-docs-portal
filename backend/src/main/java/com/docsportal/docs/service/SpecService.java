package com.docsportal.docs.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
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
import com.docsportal.docs.persistence.SpecPrLink;
import com.docsportal.docs.persistence.SpecKind;
import com.docsportal.docs.persistence.SpecStatus;
import com.docsportal.docs.persistence.TechArea;
import com.docsportal.docs.repository.SpecDocumentRepository;
import com.docsportal.docs.repository.SpecEventRepository;
import com.docsportal.docs.service.SpecTransitions.Decision;
import com.docsportal.docs.service.SpecTransitions.Facts;
import com.docsportal.docs.service.SpecTransitions.PrdFacts;
import com.docsportal.docs.service.SpecTransitions.Verdict;

/**
 * 스펙 문서 유스케이스. 허용 여부는 전부 SpecTransitions 표가 결정하고, 여기서는 결과를 반영하고 이력을 남긴다.
 *
 * 문서를 바꾸는 모든 요청은 그 문서가 속한 PRD 행을 먼저 잠근다. 같은 PRD 아래 마지막 두 영역이 동시에 승인되면
 * 각 트랜잭션이 상대의 승인을 보지 못해 집계가 영영 일어나지 않을 수 있어서다.
 * 새 PRD 버전 승인만 두 행을 잠그며, 순서는 항상 승인하는 버전 → 직전 버전이다 (ARCH-0004 4단계).
 */
@Service
public class SpecService {

    private static final SpecActor SYSTEM = new SpecActor("system", List.of(Roles.SYSTEM));
    static final int MAX_PR_LINKS = 20;

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
        Decision decision = authorize(SpecKind.PRD, null, SpecAction.CREATE, actor, new Facts(PrdFacts.NONE, 0));
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
        Decision decision = authorize(area.kind(), null, SpecAction.CREATE, actor, new Facts(prdFacts(prd), 0));
        requireText(title, "title");
        requireText(body, "body");

        SpecDocument doc = SpecDocument.techDoc(UUID.randomUUID().toString(), area, prd, title, body,
            decision.transition().to(), actor.username());
        documents.saveAndFlush(doc);
        record(doc, decision, actor, null);
        aggregate(prd);
        return detail(doc);
    }

    @Transactional
    public SpecDetailResponse setTechAreas(SpecActor actor, String prdId, Set<TechArea> areas) {
        SpecDocument prd = lockPrd(prdId);
        Decision decision = authorize(prd.getKind(), prd.getStatus(), SpecAction.SET_TECH_AREAS, actor, factsOf(prd, prd));
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
        return transition(actor, id, SpecAction.EDIT, null, (doc, prd) -> {
            requireText(body, "body");
            doc.edit(title == null || title.isBlank() ? doc.getTitle() : title, body);
        }, doc -> { });
    }

    @Transactional
    public SpecDetailResponse submit(SpecActor actor, String id, String comment) {
        return transition(actor, id, SpecAction.SUBMIT, comment);
    }

    /**
     * PRD 면 PRD 승인, 기술문서면 검토 승인. 어느 쪽인지는 전이 표가 kind 로 정한다.
     * 기술문서 승인은 부모 PRD 의 현재 버전을 확인한 것이고, 새 PRD 버전 승인은 직전 버전을 대체한다.
     */
    @Transactional
    public SpecDetailResponse approve(SpecActor actor, String id, String comment) {
        return transition(actor, id, SpecAction.APPROVE, comment, SpecDocument::confirmAgainst, this::supersedePredecessor);
    }

    @Transactional
    public SpecDetailResponse requestChanges(SpecActor actor, String id, String comment) {
        return transition(actor, id, SpecAction.REQUEST_CHANGES, comment);
    }

    @Transactional
    public SpecDetailResponse startImplementation(SpecActor actor, String id, String comment) {
        return transition(actor, id, SpecAction.START_IMPLEMENTATION, comment);
    }

    @Transactional
    public SpecDetailResponse submitImplementation(SpecActor actor, String id, String comment) {
        return transition(actor, id, SpecAction.SUBMIT_IMPLEMENTATION, comment);
    }

    @Transactional
    public SpecDetailResponse approveImplementation(SpecActor actor, String id, String comment) {
        return transition(actor, id, SpecAction.APPROVE_IMPLEMENTATION, comment);
    }

    @Transactional
    public SpecDetailResponse requestImplementationChanges(SpecActor actor, String id, String comment) {
        return transition(actor, id, SpecAction.REQUEST_IMPL_CHANGES, comment);
    }

    /**
     * 승인된 PRD 의 다음 버전을 현재 본문으로 DRAFT 로 만든다. 직전 버전 행을 잠근 뒤 후속 버전을 찾으므로,
     * 동시에 들어온 요청이나 재시도는 이미 만들어진 초안을 그대로 받는다.
     */
    @Transactional
    public SpecDetailResponse newVersion(SpecActor actor, String prdId) {
        SpecDocument prior = lockPrd(prdId);
        Decision decision = authorize(prior.getKind(), prior.getStatus(), SpecAction.NEW_VERSION, actor, factsOf(prior, prior));
        Optional<SpecDocument> pending = documents.findBySupersedesId(prior.getId());
        if (pending.isPresent()) {
            return detail(pending.get());
        }

        Decision create = authorize(SpecKind.PRD, null, SpecAction.CREATE, actor, new Facts(PrdFacts.NONE, 0));
        SpecDocument next = SpecDocument.nextVersionOf(prior, UUID.randomUUID().toString(), create.transition().to(),
            actor.username());
        documents.saveAndFlush(next);
        record(next, create, actor, "v" + prior.getRevision() + " 의 새 버전");
        record(prior, decision, actor, "v" + next.getRevision() + " 초안: " + next.getId());
        return detail(next);
    }

    /** 부모 PRD 가 바뀐 뒤 구현 영향을 확인했다는 기록. 확인할 변경이 없으면 이력 없이 성공한다. */
    @Transactional
    public SpecDetailResponse acknowledgePrdChange(SpecActor actor, String id, String comment) {
        SpecDocument doc = find(id);
        SpecDocument prd = lockPrd(doc.getRootPrdId());
        Decision decision = authorize(doc.getKind(), doc.getStatus(), SpecAction.ACKNOWLEDGE_PRD_CHANGE, actor,
            factsOf(prd, doc));
        if (!doc.isBehind(prd)) {
            return detail(doc);
        }
        doc.confirmAgainst(prd);
        documents.saveAndFlush(doc);
        record(doc, decision, actor, comment);
        return detail(doc);
    }

    /** 같은 URL 은 이미 연결된 것으로 보고 이력 없이 성공한다. 상태가 바뀌지 않으므로 집계도 하지 않는다. */
    @Transactional
    public SpecDetailResponse linkPr(SpecActor actor, String id, String url) {
        SpecDocument doc = find(id);
        SpecDocument prd = lockPrd(doc.getRootPrdId());
        Decision decision = authorize(doc.getKind(), doc.getStatus(), SpecAction.LINK_PR, actor, factsOf(prd, doc));
        SpecPrLink link = new SpecPrLink(prUrl(url), actor.username(), Instant.now());

        if (doc.hasPrLink(link)) {
            return detail(doc);
        }
        if (doc.getPrLinks().size() >= MAX_PR_LINKS) {
            throw new SpecException(HttpStatus.CONFLICT, "PR 링크는 문서당 " + MAX_PR_LINKS + "개까지 연결할 수 있습니다.");
        }
        doc.linkPr(link);
        documents.saveAndFlush(doc);
        record(doc, decision, actor, link.getUrl());
        return detail(doc);
    }

    /** 연결되지 않은 URL 은 이미 지워진 것으로 보고 이력 없이 성공한다. 구현 검토에 올라간 링크는 전이 표가 고정한다. */
    @Transactional
    public SpecDetailResponse unlinkPr(SpecActor actor, String id, String url) {
        SpecDocument doc = find(id);
        SpecDocument prd = lockPrd(doc.getRootPrdId());
        Decision decision = authorize(doc.getKind(), doc.getStatus(), SpecAction.UNLINK_PR, actor, factsOf(prd, doc));
        SpecPrLink link = new SpecPrLink(prUrl(url), actor.username(), Instant.now());

        if (!doc.hasPrLink(link)) {
            return detail(doc);
        }
        doc.unlinkPr(link);
        documents.saveAndFlush(doc);
        record(doc, decision, actor, link.getUrl());
        return detail(doc);
    }

    @Transactional(readOnly = true)
    public List<SpecSummaryResponse> list(SpecKind kind, SpecStatus status, String prdId, boolean allVersions) {
        List<SpecDocument> found = documents.search(kind, status, prdId, allVersions);
        Map<String, SpecDocument> parents = documents.findAllById(found.stream()
                .map(SpecDocument::getPrdId).filter(Objects::nonNull).distinct().toList()).stream()
            .collect(Collectors.toMap(SpecDocument::getId, Function.identity()));
        return found.stream()
            .map(d -> SpecSummaryResponse.from(d, d.getPrdId() != null && d.isBehind(parents.get(d.getPrdId()))))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<SpecSummaryResponse> techDocs(String prdId) {
        SpecDocument prd = find(prdId);
        requirePrd(prd);
        return documents.findByPrdIdOrderByCreatedAtAsc(prd.getId()).stream()
            .map(d -> SpecSummaryResponse.from(d, d.isBehind(prd)))
            .toList();
    }

    @Transactional(readOnly = true)
    public SpecDetailResponse get(String id) {
        return detail(find(id));
    }

    private SpecDetailResponse transition(SpecActor actor, String id, SpecAction action, String comment) {
        return transition(actor, id, action, comment, (doc, prd) -> { }, doc -> { });
    }

    /** change 는 상태를 옮기기 전에, afterMove 는 옮기고 이력을 남긴 뒤 집계 전에 실행된다. 둘 다 전이가 허용될 때만 돈다. */
    private SpecDetailResponse transition(SpecActor actor, String id, SpecAction action, String comment,
                                          BiConsumer<SpecDocument, SpecDocument> change, Consumer<SpecDocument> afterMove) {
        SpecDocument doc = find(id);
        SpecDocument prd = lockPrd(doc.getRootPrdId());
        Decision decision = authorize(doc.getKind(), doc.getStatus(), action, actor, factsOf(prd, doc));
        if (action.isCommentRequired()) {
            requireText(comment, "comment");
        }

        if (decision.verdict() == Verdict.ALLOWED) {
            change.accept(doc, prd);
            doc.moveTo(decision.transition().to());
            documents.saveAndFlush(doc);
            record(doc, decision, actor, comment);
            afterMove.accept(doc);
        }
        aggregate(prd);
        return detail(doc);
    }

    /**
     * 새 PRD 버전이 승인되면 직전 버전을 SUPERSEDED 로 닫고, 그 아래 기술문서를 새 버전 아래로 옮기며 표의 PRD_REVISED 행대로
     * 재검토로 돌리거나 표시만 남긴다. 직전 버전이 없는 문서(첫 버전 PRD, 기술문서)에는 아무것도 하지 않는다.
     */
    private void supersedePredecessor(SpecDocument approved) {
        if (approved.getSupersedesId() == null) {
            return;
        }
        SpecDocument prior = lockPrd(approved.getSupersedesId());
        String reason = "v" + approved.getRevision() + " 승인";
        approved.declareTechAreas(prior.getTechAreas());
        documents.saveAndFlush(approved);
        applySystem(prior, systemDecision(prior, SpecAction.SUPERSEDE), reason + ": " + approved.getId());

        for (SpecDocument tech : documents.findByPrdIdOrderByCreatedAtAsc(prior.getId())) {
            tech.moveUnder(approved);
            Decision revised = systemDecision(tech, SpecAction.PRD_REVISED);
            String impact = revised.transition().to() == tech.getStatus() ? "영향 확인 필요" : "재검토 필요";
            applySystem(tech, revised, "PRD " + reason + ", " + impact);
        }
    }

    /**
     * PRD 집계의 유일한 실행 지점 (ARCH-0004). 조건은 표의 AGGREGATE 행이 갖고, 통과할 때만 시스템 이력과 함께 올린다.
     * 더 오를 수 없을 때까지 반복하므로 몇 번 불러도 결과가 같다. 새 PRD 버전 승인 직후에는 한 번에 여러 단계를 오를 수 있다.
     */
    private void aggregate(SpecDocument prd) {
        Decision decision;
        while ((decision = SpecTransitions.decide(prd.getKind(), prd.getStatus(), SpecAction.AGGREGATE,
                SYSTEM.roles(), factsOf(prd, prd))).verdict() == Verdict.ALLOWED) {
            prd.moveTo(decision.transition().to());
            documents.saveAndFlush(prd);
            record(prd, decision, SYSTEM, null);
        }
    }

    private Decision systemDecision(SpecDocument doc, SpecAction action) {
        Decision decision = SpecTransitions.decide(doc.getKind(), doc.getStatus(), action, SYSTEM.roles(),
            new Facts(PrdFacts.NONE, 0));
        if (decision.verdict() != Verdict.ALLOWED) {
            throw new IllegalStateException("전이 표에 " + doc.getKind() + " " + doc.getStatus() + " " + action + " 행이 없습니다.");
        }
        return decision;
    }

    private void applySystem(SpecDocument doc, Decision decision, String comment) {
        doc.moveTo(decision.transition().to());
        documents.saveAndFlush(doc);
        record(doc, decision, SYSTEM, comment);
    }

    private Facts factsOf(SpecDocument prd, SpecDocument subject) {
        return new Facts(prdFacts(prd), subject.getPrLinks().size());
    }

    private PrdFacts prdFacts(SpecDocument prd) {
        Map<SpecKind, SpecStatus> techDocs = new EnumMap<>(SpecKind.class);
        documents.findByPrdIdOrderByCreatedAtAsc(prd.getId()).forEach(d -> techDocs.put(d.getKind(), d.getStatus()));
        return new PrdFacts(prd.getStatus(), prd.getTechAreas(), techDocs);
    }

    private Decision authorize(SpecKind kind, SpecStatus current, SpecAction action, SpecActor actor, Facts facts) {
        Decision decision = SpecTransitions.decide(kind, current, action, actor.roles(), facts);
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
        String supersededById = doc.getKind() == SpecKind.PRD
            ? documents.findBySupersedesId(doc.getId()).map(SpecDocument::getId).orElse(null)
            : null;
        boolean prdChanged = doc.getPrdId() != null && documents.findById(doc.getPrdId()).map(doc::isBehind).orElse(false);
        return SpecDetailResponse.from(doc, events.findBySpecIdOrderByAtAscIdAsc(doc.getId()), supersededById, prdChanged);
    }

    private static String prUrl(String raw) {
        requireText(raw, "url");
        String url = raw.strip();
        if (url.length() > SpecPrLink.MAX_URL_LENGTH) {
            throw new SpecException(HttpStatus.BAD_REQUEST, "url 은 " + SpecPrLink.MAX_URL_LENGTH + "자 이하여야 합니다.");
        }
        if (!isWebUrl(url)) {
            throw new SpecException(HttpStatus.BAD_REQUEST, "url 은 http 또는 https 주소여야 합니다: " + url);
        }
        return url;
    }

    private static boolean isWebUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new SpecException(HttpStatus.BAD_REQUEST, field + " 이(가) 필요합니다.");
        }
    }
}
