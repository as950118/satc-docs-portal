package com.docsportal.docs.service;

import org.springframework.http.HttpStatus;

/**
 * 스펙 API 오류. 컨트롤러가 status 를 그대로 HTTP 상태로 매핑한다.
 */
public class SpecException extends RuntimeException {

    private final HttpStatus status;

    public SpecException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
