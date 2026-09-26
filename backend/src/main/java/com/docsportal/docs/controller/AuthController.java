package com.docsportal.docs.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.docsportal.docs.common.RestResponse;
import com.docsportal.docs.format.request.LoginRequest;
import com.docsportal.docs.format.response.LoginResponse;
import com.docsportal.docs.format.response.UserInfoResponse;
import com.docsportal.docs.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // 로그인 (간소화된 버전)
    @PostMapping("/login")
    public RestResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            // 입력 검증
            if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
                return new RestResponse<>(
                    java.time.LocalDateTime.now().toString(),
                    "사용자명이 필요합니다.",
                    null,
                    HttpStatus.BAD_REQUEST.value()
                );
            }

            if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
                return new RestResponse<>(
                    java.time.LocalDateTime.now().toString(),
                    "비밀번호가 필요합니다.",
                    null,
                    HttpStatus.BAD_REQUEST.value()
                );
            }

            // 로그인 처리
            LoginResponse loginResponse = authService.login(request);
            return RestResponse.responseOk(loginResponse);

        } catch (IllegalArgumentException e) {
            return new RestResponse<>(
                java.time.LocalDateTime.now().toString(),
                e.getMessage(),
                null,
                HttpStatus.UNAUTHORIZED.value()
            );
        } catch (Exception e) {
            return new RestResponse<>(
                java.time.LocalDateTime.now().toString(),
                "로그인 처리 중 오류가 발생했습니다.",
                null,
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            );
        }
    }

    // 로그아웃
    @PostMapping("/logout")
    public RestResponse<Boolean> logout(HttpServletRequest request) {
        try {
            String sessionId = extractSessionFromRequest(request);
            if (sessionId != null) {
                authService.logout(sessionId);
            }
            return RestResponse.responseOk(true);
        } catch (Exception e) {
            return new RestResponse<>(
                java.time.LocalDateTime.now().toString(),
                "로그아웃 처리 중 오류가 발생했습니다.",
                null,
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            );
        }
    }

    // 현재 사용자 정보 조회
    @GetMapping("/me")
    public RestResponse<UserInfoResponse> getCurrentUser(HttpServletRequest request) {
        try {
            String sessionId = extractSessionFromRequest(request);
            if (sessionId == null) {
                return new RestResponse<>(
                    java.time.LocalDateTime.now().toString(),
                    "세션이 필요합니다.",
                    null,
                    HttpStatus.UNAUTHORIZED.value()
                );
            }

            UserInfoResponse userInfo = authService.getCurrentUser(sessionId);
            return RestResponse.responseOk(userInfo);

        } catch (IllegalArgumentException e) {
            return new RestResponse<>(
                java.time.LocalDateTime.now().toString(),
                e.getMessage(),
                null,
                HttpStatus.UNAUTHORIZED.value()
            );
        } catch (Exception e) {
            return new RestResponse<>(
                java.time.LocalDateTime.now().toString(),
                "사용자 정보 조회 중 오류가 발생했습니다.",
                null,
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            );
        }
    }

    // 세션 유효성 검증
    @GetMapping("/validate")
    public RestResponse<Boolean> validateSession(HttpServletRequest request) {
        try {
            String sessionId = extractSessionFromRequest(request);
            if (sessionId == null) {
                return RestResponse.responseOk(false);
            }

            boolean isValid = authService.validateToken(sessionId);
            return RestResponse.responseOk(isValid);

        } catch (Exception e) {
            return RestResponse.responseOk(false);
        }
    }

    // HTTP 요청에서 세션 ID 추출 (Authorization 헤더에서)
    private String extractSessionFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
