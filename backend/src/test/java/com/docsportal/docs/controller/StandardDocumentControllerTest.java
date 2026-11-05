package com.docsportal.docs.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import com.docsportal.docs.common.Roles;
import com.docsportal.docs.format.request.DocumentSyncRequest;
import com.docsportal.docs.format.response.StandardDocumentResponse;
import com.docsportal.docs.format.response.UserInfoResponse;
import com.docsportal.docs.persistence.AppUser;
import com.docsportal.docs.persistence.DocumentStatus;
import com.docsportal.docs.repository.AppUserRepository;
import com.docsportal.docs.service.AuthService;
import com.docsportal.docs.service.SpecException;
import com.docsportal.docs.service.StandardDocumentService;
import com.docsportal.docs.service.UploadActor;

class StandardDocumentControllerTest {

    private final StandardDocumentService service = mock(StandardDocumentService.class);
    private final AuthService auth = mock(AuthService.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final StandardDocumentController controller = new StandardDocumentController(service, auth, users);
    private final MockMultipartFile file = new MockMultipartFile("file", "[표준문서] t.md", "text/markdown", "x".getBytes());

    private void login(String token, String username, String... roles) {
        when(auth.getCurrentUser(token)).thenReturn(
            new UserInfoResponse(username, username, null, username, List.of(roles), null, null));
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(42L);
        when(users.findByUsername(username)).thenReturn(java.util.Optional.of(user));
    }

    private void register(String authorization) throws Exception {
        controller.register(authorization, file, "standard:https://x/y", "standard-docs", "https://x/y", "c", "h", null);
    }

    private static HttpStatus statusOf(Throwable t) {
        return ((SpecException) t).getStatus();
    }

    @Test
    void requiresBearerSession() {
        assertThatThrownBy(() -> register(null)).satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.UNAUTHORIZED));
        when(auth.getCurrentUser("bad")).thenThrow(new IllegalArgumentException("만료"));
        assertThatThrownBy(() -> register("Bearer bad")).satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(service, never()).register(any(), any());
    }

    @Test
    void viewerOnlyAccountIsForbidden() {
        login("v", "viewer", "USER");
        assertThatThrownBy(() -> register("Bearer v")).satisfies(t -> assertThat(statusOf(t)).isEqualTo(HttpStatus.FORBIDDEN));
        verify(service, never()).register(any(), any());
    }

    @Test
    void developerRegistersWithOwnUsernameAsActor() throws Exception {
        login("b", "backend", "BACKEND", "USER");
        when(service.register(any(), eq(new UploadActor(42L, "backend")))).thenReturn(new StandardDocumentResponse.Registered(
            "d1", "created", DocumentStatus.INDEXING, 42L, "backend", 42L, "backend", Instant.EPOCH));

        var response = controller.register("Bearer b", file, "standard:https://x/y", "standard-docs", "https://x/y", "c", "h", "F1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().createdBy()).isEqualTo("backend");
        ArgumentCaptor<DocumentSyncRequest> request = ArgumentCaptor.forClass(DocumentSyncRequest.class);
        verify(service).register(request.capture(), eq(new UploadActor(42L, "backend")));
        assertThat(request.getValue().parentId()).isEqualTo("F1");
        assertThat(request.getValue().fileName()).isEqualTo("[표준문서] t.md");
    }

    @Test
    void uploaderRoleAloneMayRegisterAndCombinesWithOthers() throws Exception {
        login("u", "uploader", "UPLOADER", "USER");
        when(service.register(any(), any())).thenReturn(new StandardDocumentResponse.Registered(
            "d1", "created", DocumentStatus.INDEXING, 42L, "uploader", 42L, "uploader", Instant.EPOCH));
        assertThat(controller.register("Bearer u", file, "standard:k", "r", "p", "c", "h", null).getStatusCode())
            .isEqualTo(HttpStatus.OK);
        assertThat(Roles.DOCUMENT_WRITERS).contains("UPLOADER", "ADMIN", "BACKEND", "REVIEWER");
    }

    @Test
    void historyRequiresSessionButNotWriterRole() {
        login("v", "viewer", "USER");
        when(service.history("standard:k")).thenReturn(new StandardDocumentResponse.History(
            "d1", "t", "standard:k", 1L, "a", Instant.EPOCH, 2L, "b", Instant.EPOCH, List.of()));
        assertThat(controller.history("Bearer v", "standard:k").getBody().getData().updatedBy()).isEqualTo("b");
        assertThatThrownBy(() -> controller.history(null, "standard:k")).isInstanceOf(SpecException.class);
    }

    @Test
    void mapsIllegalArgumentTo400() {
        assertThat(controller.handleBadRequest(new IllegalArgumentException("bad")).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
