package com.docsportal.docs.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import com.docsportal.docs.controller.DocumentController;
import com.docsportal.docs.format.request.DocumentStatusUpdateRequest;
import com.docsportal.docs.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;

class DocsApiAuthConfigTest {

    @Test
    void missingInternalTokenFailsOutsideDevProfile() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("satc");
        assertThatThrownBy(() -> DocsApiAuthConfig.internalToken(" ", prod))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DOCS_INTERNAL_TOKEN")
            .hasMessageContaining("DOCS_API_AUTH_REQUIRED=false");
    }

    @Test
    void devProfileStartsWithoutInternalToken() {
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");
        assertThat(DocsApiAuthConfig.internalToken("", dev)).isEmpty();
    }

    @Test
    void unsetInternalTokenNeverMatchesAnEmptyHeader() throws Exception {
        DocsAccessInterceptor interceptor = new DocsAccessInterceptor(mock(AuthService.class), "", new ObjectMapper());
        HandlerMethod statusCallback = new HandlerMethod(mock(DocumentController.class),
            DocumentController.class.getMethod("updateDocumentStatus", DocumentStatusUpdateRequest.class));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/docs/status");
        request.addHeader(DocsAccessInterceptor.INTERNAL_TOKEN_HEADER, "");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, statusCallback)).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }
}
