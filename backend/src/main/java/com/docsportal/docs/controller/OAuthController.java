package com.docsportal.docs.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.docsportal.docs.service.OAuthException;
import com.docsportal.docs.service.OAuthService;
import com.docsportal.docs.service.OAuthService.AuthorizeRequest;
import com.docsportal.docs.service.OAuthService.ConsentView;
import com.docsportal.docs.service.OAuthService.TokenRequest;

import jakarta.servlet.http.HttpServletRequest;

/**
 * OAuth 인가 서버 엔드포인트. 외부 클라이언트(MCP 클라이언트)가 부르는 부분은 RFC 형식(snake_case, 실제 HTTP 상태)을 따르고,
 * /oauth/authorize/requests 는 포털 동의 화면 전용이다.
 */
@RestController
public class OAuthController {

    private static final String BEARER = "Bearer ";
    private static final long MAX_REGISTRATION_BYTES = 16 * 1024;

    private final OAuthService oauth;

    public OAuthController(OAuthService oauth) {
        this.oauth = oauth;
    }

    @GetMapping("/.well-known/oauth-authorization-server")
    public Map<String, Object> metadata() {
        return oauth.metadata();
    }

    @PostMapping("/oauth/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> metadata, HttpServletRequest request) {
        if (request.getContentLengthLong() > MAX_REGISTRATION_BYTES) {
            throw new OAuthException("invalid_client_metadata", "등록 요청이 너무 큽니다.", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
            .body(oauth.register(metadata, request.getRemoteAddr()));
    }

    @GetMapping("/oauth/authorize")
    public ResponseEntity<Void> authorize(@RequestParam MultiValueMap<String, String> params) {
        String location = oauth.authorize(new AuthorizeRequest(
            single(params, "client_id"), single(params, "redirect_uri"), single(params, "response_type"),
            single(params, "code_challenge"), single(params, "code_challenge_method"), single(params, "state"),
            single(params, "resource")));
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }

    @GetMapping("/oauth/authorize/requests/{requestId}")
    public ConsentView consent(@PathVariable String requestId) {
        return oauth.describe(requestId);
    }

    @PostMapping("/oauth/authorize/requests/{requestId}/approve")
    public ResponseEntity<Map<String, String>> approve(@PathVariable String requestId,
                                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER)) {
            throw new OAuthException("invalid_token", "로그인이 필요합니다.", HttpStatus.UNAUTHORIZED);
        }
        return redirectTo(oauth.approve(requestId, authorization.substring(BEARER.length())));
    }

    @PostMapping("/oauth/authorize/requests/{requestId}/deny")
    public ResponseEntity<Map<String, String>> deny(@PathVariable String requestId) {
        return redirectTo(oauth.deny(requestId));
    }

    @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> token(@RequestParam MultiValueMap<String, String> params, HttpServletRequest request) {
        TokenRequest token = new TokenRequest(single(params, "grant_type"), single(params, "code"), single(params, "redirect_uri"),
            single(params, "client_id"), single(params, "code_verifier"), single(params, "resource"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(oauth.exchange(token, request.getRemoteAddr()));
    }

    @ExceptionHandler(OAuthException.class)
    public ResponseEntity<Map<String, String>> oauthError(OAuthException e) {
        return ResponseEntity.status(e.status()).cacheControl(CacheControl.noStore())
            .body(Map.of("error", e.error(), "error_description", e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadable() {
        return oauthError(new OAuthException("invalid_request", "요청 본문을 읽을 수 없습니다.", HttpStatus.BAD_REQUEST));
    }

    private static ResponseEntity<Map<String, String>> redirectTo(String location) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("redirectUri", location));
    }

    private static String single(MultiValueMap<String, String> params, String name) {
        List<String> values = params.get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        if (values.size() > 1) {
            throw new OAuthException("invalid_request", name + " 는 한 번만 보내야 합니다.", HttpStatus.BAD_REQUEST);
        }
        String value = values.get(0);
        return value.isEmpty() ? null : value;
    }
}
