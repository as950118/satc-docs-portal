package com.docsportal.docs.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 내부 서비스(인덱싱 서비스 → 상태 콜백) 토큰 (ADR-0005)
 */
@Component
public class InternalTokenVerifier extends SharedTokenVerifier {

    public static final String HEADER = "X-Docs-Internal-Token";

    public InternalTokenVerifier(@Value("${docs.internal.token:}") String token) {
        super(token);
    }
}
