package com.docsportal.docs.service;

import com.docsportal.docs.format.response.SearchResultResponse;

/**
 * 통합 외부 API 호출 서비스 인터페이스
 */
public interface ExternalService {
    
    /**
     * 문서 업로드 완료 후 외부 API를 호출합니다.
     * 
     * @param bucketName MinIO 버킷 이름
     * @param objectName MinIO Object 이름
     */
    void notifyDocumentUploaded(String bucketName, String objectName);
    
    /**
     * 외부 검색 API를 호출하여 문서를 검색합니다.
     * 
     * @param query 검색 쿼리
     * @return 포맷팅된 검색 결과
     */
    SearchResultResponse searchDocuments(String query);
    
    /**
     * 문서 삭제 후 외부 Indexing Server에 삭제된 문서 목록을 전달합니다.
     * 
     * @param deletedFileNames 삭제된 파일명 목록 (파일 아이디+확장자 형태)
     */
    void notifyDocumentsDeleted(java.util.List<String> deletedFileNames);
}
