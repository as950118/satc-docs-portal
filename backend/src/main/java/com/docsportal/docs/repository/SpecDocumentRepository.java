package com.docsportal.docs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.docsportal.docs.persistence.SpecDocument;
import com.docsportal.docs.persistence.SpecKind;
import com.docsportal.docs.persistence.SpecStatus;

import jakarta.persistence.LockModeType;

@Repository
public interface SpecDocumentRepository extends JpaRepository<SpecDocument, String> {

    /** null 인 조건은 거르지 않는다. allVersions 가 false 면 다음 버전이 있는 PRD 를 뺀다. */
    @Query("""
        select d from SpecDocument d
        where (:kind is null or d.kind = :kind)
          and (:status is null or d.status = :status)
          and (:prdId is null or d.prdId = :prdId)
          and (:allVersions = true or not exists (select n.id from SpecDocument n where n.supersedesId = d.id))
        order by d.updatedAt desc
        """)
    List<SpecDocument> search(@Param("kind") SpecKind kind, @Param("status") SpecStatus status, @Param("prdId") String prdId,
                              @Param("allVersions") boolean allVersions);

    Optional<SpecDocument> findBySupersedesId(String supersedesId);

    List<SpecDocument> findByPrdIdOrderByCreatedAtAsc(String prdId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from SpecDocument d where d.id = :id")
    Optional<SpecDocument> findForUpdate(@Param("id") String id);
}
