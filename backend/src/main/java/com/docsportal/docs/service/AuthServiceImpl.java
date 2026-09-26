package com.docsportal.docs.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.docsportal.docs.format.request.LoginRequest;
import com.docsportal.docs.format.request.PasswordChangeRequest;
import com.docsportal.docs.format.response.LoginResponse;
import com.docsportal.docs.format.response.UserInfoResponse;

@Service
public class AuthServiceImpl implements AuthService {
    
    // 임시 사용자 데이터베이스 (실제 환경에서는 데이터베이스 사용)
    private static final Map<String, UserData> USERS = Map.of(
        "cmp-admin", new UserData("cmp-admin", "admin123", "admin@okestro.com", "CMP리더", Arrays.asList("ADMIN", "USER")),
        "cmp-govern", new UserData("cmp-govern", "govern123", "govern@okestro.com", "CMP거버넌스팀", Arrays.asList("ADMIN", "USER")),
        "cmp-inven", new UserData("cmp-inven", "inven123", "inven@okestro.com", "CMP인벤토리팀", Arrays.asList("ADMIN", "USER")),
        "cmp-monitor", new UserData("cmp-monitor", "monitor123", "monitor@okestro.com", "CMP모니터링팀", Arrays.asList("ADMIN", "USER")),
        "cmp-vista", new UserData("cmp-vista", "vista123", "vista@okestro.com", "VISTA개발팀", Arrays.asList("ADMIN", "USER")),
        "cmp-plan", new UserData("cmp-plan", "plan123", "plan@okestro.com", "CMP기획팀", Arrays.asList("ADMIN","USER")),
        "cmp-satc", new UserData("cmp-satc", "satc123", "satc@okestro.com", "CMP SATC", Arrays.asList("ADMIN","USER"))
    );
    
    private static final SecureRandom RANDOM = new SecureRandom();

    // 현재 로그인한 사용자 저장 (세션 대신 간단한 메모리 저장). token → 세션
    private final Map<String, Session> loggedInUsers = new ConcurrentHashMap<>();
    private final Duration sessionTtl;
    private final Clock clock;

    @Autowired
    public AuthServiceImpl(@Value("${auth.session.ttl-hours:12}") long ttlHours) {
        this(Duration.ofHours(ttlHours), Clock.systemUTC());
    }

    AuthServiceImpl(Duration sessionTtl, Clock clock) {
        this.sessionTtl = sessionTtl;
        this.clock = clock;
    }

    private record Session(String username, Instant expiresAt) {
    }

    /**
     * 추측 불가능한 세션 토큰 (256bit). 기존 "session_{user}_{time}" 형식은 추측 가능했음 (ADR-0005)
     */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 만료되지 않은 세션의 사용자명. 만료 세션은 제거한다
     */
    private String activeUsername(String token) {
        if (token == null) {
            return null;
        }
        Session session = loggedInUsers.get(token);
        if (session == null) {
            return null;
        }
        if (!clock.instant().isBefore(session.expiresAt())) {
            loggedInUsers.remove(token);
            return null;
        }
        return session.username();
    }
    
    @Override
    public LoginResponse login(LoginRequest request) {

        UserData user = USERS.get(request.getUsername());

        if (user == null || !user.password.equals(request.getPassword())) {
            throw new IllegalArgumentException("사용자명 또는 비밀번호가 올바르지 않습니다.");
        }
        
        String sessionId = newToken();

        // 로그인 사용자 저장
        loggedInUsers.put(sessionId, new Session(user.username, clock.instant().plus(sessionTtl)));
        
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
        
        return new LoginResponse(sessionId, userInfo, sessionTtl.toSeconds());
    }
    
    @Override
    public void logout(String sessionId) {
        loggedInUsers.remove(sessionId);
    }
    
    @Override
    public LoginResponse refreshToken(String sessionId) {
        String username = activeUsername(sessionId);
        
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
        
        Session session = loggedInUsers.get(sessionId);
        long remaining = session == null ? 0L
            : Math.max(0L, Duration.between(clock.instant(), session.expiresAt()).toSeconds());
        return new LoginResponse(sessionId, userInfo, remaining);
    }
    
    @Override
    public UserInfoResponse getCurrentUser(String sessionId) {
        String username = activeUsername(sessionId);
        
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
        return activeUsername(sessionId) != null;
    }

    @Override
    public Optional<AuthenticatedUser> authenticate(String token) {
        String username = activeUsername(token);
        if (username == null) {
            return Optional.empty();
        }
        UserData user = USERS.get(username);
        return user == null ? Optional.empty() : Optional.of(new AuthenticatedUser(user.username, user.roles));
    }
    
    @Override
    public void changePassword(String sessionId, PasswordChangeRequest request) {
        String username = activeUsername(sessionId);
        
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
