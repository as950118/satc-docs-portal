package com.docsportal.docs.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.docsportal.docs.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 문서 API 인증(@DocsAccess). docs.api.auth-required=false 면 인터셉터를 등록하지 않아 예전처럼 누구나 부를 수 있다.
 */
@Configuration
public class DocsApiAuthConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(DocsApiAuthConfig.class);

    private final DocsAccessInterceptor interceptor;

    public DocsApiAuthConfig(@Value("${docs.api.auth-required:true}") boolean authRequired,
                             @Value("${docs.internal.token:}") String internalToken,
                             AuthService authService, ObjectMapper objectMapper, Environment environment) {
        if (authRequired) {
            this.interceptor = new DocsAccessInterceptor(authService, internalToken(internalToken, environment), objectMapper);
        } else {
            this.interceptor = null;
            log.warn("docs.api.auth-required=false 라서 문서 API(/api/v1/docs/**)를 인증 없이 누구나 읽고 쓰고 지울 수 있습니다.");
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (interceptor != null) {
            registry.addInterceptor(interceptor);
        }
    }

    static String internalToken(String token, Environment environment) {
        if (!token.isBlank()) {
            return token;
        }
        if (!environment.matchesProfiles("dev")) {
            throw new IllegalStateException(
                "docs.internal.token(환경변수 DOCS_INTERNAL_TOKEN)이 비어 있습니다. indexing 이 문서 상태를 알릴 때 쓰는 값이라 "
                    + "backend 와 indexing 에 같은 임의 문자열을 설정하세요(예: openssl rand -base64 32). "
                    + "문서 API 인증을 끄려면 DOCS_API_AUTH_REQUIRED=false 로 둡니다.");
        }
        log.warn("docs.internal.token 이 비어 있습니다. dev 프로필이라 기동하지만 indexing 상태 콜백은 401 로 거부되어 문서가 INDEXING 에 남습니다.");
        return "";
    }
}
