package com.docsportal.docs.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${cors.allowed-origins:}") String allowedOrigins) {
        this.allowedOrigins = parseOrigins(allowedOrigins);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOrigins(allowedOrigins.toArray(String[]::new))
            .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600L);
    }

    static List<String> parseOrigins(String raw) {
        List<String> origins = Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toList();
        if (origins.isEmpty()) {
            throw new IllegalStateException(
                "cors.allowed-origins(환경변수 CORS_ALLOWED_ORIGINS)이 비어 있습니다. 프론트엔드 Origin 을 쉼표로 구분해 설정하세요 "
                    + "(예: https://docs.example.com). 로컬 개발은 dev 프로필로 실행하면 localhost:3000 을 허용합니다.");
        }
        return origins;
    }
}
