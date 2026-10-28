package com.docsportal.docs;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "docs.auth.token-secret=test-signing-secret-at-least-32-bytes!!",
    "cors.allowed-origins=http://localhost:3000"
})
class DocsApplicationTests {

    @Test
    void contextLoads() {
    }

}
