package com.docsportal.docs.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * OAuth 요청 값 규칙. PKCE(RFC 7636, S256 만), redirect_uri(RFC 8252 루프백), resource(RFC 8707).
 */
final class OAuthRules {

    static final int MAX_URI_LENGTH = 512;

    private static final Pattern CHALLENGE = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern VERIFIER = Pattern.compile("[A-Za-z0-9._~-]{43,128}");
    private static final Set<String> LOOPBACK_HOSTS = Set.of("127.0.0.1", "[::1]", "localhost");

    private OAuthRules() {
    }

    static boolean isChallenge(String challenge) {
        return challenge != null && CHALLENGE.matcher(challenge).matches();
    }

    static boolean verifies(String verifier, String challenge) {
        if (verifier == null || !VERIFIER.matcher(verifier).matches()) {
            return false;
        }
        byte[] expected = Base64.getUrlEncoder().withoutPadding()
            .encode(sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
        return MessageDigest.isEqual(expected, challenge.getBytes(StandardCharsets.US_ASCII));
    }

    static boolean isRegistrableRedirectUri(String uri, Set<String> extraRedirectUris) {
        return extraRedirectUris.contains(uri) || loopback(uri) != null;
    }

    /**
     * 등록값과 정확히 같아야 한다. 루프백만 RFC 8252 7.3 에 따라 포트가 달라도 된다.
     */
    static boolean redirectUriMatches(String registered, String requested) {
        if (registered.equals(requested)) {
            return true;
        }
        URI a = loopback(registered);
        URI b = loopback(requested);
        return a != null && b != null
            && a.getHost().equals(b.getHost())
            && Objects.equals(a.getRawPath(), b.getRawPath())
            && Objects.equals(a.getRawQuery(), b.getRawQuery());
    }

    static boolean isResource(String resource) {
        URI uri = parse(resource);
        return uri != null && uri.getHost() != null && uri.getRawFragment() == null
            && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()));
    }

    private static URI loopback(String raw) {
        URI uri = parse(raw);
        if (uri == null || !"http".equals(uri.getScheme()) || uri.getRawFragment() != null || uri.getRawUserInfo() != null
            || uri.getHost() == null || !LOOPBACK_HOSTS.contains(uri.getHost())) {
            return null;
        }
        return uri;
    }

    private static URI parse(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > MAX_URI_LENGTH) {
            return null;
        }
        try {
            URI uri = new URI(raw);
            return uri.isAbsolute() ? uri : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
