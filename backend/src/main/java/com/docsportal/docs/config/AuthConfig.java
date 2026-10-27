package com.docsportal.docs.config;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.docsportal.docs.service.AuthTokens;

@Configuration
public class AuthConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthConfig.class);

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public AuthTokens authTokens(@Value("${docs.auth.token-secret:}") String secret,
                                 @Value("${docs.auth.token-ttl:12h}") Duration ttl,
                                 Environment environment) {
        return new AuthTokens(signingKey(secret, environment), ttl, Clock.systemUTC());
    }

    static byte[] signingKey(String secret, Environment environment) {
        if (!secret.isBlank()) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
        if (!environment.matchesProfiles("dev")) {
            throw new IllegalStateException(
                "docs.auth.token-secret(환경변수 DOCS_AUTH_TOKEN_SECRET)이 비어 있습니다. 32바이트 이상 임의 문자열을 설정하세요 "
                    + "(예: openssl rand -base64 48). 로컬 개발은 dev 프로필로 실행하면 임시 키를 씁니다.");
        }
        log.warn("docs.auth.token-secret 이 없어 dev 프로필용 임시 서명 키를 만듭니다. 재기동하면 기존 토큰은 무효가 됩니다.");
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }
}
