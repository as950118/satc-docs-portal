package com.docsportal.docs.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CorsConfigTest {

    @Test
    void commaListIsSplitAndTrimmed() {
        assertThat(CorsConfig.parseOrigins(" https://docs.example.com, http://localhost:3000 ,,"))
            .containsExactly("https://docs.example.com", "http://localhost:3000");
    }

    @Test
    void singleOriginIsAccepted() {
        assertThat(CorsConfig.parseOrigins("https://docs.example.com")).containsExactly("https://docs.example.com");
    }

    @Test
    void blankValueFailsWithEnvName() {
        for (String blank : new String[] {"", "  ", " , "}) {
            assertThatThrownBy(() -> CorsConfig.parseOrigins(blank))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ALLOWED_ORIGINS");
        }
    }
}
