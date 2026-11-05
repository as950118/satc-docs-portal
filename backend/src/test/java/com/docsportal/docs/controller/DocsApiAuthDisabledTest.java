package com.docsportal.docs.controller;

import static com.docsportal.docs.common.Path.API_DOCS_STANDARD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.docsportal.docs.common.DocsAccess.Level;
import com.docsportal.docs.config.AuthConfig;
import com.docsportal.docs.controller.DocsEndpoints.Endpoint;
import com.docsportal.docs.repository.AppUserRepository;
import com.docsportal.docs.service.AuthServiceImpl;
import com.docsportal.docs.service.DocumentService;
import com.docsportal.docs.service.DocumentSyncService;
import com.docsportal.docs.service.ExternalService;
import com.docsportal.docs.service.SpecSearchService;
import com.docsportal.docs.service.StandardDocumentService;

/**
 * DOCS_API_AUTH_REQUIRED=false 는 이 변경 전과 같다. 내부 토큰 없이도 기동하고, DocumentController 는 자격 증명 없이 열리며,
 * 원래 스스로 검증하던 동기화(동기화 토큰)와 표준문서(Bearer)는 그대로 막힌다.
 */
@WebMvcTest(controllers = {DocumentController.class, DocumentSyncController.class, StandardDocumentController.class},
    properties = {
        "docs.api.auth-required=false",
        "docs.auth.token-secret=test-signing-secret-at-least-32-bytes!!",
        "docs.sync.token=sync-token-for-tests",
        "cors.allowed-origins=https://docs.example.com"
    })
@Import({AuthServiceImpl.class, AuthConfig.class})
class DocsApiAuthDisabledTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AppUserRepository users;
    @MockitoBean
    private DocumentService documentService;
    @MockitoBean
    private ExternalService externalService;
    @MockitoBean
    private SpecSearchService specSearchService;
    @MockitoBean
    private StandardDocumentService standardDocumentService;
    @MockitoBean
    private DocumentSyncService documentSyncService;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMappingContext;

    static Stream<Endpoint> documentControllerEndpoints() {
        return DocsEndpoints.ALL.stream()
            .filter(e -> e.level() != Level.SYNC && !e.pattern().startsWith(API_DOCS_STANDARD));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("documentControllerEndpoints")
    void openWithoutCredentials(Endpoint endpoint) throws Exception {
        int status = mvc.perform(endpoint.request().get()).andReturn().getResponse().getStatus();
        assertThat(status).as(endpoint.key()).isEqualTo(200);
    }

    @Test
    void selfCheckedEndpointsStayClosed() throws Exception {
        for (Endpoint endpoint : DocsEndpoints.ALL) {
            if (endpoint.level() == Level.SYNC || endpoint.pattern().startsWith(API_DOCS_STANDARD)) {
                mvc.perform(endpoint.request().get()).andExpect(status().isUnauthorized());
            }
        }
    }
}
