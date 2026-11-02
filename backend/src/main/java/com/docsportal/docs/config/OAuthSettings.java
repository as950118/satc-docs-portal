package com.docsportal.docs.config;

import java.time.Duration;
import java.util.Set;

/**
 * OAuth 인가 서버 설정.
 *
 * @param issuer             RFC 8414 issuer 이자 /oauth/* 엔드포인트의 공개 주소(끝 슬래시 없음)
 * @param consentUrl         프론트엔드 동의 화면 주소
 * @param tokenTtl           OAuth 로 발급하는 액세스 토큰 유효 시간
 * @param extraRedirectUris  루프백 외에 등록을 허용할 redirect_uri (정확히 일치)
 */
public record OAuthSettings(String issuer, String consentUrl, Duration tokenTtl, Set<String> extraRedirectUris) {
}
