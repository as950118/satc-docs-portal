package com.docsportal.docs.controller;

import static com.docsportal.docs.common.Path.API_DOCS_ID;
import static com.docsportal.docs.common.Path.API_DOCS_STATUS;
import static com.docsportal.docs.common.Path.API_DOCS_TREE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.docsportal.docs.common.DocsAccess.Level;
import com.docsportal.docs.config.AuthConfig;
import com.docsportal.docs.controller.DocsEndpoints.Endpoint;
import com.docsportal.docs.persistence.AppUser;
import com.docsportal.docs.persistence.DocumentStatus;
import com.docsportal.docs.repository.AppUserRepository;
import com.docsportal.docs.service.AuthServiceImpl;
import com.docsportal.docs.service.AuthTokens;
import com.docsportal.docs.service.DocumentService;
import com.docsportal.docs.service.DocumentSyncService;
import com.docsportal.docs.service.ExternalService;
import com.docsportal.docs.service.SpecSearchService;
import com.docsportal.docs.service.StandardDocumentService;
import com.jayway.jsonpath.JsonPath;

/**
 * 문서 API 를 실제 MVC 설정(인터셉터·CORS)과 실제 토큰 검증(AuthServiceImpl, AuthTokens)으로 부른다.
 * 계정 저장소와 문서 서비스만 mock 이다.
 */
@WebMvcTest(controllers = {DocumentController.class, DocumentSyncController.class, StandardDocumentController.class},
    properties = {
        "docs.auth.token-secret=test-signing-secret-at-least-32-bytes!!",
        "docs.internal.token=" + DocsApiAuthTest.INTERNAL_TOKEN,
        "docs.sync.token=" + DocsApiAuthTest.SYNC_TOKEN,
        "cors.allowed-origins=" + DocsApiAuthTest.ORIGIN
    })
@Import({AuthServiceImpl.class, AuthConfig.class})
class DocsApiAuthTest {

    static final String INTERNAL_TOKEN = "internal-token-for-tests";
    static final String SYNC_TOKEN = "sync-token-for-tests";
    static final String ORIGIN = "https://docs.example.com";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private AuthTokens tokens;

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

    enum Caller { NONE, BAD_SESSION, VIEWER, WRITER, INTERNAL, WRONG_INTERNAL }

    @BeforeEach
    void accounts() {
        account("viewer", "USER");
        account("uploader", "UPLOADER", "USER");
    }

    private void account(String username, String... roles) {
        AppUser user = new AppUser(username, "{noop}x", null, username, Set.of(roles));
        ReflectionTestUtils.setField(user, "id", (long) username.hashCode());
        when(users.findByUsername(username)).thenReturn(Optional.of(user));
    }

    private MockHttpServletRequestBuilder as(Caller caller, MockHttpServletRequestBuilder request) {
        return switch (caller) {
            case NONE -> request;
            case BAD_SESSION -> request.header("Authorization", "Bearer not-a-jwt");
            case VIEWER -> request.header("Authorization", "Bearer " + tokens.issue("viewer"));
            case WRITER -> request.header("Authorization", "Bearer " + tokens.issue("uploader"));
            case INTERNAL -> request.header("X-Internal-Token", INTERNAL_TOKEN);
            case WRONG_INTERNAL -> request.header("X-Internal-Token", INTERNAL_TOKEN + "x");
        };
    }

    private static int expected(Level level, Caller caller) {
        return switch (level) {
            case READ -> caller == Caller.VIEWER || caller == Caller.WRITER ? 200 : 401;
            case WRITE -> switch (caller) {
                case WRITER -> 200;
                case VIEWER -> 403;
                default -> 401;
            };
            case INTERNAL -> caller == Caller.INTERNAL ? 200 : 401;
            case SYNC -> throw new IllegalArgumentException("sync 는 syncKeepsItsOwnToken 이 다룬다");
        };
    }

    static Stream<Arguments> endpointsByCaller() {
        return DocsEndpoints.ALL.stream()
            .filter(e -> e.level() != Level.SYNC)
            .flatMap(e -> Arrays.stream(Caller.values()).map(c -> Arguments.of(e, c)));
    }

    @ParameterizedTest(name = "{0} as {1}")
    @MethodSource("endpointsByCaller")
    void eachEndpointAdmitsOnlyItsCallers(Endpoint endpoint, Caller caller) throws Exception {
        MvcResult result = mvc.perform(as(caller, endpoint.request().get())).andReturn();

        int expected = expected(endpoint.level(), caller);
        assertThat(result.getResponse().getStatus()).as(endpoint + " as " + caller).isEqualTo(expected);
        if (expected != 200) {
            assertThat(JsonPath.<Integer>read(result.getResponse().getContentAsString(), "$.status"))
                .isEqualTo(expected);
            verifyNoInteractions(documentService, externalService, specSearchService, standardDocumentService,
                documentSyncService);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "PLANNER", "BACKEND", "FRONTEND", "DATA", "REVIEWER", "UPLOADER"})
    void everyWriterRoleMayDelete(String role) throws Exception {
        account("w-" + role, role, "USER");
        mvc.perform(get(API_DOCS_TREE).header("Authorization", "Bearer " + tokens.issue("w-" + role)))
            .andExpect(status().isOk());
        mvc.perform(delete(API_DOCS_ID, "d1")
                .header("Authorization", "Bearer " + tokens.issue("w-" + role)))
            .andExpect(status().isOk());
        verify(documentService).deleteDocument("d1");
    }

    @Test
    void statusCallbackReachesTheServiceWithInternalToken() throws Exception {
        mvc.perform(as(Caller.INTERNAL, DocsEndpoints.ALL.stream()
                .filter(e -> e.pattern().equals(API_DOCS_STATUS)).findFirst().orElseThrow().request().get()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(true));
        verify(documentService).updateDocumentStatus("d1", DocumentStatus.COMPLETE);
    }

    @Test
    void expiredForeignAndOrphanSessionsAre401() throws Exception {
        String expired = tokens.issue("uploader", Duration.ofSeconds(-1), null, null);
        String foreign = new AuthTokens("another-signing-secret-at-least-32-bytes".getBytes(), Duration.ofHours(1),
            Clock.systemUTC()).issue("uploader");
        String orphan = tokens.issue("deleted-user");
        for (String token : new String[] {expired, foreign, orphan}) {
            mvc.perform(get(API_DOCS_TREE).header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(documentService);
    }

    @Test
    void syncKeepsItsOwnToken() throws Exception {
        Endpoint sync = DocsEndpoints.ALL.stream().filter(e -> e.level() == Level.SYNC).findFirst().orElseThrow();
        mvc.perform(sync.request().get().header("X-Docs-Sync-Token", SYNC_TOKEN)).andExpect(status().isOk());
        mvc.perform(sync.request().get().header("X-Docs-Sync-Token", "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(sync.request().get()).andExpect(status().isUnauthorized());
        verify(documentSyncService).sync(any());
    }

    @Test
    void healthCheckStaysOpen() throws Exception {
        mvc.perform(get("/hello")).andExpect(status().isOk());
    }

    @Test
    void browserCanReadTheRejection() throws Exception {
        mvc.perform(options(API_DOCS_TREE).header("Origin", ORIGIN).header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
        mvc.perform(get(API_DOCS_TREE).header("Origin", ORIGIN))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
    }
}
