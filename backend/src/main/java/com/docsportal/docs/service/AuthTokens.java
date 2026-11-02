package com.docsportal.docs.service;

import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * 로그인 토큰 발급·검증. HS256 JWT 이고 클레임은 sub(username), iat, exp 다.
 * OAuth 로 발급한 토큰만 aud(RFC 8707 resource)와 client_id 가 더 붙고, 검증은 이 둘을 보지 않는다.
 * 역할과 활성 여부는 매 요청 DB 계정에서 읽으므로 토큰에 넣지 않는다.
 */
public class AuthTokens {

    private static final int MIN_SECRET_BYTES = 32;

    private final MACSigner signer;
    private final MACVerifier verifier;
    private final Duration ttl;
    private final Clock clock;

    public AuthTokens(byte[] secret, Duration ttl, Clock clock) {
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("토큰 서명 키는 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다.");
        }
        try {
            this.signer = new MACSigner(secret);
            this.verifier = new MACVerifier(secret);
        } catch (JOSEException e) {
            throw new IllegalStateException("토큰 서명 키를 만들 수 없습니다.", e);
        }
        this.ttl = ttl;
        this.clock = clock;
    }

    public Duration ttl() {
        return ttl;
    }

    public String issue(String username) {
        return issue(username, ttl, null, null);
    }

    public String issue(String username, Duration ttl, String audience, String clientId) {
        Instant now = clock.instant();
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
            .subject(username)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(ttl)));
        if (audience != null) {
            builder.audience(audience);
        }
        if (clientId != null) {
            builder.claim("client_id", clientId);
        }
        JWTClaimsSet claims = builder.build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("토큰 서명에 실패했습니다.", e);
        }
        return jwt.serialize();
    }

    /**
     * @return 토큰의 username
     * @throws IllegalArgumentException 형식·서명·만료 중 하나라도 맞지 않을 때
     */
    public String verify(String token) {
        JWTClaimsSet claims;
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(verifier)) {
                throw invalid();
            }
            claims = jwt.getJWTClaimsSet();
        } catch (ParseException | JOSEException e) {
            throw invalid();
        }
        Date expiresAt = claims.getExpirationTime();
        if (expiresAt == null || !clock.instant().isBefore(expiresAt.toInstant())) {
            throw new IllegalArgumentException("세션이 만료되었습니다.");
        }
        if (claims.getSubject() == null) {
            throw invalid();
        }
        return claims.getSubject();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("유효하지 않은 세션입니다.");
    }
}
