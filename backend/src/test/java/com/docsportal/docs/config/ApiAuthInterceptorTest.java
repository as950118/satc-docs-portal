package com.docsportal.docs.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.docsportal.docs.common.InternalTokenVerifier;
import com.docsportal.docs.common.SyncTokenVerifier;
import com.docsportal.docs.service.AuthService;
import com.docsportal.docs.service.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;

class ApiAuthInterceptorTest {

    private AuthService authService;
    private ApiAuthInterceptor interceptor;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        when(authService.authenticate(any())).thenReturn(Optional.empty());
        when(authService.authenticate("admin-token"))
            .thenReturn(Optional.of(new AuthenticatedUser("admin", List.of("ADMIN", "USER"))));
        when(authService.authenticate("viewer-token"))
            .thenReturn(Optional.of(new AuthenticatedUser("viewer", List.of("USER"))));
        interceptor = new ApiAuthInterceptor(authService, new SyncTokenVerifier("sync"),
            new InternalTokenVerifier("internal"), new ObjectMapper(), true);
        response = new MockHttpServletResponse();
    }

    private boolean call(String method, String path, String bearer) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (bearer != null) {
            request.addHeader("Authorization", "Bearer " + bearer);
        }
        return interceptor.preHandle(request, response, new Object());
    }

    private boolean callWithHeader(String method, String path, String header, String value) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader(header, value);
        return interceptor.preHandle(request, response, new Object());
    }

    @Test
    void unauthenticatedReadIs401WithJsonBody() throws Exception {
        assertThat(call("GET", "/api/v1/docs/tree", null)).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("\"status\":401");
    }

    @Test
    void invalidTokenIs401() throws Exception {
        assertThat(call("GET", "/api/v1/docs/tree", "forged")).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void anyLoggedInUserCanRead() throws Exception {
        assertThat(call("GET", "/api/v1/docs/tree", "viewer-token")).isTrue();
        assertThat(call("GET", "/api/v1/docs/search", "admin-token")).isTrue();
    }

    @Test
    void writesRequireAdmin() throws Exception {
        assertThat(call("DELETE", "/api/v1/docs/abc", "viewer-token")).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);

        response = new MockHttpServletResponse();
        assertThat(call("POST", "/api/v1/docs", "admin-token")).isTrue();
        assertThat(call("POST", "/api/v1/docs/move", "admin-token")).isTrue();
    }

    @Test
    void preflightPasses() throws Exception {
        assertThat(call("OPTIONS", "/api/v1/docs/tree", null)).isTrue();
    }

    @Test
    void ciEndpointsAreLeftToControllerTokenCheck() throws Exception {
        assertThat(call("PUT", "/api/v1/docs/sync", null)).isTrue();
        assertThat(call("POST", "/api/v1/docs/abc/drift", null)).isTrue();
        // GET /drift 는 화면 배지용 — 세션 필요
        assertThat(call("GET", "/api/v1/docs/abc/drift", null)).isFalse();
    }

    @Test
    void contentAcceptsSyncTokenOrSession() throws Exception {
        assertThat(callWithHeader("GET", "/api/v1/docs/abc/content", SyncTokenVerifier.HEADER, "sync")).isTrue();
        assertThat(call("GET", "/api/v1/docs/abc/content", "viewer-token")).isTrue();
        assertThat(callWithHeader("GET", "/api/v1/docs/abc/content", SyncTokenVerifier.HEADER, "wrong")).isFalse();
    }

    @Test
    void statusCallbackAcceptsInternalTokenOrAdmin() throws Exception {
        assertThat(callWithHeader("POST", "/api/v1/docs/status", InternalTokenVerifier.HEADER, "internal")).isTrue();
        assertThat(call("POST", "/api/v1/docs/status", "admin-token")).isTrue();
        assertThat(callWithHeader("POST", "/api/v1/docs/status", SyncTokenVerifier.HEADER, "sync")).isFalse();
    }

    @Test
    void emptySharedTokensNeverMatch() throws Exception {
        interceptor = new ApiAuthInterceptor(authService, new SyncTokenVerifier(""),
            new InternalTokenVerifier(""), new ObjectMapper(), true);
        assertThat(callWithHeader("POST", "/api/v1/docs/status", InternalTokenVerifier.HEADER, "")).isFalse();
        assertThat(callWithHeader("GET", "/api/v1/docs/abc/content", SyncTokenVerifier.HEADER, "")).isFalse();
    }

    @Test
    void disabledSwitchLetsEverythingThrough() throws Exception {
        interceptor = new ApiAuthInterceptor(authService, new SyncTokenVerifier("sync"),
            new InternalTokenVerifier("internal"), new ObjectMapper(), false);
        assertThat(call("DELETE", "/api/v1/docs/abc", null)).isTrue();
    }
}
