package com.docsportal.docs.persistence;

/**
 * 문서의 상태를 나타내는 Enum
 */
public enum DocumentStatus {
    /**
     * 문서를 MinIO에 업로드하고 있는 중
     */
    UPLOADING,
    
    /**
     * 문서를 Chunking하여 ES에 적재 중 (추후 구현 예정)
     */
    INDEXING,
    
    /**
     * 모든 과정이 완료된 상태
     */
    COMPLETE,

    /**
     * 인덱싱에 실패한 상태. 파일은 MinIO 에 있어 열어볼 수 있지만 검색에는 잡히지 않을 수 있다.
     * 다시 올리거나 동기화하면 INDEXING 부터 다시 시작한다.
     */
    FAILED
}

