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

    /**
     * 내용이 바뀐 동기화 문서를 재색인합니다.
     * Indexing Server는 청크를 문서 ID 기준으로 삭제하고 새 청크는 랜덤 ID로 추가하므로,
     * 반드시 "기존 청크 삭제 → 재처리" 순서로 한 비동기 작업 안에서 실행합니다.
     *
     * @param bucketName MinIO 버킷
     * @param documentFileName 삭제 대상 파일명 (파일 아이디+확장자 형태)
     * @param objectName 재처리할 MinIO 객체 경로
     */
    void reindexDocument(String bucketName, String documentFileName, String objectName);
}
