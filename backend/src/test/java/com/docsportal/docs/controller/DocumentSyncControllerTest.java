package com.docsportal.docs.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.docsportal.docs.common.RestResponse;
import com.docsportal.docs.format.response.DocumentSyncResponse;
import com.docsportal.docs.format.response.DocumentSyncResponse.Result;
import com.docsportal.docs.persistence.DocumentStatus;
import com.docsportal.docs.service.DocumentSyncService;
import com.docsportal.docs.service.DocumentSyncStorageException;

class DocumentSyncControllerTest {

    private final DocumentSyncService service = mock(DocumentSyncService.class);
    private final MockMultipartFile file = new MockMultipartFile("file", "a.yaml", "application/x-yaml", "x".getBytes());

    private ResponseEntity<RestResponse<DocumentSyncResponse>> call(String configuredToken, String headerToken) {
        return new DocumentSyncController(service, configuredToken)
            .sync(headerToken, file, "o/r:.#java", "o/r", ".", "c1", "h", null);
    }

    @Test
    void disabledWhenTokenNotConfigured() {
        assertThat(call("", "anything").getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(service, never()).sync(any());
    }

    @Test
    void rejectsMissingOrWrongToken() {
        assertThat(call("secret", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call("secret", "wrong").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(service, never()).sync(any());
    }

    @Test
    void returnsOkWithBodyStatus200() {
        when(service.sync(any())).thenReturn(new DocumentSyncResponse("d1", Result.CREATED, DocumentStatus.INDEXING));

        ResponseEntity<RestResponse<DocumentSyncResponse>> response = call("secret", "secret");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getStatus()).isEqualTo(200);
        assertThat(response.getBody().getData().getResult()).isEqualTo("created");
    }

    @Test
    void mapsServiceErrorsToHttpStatus() {
        doThrow(new IllegalArgumentException("bad")).when(service).sync(any());
        assertThat(call("secret", "secret").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        doThrow(new DataIntegrityViolationException("dup")).when(service).sync(any());
        assertThat(call("secret", "secret").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        doThrow(new DocumentSyncStorageException("minio", null)).when(service).sync(any());
        ResponseEntity<RestResponse<DocumentSyncResponse>> response = call("secret", "secret");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().getStatus()).isEqualTo(502);
    }
}
