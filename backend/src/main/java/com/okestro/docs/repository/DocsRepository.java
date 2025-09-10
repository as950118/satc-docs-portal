package com.okestro.docs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.okestro.docs.persistence.DocumentEntity;

@Repository
public interface DocsRepository extends JpaRepository<DocumentEntity, String> {
    
    // 특정 부모의 직접적인 자식들 조회 (삭제되지 않은 것만)
    // 정렬: 1) 폴더 우선 (isFolder DESC), 2) 이름 순 (title ASC)
    @Query("SELECT d FROM DocumentEntity d WHERE d.parentId = :parentId AND d.isDeleted = false ORDER BY d.isFolder DESC, d.title ASC")
    List<DocumentEntity> findByParentIdOrderByCreatedAtAsc(@Param("parentId") String parentId);
    
    // 루트 노드들 조회 (parentId가 null이고 삭제되지 않은 것들)
    // 정렬: 1) 폴더 우선 (isFolder DESC), 2) 이름 순 (title ASC)
    @Query("SELECT d FROM DocumentEntity d WHERE d.parentId IS NULL AND d.isDeleted = false ORDER BY d.isFolder DESC, d.title ASC")
    List<DocumentEntity> findByParentIdIsNullOrderByCreatedAtAsc();
    
    // 모든 문서를 parentId로 그룹화하여 조회
    @Query("SELECT d FROM DocumentEntity d WHERE d.isDeleted = false ORDER BY d.createdAt ASC")
    List<DocumentEntity> findAllNotDeletedOrderByCreatedAt();
    
    // 특정 부모 하위의 모든 문서 개수 조회
    @Query("SELECT COUNT(d) FROM DocumentEntity d WHERE d.parentId = :parentId AND d.isDeleted = false")
    Long countByParentId(@Param("parentId") String parentId);
    
    // 특정 부모의 모든 하위 문서 조회 (삭제되지 않은 것만, 재귀 삭제용)
    @Query("SELECT d FROM DocumentEntity d WHERE d.parentId = :parentId AND d.isDeleted = false")
    List<DocumentEntity> findAllByParentId(@Param("parentId") String parentId);
    
    // 특정 부모 하위의 모든 문서 개수 조회 (폴더 제외, 재귀적)
    @Query(value = """
        WITH RECURSIVE document_tree AS (
            -- 시작점: 특정 부모의 직접 자식들
            SELECT id, parent_id, is_folder, is_deleted
            FROM document 
            WHERE parent_id = :parentId AND is_deleted = false
            
            UNION ALL
            
            -- 재귀: 자식들의 자식들
            SELECT d.id, d.parent_id, d.is_folder, d.is_deleted
            FROM document d
            INNER JOIN document_tree dt ON d.parent_id = dt.id
            WHERE d.is_deleted = false
        )
        SELECT COUNT(*) 
        FROM document_tree 
        WHERE is_folder = false OR is_folder IS NULL
        """, nativeQuery = true)
    Long countDocumentsRecursively(@Param("parentId") String parentId);
    
    // === 통계 조회 메서드들 ===
    
    // 전체 폴더 수 조회
    @Query("SELECT COUNT(d) FROM DocumentEntity d WHERE d.isDeleted = false AND d.isFolder = true")
    Long countTotalFolders();
    
    // 전체 문서 수 조회 (폴더 제외)
    @Query("SELECT COUNT(d) FROM DocumentEntity d WHERE d.isDeleted = false AND (d.isFolder = false OR d.isFolder IS NULL)")
    Long countTotalDocuments();
    
    // 업로드 중인 문서 수 조회
    @Query("SELECT COUNT(d) FROM DocumentEntity d WHERE d.isDeleted = false AND d.status = 'UPLOADING' AND isFolder = false")
    Long countUploadingDocuments();
    
    // 인덱싱 중인 문서 수 조회
    @Query("SELECT COUNT(d) FROM DocumentEntity d WHERE d.isDeleted = false AND d.status = 'INDEXING' AND isFolder = false")
    Long countIndexingDocuments();
    
    // 처리 완료된 문서 수 조회
    @Query("SELECT COUNT(d) FROM DocumentEntity d WHERE d.isDeleted = false AND d.status = 'COMPLETE' AND isFolder = false")
    Long countCompletedDocuments();
    
    // === 폴더 목록 조회 메서드 ===
    
    // 전체 폴더 목록 조회 (이름 오름차순 정렬)
    @Query("SELECT d FROM DocumentEntity d WHERE d.isDeleted = false AND d.isFolder = true ORDER BY UPPER(d.title) ASC")
    List<DocumentEntity> findAllFoldersOrderByTitleAsc();
}
