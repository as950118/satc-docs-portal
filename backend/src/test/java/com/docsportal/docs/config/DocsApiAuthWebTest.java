package com.docsportal.docs.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.docsportal.docs.common.InternalTokenVerifier;
import com.docsportal.docs.common.SyncTokenVerifier;
import com.docsportal.docs.controller.DocumentController;
import com.docsportal.docs.service.AuthService;
import com.docsportal.docs.service.AuthenticatedUser;
import com.docsportal.docs.service.DocumentService;
import com.docsportal.docs.service.ExternalService;

/**
 * 인터셉터가 실제 MVC 체인에 연결되는지 + 401 응답에도 CORS 헤더가 붙는지 (ADR-0005)
 */
@WebMvcTest(controllers = DocumentController.class)
@Import({WebMvcAuthConfig.class, SyncTokenVerifier.class, InternalTokenVerifier.class})
@TestPropertySource(properties = {"cors.host=http://localhost:3000", "docs.sync.token=sync", "docs.internal.token=internal"})
class DocsApiAuthWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private DocumentService documentService;
    @MockitoBean
    private ExternalService externalService;
    // DocsApplication 의 @EnableJpaAuditing 이 웹 슬라이스에서도 JPA 메타모델을 요구함
    @MockitoBean
    private JpaMetamodelMappingContext jpaMappingContext;

    @BeforeEach
    void setUp() {
        when(authService.authenticate(any())).thenReturn(Optional.empty());
        when(authService.authenticate("viewer"))
            .thenReturn(Optional.of(new AuthenticatedUser("viewer", List.of("USER"))));
    }

    @Test
    void unauthenticatedRequestGets401WithCorsHeader() throws Exception {
        mvc.perform(get("/api/v1/docs/tree").header("Origin", "http://localhost:3000"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    @Test
    void authenticatedReadReachesController() throws Exception {
        mvc.perform(get("/api/v1/docs/statistics").header("Authorization", "Bearer viewer"))
            .andExpect(status().isOk());
    }

    @Test
    void viewerCannotDelete() throws Exception {
        mvc.perform(delete("/api/v1/docs/abc").header("Authorization", "Bearer viewer"))
            .andExpect(status().isForbidden());
    }

    @Test
    void preflightIsNotBlocked() throws Exception {
        mvc.perform(options("/api/v1/docs/tree")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk());
    }

    @Test
    void helloHealthcheckIsNotProtected() throws Exception {
        mvc.perform(get("/hello")).andExpect(status().isOk());
    }
}
