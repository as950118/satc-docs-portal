package com.docsportal.docs;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "docs.auth.token-secret=test-signing-secret-at-least-32-bytes!!",
    "cors.allowed-origins=http://localhost:3000",
    "docs.oauth.issuer=http://localhost:8080",
    "docs.oauth.consent-url=http://localhost:3000/oauth/consent",
    "docs.internal.token=test-internal-token"
})
class DocsApplicationTests {

    @Test
    void contextLoads() {
    }

}
