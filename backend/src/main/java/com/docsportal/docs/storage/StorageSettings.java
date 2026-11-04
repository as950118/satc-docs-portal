package com.docsportal.docs.storage;

import java.net.URI;

/**
 * 문서 파일 저장소(S3 호환) 설정. 값의 출처와 검증은 {@code StorageConfig} 가 맡는다.
 *
 * @param endpoint     S3 API 주소. Supabase 처럼 경로가 붙을 수 있다 (https://ref.storage.supabase.co/storage/v1/s3)
 * @param pathStyle    버킷을 호스트가 아니라 경로에 넣는다 (MinIO, Supabase 는 true)
 * @param manageBucket 버킷이 없으면 만든다. Supabase 는 S3 API 로 버킷을 만들 수 없어 false 로 둔다
 * @param publicRead   버킷을 새로 만들 때 익명 읽기(s3:GetObject) 정책을 건다
 * @param asciiKeys    객체 키에 제목 대신 문서 ID 를 쓴다. 한글 키를 거부하는 저장소(Supabase)는 true 로 둔다
 */
public record StorageSettings(URI endpoint, String region, String bucket, String accessKey, String secretKey,
                              boolean pathStyle, boolean manageBucket, boolean publicRead, boolean asciiKeys) {

    /** 업로드 문서의 객체 키. Indexing Server 는 두 번째 경로 조각을 문서 ID 로 읽는다. */
    public String documentKey(String documentId, String title, String extension) {
        return "documents/%s/%s.%s".formatted(documentId, asciiKeys ? documentId : title, extension);
    }

    @Override
    public String toString() {
        return "StorageSettings[endpoint=" + endpoint + ", region=" + region + ", bucket=" + bucket
            + ", pathStyle=" + pathStyle + ", manageBucket=" + manageBucket + ", publicRead=" + publicRead
            + ", asciiKeys=" + asciiKeys + "]";
    }
}
