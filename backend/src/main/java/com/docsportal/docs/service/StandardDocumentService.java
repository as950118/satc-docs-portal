package com.docsportal.docs.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.docsportal.docs.format.request.DocumentSyncRequest;
import com.docsportal.docs.format.response.DocumentSyncResponse;
import com.docsportal.docs.format.response.StandardDocumentResponse;
import com.docsportal.docs.persistence.DocumentEntity;
import com.docsportal.docs.repository.DocsRepository;
import com.docsportal.docs.repository.DocumentEventRepository;

/**
 * 사용자 토큰으로 등록하는 표준문서. 저장·색인은 코드 동기화(DocumentSyncService)를 그대로 쓰고,
 * 등록자/수정자와 이력만 더한다. PRD/기술문서 워크플로우와는 무관하다.
 */
@Service
public class StandardDocumentService {

    /** 코드 동기화 문서(OpenAPI 등)를 이 경로로 덮어쓰지 못하게 표준문서 키는 이 접두사로 구분한다. */
    public static final String SOURCE_KEY_PREFIX = "standard:";

    private final DocumentSyncService syncService;
    private final DocsRepository docsRepository;
    private final DocumentEventRepository eventRepository;

    public StandardDocumentService(DocumentSyncService syncService, DocsRepository docsRepository,
                                   DocumentEventRepository eventRepository) {
        this.syncService = syncService;
        this.docsRepository = docsRepository;
        this.eventRepository = eventRepository;
    }

    public StandardDocumentResponse.Registered register(DocumentSyncRequest request, UploadActor actor) {
        if (request.sourceKey() == null || !request.sourceKey().startsWith(SOURCE_KEY_PREFIX)) {
            throw new IllegalArgumentException("표준문서의 sourceKey 는 '" + SOURCE_KEY_PREFIX + "' 로 시작해야 합니다.");
        }
        DocumentSyncResponse result = syncService.sync(request, actor);
        DocumentEntity document = docsRepository.findById(result.getDocumentId()).orElseThrow();
        return new StandardDocumentResponse.Registered(document.getId(), result.getResult(), result.getStatus(),
            document.getCreatedById(), document.getCreatedBy(), document.getUpdatedById(), document.getUpdatedBy(),
            document.getUpdatedAt());
    }

    public StandardDocumentResponse.History history(String sourceKey) {
        if (sourceKey == null || !sourceKey.startsWith(SOURCE_KEY_PREFIX)) {
            throw new IllegalArgumentException("sourceKey 는 '" + SOURCE_KEY_PREFIX + "' 로 시작해야 합니다.");
        }
        DocumentEntity document = docsRepository.findBySourceKey(sourceKey)
            .filter(d -> !Boolean.TRUE.equals(d.getDeleted()))
            .orElseThrow(() -> new SpecException(HttpStatus.NOT_FOUND, "등록된 표준문서가 없습니다."));
        List<StandardDocumentResponse.Event> events = eventRepository.findByDocumentIdOrderByAtAscIdAsc(document.getId())
            .stream()
            .map(e -> new StandardDocumentResponse.Event(e.getAction().name(), e.getUserId(), e.getActor(), e.getAt(),
                e.getContentHash(), e.getSourceCommit()))
            .toList();
        return new StandardDocumentResponse.History(document.getId(), document.getTitle(), document.getSourceKey(),
            document.getCreatedById(), document.getCreatedBy(), document.getCreatedAt(),
            document.getUpdatedById(), document.getUpdatedBy(), document.getUpdatedAt(), events);
    }
}
