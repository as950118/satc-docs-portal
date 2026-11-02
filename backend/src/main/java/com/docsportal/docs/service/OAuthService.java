package com.docsportal.docs.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.docsportal.docs.config.OAuthSettings;
import com.docsportal.docs.persistence.OAuthClient;
import com.docsportal.docs.repository.OAuthClientRepository;

/**
 * 공개 클라이언트용 최소 OAuth 2.1 인가 서버. 계정과 토큰은 포털 로그인(app_user, AuthTokens)을 그대로 쓴다.
 *
 * <p>흐름: authorize 가 요청을 검증해 대기 요청(PendingAuthorization)으로 맡기고 동의 화면으로 보낸다 →
 * 로그인한 사용자가 승인하면 대기 요청을 꺼내 1회용 코드(IssuedCode)로 바꾼다 → token 이 코드를 꺼내
 * PKCE·client_id·redirect_uri 를 확인하고 포털 JWT 를 발급한다. 대기 요청과 코드는 모두 한 번만 꺼낼 수 있다.
 */
public class OAuthService {

    public record AuthorizeRequest(String clientId, String redirectUri, String responseType, String codeChallenge,
                                   String codeChallengeMethod, String state, String resource) {
    }

    public record TokenRequest(String grantType, String code, String redirectUri, String clientId, String codeVerifier,
                               String resource) {
    }

    public record ConsentView(String clientName, String redirectUri, String resource, long tokenTtlSeconds) {
    }

    record PendingAuthorization(String clientId, String clientName, String redirectUri, String codeChallenge,
                                String state, String resource) {
    }

    record IssuedCode(String clientId, String redirectUri, String codeChallenge, String username, String resource) {
    }

    static final Duration PENDING_TTL = Duration.ofMinutes(10);
    static final Duration CODE_TTL = Duration.ofSeconds(60);
    private static final int MAX_REDIRECT_URIS = 5;
    private static final int MAX_CLIENT_NAME = 100;
    private static final int MAX_STATE = 512;

    private final OAuthClientRepository clients;
    private final AuthService auth;
    private final AuthTokens tokens;
    private final OAuthSettings settings;
    private final Clock clock;
    private final ExpiringStore<PendingAuthorization> pending;
    private final ExpiringStore<IssuedCode> codes;
    private final RateLimiter registrations;
    private final RateLimiter tokenRequests;

    public OAuthService(OAuthClientRepository clients, AuthService auth, AuthTokens tokens, OAuthSettings settings, Clock clock) {
        this.clients = clients;
        this.auth = auth;
        this.tokens = tokens;
        this.settings = settings;
        this.clock = clock;
        this.pending = new ExpiringStore<>(PENDING_TTL, 10_000, clock);
        this.codes = new ExpiringStore<>(CODE_TTL, 10_000, clock);
        this.registrations = new RateLimiter(10, Duration.ofMinutes(1), clock);
        this.tokenRequests = new RateLimiter(30, Duration.ofMinutes(1), clock);
    }

