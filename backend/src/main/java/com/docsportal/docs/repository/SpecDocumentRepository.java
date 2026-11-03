package com.docsportal.docs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /** search 와 같은 조건에 제목·작성자 부분 일치(pattern: 소문자, '\\' 이스케이프 적용, null 이면 거르지 않음)를 더한 페이지 조회. */
    @Query(value = """
        select d from SpecDocument d
        where (:kind is null or d.kind = :kind)
          and (:status is null or d.status = :status)
          and (:prdId is null or d.prdId = :prdId)
          and (:allVersions = true or not exists (select n.id from SpecDocument n where n.supersedesId = d.id))
          and (:pattern is null or lower(d.title) like :pattern escape '\\' or lower(d.author) like :pattern escape '\\')
        order by d.updatedAt desc, d.id asc
        """,
        countQuery = """
        select count(d) from SpecDocument d
        where (:kind is null or d.kind = :kind)
          and (:status is null or d.status = :status)
          and (:prdId is null or d.prdId = :prdId)
          and (:allVersions = true or not exists (select n.id from SpecDocument n where n.supersedesId = d.id))
          and (:pattern is null or lower(d.title) like :pattern escape '\\' or lower(d.author) like :pattern escape '\\')
        """)
    Page<SpecDocument> searchPage(@Param("kind") SpecKind kind, @Param("status") SpecStatus status, @Param("prdId") String prdId,
                                  @Param("allVersions") boolean allVersions, @Param("pattern") String pattern, Pageable pageable);

    /** 제목이나 본문에 pattern(소문자, like 와일드카드 이스케이프 '\\' 적용)이 들어 있는 최신 버전 문서. 다음 버전이 있는 PRD 는 뺀다. */
    @Query("""
        select d from SpecDocument d
        where (lower(d.title) like :pattern escape '\\' or lower(d.body) like :pattern escape '\\')
          and not exists (select n.id from SpecDocument n where n.supersedesId = d.id)
        order by d.updatedAt desc
        """)
    List<SpecDocument> searchText(@Param("pattern") String pattern);

    Optional<SpecDocument> findBySupersedesId(String supersedesId);

    List<SpecDocument> findByPrdIdOrderByCreatedAtAsc(String prdId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from SpecDocument d where d.id = :id")
    Optional<SpecDocument> findForUpdate(@Param("id") String id);
}
