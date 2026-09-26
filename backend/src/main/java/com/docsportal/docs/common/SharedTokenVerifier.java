package com.docsportal.docs.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 서비스/CI 간 공유 토큰 검증 (상수 시간 비교). 토큰이 비어 있으면 해당 경로는 비활성.
 */
public abstract class SharedTokenVerifier {

    public enum Result {
        OK, DISABLED, INVALID
    }

    private final byte[] token;

    protected SharedTokenVerifier(String token) {
        this.token = (token == null ? "" : token).getBytes(StandardCharsets.UTF_8);
    }

    public Result verify(String presented) {
        if (token.length == 0) {
            return Result.DISABLED;
        }
        if (presented == null || !MessageDigest.isEqual(token, presented.getBytes(StandardCharsets.UTF_8))) {
            return Result.INVALID;
        }
        return Result.OK;
    }

    public boolean isValid(String presented) {
        return verify(presented) == Result.OK;
    }
}
