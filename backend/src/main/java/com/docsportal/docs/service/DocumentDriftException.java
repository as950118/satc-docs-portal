package com.docsportal.docs.service;

import org.springframework.http.HttpStatus;

/**
 * 드리프트 검사/본문 조회 실패 (HTTP 상태 포함)
 */
public class DocumentDriftException extends RuntimeException {

    private final HttpStatus status;

    public DocumentDriftException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public DocumentDriftException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
