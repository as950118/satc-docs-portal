package com.docsportal.docs.service;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.docsportal.docs.common.Roles;
import com.docsportal.docs.persistence.AppUser;
import com.docsportal.docs.repository.AppUserRepository;

import jakarta.annotation.PostConstruct;

/**
 * 기동 시 계정을 만든다. 이미 있는 username 은 건드리지 않으므로 매 기동마다 실행해도 결과가 같다.
 * 개발용 계정(비밀번호 &lt;이름&gt;123)은 docs.auth.dev-accounts=true 일 때만, 첫 관리자는 docs.auth.initial-admin.* 이 있을 때만 만든다.
 */
@Component
public class AccountSeeder {

    private static final Logger log = LoggerFactory.getLogger(AccountSeeder.class);

    private record DevAccount(String username, String fullName, Set<String> roles) {
    }

    private static final List<DevAccount> DEV_ACCOUNTS = List.of(
        new DevAccount("admin", "관리자", Set.of(Roles.ADMIN, Roles.USER)),
        new DevAccount("editor", "편집자", Set.of(Roles.ADMIN, Roles.USER)),
        new DevAccount("viewer", "조회자", Set.of(Roles.USER)),
        new DevAccount("planner", "기획자", Set.of(Roles.PLANNER, Roles.USER)),
        new DevAccount("backend", "백엔드 개발자", Set.of(Roles.BACKEND, Roles.USER)),
        new DevAccount("frontend", "프론트엔드 개발자", Set.of(Roles.FRONTEND, Roles.USER)),
        new DevAccount("data", "데이터 엔지니어", Set.of(Roles.DATA, Roles.USER)),
        new DevAccount("reviewer", "코드 리뷰어", Set.of(Roles.REVIEWER, Roles.USER))
    );

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final boolean devAccounts;
    private final String adminUsername;
    private final String adminPassword;

    public AccountSeeder(AppUserRepository users, PasswordEncoder passwordEncoder,
                         @Value("${docs.auth.dev-accounts:false}") boolean devAccounts,
                         @Value("${docs.auth.initial-admin.username:}") String adminUsername,
                         @Value("${docs.auth.initial-admin.password:}") String adminPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.devAccounts = devAccounts;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @PostConstruct
    public void seed() {
        if (adminUsername.isBlank() != adminPassword.isBlank()) {
            throw new IllegalStateException(
                "docs.auth.initial-admin.username 과 docs.auth.initial-admin.password 는 함께 설정해야 합니다.");
        }
        if (!adminUsername.isBlank()) {
            createIfAbsent(adminUsername, adminPassword, null, "관리자", Set.of(Roles.ADMIN, Roles.USER));
        }
        if (devAccounts) {
            for (DevAccount account : DEV_ACCOUNTS) {
                createIfAbsent(account.username(), account.username() + "123", account.username() + "@example.com",
                    account.fullName(), account.roles());
            }
        }
        if (users.count() == 0) {
            log.warn("로그인 계정이 없습니다. docs.auth.initial-admin.username/password 로 첫 관리자를 만드세요.");
        }
    }

    private void createIfAbsent(String username, String rawPassword, String email, String fullName, Set<String> roles) {
        if (users.existsByUsername(username)) {
            return;
        }
        users.save(new AppUser(username, passwordEncoder.encode(rawPassword), email, fullName, roles));
        log.info("계정 생성: {} {}", username, roles);
    }
}
