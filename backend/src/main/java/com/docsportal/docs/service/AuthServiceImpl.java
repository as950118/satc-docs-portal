package com.docsportal.docs.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.docsportal.docs.format.request.LoginRequest;
import com.docsportal.docs.format.request.PasswordChangeRequest;
import com.docsportal.docs.format.response.LoginResponse;
import com.docsportal.docs.format.response.UserInfoResponse;

@Service
public class AuthServiceImpl implements AuthService {
    
    // 임시 사용자 데이터베이스 (실제 환경에서는 데이터베이스 사용)
    private static final Map<String, UserData> USERS = Map.of(
        "admin", new UserData("admin", "admin123", "admin@example.com", "관리자", Arrays.asList("ADMIN", "USER")),
        "editor", new UserData("editor", "editor123", "editor@example.com", "편집자", Arrays.asList("ADMIN", "USER")),
        "viewer", new UserData("viewer", "viewer123", "viewer@example.com", "조회자", Arrays.asList("USER"))
    );
    
    // 현재 로그인한 사용자 저장 (세션 대신 간단한 메모리 저장)
    private final Map<String, String> loggedInUsers = new ConcurrentHashMap<>();
    
    @Override
    public LoginResponse login(LoginRequest request) {

        UserData user = USERS.get(request.getUsername());

        if (user == null || !user.password.equals(request.getPassword())) {
            throw new IllegalArgumentException("사용자명 또는 비밀번호가 올바르지 않습니다.");
        }
        
        // 간단한 세션 ID 생성 (테스트용)
        String sessionId = "session_" + user.username + "_" + System.currentTimeMillis();
        
        // 로그인 사용자 저장
        loggedInUsers.put(sessionId, user.username);
        
        // 사용자 정보 생성
        UserInfoResponse userInfo = new UserInfoResponse(
            user.username, // ID로 username 사용
            user.username,
            user.email,
            user.fullName,
            user.roles,
            LocalDateTime.now(),
            LocalDateTime.now().minusDays(30) // 가상의 생성 날짜
        );
        
        return new LoginResponse(sessionId, userInfo, 0L); // 만료시간 없음
    }
    
    @Override
    public void logout(String sessionId) {
        loggedInUsers.remove(sessionId);
    }
    
    @Override
    public LoginResponse refreshToken(String sessionId) {
        String username = loggedInUsers.get(sessionId);
        
        if (username == null) {
            throw new IllegalArgumentException("유효하지 않은 세션입니다.");
        }
        
        // 사용자 정보 조회
        UserData user = USERS.get(username);
        UserInfoResponse userInfo = new UserInfoResponse(
            user.username,
            user.username,
            user.email,
            user.fullName,
            user.roles,
            LocalDateTime.now(),
            LocalDateTime.now().minusDays(30)
        );
        
        return new LoginResponse(sessionId, userInfo, 0L); // 만료시간 없음
    }
    
    @Override
    public UserInfoResponse getCurrentUser(String sessionId) {
        String username = loggedInUsers.get(sessionId);
        
        if (username == null) {
            throw new IllegalArgumentException("유효하지 않은 세션입니다.");
        }
        
        UserData user = USERS.get(username);
        
        return new UserInfoResponse(
            user.username,
            user.username,
            user.email,
            user.fullName,
            user.roles,
            LocalDateTime.now(),
            LocalDateTime.now().minusDays(30)
        );
    }
    
    @Override
    public boolean validateToken(String sessionId) {
        return loggedInUsers.containsKey(sessionId);
    }
    
    @Override
    public void changePassword(String sessionId, PasswordChangeRequest request) {
        String username = loggedInUsers.get(sessionId);
        
        if (username == null) {
            throw new IllegalArgumentException("유효하지 않은 세션입니다.");
        }
        
        UserData user = USERS.get(username);
        
        if (!user.password.equals(request.getCurrentPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }
        
        if (request.getNewPassword().length() < 6) {
            throw new IllegalArgumentException("새 비밀번호는 최소 6자 이상이어야 합니다.");
        }
        
        // 실제 환경에서는 데이터베이스 업데이트
        // 여기서는 임시 데이터이므로 변경하지 않음
        throw new UnsupportedOperationException("데모 환경에서는 비밀번호 변경이 지원되지 않습니다.");
    }
    
    // 내부 클래스
    private static class UserData {
        final String username;
        final String password;
        final String email;
        final String fullName;
        final List<String> roles;
        
        UserData(String username, String password, String email, String fullName, List<String> roles) {
            this.username = username;
            this.password = password;
            this.email = email;
            this.fullName = fullName;
            this.roles = roles;
        }
    }
    
}
