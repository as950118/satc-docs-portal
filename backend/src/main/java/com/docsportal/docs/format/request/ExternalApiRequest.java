package com.docsportal.docs.format.request;

/**
 * 문서 업로드 완료 후 Indexing Server 에 보내는 색인 요청 (POST /api/v1/process-document).
 *
 * @param bucketName 저장소 버킷 이름 (ex. docs)
 * @param objectName 저장소 객체 키 (ex. documents/{id}/{제목}.md 또는 documents/{id}/{id}.md)
 * @param title      검색 색인에 쓸 문서 제목. 없으면 Indexing Server 가 objectName 에서 뽑는다
 */
public record ExternalApiRequest(String bucketName, String objectName, String title) {
}
