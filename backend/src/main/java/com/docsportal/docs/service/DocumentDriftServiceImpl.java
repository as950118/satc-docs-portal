package com.docsportal.docs.service;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.docsportal.docs.format.request.DocumentDriftReportRequest;
import com.docsportal.docs.format.response.DocumentDriftResponse;
import com.docsportal.docs.persistence.DocumentEntity;
import com.docsportal.docs.persistence.DriftStatus;
import com.docsportal.docs.repository.DocsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;

@Service
public class DocumentDriftServiceImpl implements DocumentDriftService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentDriftServiceImpl.class);

    static final int MAX_CONTENT_BYTES = 5 * 1024 * 1024;
    static final int MAX_LIST_ITEMS = 200;
    private static final int MAX_COMMIT_LENGTH = 40;
    private static final Set<String> TEXT_EXTENSIONS = Set.of("md", "markdown", "txt", "yaml", "yml", "json");

    private final MinioClient minioClient;
    private final DocsRepository docsRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public DocumentDriftServiceImpl(MinioClient minioClient, DocsRepository docsRepository, ObjectMapper objectMapper) {
        this(minioClient, docsRepository, objectMapper, Clock.systemUTC());
    }

    DocumentDriftServiceImpl(MinioClient minioClient, DocsRepository docsRepository, ObjectMapper objectMapper,
                             Clock clock) {
        this.minioClient = minioClient;
        this.docsRepository = docsRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public DocumentContent getContent(String documentId) {
        DocumentEntity document = findDocument(documentId);
        String extension = document.getExtension() == null ? "" : document.getExtension().toLowerCase();
        if (!TEXT_EXTENSIONS.contains(extension)) {
            throw new DocumentDriftException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "텍스트 문서만 지원합니다 (" + String.join(", ", TEXT_EXTENSIONS) + "): " + extension);
        }

        byte[] bytes;
        try (InputStream in = minioClient.getObject(
                GetObjectArgs.builder().bucket(document.getBucket()).object(document.getFilePath()).build())) {
            bytes = in.readNBytes(MAX_CONTENT_BYTES + 1);
        } catch (Exception e) {
            logger.error("[DRIFT] Failed to read {}: {}", document.getFilePath(), e.getMessage(), e);
            throw new DocumentDriftException(HttpStatus.BAD_GATEWAY, "문서 본문을 읽지 못했습니다: " + e.getMessage(), e);
        }
        if (bytes.length > MAX_CONTENT_BYTES) {
            throw new DocumentDriftException(HttpStatus.PAYLOAD_TOO_LARGE, "문서가 5MB를 초과합니다.");
        }
        String contentType = "json".equals(extension) ? "application/json" : "text/plain; charset=UTF-8";
        return new DocumentContent(document.getTitle() + "." + extension, contentType, bytes);
    }

    @Override
    public DocumentDriftResponse report(String documentId, DocumentDriftReportRequest request) {
        validate(request);
        DocumentEntity document = findDocument(documentId);

        List<String> missingInCode = nullToEmpty(request.missingInCode());
        List<String> missingInDoc = nullToEmpty(request.missingInDoc());
        int referenced = request.referenced() == null ? 0 : request.referenced();

        DriftStatus status;
        if (referenced == 0) {
            status = DriftStatus.NO_API_REFERENCES;
        } else if (missingInCode.isEmpty()) {
            status = DriftStatus.IN_SYNC;
        } else {
            status = DriftStatus.DRIFTED;
        }

        boolean truncated = missingInCode.size() > MAX_LIST_ITEMS || missingInDoc.size() > MAX_LIST_ITEMS;
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("referenced", referenced);
        detail.put("missingInCode", head(missingInCode));
        detail.put("missingInDoc", head(missingInDoc));
        detail.put("unresolvedPaths", request.unresolvedPaths() == null ? 0 : request.unresolvedPaths());
        detail.put("truncated", truncated);

        document.recordDrift(status, Instant.now(clock), request.sourceKey(), request.sourceCommit(), toJson(detail));
        docsRepository.save(document);
        logger.info("[DRIFT] {} -> {} (referenced={}, missingInCode={})",
            documentId, status, referenced, missingInCode.size());
        return toResponse(document);
    }

    @Override
    public DocumentDriftResponse get(String documentId) {
        return toResponse(findDocument(documentId));
    }

    private DocumentEntity findDocument(String documentId) {
        DocumentEntity document = docsRepository.findById(documentId).orElse(null);
        if (document == null || Boolean.TRUE.equals(document.getDeleted())
                || Boolean.TRUE.equals(document.getIsFolder())) {
            throw new DocumentDriftException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다: " + documentId);
        }
        return document;
    }

    private DocumentDriftResponse toResponse(DocumentEntity document) {
        return new DocumentDriftResponse(
            document.getId(),
            document.getDriftStatus(),
            document.getDriftCheckedAt(),
            document.getDriftSourceKey(),
            document.getDriftCommit(),
            fromJson(document.getDriftDetail()),
            document.getSourceKey() != null,
            document.getSourceKey(),
            document.getSourceCommit(),
            document.getSyncedAt());
    }

    private static void validate(DocumentDriftReportRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("요청 본문이 필요합니다.");
        }
        if (request.sourceKey() == null || request.sourceKey().isBlank()) {
            throw new IllegalArgumentException("sourceKey 가 필요합니다.");
        }
        if (request.sourceCommit() == null || request.sourceCommit().isBlank()
                || request.sourceCommit().length() > MAX_COMMIT_LENGTH) {
            throw new IllegalArgumentException("sourceCommit 은 1~" + MAX_COMMIT_LENGTH + "자여야 합니다.");
        }
        if (request.referenced() != null && request.referenced() < 0) {
            throw new IllegalArgumentException("referenced 는 0 이상이어야 합니다.");
        }
    }

    private static List<String> nullToEmpty(List<String> list) {
        return list == null ? List.of() : list;
    }

    private static List<String> head(List<String> list) {
        return list.size() > MAX_LIST_ITEMS ? list.subList(0, MAX_LIST_ITEMS) : list;
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException e) {
            logger.warn("[DRIFT] Invalid stored detail JSON: {}", e.getMessage());
            return null;
        }
    }
}
