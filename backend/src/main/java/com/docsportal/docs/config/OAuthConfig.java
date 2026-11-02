package com.docsportal.docs.config;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.docsportal.docs.repository.OAuthClientRepository;
import com.docsportal.docs.service.AuthService;
import com.docsportal.docs.service.AuthTokens;
import com.docsportal.docs.service.OAuthService;

@Configuration
public class OAuthConfig {

    @Bean
    public OAuthSettings oauthSettings(@Value("${docs.oauth.issuer:}") String issuer,
                                       @Value("${docs.oauth.consent-url:}") String consentUrl,
                                       @Value("${docs.oauth.token-ttl:168h}") Duration tokenTtl,
                                       @Value("${docs.oauth.allowed-redirect-uris:}") String allowedRedirectUris) {
        return settings(issuer, consentUrl, tokenTtl, allowedRedirectUris);
    }

    @Bean
    public OAuthService oauthService(OAuthClientRepository clients, AuthService auth, AuthTokens tokens, OAuthSettings settings) {
        return new OAuthService(clients, auth, tokens, settings, Clock.systemUTC());
    }

    static OAuthSettings settings(String issuer, String consentUrl, Duration tokenTtl, String allowedRedirectUris) {
        Set<String> extraRedirectUris = Arrays.stream(allowedRedirectUris.split(","))
            .map(String::trim)
            .filter(uri -> !uri.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
        return new OAuthSettings(
            absoluteUrl(issuer, "docs.oauth.issuer(환경변수 DOCS_OAUTH_ISSUER)", "https://docs-api.example.com"),
            absoluteUrl(consentUrl, "docs.oauth.consent-url(환경변수 DOCS_OAUTH_CONSENT_URL)", "https://docs.example.com/oauth/consent"),
            tokenTtl,
            extraRedirectUris);
    }

    private static String absoluteUrl(String raw, String name, String example) {
        String value = raw.trim();
        if (value.isEmpty()) {
            throw new IllegalStateException(name + "이 비어 있습니다. 브라우저에서 접근하는 주소를 설정하세요 (예: " + example
                + "). 로컬 개발은 dev 프로필로 실행하면 localhost 주소를 씁니다.");
        }
        URI uri = URI.create(value);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
            || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalStateException(name + "은 쿼리·프래그먼트 없는 http(s) 절대 주소여야 합니다: " + value);
        }
        return value.replaceAll("/+$", "");
    }
}
