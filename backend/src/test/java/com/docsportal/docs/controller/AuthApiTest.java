package com.docsportal.docs.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.docsportal.docs.common.Roles;
import com.docsportal.docs.format.request.LoginRequest;
import com.docsportal.docs.persistence.AppUser;
import com.docsportal.docs.repository.AppUserRepository;
import com.docsportal.docs.service.AccountSeeder;
import com.docsportal.docs.service.AuthTokens;
import com.jayway.jsonpath.JsonPath;

class AuthApiTest extends SpecApiTestSupport {

    private static final byte[] SECRET = "test-signing-secret-at-least-32-bytes!!".getBytes(StandardCharsets.UTF_8);
    private static final List<String> DEV_USERS =
        List.of("admin", "editor", "viewer", "planner", "backend", "frontend", "data", "reviewer");

    @Autowired
    private AppUserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc authMvc;

    @BeforeEach
    void buildAuthMvc() {
        authMvc = MockMvcBuilders.standaloneSetup(new AuthController(auth)).build();
    }

    private ResultActions loginRequest(String username, String password) throws Exception {
        return authMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions createPrd(String bearer) throws Exception {
        return postJson("/api/v1/specs", bearer, "{\"title\":\"T\",\"body\":\"B\"}");
    }

    @Test
    void loginKeepsResponseContract() throws Exception {
        String body = loginRequest("planner", "planner123")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.userInfo.username").value("planner"))
            .andExpect(jsonPath("$.data.userInfo.fullName").value("기획자"))
            .andExpect(jsonPath("$.data.userInfo.roles").value(org.hamcrest.Matchers.contains(Roles.PLANNER, Roles.USER)))
            .andExpect(jsonPath("$.data.expiresIn").value(Duration.ofHours(12).toSeconds()))
            .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(body, "$.data.token");
        authMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.username").value("planner"));
        authMvc.perform(get("/api/v1/auth/validate").header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$.data").value(true));
        createPrd("Bearer " + token).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordOrUnknownUserIs401InBody() throws Exception {
        for (String[] attempt : List.of(new String[] {"planner", "planner124"}, new String[] {"nobody", "nobody123"})) {
            loginRequest(attempt[0], attempt[1])
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.data").doesNotExist());
        }
    }

    @Test
    void passwordsAreStoredOnlyAsHashes() {
        List<Map<String, Object>> rows = jdbc.queryForList("select username, password_hash from app_user");
        assertThat(rows).extracting(r -> r.get("USERNAME")).containsAll(DEV_USERS);
        for (Map<String, Object> row : rows) {
            String hash = (String) row.get("PASSWORD_HASH");
            assertThat(hash).startsWith("{bcrypt}$2").doesNotContain(row.get("USERNAME") + "123");
        }
    }

    @Test
    void tamperedForeignExpiredOrUnsignedTokensAre401() throws Exception {
        String valid = login("planner").substring("Bearer ".length());
        String[] parts = valid.split("\\.");
        String forgedClaims = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(("{\"sub\":\"admin\",\"exp\":" + (Instant.now().getEpochSecond() + 3600) + "}").getBytes(StandardCharsets.UTF_8));
        String unsignedHeader = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        Clock thirteenHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(13)), ZoneOffset.UTC);

        List<String> rejected = List.of(
            parts[0] + "." + forgedClaims + "." + parts[2],
            parts[0] + "." + parts[1] + "." + (parts[2].charAt(0) == 'A' ? 'B' : 'A') + parts[2].substring(1),
            new AuthTokens("another-secret-of-at-least-32-bytes-long".getBytes(StandardCharsets.UTF_8), Duration.ofHours(12), Clock.systemUTC()).issue("planner"),
            new AuthTokens(SECRET, Duration.ofHours(12), thirteenHoursAgo).issue("planner"),
            unsignedHeader + "." + forgedClaims + ".",
            "session_planner_1700000000000");

        for (String token : rejected) {
            createPrd("Bearer " + token).andExpect(status().isUnauthorized());
            authMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.status").value(401));
            authMvc.perform(get("/api/v1/auth/validate").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data").value(false));
        }
        createPrd("Bearer " + valid).andExpect(status().isOk());
        assertThat(documents.count()).isEqualTo(1);
    }

    @Test
    void disabledAccountCannotLoginAndItsTokenStopsWorking() throws Exception {
        users.save(new AppUser("temp-planner", passwordEncoder.encode("temp-pass-1"), null, "임시", Set.of(Roles.PLANNER, Roles.USER)));
        try {
            String bearer = login("temp-planner", "temp-pass-1");
            createPrd(bearer).andExpect(status().isOk());

            jdbc.update("update app_user set enabled = false where username = 'temp-planner'");

            createPrd(bearer).andExpect(status().isUnauthorized());
            loginRequest("temp-planner", "temp-pass-1").andExpect(jsonPath("$.status").value(401));
        } finally {
            users.findByUsername("temp-planner").ifPresent(users::delete);
        }
    }

    @Test
    void rolesComeFromTheAccountSoNonPlannerTransitionIs403() throws Exception {
        String id = idOf(createPrd(login("planner")));
        mvc.perform(post("/api/v1/specs/" + id + "/approve").header("Authorization", login("backend")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/specs/" + id + "/approve").header("Authorization", login("planner")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PRD_APPROVED"));
    }

    @Test
    void initialAdminIsCreatedOnceAndNeverOverwritten() {
        try {
            new AccountSeeder(users, passwordEncoder, false, "root", "first-pass-1").seed();
            new AccountSeeder(users, passwordEncoder, false, "root", "second-pass-2").seed();

            AppUser root = users.findByUsername("root").orElseThrow();
            assertThat(root.getRoles()).containsExactly(Roles.ADMIN, Roles.USER);
            assertThat(passwordEncoder.matches("first-pass-1", root.getPasswordHash())).isTrue();
            assertThat(passwordEncoder.matches("second-pass-2", root.getPasswordHash())).isFalse();
        } finally {
            users.findByUsername("root").ifPresent(users::delete);
        }
    }

    @Test
    void halfConfiguredInitialAdminOrSystemRoleIsRejected() {
        assertThatThrownBy(() -> new AccountSeeder(users, passwordEncoder, false, "root", "").seed())
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AppUser("bot", "{noop}x", null, null, Set.of(Roles.SYSTEM)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(users.findByUsername("root")).isEmpty();
    }

    private String login(String username, String password) {
        return "Bearer " + auth.login(new LoginRequest(username, password, false)).getToken();
    }
}
