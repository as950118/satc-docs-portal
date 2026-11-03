package com.docsportal.docs.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.docsportal.docs.format.request.DocumentStatusUpdateRequest;
import com.docsportal.docs.persistence.DocumentStatus;
import com.fasterxml.jackson.databind.ObjectMapper;

class DocumentStatusTest {

    @Test
    void 인덱싱_서비스가_보내는_FAILED를_상태_갱신_요청으로_읽는다() throws Exception {
        DocumentStatusUpdateRequest request = new ObjectMapper()
            .readValue("{\"documentId\":\"abc\",\"status\":\"FAILED\"}", DocumentStatusUpdateRequest.class);

        assertThat(request.getStatus()).isEqualTo(DocumentStatus.FAILED);
    }
}
