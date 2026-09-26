package com.docsportal.docs.service;

import com.docsportal.docs.format.request.DocumentSyncRequest;
import com.docsportal.docs.format.response.DocumentSyncResponse;

public interface DocumentSyncService {

    /**
     * sourceKey 기준으로 문서를 생성/교체/유지합니다 (ADR-0003).
     * 스토리지 저장까지 동기 처리하며, 실패 시 기존 문서는 변경되지 않습니다.
     *
     * @throws IllegalArgumentException 요청 값이 잘못된 경우
     * @throws DocumentSyncStorageException 스토리지(MinIO) 저장 실패
     */
    DocumentSyncResponse sync(DocumentSyncRequest request);
}
