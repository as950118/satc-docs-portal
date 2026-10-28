package com.docsportal.docs.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class OAuthConfigTest {

    @Test
    void missingIssuerOrConsentUrlFailsStartup() {
        assertThatThrownBy(() -> OAuthConfig.settings("", "http://localhost:3000/oauth/consent", Duration.ofDays(7), ""))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DOCS_OAUTH_ISSUER");
        assertThatThrownBy(() -> OAuthConfig.settings("http://localhost:8080", " ", Duration.ofDays(7), ""))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DOCS_OAUTH_CONSENT_URL");
    }

    @Test
    void issuerMustBeAPlainHttpUrl() {
        for (String bad : new String[] {"localhost:8080", "ftp://x", "https://x/?a=1", "https://x/#f"}) {
            assertThatThrownBy(() -> OAuthConfig.settings(bad, "http://localhost:3000/c", Duration.ofDays(7), ""))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void trailingSlashIsDroppedAndAllowlistIsParsed() {
        OAuthSettings settings = OAuthConfig.settings("https://docs-api.example.com/", "https://docs.example.com/oauth/consent",
            Duration.ofHours(168), " https://a.example/cb , ,https://b.example/cb");
        assertThat(settings.issuer()).isEqualTo("https://docs-api.example.com");
        assertThat(settings.tokenTtl()).isEqualTo(Duration.ofDays(7));
        assertThat(settings.extraRedirectUris()).containsExactlyInAnyOrder("https://a.example/cb", "https://b.example/cb");
    }
}
