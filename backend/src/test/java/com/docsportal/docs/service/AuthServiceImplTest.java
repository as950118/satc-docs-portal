package com.docsportal.docs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.docsportal.docs.format.request.LoginRequest;
import com.docsportal.docs.format.response.LoginResponse;

class AuthServiceImplTest {

    /** 테스트에서 시간을 앞당길 수 있는 Clock */
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-09-26T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private MutableClock clock;
    private AuthServiceImpl service;
    private String username;
    private String password;

    @BeforeEach
    void setUp() throws Exception {
        clock = new MutableClock();
        service = new AuthServiceImpl(Duration.ofHours(12), clock);
        // 사용자 목록은 코드에 하드코딩되어 있어 환경마다 다를 수 있으므로 첫 사용자를 리플렉션으로 사용
        var usersField = AuthServiceImpl.class.getDeclaredField("USERS");
        usersField.setAccessible(true);
        Object user = ((java.util.Map<?, ?>) usersField.get(null)).values().iterator().next();
        var u = user.getClass().getDeclaredField("username");
        var p = user.getClass().getDeclaredField("password");
        u.setAccessible(true);
        p.setAccessible(true);
        username = (String) u.get(user);
        password = (String) p.get(user);
    }

    private LoginResponse login() {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return service.login(request);
    }

    @Test
    void tokensAreRandomAndDoNotContainUsername() {
        String a = login().getToken();
        String b = login().getToken();
        assertThat(a).isNotEqualTo(b).doesNotContain(username).hasSizeGreaterThanOrEqualTo(43);
    }

    @Test
    void loginReportsTtl() {
        assertThat(login().getExpiresIn()).isEqualTo(Duration.ofHours(12).toSeconds());
    }

    @Test
    void authenticateReturnsUserUntilExpiry() {
        String token = login().getToken();
        assertThat(service.authenticate(token)).get().extracting(AuthenticatedUser::username).isEqualTo(username);

        clock.now = clock.now.plus(Duration.ofHours(12));
        assertThat(service.authenticate(token)).isEmpty();
        assertThat(service.validateToken(token)).isFalse();
        assertThatThrownBy(() -> service.getCurrentUser(token)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void logoutInvalidatesToken() {
        String token = login().getToken();
        service.logout(token);
        assertThat(service.authenticate(token)).isEmpty();
    }

    @Test
    void unknownOrNullTokenIsEmpty() {
        assertThat(service.authenticate(null)).isEmpty();
        assertThat(service.authenticate("session_" + username + "_" + System.currentTimeMillis())).isEmpty();
    }
}
