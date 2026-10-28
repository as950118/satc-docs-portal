package com.docsportal.docs.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.docsportal.docs.config.OAuthSettings;
import com.docsportal.docs.repository.OAuthClientRepository;
import com.docsportal.docs.service.AuthTokens;
import com.docsportal.docs.service.OAuthService;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

class OAuthApiTest extends SpecApiTestSupport {

    private static final String ISSUER = "http://localhost:8080";
    private static final String CONSENT = "http://localhost:3000/oauth/consent";
    private static final String REDIRECT = "http://127.0.0.1:53682/callback";
    private static final String RESOURCE = "http://localhost:5555";
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    private static final String STATE = "st&te=1";

    private static final class MovableClock extends Clock {
        private Instant now = Instant.now();

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

    @Autowired
    private OAuthClientRepository clients;
    @Autowired
    private AuthTokens tokens;

    private MovableClock clock;
    private MockMvc oauthMvc;

    @BeforeEach
    void buildOAuthMvc() {
        clients.deleteAll();
        clock = new MovableClock();
        OAuthSettings settings = new OAuthSettings(ISSUER, CONSENT, Duration.ofHours(168), Set.of("https://allowed.example/cb"));
        oauthMvc = MockMvcBuilders.standaloneSetup(new OAuthController(new OAuthService(clients, auth, tokens, settings, clock))).build();
    }

