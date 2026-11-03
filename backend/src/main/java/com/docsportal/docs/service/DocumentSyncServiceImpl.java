package com.docsportal.docs.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.docsportal.docs.format.request.DocumentSyncRequest;
import com.docsportal.docs.format.response.DocumentSyncResponse;
import com.docsportal.docs.format.response.DocumentSyncResponse.Result;
import com.docsportal.docs.live.LiveEventPublisher;
import com.docsportal.docs.live.LiveEventType;
import com.docsportal.docs.persistence.DocumentEntity;
import com.docsportal.docs.persistence.DocumentEvent;
import com.docsportal.docs.persistence.DocumentEventAction;
import com.docsportal.docs.repository.DocumentEventRepository;
import com.docsportal.docs.persistence.DocumentStatus;
import com.docsportal.docs.repository.DocsRepository;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;

@Service
public class DocumentSyncServiceImpl implements DocumentSyncService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentSyncServiceImpl.class);
    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-f]{64}$");
    private static final int MAX_COMMIT_LENGTH = 40;

    private final MinioClient minioClient;
    private final DocsRepository docsRepository;
    private final ExternalService externalService;
    private final DocumentService documentService;
    private final Clock clock;
    private final LiveEventPublisher liveEvents;
    private final DocumentEventRepository eventRepository;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Autowired
    public DocumentSyncServiceImpl(MinioClient minioClient, DocsRepository docsRepository,
                                   ExternalService externalService, DocumentService documentService,
                                   LiveEventPublisher liveEvents, DocumentEventRepository eventRepository) {
        this(minioClient, docsRepository, externalService, documentService, liveEvents, eventRepository, Clock.systemUTC());
    }

    DocumentSyncServiceImpl(MinioClient minioClient, DocsRepository docsRepository,
                            ExternalService externalService, DocumentService documentService,
                            LiveEventPublisher liveEvents, DocumentEventRepository eventRepository, Clock clock) {
        this.eventRepository = eventRepository;
        this.minioClient = minioClient;
        this.docsRepository = docsRepository;
        this.externalService = externalService;
        this.documentService = documentService;
        this.clock = clock;
        this.liveEvents = liveEvents;
    }

    @Override
    public DocumentSyncResponse sync(DocumentSyncRequest request, UploadActor actor) {
        validate(request);
        Instant now = Instant.now(clock);

        Optional<DocumentEntity> found = docsRepository.findBySourceKey(request.sourceKey());

        // 포털에서 삭제된 동기화 문서: 기존 레코드는 삭제 상태로 두고 새 문서로 다시 만든다
        if (found.isPresent() && Boolean.TRUE.equals(found.get().getDeleted())) {
            DocumentEntity deleted = found.get();
            deleted.releaseSourceKey();
            docsRepository.saveAndFlush(deleted);
            logger.info("[SYNC] Released sourceKey of deleted document {}: {}", deleted.getId(), request.sourceKey());
            found = Optional.empty();
        }

        if (found.isEmpty()) {
            return create(request, now, actor);
        }

        DocumentEntity document = found.get();
        if (request.contentHash().equals(document.getContentHash())) {
            document.markSynced(request.sourceCommit(), request.contentHash(), now);
            docsRepository.save(document);
            logger.info("[SYNC] Unchanged: {} ({})", request.sourceKey(), document.getId());
            return new DocumentSyncResponse(document.getId(), Result.UNCHANGED, document.getStatus());
        }

        // 같은 경로에 덮어쓴다 — putObject 실패 시 기존 객체·엔티티는 그대로 남는다
        putObject(document.getFilePath(), request);
        document.markSynced(request.sourceCommit(), request.contentHash(), now);
        document.updateStatus(DocumentStatus.INDEXING);
        if (actor != null) {
            document.recordActor(actor.userId(), actor.username(), false);
            eventRepository.save(new DocumentEvent(document.getId(), DocumentEventAction.UPDATED, actor.userId(), actor.username(),
                request.contentHash(), request.sourceCommit(), now));
        }
        docsRepository.save(document);

        externalService.reindexDocument(bucketName, document.getId() + "." + document.getExtension(), document.getFilePath());
        liveEvents.publish(LiveEventType.DOCUMENT_UPDATED, document.getId(), document.getTitle(), document.getParentId(),
            DocumentStatus.INDEXING.name(), null);
        logger.info("[SYNC] Updated: {} ({})", request.sourceKey(), document.getId());
        return new DocumentSyncResponse(document.getId(), Result.UPDATED, DocumentStatus.INDEXING);
    }

    private DocumentSyncResponse create(DocumentSyncRequest request, Instant now, UploadActor actor) {
        documentService.createBucketIfNotExists();

        String documentId = UUID.randomUUID().toString();
        String title = Normalizer.normalize(removeExtension(request.fileName()), Normalizer.Form.NFC);
        String extension = extensionOf(request.fileName());
        // DocumentServiceImpl 과 동일한 경로 규칙 (Indexing Server가 경로에서 문서 ID를 추출함)
        String filePath = String.format("documents/%s/%s.%s", documentId, title, extension);

        // 저장소 먼저 — 실패하면 DB에 아무것도 남기지 않는다
        putObject(filePath, request);

        String parentId = (request.parentId() == null || request.parentId().isBlank()) ? null : request.parentId();
        DocumentEntity document = DocumentEntity.builder()
            .id(documentId)
            .parentId(parentId)
            .title(title)
            .bucket(bucketName)
            .filePath(filePath)
            .extension(extension)
            .isFolder(false)
            .status(DocumentStatus.INDEXING)
            .build();
        document.attachSource(request.sourceKey(), request.sourceRepo(), request.sourcePath());
        document.markSynced(request.sourceCommit(), request.contentHash(), now);
        if (actor != null) {
            document.recordActor(actor.userId(), actor.username(), true);
            eventRepository.save(new DocumentEvent(documentId, DocumentEventAction.CREATED, actor.userId(), actor.username(),
                request.contentHash(), request.sourceCommit(), now));
        }
        docsRepository.save(document);

        externalService.notifyDocumentUploaded(bucketName, filePath);
        liveEvents.publish(LiveEventType.DOCUMENT_CREATED, documentId, title, parentId,
            DocumentStatus.INDEXING.name(), null);
        logger.info("[SYNC] Created: {} ({})", request.sourceKey(), documentId);
        return new DocumentSyncResponse(documentId, Result.CREATED, DocumentStatus.INDEXING);
    }

    private void putObject(String filePath, DocumentSyncRequest request) {
        byte[] bytes = request.fileBytes();
        try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(filePath)
                    .stream(inputStream, bytes.length, -1)
                    .contentType(request.contentType() == null ? "application/x-yaml" : request.contentType())
                    .build()
            );
        } catch (Exception e) {
            logger.error("[SYNC] Storage upload failed: {} - {}", filePath, e.getMessage(), e);
            throw new DocumentSyncStorageException("스토리지 저장에 실패했습니다: " + e.getMessage(), e);
        }
    }

    private void validate(DocumentSyncRequest request) {
        requireText(request.sourceKey(), "sourceKey");
        requireText(request.sourceRepo(), "sourceRepo");
        requireText(request.sourcePath(), "sourcePath");
        requireText(request.sourceCommit(), "sourceCommit");
        requireText(request.contentHash(), "contentHash");
        requireText(request.fileName(), "file 이름");
        if (request.sourceCommit().length() > MAX_COMMIT_LENGTH) {
            throw new IllegalArgumentException("sourceCommit 은 " + MAX_COMMIT_LENGTH + "자 이하여야 합니다.");
        }
        if (!SHA256_HEX.matcher(request.contentHash()).matches()) {
            throw new IllegalArgumentException("contentHash 는 소문자 sha256 hex 여야 합니다.");
        }
        if (extensionOf(request.fileName()).isEmpty()) {
            throw new IllegalArgumentException("file 이름에 확장자가 필요합니다.");
        }
        if (request.fileBytes() == null || request.fileBytes().length == 0) {
            throw new IllegalArgumentException("파일이 비어있습니다.");
        }
        String actual = sha256Hex(request.fileBytes());
        if (!actual.equals(request.contentHash())) {
            throw new IllegalArgumentException("contentHash 가 파일 내용과 일치하지 않습니다.");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 이(가) 필요합니다.");
        }
    }

    static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256Hex(String text) {
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot == -1 ? "" : fileName.substring(dot + 1);
    }

    private static String removeExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot == -1 ? fileName : fileName.substring(0, dot);
    }
}
