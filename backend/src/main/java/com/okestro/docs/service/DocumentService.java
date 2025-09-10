package com.okestro.docs.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.okestro.docs.format.response.DocumentCreateResponse;
import com.okestro.docs.format.response.DocumentResponse;
import com.okestro.docs.format.response.DocumentStatisticsResponse;
import com.okestro.docs.format.response.DocumentTreeResponse;
import com.okestro.docs.format.response.FolderListResponse;

public interface DocumentService {
    void createBucketIfNotExists();
    void uploadDocumentAsync(MultipartFile file, String parentId);
    
    // 새 문서/폴더 생성 (동기 + 비동기)
    DocumentCreateResponse createFolder(String title, String parentId);
    List<DocumentCreateResponse> createDocuments(MultipartFile[] files, String parentId);
    
    // 트리 구조 관련 메서드
    DocumentTreeResponse getDocumentTree(); // 최상위 부모들만 조회 (children 배열 비우고 개수만)
    DocumentTreeResponse getFullDocumentTree(); // 전체 계층 구조 조회
    DocumentTreeResponse getDocumentTreeByParent(String parentId);
    List<DocumentResponse> getRootDocuments();
    List<DocumentResponse> getChildDocuments(String parentId);
    
    // 문서 삭제
    void deleteDocument(String documentId);
    
    // 문서/폴더 이동
    void moveDocuments(List<String> documentIds, String targetParentId);
    
    // 문서 상태 업데이트
    void updateDocumentStatus(String documentId, com.okestro.docs.persistence.DocumentStatus status);
    
    // 문서 통계 조회
    DocumentStatisticsResponse getDocumentStatistics();
    
    // 폴더 목록 조회
    List<FolderListResponse> getAllFolders();
}