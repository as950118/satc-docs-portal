package com.docsportal.docs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docsportal.docs.persistence.SpecDocument;

@Repository
public interface SpecDocumentRepository extends JpaRepository<SpecDocument, String> {
}
