package com.docsportal.docs.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;

import com.docsportal.docs.live.LiveEventPublisher;
import com.docsportal.docs.live.LiveEventType;
import com.docsportal.docs.live.SseHub;
import com.docsportal.docs.repository.DocsRepository;
import com.docsportal.docs.service.DocumentServiceImpl;
import com.docsportal.docs.service.ExternalService;
import com.docsportal.docs.service.SpecExportService;
import com.docsportal.docs.service.SpecSearchService;
import com.docsportal.docs.storage.InMemoryObjectStorage;
import com.docsportal.docs.storage.ObjectStorage;
import com.docsportal.docs.storage.StorageSettings;

/**
 * GET /api/v1/events 를 실제 SseHub·서비스·H2 와 함께 확인한다. MockMvc 가 비동기로 시작한 응답에 쌓이는
 * 바이트를 읽어 SSE 프레임을 검사한다 (스트림이 끝나지 않으므로 폴링).
 */
@Import({DocumentServiceImpl.class, TransactionTemplateConfig.class, LiveEventsApiTest.Storage.class})
class LiveEventsApiTest extends SpecApiTestSupport {

    @TestConfiguration
    static class Storage {
        @Bean
        ObjectStorage objectStorage() {
            return new InMemoryObjectStorage();
        }

        @Bean
        StorageSettings storageSettings() {
            return InMemoryObjectStorage.settings(false);
        }
    }

    @MockitoBean
    ExternalService externalService;
    @MockitoBean
    SpecSearchService specSearchService;

    @Autowired
    DocumentServiceImpl documentService;
    @Autowired
    DocsRepository docs;
    @Autowired
    SseHub hub;
    @Autowired
    LiveEventPublisher publisher;
    @Autowired
    TransactionTemplate tx;

    MockMvc all;

    @BeforeEach
    void setUpAll() {
        docs.deleteAll();
        all = MockMvcBuilders.standaloneSetup(
            new SpecController(specService, new SpecExportService(documents), auth),
            new DocumentController(documentService, externalService, specSearchService),
            new LiveEventsController(hub))
            // Spring Boot 는 String 변환기를 UTF-8 로 등록한다. standalone 은 기본값이 ISO-8859-1 이라 맞춰 준다
            .setMessageConverters(new org.springframework.http.converter.StringHttpMessageConverter(
                    java.nio.charset.StandardCharsets.UTF_8),
                new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter())
            .build();
    }

