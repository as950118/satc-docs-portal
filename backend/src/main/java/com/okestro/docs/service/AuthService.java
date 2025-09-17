package com.okestro.docs.service;

import com.okestro.docs.format.request.LoginRequest;
import com.okestro.docs.format.request.PasswordChangeRequest;
import com.okestro.docs.format.response.LoginResponse;
import com.okestro.docs.format.response.UserInfoResponse;

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
     * 비밀번호 변경
     */
    void changePassword(String token, PasswordChangeRequest request);
}