    public Map<String, Object> metadata() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("issuer", settings.issuer());
        m.put("authorization_endpoint", settings.issuer() + "/oauth/authorize");
        m.put("token_endpoint", settings.issuer() + "/oauth/token");
        m.put("registration_endpoint", settings.issuer() + "/oauth/register");
        m.put("response_types_supported", List.of("code"));
        m.put("grant_types_supported", List.of("authorization_code"));
        m.put("code_challenge_methods_supported", List.of("S256"));
        m.put("token_endpoint_auth_methods_supported", List.of("none"));
        m.put("authorization_response_iss_parameter_supported", true);
        return m;
    }

    public Map<String, Object> register(Map<String, Object> metadata, String caller) {
        registrations.check(caller);
        if (!(metadata.get("redirect_uris") instanceof List<?> uris) || uris.isEmpty() || uris.size() > MAX_REDIRECT_URIS
            || !uris.stream().allMatch(String.class::isInstance)) {
            throw new OAuthException("invalid_redirect_uri",
                "redirect_uris 는 1~" + MAX_REDIRECT_URIS + "개의 문자열이어야 합니다.", HttpStatus.BAD_REQUEST);
        }
        List<String> redirectUris = uris.stream().map(String.class::cast).distinct().toList();
        for (String uri : redirectUris) {
            if (!OAuthRules.isRegistrableRedirectUri(uri, settings.extraRedirectUris())) {
                throw new OAuthException("invalid_redirect_uri",
                    "redirect_uri 는 http 루프백(127.0.0.1, [::1], localhost) 주소이거나 허용 목록에 있어야 합니다: " + uri,
                    HttpStatus.BAD_REQUEST);
            }
        }
        Object authMethod = metadata.getOrDefault("token_endpoint_auth_method", "none");
        if (!"none".equals(authMethod)) {
            throw new OAuthException("invalid_client_metadata",
                "공개 클라이언트만 등록할 수 있습니다(token_endpoint_auth_method=none).", HttpStatus.BAD_REQUEST);
        }
        Object name = metadata.get("client_name");
        if (name != null && !(name instanceof String)) {
            throw new OAuthException("invalid_client_metadata", "client_name 은 문자열이어야 합니다.", HttpStatus.BAD_REQUEST);
        }
        String clientName = name == null || ((String) name).isBlank() ? "이름 없는 클라이언트" : ((String) name).strip();
        if (clientName.length() > MAX_CLIENT_NAME) {
            throw new OAuthException("invalid_client_metadata",
                "client_name 은 " + MAX_CLIENT_NAME + "자 이하여야 합니다.", HttpStatus.BAD_REQUEST);
        }

        OAuthClient client = clients.save(new OAuthClient(UUID.randomUUID().toString(), clientName, redirectUris, clock.instant()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("client_id", client.getClientId());
        m.put("client_id_issued_at", client.getCreatedAt().getEpochSecond());
        m.put("client_name", client.getClientName());
        m.put("redirect_uris", client.getRedirectUris());
        m.put("grant_types", List.of("authorization_code"));
        m.put("response_types", List.of("code"));
        m.put("token_endpoint_auth_method", "none");
        return m;
    }

    /**
     * @return 브라우저를 보낼 주소. 정상이면 동의 화면, 클라이언트로 돌려보낼 수 있는 오류면 redirect_uri
     * @throws OAuthException client_id 나 redirect_uri 를 믿을 수 없어 리다이렉트하면 안 되는 오류
     */
    public String authorize(AuthorizeRequest request) {
        OAuthClient client = request.clientId() == null ? null : clients.findById(request.clientId()).orElse(null);
        if (client == null) {
            throw OAuthException.invalidRequest("등록되지 않은 client_id 입니다.");
        }
        String redirectUri = request.redirectUri();
        if (redirectUri == null || !OAuthRules.isRegistrableRedirectUri(redirectUri, settings.extraRedirectUris())
            || client.getRedirectUris().stream().noneMatch(registered -> OAuthRules.redirectUriMatches(registered, redirectUri))) {
            throw OAuthException.invalidRequest("redirect_uri 가 등록된 값과 일치하지 않습니다.");
        }
        if (request.state() != null && request.state().length() > MAX_STATE) {
            throw OAuthException.invalidRequest("state 가 너무 깁니다.");
        }

        OAuthException error = authorizeError(request);
        if (error != null) {
            return withQuery(redirectUri, responseParams(request.state(), "error", error.error(), "error_description", error.getMessage()));
        }
        String requestId = pending.put(new PendingAuthorization(client.getClientId(), client.getClientName(), redirectUri,
            request.codeChallenge(), request.state(), request.resource()));
        return withQuery(settings.consentUrl(), Map.of("request_id", requestId));
    }

    public ConsentView describe(String requestId) {
        PendingAuthorization p = pending.peek(requestId).orElseThrow(OAuthService::expiredRequest);
        return new ConsentView(p.clientName(), p.redirectUri(), p.resource(), settings.tokenTtl().toSeconds());
    }

    /**
     * 동의 화면이 사용자 본인의 포털 토큰(Authorization 헤더)으로 부른다. 쿠키를 쓰지 않으므로 다른 사이트가 대신 승인시킬 수 없다.
     */
    public String approve(String requestId, String bearerToken) {
        String username;
        try {
            username = auth.getCurrentUser(bearerToken).getUsername();
        } catch (IllegalArgumentException e) {
            throw new OAuthException("invalid_token", "로그인이 필요합니다.", HttpStatus.UNAUTHORIZED);
        }
        PendingAuthorization p = pending.take(requestId).orElseThrow(OAuthService::expiredRequest);
        String code = codes.put(new IssuedCode(p.clientId(), p.redirectUri(), p.codeChallenge(), username, p.resource()));
        return withQuery(p.redirectUri(), responseParams(p.state(), "code", code));
    }

    public String deny(String requestId) {
        PendingAuthorization p = pending.take(requestId).orElseThrow(OAuthService::expiredRequest);
        return withQuery(p.redirectUri(), responseParams(p.state(), "error", "access_denied",
            "error_description", "사용자가 접근을 거부했습니다."));
    }

    public Map<String, Object> exchange(TokenRequest request, String caller) {
        tokenRequests.check(caller);
        if (!"authorization_code".equals(request.grantType())) {
            throw new OAuthException("unsupported_grant_type", "authorization_code 만 지원합니다.", HttpStatus.BAD_REQUEST);
        }
        if (request.code() == null || request.clientId() == null || request.redirectUri() == null || request.codeVerifier() == null) {
            throw OAuthException.invalidRequest("code, client_id, redirect_uri, code_verifier 가 모두 필요합니다.");
        }
        if (!clients.existsById(request.clientId())) {
            throw OAuthException.invalidClient("등록되지 않은 client_id 입니다.");
        }
        IssuedCode grant = codes.take(request.code())
            .orElseThrow(() -> OAuthException.invalidGrant("코드가 없거나 만료됐거나 이미 사용됐습니다."));
        if (!grant.clientId().equals(request.clientId())) {
            throw OAuthException.invalidGrant("코드를 받은 클라이언트가 아닙니다.");
        }
        if (!grant.redirectUri().equals(request.redirectUri())) {
            throw OAuthException.invalidGrant("redirect_uri 가 인가 요청과 다릅니다.");
        }
        if (!OAuthRules.verifies(request.codeVerifier(), grant.codeChallenge())) {
            throw OAuthException.invalidGrant("code_verifier 가 맞지 않습니다.");
        }
        if (request.resource() != null && !request.resource().equals(grant.resource())) {
            throw new OAuthException("invalid_target", "resource 가 인가 요청과 다릅니다.", HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("access_token", tokens.issue(grant.username(), settings.tokenTtl(), grant.resource(), grant.clientId()));
        m.put("token_type", "Bearer");
        m.put("expires_in", settings.tokenTtl().toSeconds());
        return m;
    }

    private static OAuthException authorizeError(AuthorizeRequest request) {
        if (!"code".equals(request.responseType())) {
            return new OAuthException("unsupported_response_type", "response_type 은 code 만 지원합니다.", HttpStatus.BAD_REQUEST);
        }
        if (!OAuthRules.isChallenge(request.codeChallenge()) || !"S256".equals(request.codeChallengeMethod())) {
            return OAuthException.invalidRequest("PKCE code_challenge(S256)가 필요합니다.");
        }
        if (request.resource() != null && !OAuthRules.isResource(request.resource())) {
            return new OAuthException("invalid_target", "resource 는 프래그먼트 없는 http(s) 절대 주소여야 합니다.", HttpStatus.BAD_REQUEST);
        }
        return null;
    }

    private Map<String, String> responseParams(String state, String... pairs) {
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            params.put(pairs[i], pairs[i + 1]);
        }
        if (state != null) {
            params.put("state", state);
        }
        params.put("iss", settings.issuer());
        return params;
    }

    private static OAuthException expiredRequest() {
        return new OAuthException("invalid_request", "인가 요청이 없거나 만료됐습니다. 클라이언트에서 다시 시도하세요.", HttpStatus.NOT_FOUND);
    }

    private static String withQuery(String uri, Map<String, String> params) {
        StringBuilder out = new StringBuilder(uri).append(uri.contains("?") ? '&' : '?');
        params.forEach((k, v) -> out.append(k).append('=').append(URLEncoder.encode(v, StandardCharsets.UTF_8)).append('&'));
        out.setLength(out.length() - 1);
        return out.toString();
    }
}
