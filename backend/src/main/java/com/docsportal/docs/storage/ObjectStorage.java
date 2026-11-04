package com.docsportal.docs.storage;

/**
 * 문서 파일을 담는 버킷 하나. 구현은 S3 호환 저장소(MinIO, Supabase Storage, Cloudflare R2)를 쓴다.
 * 실패는 RuntimeException 으로 알린다.
 */
public interface ObjectStorage {

    /** 설정이 허용하면 버킷이 없을 때 만든다. 저장소에 닿지 못해도 예외 없이 로그만 남긴다. */
    void ensureBucket();

    void put(String key, byte[] content, String contentType);

    byte[] get(String key);

    boolean exists(String key);

    void delete(String key);
}
