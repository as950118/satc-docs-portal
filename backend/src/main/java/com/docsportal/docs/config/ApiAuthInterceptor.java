package com.docsportal.docs.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import com.docsportal.docs.common.InternalTokenVerifier;
import com.docsportal.docs.common.RestResponse;
import com.docsportal.docs.common.SyncTokenVerifier;
import com.docsportal.docs.service.AuthService;
import com.docsportal.docs.service.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 문서 API 인증/인가 (ADR-0005)
 *
 * <ul>
 *   <li>GET: 로그인 세션</li>
 *   <li>쓰기(POST/PUT/PATCH/DELETE): 로그인 세션 + ADMIN</li>
 *   <li>PUT /docs/sync, POST /docs/{id}/drift: 컨트롤러가 sync 토큰 검증 (여기서는 통과)</li>
 *   <li>GET /docs/{id}/content: 로그인 세션 또는 sync 토큰 (CI check)</li>
 *   <li>POST /docs/status: 내부 토큰(인덱싱 서비스) 또는 ADMIN 세션</li>
 * </ul>
 */
public class ApiAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER = ApiAuthInterceptor.class.getName() + ".user";

    private static final Pattern SYNC_PATH = Pattern.compile("^/api/v1/docs/sync/?$");
    private static final Pattern DRIFT_PATH = Pattern.compile("^/api/v1/docs/[^/]+/drift/?$");
    private static final Pattern CONTENT_PATH = Pattern.compile("^/api/v1/docs/[^/]+/content/?$");
    private static final Pattern STATUS_PATH = Pattern.compile("^/api/v1/docs/status/?$");

    private final AuthService authService;
    private final SyncTokenVerifier syncTokenVerifier;
    private final InternalTokenVerifier internalTokenVerifier;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public ApiAuthInterceptor(AuthService authService, SyncTokenVerifier syncTokenVerifier,
                              InternalTokenVerifier internalTokenVerifier, ObjectMapper objectMapper,
                              boolean enabled) {
        this.authService = authService;
        this.syncTokenVerifier = syncTokenVerifier;
        this.internalTokenVerifier = internalTokenVerifier;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String method = request.getMethod();
        if (!enabled || "OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        String path = pathWithinApp(request);

        // CI 전용 쓰기 엔드포인트는 컨트롤러가 sync 토큰으로 검증한다
        if (("PUT".equalsIgnoreCase(method) && SYNC_PATH.matcher(path).matches())
                || ("POST".equalsIgnoreCase(method) && DRIFT_PATH.matcher(path).matches())) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && CONTENT_PATH.matcher(path).matches()
                && syncTokenVerifier.isValid(request.getHeader(SyncTokenVerifier.HEADER))) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && STATUS_PATH.matcher(path).matches()
                && internalTokenVerifier.isValid(request.getHeader(InternalTokenVerifier.HEADER))) {
            return true;
        }

        Optional<AuthenticatedUser> user = authService.authenticate(bearerToken(request));
        if (user.isEmpty()) {
            return reject(response, HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method) && !user.get().isAdmin()) {
            return reject(response, HttpStatus.FORBIDDEN, "문서를 변경할 권한이 없습니다.");
        }
        request.setAttribute(ATTR_USER, user.get());
        return true;
    }

    private static String pathWithinApp(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }

    static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = header.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    private boolean reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
            new RestResponse<>(LocalDateTime.now().toString(), message, null, status.value()));
        return false;
    }
}
