package com.docsportal.docs.service;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.docsportal.docs.repository.DocsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Indexing Server 가 받는 색인 요청 본문을 확인한다. */
class ExternalServiceImplNotifyTest {

    private final RestTemplate restTemplate = new RestTemplate();
    private final MockRestServiceServer indexing = MockRestServiceServer.bindTo(restTemplate).build();
    private final ExternalServiceImpl service = new ExternalServiceImpl(restTemplate, new ObjectMapper(), mock(DocsRepository.class));

    {
        ReflectionTestUtils.setField(service, "apiHost", "http://indexing");
        ReflectionTestUtils.setField(service, "apiPort", 8000);
        ReflectionTestUtils.setField(service, "externalApiEnabled", true);
    }

    @Test
    void uploadNotificationCarriesTheTitle() {
        indexing.expect(requestTo("http://indexing:8000/api/v1/process-document"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""
                {"bucketName":"docs","objectName":"documents/a1/a1.md","title":"배포 가이드(v2)"}""", true))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service.notifyDocumentUploaded("docs", "documents/a1/a1.md", "배포 가이드(v2)");

        indexing.verify();
    }

    @Test
    void reindexDeletesByFileNameThenProcessesWithTheTitle() {
        indexing.expect(requestTo("http://indexing:8000/api/v1/documents"))
            .andExpect(method(HttpMethod.DELETE))
            .andExpect(content().json("{\"files\":[\"a1.md\"]}", true))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        indexing.expect(requestTo("http://indexing:8000/api/v1/process-document"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""
                {"bucketName":"docs","objectName":"documents/a1/a1.md","title":"배포 가이드"}""", true))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service.reindexDocument("docs", "a1.md", "documents/a1/a1.md", "배포 가이드");

        indexing.verify();
    }
}
