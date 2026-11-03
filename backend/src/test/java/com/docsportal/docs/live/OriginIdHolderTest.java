package com.docsportal.docs.live;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class OriginIdHolderTest {

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static void request(String header) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (header != null) {
            request.addHeader("X-Client-Id", header);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void readsHeader() {
        request("abc");
        assertThat(OriginIdHolder.current()).isEqualTo("abc");
    }

    @Test
    void nullWithoutHeader() {
        request(null);
        assertThat(OriginIdHolder.current()).isNull();
    }

    @Test
    void nullForBlankHeader() {
        request("   ");
        assertThat(OriginIdHolder.current()).isNull();
    }

    @Test
    void nullOutsideRequestThread() {
        assertThat(OriginIdHolder.current()).isNull();
    }

    @Test
    void overlongValueIsTruncated() {
        request("x".repeat(500));
        assertThat(OriginIdHolder.current()).hasSize(OriginIdHolder.MAX_LENGTH);
    }
}
