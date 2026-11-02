package com.docsportal.docs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docsportal.docs.persistence.OAuthClient;

@Repository
public interface OAuthClientRepository extends JpaRepository<OAuthClient, String> {
}
