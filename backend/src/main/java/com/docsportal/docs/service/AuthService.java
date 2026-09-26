package com.docsportal.docs.service;

import com.docsportal.docs.format.request.LoginRequest;
import com.docsportal.docs.format.request.PasswordChangeRequest;
import com.docsportal.docs.format.response.LoginResponse;
import com.docsportal.docs.format.response.UserInfoResponse;

public interface AuthService {
    
    /**
     * 사용자 로그인
     */
    LoginResponse login(LoginRequest request);
    
    /**
     * 사용자 로그아웃
     */
    void logout(String token);
    
    /**
     * 토큰 갱신
     */
    LoginResponse refreshToken(String token);
    
    /**
     * 현재 사용자 정보 조회
     */
    UserInfoResponse getCurrentUser(String token);
    
    /**
     * 토큰 유효성 검증
     */
    boolean validateToken(String token);

    /**
     * 세션 토큰으로 사용자 확인 (만료·미존재 시 empty)
     */
    java.util.Optional<AuthenticatedUser> authenticate(String token);
    
    /**
     * 비밀번호 변경
     */
    void changePassword(String token, PasswordChangeRequest request);
}
