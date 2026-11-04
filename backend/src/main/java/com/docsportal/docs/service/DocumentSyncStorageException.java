package com.docsportal.docs.service;

/**
 * 동기화 중 스토리지 저장 실패
 */
public class DocumentSyncStorageException extends RuntimeException {

    public DocumentSyncStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