    private MvcResult subscribe() throws Exception {
        MvcResult result = all.perform(get("/api/v1/events").accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(request().asyncStarted())
            .andExpect(status().isOk())
            .andReturn();
        awaitContent(result, "event:hello");
        return result;
    }

    private static String awaitContent(MvcResult result, String needle) throws Exception {
        MockHttpServletResponse response = result.getResponse();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            String text = response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            if (text.contains(needle)) {
                return text;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("SSE 응답에 '" + needle + "' 가 없습니다: " + response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void streamsWithEventStreamContentTypeAndHelloFirst() throws Exception {
        MvcResult result = subscribe();

        assertThat(result.getResponse().getContentType()).startsWith("text/event-stream");
        String text = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(text).startsWith("event:hello");
        assertThat(text).contains("\"type\":\"hello\"");
    }

    @Test
    void folderMoveStatusDeleteReachSubscribers() throws Exception {
        MvcResult stream = subscribe();

        all.perform(post("/api/v1/docs/folder").param("title", "\ud3f4\ub354").header("X-Client-Id", "abc"))
            .andExpect(status().isOk());
        String created = awaitContent(stream, "event:document.created");
        assertThat(created).contains("\"title\":\"\ud3f4\ub354\"").contains("\"originId\":\"abc\"").contains("\"status\":\"COMPLETE\"");
        assertThat(created).doesNotContain("\"body\"").doesNotContain("\"content\"");
        String folderId = docs.findAll().get(0).getId();

        com.docsportal.docs.persistence.DocumentEntity file = com.docsportal.docs.persistence.DocumentEntity.builder()
            .id("f1").title("doc").bucket("docs").filePath("documents/f1/doc.md").extension("md").isFolder(false)
            .status(com.docsportal.docs.persistence.DocumentStatus.COMPLETE).build();
        docs.save(file);

        all.perform(post("/api/v1/docs/move").contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentIds\":[\"f1\"],\"targetParentId\":\"" + folderId + "\"}"))
            .andExpect(status().isOk());
        String moved = awaitContent(stream, "event:document.moved");
        assertThat(moved).contains("\"id\":\"f1\"").contains("\"parentId\":\"" + folderId + "\"");

        all.perform(post("/api/v1/docs/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentId\":\"f1\",\"status\":\"INDEXING\"}"))
            .andExpect(status().isOk());
        assertThat(awaitContent(stream, "event:document.status")).contains("\"status\":\"INDEXING\"");

        all.perform(delete("/api/v1/docs/" + folderId)).andExpect(status().isOk());
        // 폴더 아래 문서와 폴더 자신 둘 다 삭제 이벤트
        awaitContent(stream, "\"type\":\"document.deleted\",\"id\":\"f1\"");
        awaitContent(stream, "\"type\":\"document.deleted\",\"id\":\"" + folderId + "\"");
    }

    @Test
    void asyncUploadCarriesOriginIdPassedFromController() throws Exception {
        MvcResult stream = subscribe();

        all.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/docs")
                .file(new org.springframework.mock.web.MockMultipartFile("file", "a.md", "text/markdown", "hi".getBytes()))
                .header("X-Client-Id", "up-1"))
            .andExpect(status().isOk());

        String text = awaitContent(stream, "event:document.created");
        assertThat(text).contains("\"originId\":\"up-1\"").contains("\"title\":\"a\"").contains("\"status\":\"INDEXING\"");
        assertThat(text).doesNotContain("\"body\"").doesNotContain("\"content\"");
    }

    @Test
    void specCreateAndApproveEmitSpecChangedWithActor() throws Exception {
        MvcResult stream = subscribe();
        String planner = login("planner");

        String id = idOf(all.perform(post("/api/v1/specs").header("Authorization", planner)
            .header("X-Client-Id", "abc").contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"new PRD\",\"body\":\"secret-body\"}")));
        String created = awaitContent(stream, "event:spec.changed");
        assertThat(created).contains("\"id\":\"" + id + "\"").contains("\"actor\":\"planner\"")
            .contains("\"status\":\"DRAFT\"").contains("\"originId\":\"abc\"");
        assertThat(created).doesNotContain("secret-body");

        all.perform(post("/api/v1/specs/" + id + "/approve").header("Authorization", planner)).andExpect(status().isOk());
        assertThat(awaitContent(stream, "\"status\":\"PRD_APPROVED\"")).contains("\"actor\":\"planner\"");
    }

    @Test
    void specEditEmitsSpecChanged() throws Exception {
        String planner = login("planner");
        String id = idOf(postJson("/api/v1/specs", planner, "{\"title\":\"t\",\"body\":\"b\"}"));
        MvcResult stream = subscribe();

        all.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/specs/" + id)
                .header("Authorization", planner).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"t2\",\"body\":\"b2\"}"))
            .andExpect(status().isOk());

        assertThat(awaitContent(stream, "event:spec.changed")).contains("\"title\":\"t2\"");
    }

    @Test
    void rolledBackChangesAreNotPublishedButCommittedOnesAre() throws Exception {
        MvcResult stream = subscribe();

        tx.executeWithoutResult(status -> {
            publisher.publish(LiveEventType.DOCUMENT_STATUS, "rolled-back", "t", null, "COMPLETE", null, null);
            status.setRollbackOnly();
        });
        tx.executeWithoutResult(status ->
            publisher.publish(LiveEventType.DOCUMENT_STATUS, "committed", "t", null, "COMPLETE", null, null));

        String text = awaitContent(stream, "\"id\":\"committed\"");
        assertThat(text).doesNotContain("rolled-back");
    }

    @Test
    void returns503WhenConnectionLimitExceeded() throws Exception {
        SseHub small = new SseHub(1, 60_000L, Runnable::run);
        MockMvc limited = MockMvcBuilders.standaloneSetup(new LiveEventsController(small)).build();
        limited.perform(get("/api/v1/events")).andExpect(request().asyncStarted());

        limited.perform(get("/api/v1/events")).andExpect(status().isServiceUnavailable());
    }
}
