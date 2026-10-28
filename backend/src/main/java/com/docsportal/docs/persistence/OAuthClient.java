package com.docsportal.docs.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * 동적 등록(RFC 7591)된 공개 OAuth 클라이언트. 비밀이 없으므로 client_id 와 redirect_uri 만 둔다.
 */
@Entity
@Table(name = "oauth_client")
public class OAuthClient {

    @Id
    @Column(name = "client_id", length = 64)
    private String clientId;

    @Column(name = "client_name", nullable = false, length = 100)
    private String clientName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "oauth_client_redirect_uri", joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "redirect_uri", nullable = false, length = 512)
    private List<String> redirectUris = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OAuthClient() {
    }

    public OAuthClient(String clientId, String clientName, List<String> redirectUris, Instant createdAt) {
        this.clientId = clientId;
        this.clientName = clientName;
        this.redirectUris = new ArrayList<>(redirectUris);
        this.createdAt = createdAt;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public List<String> getRedirectUris() {
        return List.copyOf(redirectUris);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
