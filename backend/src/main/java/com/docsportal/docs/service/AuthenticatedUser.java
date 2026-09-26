package com.docsportal.docs.service;

import java.util.List;

/**
 * 인증된 세션의 사용자 (ADR-0005)
 */
public record AuthenticatedUser(String username, List<String> roles) {

    public static final String ROLE_ADMIN = "ADMIN";

    public boolean isAdmin() {
        return roles != null && roles.contains(ROLE_ADMIN);
    }
}