    @Test
    void metadataAdvertisesCodeFlowWithS256Only() throws Exception {
        oauthMvc.perform(get("/.well-known/oauth-authorization-server"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issuer").value(ISSUER))
            .andExpect(jsonPath("$.authorization_endpoint").value(ISSUER + "/oauth/authorize"))
            .andExpect(jsonPath("$.token_endpoint").value(ISSUER + "/oauth/token"))
            .andExpect(jsonPath("$.registration_endpoint").value(ISSUER + "/oauth/register"))
            .andExpect(jsonPath("$.code_challenge_methods_supported").value(org.hamcrest.Matchers.contains("S256")))
            .andExpect(jsonPath("$.token_endpoint_auth_methods_supported").value(org.hamcrest.Matchers.contains("none")))
            .andExpect(jsonPath("$.authorization_response_iss_parameter_supported").value(true));
    }

    @Test
    void fullFlowIssuesSevenDayPortalTokenThatActsAsTheApprovingUser() throws Exception {
        String clientId = register("Claude Code (satc)", REDIRECT);

        String consent = authorize(clientId, REDIRECT).andExpect(status().isFound()).andReturn().getResponse().getHeader("Location");
        assertThat(consent).startsWith(CONSENT + "?request_id=");
        String requestId = query(consent).get("request_id");

        oauthMvc.perform(get("/oauth/authorize/requests/" + requestId))
            .andExpect(jsonPath("$.clientName").value("Claude Code (satc)"))
            .andExpect(jsonPath("$.redirectUri").value(REDIRECT))
            .andExpect(jsonPath("$.resource").value(RESOURCE))
            .andExpect(jsonPath("$.tokenTtlSeconds").value(Duration.ofDays(7).toSeconds()));

        Map<String, String> callback = approve(requestId, login("planner"));
        assertThat(callback).containsEntry("state", STATE).containsEntry("iss", ISSUER).containsKey("code");

        String body = exchange(clientId, callback.get("code"), REDIRECT, VERIFIER)
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andExpect(jsonPath("$.expires_in").value(Duration.ofDays(7).toSeconds()))
            .andExpect(jsonPath("$.refresh_token").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(body, "$.access_token");

        JWTClaimsSet claims = SignedJWT.parse(accessToken).getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo("planner");
        assertThat(claims.getAudience()).containsExactly(RESOURCE);
        assertThat(claims.getClaim("client_id")).isEqualTo(clientId);
        assertThat(Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant())).isEqualTo(Duration.ofDays(7));

        assertThat(auth.getCurrentUser(accessToken).getUsername()).isEqualTo("planner");
        postJson("/api/v1/specs", "Bearer " + accessToken, "{\"title\":\"T\",\"body\":\"B\"}").andExpect(status().isOk());
    }

    @Test
    void codeIsSingleUse() throws Exception {
        String clientId = register("c", REDIRECT);
        String code = approvedCode(clientId, REDIRECT);
        exchange(clientId, code, REDIRECT, VERIFIER).andExpect(status().isOk());
        exchange(clientId, code, REDIRECT, VERIFIER)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void wrongVerifierFailsAndBurnsTheCode() throws Exception {
        String clientId = register("c", REDIRECT);
        String code = approvedCode(clientId, REDIRECT);
        exchange(clientId, code, REDIRECT, VERIFIER.replace('d', 'e'))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_grant"));
        exchange(clientId, code, REDIRECT, VERIFIER).andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void redirectUriMustMatchExactlyAtTokenTime() throws Exception {
        String clientId = register("c", REDIRECT);
        String code = approvedCode(clientId, REDIRECT);
        exchange(clientId, code, REDIRECT + "/", VERIFIER)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void codeExpiresAfterSixtySeconds() throws Exception {
        String clientId = register("c", REDIRECT);
        String code = approvedCode(clientId, REDIRECT);
        clock.now = clock.now.plusSeconds(61);
        exchange(clientId, code, REDIRECT, VERIFIER)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void codeIsBoundToTheClientItWasIssuedTo() throws Exception {
        String clientId = register("c", REDIRECT);
        String other = register("other", REDIRECT);
        String code = approvedCode(clientId, REDIRECT);
        exchange(other, code, REDIRECT, VERIFIER).andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void unknownClientIsRejectedWithoutRedirect() throws Exception {
        authorize("no-such-client", REDIRECT)
            .andExpect(status().isBadRequest())
            .andExpect(header().doesNotExist("Location"))
            .andExpect(jsonPath("$.error").value("invalid_request"));
        exchange("no-such-client", "x", REDIRECT, VERIFIER)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    void unregisteredRedirectIsRejectedWithoutRedirectButLoopbackPortMayVary() throws Exception {
        String clientId = register("c", REDIRECT);
        for (String evil : new String[] {"https://evil.example/cb", "http://127.0.0.1:53682/other", "http://localhost:53682/callback"}) {
            authorize(clientId, evil)
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Location"));
        }
        authorize(clientId, "http://127.0.0.1:61000/callback")
            .andExpect(status().isFound())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(CONSENT)));
    }

    @Test
    void registrationAcceptsOnlyLoopbackOrAllowlistedRedirects() throws Exception {
        for (String bad : new String[] {"https://evil.example/cb", "http://evil.example/cb", "http://localhost.evil.example/cb",
            "http://127.0.0.1:1/cb#frag", "http://user@127.0.0.1/cb", "https://127.0.0.1/cb", "/relative"}) {
            registerRequest("c", bad)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_redirect_uri"));
        }
        registerRequest("c", "http://[::1]:9000/cb").andExpect(status().isCreated());
        registerRequest("c", "https://allowed.example/cb").andExpect(status().isCreated())
            .andExpect(jsonPath("$.token_endpoint_auth_method").value("none"))
            .andExpect(jsonPath("$.grant_types").value(org.hamcrest.Matchers.contains("authorization_code")));
        clock.now = clock.now.plusSeconds(61);
        oauthMvc.perform(post("/oauth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"redirect_uris\":[\"" + REDIRECT + "\"],\"token_endpoint_auth_method\":\"client_secret_basic\"}"))
            .andExpect(jsonPath("$.error").value("invalid_client_metadata"));
        oauthMvc.perform(post("/oauth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"redirect_uris\":[\"http://127.0.0.1/1\",\"http://127.0.0.1/2\",\"http://127.0.0.1/3\",\"http://127.0.0.1/4\",\"http://127.0.0.1/5\",\"http://127.0.0.1/6\"]}"))
            .andExpect(jsonPath("$.error").value("invalid_redirect_uri"));
    }

    @Test
    void registrationIsRateLimitedPerCaller() throws Exception {
        for (int i = 0; i < 10; i++) {
            registerRequest("c" + i, REDIRECT).andExpect(status().isCreated());
        }
        registerRequest("c", REDIRECT).andExpect(status().isTooManyRequests());
        assertThat(clients.count()).isEqualTo(10);
        clock.now = clock.now.plusSeconds(61);
        registerRequest("c", REDIRECT).andExpect(status().isCreated());
    }

    @Test
    void protocolErrorsAfterClientCheckGoBackToTheClientWithStateAndIss() throws Exception {
        String clientId = register("c", REDIRECT);
        String[][] cases = {
            {"response_type=token&code_challenge=" + CHALLENGE + "&code_challenge_method=S256", "unsupported_response_type"},
            {"response_type=code", "invalid_request"},
            {"response_type=code&code_challenge=" + CHALLENGE + "&code_challenge_method=plain", "invalid_request"},
            {"response_type=code&code_challenge=" + CHALLENGE + "&code_challenge_method=S256&resource=not-a-uri", "invalid_target"},
        };
        for (String[] c : cases) {
            String location = oauthMvc.perform(get(URI.create("/oauth/authorize?client_id=" + clientId + "&redirect_uri=" + enc(REDIRECT)
                    + "&state=" + enc(STATE) + "&" + c[0])))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");
            assertThat(location).startsWith(REDIRECT + "?");
            assertThat(query(location)).containsEntry("error", c[1]).containsEntry("state", STATE).containsEntry("iss", ISSUER);
        }
        oauthMvc.perform(get(URI.create("/oauth/authorize?client_id=" + clientId + "&client_id=" + clientId + "&redirect_uri=" + enc(REDIRECT))))
            .andExpect(status().isBadRequest())
            .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void approveNeedsAValidPortalSessionAndDenyReturnsAccessDenied() throws Exception {
        String clientId = register("c", REDIRECT);
        String requestId = pendingRequest(clientId, REDIRECT);

        oauthMvc.perform(post("/oauth/authorize/requests/" + requestId + "/approve"))
            .andExpect(status().isUnauthorized());
        oauthMvc.perform(post("/oauth/authorize/requests/" + requestId + "/approve").header("Authorization", "Bearer forged"))
            .andExpect(status().isUnauthorized());

        String location = JsonPath.read(oauthMvc.perform(post("/oauth/authorize/requests/" + requestId + "/deny"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.redirectUri");
        assertThat(query(location)).containsEntry("error", "access_denied").containsEntry("state", STATE).containsEntry("iss", ISSUER);

        oauthMvc.perform(post("/oauth/authorize/requests/" + requestId + "/approve").header("Authorization", login("planner")))
            .andExpect(status().isNotFound());
        oauthMvc.perform(get("/oauth/authorize/requests/" + requestId)).andExpect(status().isNotFound());
    }

    @Test
    void resourceAtTokenTimeMustMatchTheAuthorizedOne() throws Exception {
        String clientId = register("c", REDIRECT);
        String code = approvedCode(clientId, REDIRECT);
        oauthMvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code").param("code", code).param("client_id", clientId)
                .param("redirect_uri", REDIRECT).param("code_verifier", VERIFIER).param("resource", "http://other:1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_target"));
    }

    @Test
    void s256ChallengeFixtureMatchesTheVerifier() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(StandardCharsets.US_ASCII));
        assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(digest)).isEqualTo(CHALLENGE);
    }

    private ResultActions registerRequest(String name, String redirectUri) throws Exception {
        return oauthMvc.perform(post("/oauth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"client_name\":\"" + name + "\",\"redirect_uris\":[\"" + redirectUri + "\"],"
                + "\"grant_types\":[\"authorization_code\",\"refresh_token\"],\"token_endpoint_auth_method\":\"none\"}"));
    }

    private String register(String name, String redirectUri) throws Exception {
        return JsonPath.read(registerRequest(name, redirectUri).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString(), "$.client_id");
    }

    private ResultActions authorize(String clientId, String redirectUri) throws Exception {
        return oauthMvc.perform(get("/oauth/authorize").param("client_id", clientId).param("redirect_uri", redirectUri)
            .param("response_type", "code").param("code_challenge", CHALLENGE).param("code_challenge_method", "S256")
            .param("state", STATE).param("resource", RESOURCE));
    }

    private String pendingRequest(String clientId, String redirectUri) throws Exception {
        return query(authorize(clientId, redirectUri).andReturn().getResponse().getHeader("Location")).get("request_id");
    }

    private Map<String, String> approve(String requestId, String bearer) throws Exception {
        String body = oauthMvc.perform(post("/oauth/authorize/requests/" + requestId + "/approve").header("Authorization", bearer))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn().getResponse().getContentAsString();
        String location = JsonPath.read(body, "$.redirectUri");
        assertThat(location).startsWith(REDIRECT + "?");
        return query(location);
    }

    private String approvedCode(String clientId, String redirectUri) throws Exception {
        return approve(pendingRequest(clientId, redirectUri), login("planner")).get("code");
    }

    private ResultActions exchange(String clientId, String code, String redirectUri, String verifier) throws Exception {
        return oauthMvc.perform(post("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("grant_type", "authorization_code").param("code", code).param("client_id", clientId)
            .param("redirect_uri", redirectUri).param("code_verifier", verifier).param("resource", RESOURCE));
    }

    private static Map<String, String> query(String uri) {
        return Arrays.stream(URI.create(uri).getRawQuery().split("&"))
            .map(pair -> pair.split("=", 2))
            .collect(Collectors.toMap(p -> p[0], p -> URLDecoder.decode(p[1], StandardCharsets.UTF_8), (a, b) -> b, LinkedHashMap::new));
    }

    private static String enc(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
