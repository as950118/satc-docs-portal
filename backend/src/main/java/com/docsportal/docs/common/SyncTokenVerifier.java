package com.docsportal.docs.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * CI 전용 쓰기 엔드포인트(동기화·드리프트 보고)의 공유 토큰 검증 (ARCH-0001)
 */
@Component
public class SyncTokenVerifier {

    public static final String HEADER = "X-Docs-Sync-Token";

    public enum Result {
        OK, DISABLED, INVALID
    }

    private final byte[] token;

    public SyncTokenVerifier(@Value("${docs.sync.token:}") String token) {
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    public Result verify(String presented) {
        if (token.length == 0) {
            // 토큰 미설정 시 비활성 (무인증 개방 방지)
            return Result.DISABLED;
        }
        if (presented == null || !MessageDigest.isEqual(token, presented.getBytes(StandardCharsets.UTF_8))) {
            return Result.INVALID;
        }
        return Result.OK;
    }
}
