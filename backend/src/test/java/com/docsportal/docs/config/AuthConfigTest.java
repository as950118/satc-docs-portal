package com.docsportal.docs.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.docsportal.docs.service.AuthTokens;

class AuthConfigTest {

    @Test
    void missingSecretFailsOutsideDevProfile() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("satc");
        assertThatThrownBy(() -> AuthConfig.signingKey("", prod))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DOCS_AUTH_TOKEN_SECRET");
    }

    @Test
    void devProfileGetsEphemeralKey() {
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");
        assertThat(AuthConfig.signingKey("", dev)).hasSize(32);
    }

    @Test
    void shortSecretIsRejected() {
        byte[] shortKey = AuthConfig.signingKey("too-short", new MockEnvironment());
        assertThatThrownBy(() -> new AuthTokens(shortKey, Duration.ofHours(1), Clock.systemUTC()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("32");
    }
}
