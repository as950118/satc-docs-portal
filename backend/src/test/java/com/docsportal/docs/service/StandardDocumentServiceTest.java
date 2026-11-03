package com.docsportal.docs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.docsportal.docs.format.request.DocumentSyncRequest;
import com.docsportal.docs.format.response.DocumentSyncResponse;
import com.docsportal.docs.format.response.DocumentSyncResponse.Result;
import com.docsportal.docs.persistence.DocumentEntity;
import com.docsportal.docs.persistence.DocumentEvent;
import com.docsportal.docs.persistence.DocumentEventAction;
import com.docsportal.docs.persistence.DocumentStatus;
import com.docsportal.docs.repository.DocsRepository;
import com.docsportal.docs.repository.DocumentEventRepository;

class StandardDocumentServiceTest {

    private final DocumentSyncService sync = mock(DocumentSyncService.class);
    private final DocsRepository docs = mock(DocsRepository.class);
    private final DocumentEventRepository events = mock(DocumentEventRepository.class);
    private final StandardDocumentService service = new StandardDocumentService(sync, docs, events);

    private static DocumentSyncRequest request(String key) {
        return new DocumentSyncRequest(key, "standard-docs", "p", "c", "h", null, "t.md", "text/markdown", new byte[] {1});
    }

    private static DocumentEntity entity() {
        DocumentEntity e = DocumentEntity.builder().id("d1").title("t").status(DocumentStatus.INDEXING).build();
        e.attachSource("standard:k", "standard-docs", "p");
        e.recordActor(7L, "alice", true);
        e.recordActor(8L, "bob", false);
        return e;
    }

    @Test
    void rejectsKeysThatCouldOverwriteCodeSyncedDocuments() {
        assertThatThrownBy(() -> service.register(request("o/r:backend#java"), new UploadActor(7L, "alice")))
            .isInstanceOf(IllegalArgumentException.class);
        verify(sync, never()).sync(any(), any());
    }

    @Test
    void registerReturnsCreatorAndUpdater() {
        when(sync.sync(any(), any())).thenReturn(new DocumentSyncResponse("d1", Result.UPDATED, DocumentStatus.INDEXING));
        when(docs.findById("d1")).thenReturn(Optional.of(entity()));

        var r = service.register(request("standard:k"), new UploadActor(8L, "bob"));

        assertThat(r.result()).isEqualTo("updated");
        assertThat(r.createdBy()).isEqualTo("alice");
        assertThat(r.updatedBy()).isEqualTo("bob");
        assertThat(r.createdByUserId()).isEqualTo(7L);
        assertThat(r.updatedByUserId()).isEqualTo(8L);
    }

    @Test
    void historyListsWhoChangedWhen() {
        when(docs.findBySourceKey("standard:k")).thenReturn(Optional.of(entity()));
        when(events.findByDocumentIdOrderByAtAscIdAsc("d1")).thenReturn(List.of(
            new DocumentEvent("d1", DocumentEventAction.CREATED, 7L, "alice", "h1", "c1", Instant.parse("2026-01-01T00:00:00Z")),
            new DocumentEvent("d1", DocumentEventAction.UPDATED, 8L, "bob", "h2", "c2", Instant.parse("2026-01-02T00:00:00Z"))));

        var h = service.history("standard:k");

        assertThat(h.createdBy()).isEqualTo("alice");
        assertThat(h.createdByUserId()).isEqualTo(7L);
        assertThat(h.updatedByUserId()).isEqualTo(8L);
        assertThat(h.events()).extracting("userId").containsExactly(7L, 8L);
        assertThat(h.events()).extracting("actor").containsExactly("alice", "bob");
        assertThat(h.events()).extracting("action").containsExactly("CREATED", "UPDATED");
    }

    @Test
    void historyOfUnknownDocumentIs404() {
        when(docs.findBySourceKey("standard:none")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.history("standard:none")).isInstanceOf(SpecException.class)
            .satisfies(t -> assertThat(((SpecException) t).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
