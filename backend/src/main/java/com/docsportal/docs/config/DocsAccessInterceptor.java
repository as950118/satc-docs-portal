package com.docsportal.docs.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.docsportal.docs.common.DocsAccess;
import com.docsportal.docs.common.RestResponse;
import com.docsportal.docs.common.Roles;
import com.docsportal.docs.format.response.UserInfoResponse;
import com.docsportal.docs.service.AuthService;
import com.docsportal.docs.service.SpecException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 핸들러의 @DocsAccess 를 집행한다. 사용자 세션은 SpecController·StandardDocumentController 와 같이
 * AuthService.getCurrentUser 로 검증하고, 역할은 그 응답의 roles 로 판정한다.
 */
public class DocsAccessInterceptor implements HandlerInterceptor {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String BEARER = "Bearer ";

    private final AuthService authService;
    private final byte[] internalToken;
    private final ObjectMapper objectMapper;

    public DocsAccessInterceptor(AuthService authService, String internalToken, ObjectMapper objectMapper) {
        this.authService = authService;
        this.internalToken = internalToken.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        DocsAccess access = handler instanceof HandlerMethod method ? method.getMethodAnnotation(DocsAccess.class) : null;
        if (access == null) {
            return true;
        }
        try {
            switch (access.value()) {
                case READ -> currentUser(request);
                case WRITE -> requireWriter(currentUser(request));
                case INTERNAL -> requireInternalToken(request.getHeader(INTERNAL_TOKEN_HEADER));
                case SYNC -> { }
            }
            return true;
        } catch (SpecException e) {
            response.setStatus(e.getStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(), RestResponse.responseError(e.getMessage(), e.getStatus()));
            return false;
        }
    }

    private UserInfoResponse currentUser(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER)) {
            throw new SpecException(HttpStatus.UNAUTHORIZED, "세션이 필요합니다.");
        }
        try {
            return authService.getCurrentUser(authorization.substring(BEARER.length()));
        } catch (IllegalArgumentException e) {
            throw new SpecException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    private static void requireWriter(UserInfoResponse user) {
        List<String> roles = user.getRoles() == null ? List.of() : user.getRoles();
        if (roles.stream().noneMatch(Roles.DOCUMENT_WRITERS::contains)) {
            throw new SpecException(HttpStatus.FORBIDDEN, "문서를 변경할 권한(역할)이 없습니다.");
        }
    }

    // 설정이 비어 있으면 빈 헤더와 같다고 보지 않도록 길이부터 막는다 (MessageDigest.isEqual 은 빈 배열끼리 true)
    private void requireInternalToken(String token) {
        if (internalToken.length == 0 || token == null
                || !MessageDigest.isEqual(internalToken, token.getBytes(StandardCharsets.UTF_8))) {
            throw new SpecException(HttpStatus.UNAUTHORIZED, "유효하지 않은 내부 토큰입니다.");
        }
    }
}
