package com.docsportal.docs.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docsportal.docs.format.request.LoginRequest;
import com.docsportal.docs.format.response.LoginResponse;
import com.docsportal.docs.format.response.UserInfoResponse;
import com.docsportal.docs.persistence.AppUser;
import com.docsportal.docs.repository.AppUserRepository;

/**
 * DB 계정 + 서명 토큰(AuthTokens). 서버는 세션을 저장하지 않고, 토큰 검증 뒤 계정을 다시 읽어
 * 삭제·비활성 계정과 역할 변경을 즉시 반영한다.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokens tokens;

    public AuthServiceImpl(AppUserRepository users, PasswordEncoder passwordEncoder, AuthTokens tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        AppUser user = users.findByUsername(request.getUsername())
            .filter(AppUser::isEnabled)
            .filter(u -> passwordEncoder.matches(request.getPassword(), u.getPasswordHash()))
            .orElseThrow(() -> new IllegalArgumentException("사용자명 또는 비밀번호가 올바르지 않습니다."));
        user.recordLogin(Instant.now());
        return new LoginResponse(tokens.issue(user.getUsername()), userInfo(user), tokens.ttl().toSeconds());
    }

    // 토큰을 서버에 두지 않으므로 클라이언트가 버리면 끝난다. 만료 전 강제 무효화는 계정 비활성화로 한다
    @Override
    public void logout(String token) {
    }

    @Override
    @Transactional(readOnly = true)
    public UserInfoResponse getCurrentUser(String token) {
        return userInfo(userOf(token));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean validateToken(String token) {
        try {
            userOf(token);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private AppUser userOf(String token) {
        return users.findByUsername(tokens.verify(token))
            .filter(AppUser::isEnabled)
            .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 세션입니다."));
    }

    private static UserInfoResponse userInfo(AppUser user) {
        return new UserInfoResponse(
            user.getUsername(),
            user.getUsername(),
            user.getEmail(),
            user.getFullName(),
            user.getRoles(),
            localTime(user.getLastLoginAt()),
            localTime(user.getCreatedAt())
        );
    }

    private static LocalDateTime localTime(Instant at) {
        return at == null ? null : LocalDateTime.ofInstant(at, ZoneId.systemDefault());
    }
}
