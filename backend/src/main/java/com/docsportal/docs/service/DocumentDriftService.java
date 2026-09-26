package com.docsportal.docs.service;

import com.docsportal.docs.format.request.DocumentDriftReportRequest;
import com.docsportal.docs.format.response.DocumentDriftResponse;

public interface DocumentDriftService {

    /**
     * 드리프트 검사용 텍스트 본문 (md, txt, yaml, json 등)
     *
     * @throws DocumentDriftException 404 없음 / 415 텍스트 아님 / 413 너무 큼 / 502 스토리지 오류
     */
    DocumentContent getContent(String documentId);

    /**
     * CI 검사 결과 저장. 상태는 서버가 목록으로 판정한다
     */
    DocumentDriftResponse report(String documentId, DocumentDriftReportRequest request);

    DocumentDriftResponse get(String documentId);

    record DocumentContent(String fileName, String contentType, byte[] bytes) {
    }
}
