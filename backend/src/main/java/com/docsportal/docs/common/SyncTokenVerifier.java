package com.docsportal.docs.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * CI 전용 엔드포인트(동기화·드리프트 보고·본문 조회) 토큰 (ARCH-0001, ARCH-0003)
 */
@Component
public class SyncTokenVerifier extends SharedTokenVerifier {

    public static final String HEADER = "X-Docs-Sync-Token";

    public SyncTokenVerifier(@Value("${docs.sync.token:}") String token) {
        super(token);
    }
}
