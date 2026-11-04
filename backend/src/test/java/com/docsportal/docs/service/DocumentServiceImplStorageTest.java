package com.docsportal.docs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.docsportal.docs.format.response.DocumentResponse;
import com.docsportal.docs.live.LiveEventPublisher;
import com.docsportal.docs.persistence.DocumentEntity;
import com.docsportal.docs.persistence.DocumentStatus;
import com.docsportal.docs.repository.DocsRepository;
import com.docsportal.docs.storage.InMemoryObjectStorage;

/** 포털 업로드가 파일을 어느 키에 두고, 그 키와 제목을 DB·Indexing Server 에 어떻게 넘기는지 확인한다. */
class DocumentServiceImplStorageTest {

    private static final byte[] BODY = "# 배포 가이드\n본문".getBytes(StandardCharsets.UTF_8);

    private final InMemoryObjectStorage storage = new InMemoryObjectStorage();
    private final DocsRepository repository = mock(DocsRepository.class);
    private final ExternalService externalService = mock(ExternalService.class);

    private DocumentServiceImpl service(boolean asciiKeys) {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return new DocumentServiceImpl(storage, InMemoryObjectStorage.settings(asciiKeys), repository, externalService,
            mock(LiveEventPublisher.class));
    }

    @Test
    void titleKeysKeepTheTitleInTheObjectKey() {
        DocumentResponse doc = service(false).uploadDocument(BODY, "배포 가이드(v2).md", "text/markdown", null);

        String key = "documents/" + doc.getId() + "/배포 가이드(v2).md";
        assertThat(doc.getFilePath()).isEqualTo(key);
        assertThat(storage.get(key)).isEqualTo(BODY);
        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.INDEXING);
        verify(externalService).notifyDocumentUploaded("docs", key, "배포 가이드(v2)");
    }

    @Test
    void asciiKeysUseOnlyTheDocumentIdAndStillSendTheTitle() {
        DocumentResponse doc = service(true).uploadDocument(BODY, "배포 가이드(v2).md", "text/markdown", null);

        String key = "documents/" + doc.getId() + "/" + doc.getId() + ".md";
        assertThat(doc.getFilePath()).isEqualTo(key).matches("[\\x21-\\x7E]+");
        assertThat(doc.getTitle()).isEqualTo("배포 가이드(v2)");
        assertThat(storage.keys()).containsExactly(key);
        assertThat(storage.get(key)).isEqualTo(BODY);
        verify(externalService).notifyDocumentUploaded("docs", key, "배포 가이드(v2)");
    }

    @Test
    void contentIsReadFromTheStoredKeyAndDeleteRemovesTheObject() {
        DocumentServiceImpl service = service(true);
        DocumentResponse uploaded = service.uploadDocument(BODY, "a.md", "text/markdown", null);
        DocumentEntity entity = DocumentEntity.builder().id(uploaded.getId()).title("a").bucket("docs")
            .filePath(uploaded.getFilePath()).extension("md").isFolder(false).status(DocumentStatus.COMPLETE).build();
        when(repository.findById(uploaded.getId())).thenReturn(Optional.of(entity));
        when(repository.findAllByParentId(uploaded.getId())).thenReturn(List.of());

        assertThat(service.getDocumentContent(uploaded.getId())).isEqualTo("# 배포 가이드\n본문");

        service.deleteDocument(uploaded.getId());

        assertThat(storage.exists(uploaded.getFilePath())).isFalse();
    }

    @Test
    void storageFailureFailsTheUploadAndDoesNotNotify() {
        DocumentServiceImpl service = service(false);
        storage.failWith(new IllegalStateException("storage down"));

        assertThatThrownBy(() -> service.uploadDocument(BODY, "a.md", "text/markdown", null))
            .isInstanceOf(RuntimeException.class);
        verify(externalService, never()).notifyDocumentUploaded(any(), any(), any());
    }
}
