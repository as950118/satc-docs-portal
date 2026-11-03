package com.docsportal.docs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docsportal.docs.persistence.DocumentEvent;

@Repository
public interface DocumentEventRepository extends JpaRepository<DocumentEvent, Long> {

    List<DocumentEvent> findByDocumentIdOrderByAtAscIdAsc(String documentId);
}
