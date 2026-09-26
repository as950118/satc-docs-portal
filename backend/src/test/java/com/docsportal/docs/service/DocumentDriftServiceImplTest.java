package com.docsportal.docs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.docsportal.docs.format.request.DocumentDriftReportRequest;
import com.docsportal.docs.format.response.DocumentDriftResponse;
import com.docsportal.docs.persistence.DocumentEntity;
import com.docsportal.docs.persistence.DocumentStatus;
import com.docsportal.docs.persistence.DriftStatus;
import com.docsportal.docs.repository.DocsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import okhttp3.Headers;

class DocumentDriftServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-09-26T00:00:00Z");
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private MinioClient minioClient;
    private DocsRepository repository;
    private DocumentDriftServiceImpl service;

    @BeforeEach
    void setUp() {
        minioClient = mock(MinioClient.class);
        repository = mock(DocsRepository.class);
        service = new DocumentDriftServiceImpl(minioClient, repository, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private DocumentEntity document(String extension) {
        DocumentEntity entity = DocumentEntity.builder()
            .id(ID).title("spec").bucket("docs").filePath("documents/" + ID + "/spec." + extension)
            .extension(extension).isFolder(false).status(DocumentStatus.COMPLETE).build();
        when(repository.findById(ID)).thenReturn(Optional.of(entity));
        return entity;
    }

    private static DocumentDriftReportRequest report(int referenced, List<String> missingInCode, List<String> missingInDoc) {
        return new DocumentDriftReportRequest("o/r:backend#java", "c1", referenced, missingInCode, missingInDoc, 2);
    }

    private void stubObject(byte[] bytes) throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(
            new GetObjectResponse(Headers.of(), "docs", "", "o", new ByteArrayInputStream(bytes)));
    }

    @Test
    void returnsTextContent() throws Exception {
        document("md");
        stubObject("GET /api/v1/docs".getBytes(StandardCharsets.UTF_8));

        DocumentDriftService.DocumentContent content = service.getContent(ID);

        assertThat(new String(content.bytes(), StandardCharsets.UTF_8)).isEqualTo("GET /api/v1/docs");
        assertThat(content.contentType()).startsWith("text/plain");
    }

    @Test
    void rejectsBinaryDocumentTypes() throws Exception {
        document("pdf");
        assertThatThrownBy(() -> service.getContent(ID))
            .isInstanceOfSatisfying(DocumentDriftException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
        verify(minioClient, never()).getObject(any(GetObjectArgs.class));
    }

    @Test
    void rejectsTooLargeContent() throws Exception {
        document("md");
        stubObject(new byte[DocumentDriftServiceImpl.MAX_CONTENT_BYTES + 1]);
        assertThatThrownBy(() -> service.getContent(ID))
            .isInstanceOfSatisfying(DocumentDriftException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @Test
    void missingOrDeletedOrFolderIsNotFound() {
        when(repository.findById(ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(ID))
            .isInstanceOfSatisfying(DocumentDriftException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        DocumentEntity deleted = document("md");
        deleted.delete();
        assertThatThrownBy(() -> service.get(ID)).isInstanceOf(DocumentDriftException.class);
    }

    @Test
    void missingInCodeMeansDrifted() {
        DocumentEntity entity = document("md");

        DocumentDriftResponse response = service.report(ID, report(3, List.of("DELETE /api/v1/old"), List.of("GET /api/v1/new")));

        assertThat(response.driftStatus()).isEqualTo(DriftStatus.DRIFTED);
        assertThat(response.checkedAt()).isEqualTo(NOW);
        assertThat(response.driftCommit()).isEqualTo("c1");
        assertThat(response.detail()).containsEntry("referenced", 3)
            .containsEntry("missingInCode", List.of("DELETE /api/v1/old"))
            .containsEntry("unresolvedPaths", 2)
            .containsEntry("truncated", false);
        assertThat(response.synced()).isFalse();
        assertThat(entity.getDriftStatus()).isEqualTo(DriftStatus.DRIFTED);
        verify(repository).save(entity);
    }

    @Test
    void onlyMissingInDocIsStillInSync() {
        document("md");
        assertThat(service.report(ID, report(2, List.of(), List.of("GET /api/v1/new"))).driftStatus())
            .isEqualTo(DriftStatus.IN_SYNC);
    }

    @Test
    void noReferencesIsReportedSeparately() {
        document("md");
        assertThat(service.report(ID, report(0, null, null)).driftStatus())
            .isEqualTo(DriftStatus.NO_API_REFERENCES);
    }

    @Test
    void longListsAreTruncated() {
        document("md");
        List<String> many = IntStream.range(0, 500).mapToObj(i -> "GET /x/" + i).toList();

        DocumentDriftResponse response = service.report(ID, report(500, many, Collections.emptyList()));

        assertThat((List<?>) response.detail().get("missingInCode")).hasSize(DocumentDriftServiceImpl.MAX_LIST_ITEMS);
        assertThat(response.detail()).containsEntry("truncated", true);
    }

    @Test
    void rejectsInvalidReport() {
        document("md");
        assertThatThrownBy(() -> service.report(ID, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.report(ID,
            new DocumentDriftReportRequest("k", "c".repeat(41), 1, null, null, null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.report(ID,
            new DocumentDriftReportRequest("k", "c", -1, null, null, null)))
            .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }
}
