package com.docsportal.docs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import com.docsportal.docs.format.request.DocumentSyncRequest;
import com.docsportal.docs.format.response.DocumentSyncResponse;
import com.docsportal.docs.persistence.DocumentEntity;
import com.docsportal.docs.persistence.DocumentStatus;
import com.docsportal.docs.repository.DocsRepository;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;

class DocumentSyncServiceImplTest {

    private static final String KEY = "o/r:backend#java";
    private static final Instant NOW = Instant.parse("2026-09-26T00:00:00Z");

    private MinioClient minioClient;
    private DocsRepository repository;
    private ExternalService externalService;
    private DocumentService documentService;
    private DocumentSyncServiceImpl service;

    @BeforeEach
    void setUp() {
        minioClient = mock(MinioClient.class);
        repository = mock(DocsRepository.class);
        externalService = mock(ExternalService.class);
        documentService = mock(DocumentService.class);
        service = new DocumentSyncServiceImpl(minioClient, repository, externalService, documentService,
            Clock.fixed(NOW, ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "bucketName", "docs");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static DocumentSyncRequest request(String content, String commit) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new DocumentSyncRequest(KEY, "o/r", "backend", commit, DocumentSyncServiceImpl.sha256Hex(bytes),
            null, "r-backend-java-openapi.yaml", "application/x-yaml", bytes);
    }

    private static DocumentEntity existing(String content, boolean deleted) {
        DocumentEntity entity = DocumentEntity.builder()
            .id("11111111-1111-1111-1111-111111111111")
            .title("r-backend-java-openapi")
            .bucket("docs")
            .filePath("documents/11111111-1111-1111-1111-111111111111/r-backend-java-openapi.yaml")
            .extension("yaml")
            .isFolder(false)
            .status(DocumentStatus.COMPLETE)
            .build();
        entity.attachSource(KEY, "o/r", "backend");
        entity.markSynced("old", DocumentSyncServiceImpl.sha256Hex(content), Instant.EPOCH);
        if (deleted) {
            entity.delete();
        }
        return entity;
    }

    @Test
    void createsDocumentWhenSourceKeyIsNew() throws Exception {
        when(repository.findBySourceKey(KEY)).thenReturn(Optional.empty());

        DocumentSyncResponse response = service.sync(request("paths: {}\n", "c1"));

        assertThat(response.getResult()).isEqualTo("created");
        assertThat(response.getStatus()).isEqualTo(DocumentStatus.INDEXING);
        ArgumentCaptor<DocumentEntity> saved = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(repository).save(saved.capture());
        DocumentEntity entity = saved.getValue();
        assertThat(entity.getSourceKey()).isEqualTo(KEY);
        assertThat(entity.getSourceCommit()).isEqualTo("c1");
        assertThat(entity.getSyncedAt()).isEqualTo(NOW);
        assertThat(entity.getFilePath()).isEqualTo("documents/" + entity.getId() + "/r-backend-java-openapi.yaml");
        verify(externalService).notifyDocumentUploaded("docs", entity.getFilePath());
    }

    @Test
    void storageFailureOnCreateLeavesNoEntity() throws Exception {
        when(repository.findBySourceKey(KEY)).thenReturn(Optional.empty());
        when(minioClient.putObject(any(PutObjectArgs.class))).thenThrow(new RuntimeException("minio down"));

        assertThatThrownBy(() -> service.sync(request("paths: {}\n", "c1")))
            .isInstanceOf(DocumentSyncStorageException.class);
        verify(repository, never()).save(any());
        verify(externalService, never()).notifyDocumentUploaded(anyString(), anyString());
    }

    @Test
    void sameHashIsUnchangedAndOnlyUpdatesCommit() throws Exception {
        DocumentEntity entity = existing("paths: {}\n", false);
        when(repository.findBySourceKey(KEY)).thenReturn(Optional.of(entity));

        DocumentSyncResponse response = service.sync(request("paths: {}\n", "c2"));

        assertThat(response.getResult()).isEqualTo("unchanged");
        assertThat(response.getStatus()).isEqualTo(DocumentStatus.COMPLETE);
        assertThat(entity.getSourceCommit()).isEqualTo("c2");
        assertThat(entity.getSyncedAt()).isEqualTo(NOW);
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
        verify(externalService, never()).reindexDocument(anyString(), anyString(), anyString());
    }

    @Test
    void changedHashOverwritesSamePathKeepsIdAndReindexes() throws Exception {
        DocumentEntity entity = existing("paths: {}\n", false);
        String originalPath = entity.getFilePath();
        when(repository.findBySourceKey(KEY)).thenReturn(Optional.of(entity));

        DocumentSyncResponse response = service.sync(request("paths:\n  /a: {}\n", "c2"));

        assertThat(response.getResult()).isEqualTo("updated");
        assertThat(response.getDocumentId()).isEqualTo(entity.getId());
        assertThat(entity.getFilePath()).isEqualTo(originalPath);
        assertThat(entity.getStatus()).isEqualTo(DocumentStatus.INDEXING);
        assertThat(entity.getContentHash()).isEqualTo(DocumentSyncServiceImpl.sha256Hex("paths:\n  /a: {}\n"));
        InOrder order = inOrder(minioClient, repository, externalService);
        order.verify(minioClient).putObject(any(PutObjectArgs.class));
        order.verify(repository).save(entity);
        order.verify(externalService).reindexDocument("docs", entity.getId() + ".yaml", originalPath);
    }

    @Test
    void storageFailureOnUpdateKeepsPreviousState() throws Exception {
        DocumentEntity entity = existing("paths: {}\n", false);
        String previousHash = entity.getContentHash();
        when(repository.findBySourceKey(KEY)).thenReturn(Optional.of(entity));
        when(minioClient.putObject(any(PutObjectArgs.class))).thenThrow(new RuntimeException("minio down"));

        assertThatThrownBy(() -> service.sync(request("paths:\n  /a: {}\n", "c2")))
            .isInstanceOf(DocumentSyncStorageException.class);
        assertThat(entity.getContentHash()).isEqualTo(previousHash);
        assertThat(entity.getSourceCommit()).isEqualTo("old");
        assertThat(entity.getStatus()).isEqualTo(DocumentStatus.COMPLETE);
        verify(repository, never()).save(any());
        verify(externalService, never()).reindexDocument(anyString(), anyString(), anyString());
    }

    @Test
    void deletedDocumentReleasesKeyAndCreatesNewOne() {
        DocumentEntity deleted = existing("paths: {}\n", true);
        when(repository.findBySourceKey(KEY)).thenReturn(Optional.of(deleted));

        DocumentSyncResponse response = service.sync(request("paths: {}\n", "c2"));

        assertThat(response.getResult()).isEqualTo("created");
        assertThat(response.getDocumentId()).isNotEqualTo(deleted.getId());
        assertThat(deleted.getSourceKey()).isNull();
        verify(repository).saveAndFlush(deleted);
    }

    @Test
    void rejectsHashThatDoesNotMatchContent() {
        byte[] bytes = "paths: {}\n".getBytes(StandardCharsets.UTF_8);
        DocumentSyncRequest tampered = new DocumentSyncRequest(KEY, "o/r", "backend", "c1",
            DocumentSyncServiceImpl.sha256Hex("other"), null, "a.yaml", null, bytes);

        assertThatThrownBy(() -> service.sync(tampered))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("contentHash");
        verify(repository, never()).findBySourceKey(anyString());
    }

    @Test
    void rejectsMissingFieldsAndLongCommit() {
        byte[] bytes = "x".getBytes(StandardCharsets.UTF_8);
        String hash = DocumentSyncServiceImpl.sha256Hex(bytes);
        assertThatThrownBy(() -> service.sync(new DocumentSyncRequest(" ", "o/r", "p", "c", hash, null, "a.yaml", null, bytes)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.sync(new DocumentSyncRequest(KEY, "o/r", "p", "c".repeat(41), hash, null, "a.yaml", null, bytes)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.sync(new DocumentSyncRequest(KEY, "o/r", "p", "c", hash, null, "noext", null, bytes)))
            .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).findBySourceKey(eq(KEY));
    }
}
