package com.docsportal.docs.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.docsportal.docs.common.InternalTokenVerifier;
import com.docsportal.docs.common.SyncTokenVerifier;
import com.docsportal.docs.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 문서 API 인증 인터셉터 등록 (ADR-0005). docs.auth.enabled=false 로 긴급 비활성 가능
 */
@Configuration
public class WebMvcAuthConfig implements WebMvcConfigurer {

    private final ApiAuthInterceptor interceptor;

    public WebMvcAuthConfig(AuthService authService, SyncTokenVerifier syncTokenVerifier,
                            InternalTokenVerifier internalTokenVerifier, ObjectMapper objectMapper,
                            @Value("${docs.auth.enabled:true}") boolean enabled) {
        this.interceptor = new ApiAuthInterceptor(authService, syncTokenVerifier, internalTokenVerifier,
            objectMapper, enabled);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/v1/docs", "/api/v1/docs/**");
    }
}
