package com.docsportal.docs.service;

import org.springframework.http.HttpStatus;

/**
 * RFC 6749 5.2 / RFC 7591 3.2.2 형식으로 응답할 OAuth 오류.
 */
public class OAuthException extends RuntimeException {

    private final String error;
    private final HttpStatus status;

    public OAuthException(String error, String description, HttpStatus status) {
        super(description);
        this.error = error;
        this.status = status;
    }

    static OAuthException invalidRequest(String description) {
        return new OAuthException("invalid_request", description, HttpStatus.BAD_REQUEST);
    }

    static OAuthException invalidGrant(String description) {
        return new OAuthException("invalid_grant", description, HttpStatus.BAD_REQUEST);
    }

    static OAuthException invalidClient(String description) {
        return new OAuthException("invalid_client", description, HttpStatus.UNAUTHORIZED);
    }

    public static OAuthException rateLimited() {
        return new OAuthException("temporarily_unavailable", "요청이 너무 많습니다. 잠시 후 다시 시도하세요.", HttpStatus.TOO_MANY_REQUESTS);
    }

    public String error() {
        return error;
    }

    public HttpStatus status() {
        return status;
    }
}
