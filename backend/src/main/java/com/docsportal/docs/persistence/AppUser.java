package com.docsportal.docs.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.docsportal.docs.common.Roles;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * 로그인 계정. 비밀번호는 DelegatingPasswordEncoder 해시({bcrypt}...)로만 저장한다.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    private String email;

    @Column(name = "full_name")
    private String fullName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 50)
    private Set<String> roles = new TreeSet<>();

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected AppUser() {
    }

    public AppUser(String username, String passwordHash, String email, String fullName, Set<String> roles) {
        if (roles.contains(Roles.SYSTEM)) {
            throw new IllegalArgumentException("SYSTEM 역할은 사용자에게 부여할 수 없습니다.");
        }
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.fullName = fullName;
        this.roles = new TreeSet<>(roles);
        this.createdAt = Instant.now();
    }

    public void recordLogin(Instant at) {
        this.lastLoginAt = at;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public List<String> getRoles() {
        return List.copyOf(new TreeSet<>(roles));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
