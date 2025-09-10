package com.okestro.docs.persistence;

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
    COMPLETE
}

