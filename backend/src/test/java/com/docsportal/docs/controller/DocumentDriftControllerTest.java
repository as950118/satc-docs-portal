package com.docsportal.docs.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.docsportal.docs.common.SyncTokenVerifier;
import com.docsportal.docs.format.request.DocumentDriftReportRequest;
import com.docsportal.docs.format.response.DocumentDriftResponse;
import com.docsportal.docs.persistence.DriftStatus;
import com.docsportal.docs.service.DocumentDriftException;
import com.docsportal.docs.service.DocumentDriftService;
import com.docsportal.docs.service.DocumentDriftService.DocumentContent;

class DocumentDriftControllerTest {

    private final DocumentDriftService service = mock(DocumentDriftService.class);
    private final DocumentDriftReportRequest request =
        new DocumentDriftReportRequest("k", "c1", 1, List.of(), List.of(), 0);

    private DocumentDriftController controller(String token) {
        return new DocumentDriftController(service, new SyncTokenVerifier(token));
    }

    @Test
    void reportRequiresToken() {
        assertThat(controller("").report("x", "id", request).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(controller("secret").report(null, "id", request).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller("secret").report("nope", "id", request).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(service, never()).report(any(), any());
    }

    @Test
    void reportReturnsStoredState() {
        DocumentDriftResponse stored = new DocumentDriftResponse("id", DriftStatus.IN_SYNC, null, "k", "c1", null,
            false, null, null, null);
        when(service.report(eq("id"), any())).thenReturn(stored);

        var response = controller("secret").report("secret", "id", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().driftStatus()).isEqualTo(DriftStatus.IN_SYNC);
    }

    @Test
    void mapsErrors() {
        doThrow(new IllegalArgumentException("bad")).when(service).report(eq("id"), any());
        assertThat(controller("secret").report("secret", "id", request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        doThrow(new DocumentDriftException(HttpStatus.NOT_FOUND, "none")).when(service).get("id");
        assertThat(controller("secret").get("id").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        doThrow(new DocumentDriftException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "pdf")).when(service).getContent("id");
        assertThat(controller("secret").getContent("id").getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void contentIsServedWithType() {
        when(service.getContent("id")).thenReturn(new DocumentContent("a.md", "text/plain; charset=UTF-8", "x".getBytes()));

        ResponseEntity<?> response = controller("").getContent("id");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_PLAIN)).isTrue();
    }
}
