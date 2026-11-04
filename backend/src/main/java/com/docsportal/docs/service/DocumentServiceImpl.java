package com.docsportal.docs.service;


import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.docsportal.docs.format.dto.DocumentTreeNode;
import com.docsportal.docs.format.response.DocumentCreateResponse;
import com.docsportal.docs.format.response.DocumentResponse;
import com.docsportal.docs.format.response.DocumentStatisticsResponse;
import com.docsportal.docs.format.response.DocumentTreeResponse;
import com.docsportal.docs.format.response.FolderListResponse;
import com.docsportal.docs.live.LiveEventPublisher;
import com.docsportal.docs.live.LiveEventType;
import com.docsportal.docs.live.OriginIdHolder;
import com.docsportal.docs.persistence.DocumentEntity;
import com.docsportal.docs.persistence.DocumentStatus;
import com.docsportal.docs.repository.DocsRepository;
import com.docsportal.docs.storage.ObjectStorage;
import com.docsportal.docs.storage.StorageSettings;


@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentServiceImpl.class);
    private final ObjectStorage storage;
    private final StorageSettings storageSettings;
    private final DocsRepository docsRepository;
    private final ExternalService externalService;
    private final LiveEventPublisher liveEvents;

    public DocumentServiceImpl(ObjectStorage storage, StorageSettings storageSettings, DocsRepository docsRepository,
                               ExternalService externalService, LiveEventPublisher liveEvents) {
        this.storage = storage;
        this.storageSettings = storageSettings;
        this.docsRepository = docsRepository;
        this.externalService = externalService;
        this.liveEvents = liveEvents;
    }

    @Override
    public String getDocumentContent(String documentId) {
        DocumentEntity doc = docsRepository.findById(documentId).orElse(null);
        if (doc == null || Boolean.TRUE.equals(doc.getDeleted()) || Boolean.TRUE.equals(doc.getIsFolder())
                || doc.getFilePath() == null) {
            return null;
        }
        try {
            return new String(storage.get(doc.getFilePath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("[CONTENT] Failed to read {} from storage: {}", documentId, e.getMessage());
            return null;
        }
    }
    
    public DocumentResponse uploadDocument(byte[] fileBytes, String originalFilename, String contentType, String parentId) {
        return uploadDocument(fileBytes, originalFilename, contentType, parentId, null);
    }

    public DocumentResponse uploadDocument(byte[] fileBytes, String originalFilename, String contentType, String parentId,
                                           String originId) {
        // 새로운 UUID 생성하여 uploadDocumentWithId 호출
        String documentId = UUID.randomUUID().toString();
        String title = removeFileExtension(originalFilename);
        String extension = getFileExtension(originalFilename);
        String normalizedTitle = Normalizer.normalize(title, Normalizer.Form.NFC);

        return uploadDocumentWithId(fileBytes, contentType, parentId, documentId, normalizedTitle, extension, originId);
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }
    
    private String removeFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return filename;
        }
        return filename.substring(0, lastDotIndex);
    }
    
    @Override
    @Async("fileUploadExecutor")
    public void uploadDocumentAsync(MultipartFile file, String parentId, String originId) {
        try {
            // 빈 문자열을 null로 변환
            String normalizedParentId = (parentId != null && parentId.trim().isEmpty()) ? null : parentId;
            
            // 파일 정보를 미리 추출 (비동기 처리 전에)
            String originalFilename = file.getOriginalFilename();
            String contentType = file.getContentType();
            byte[] fileBytes = file.getBytes(); // 파일 데이터를 메모리에 복사
            
            // 실제 업로드 처리
            uploadDocument(fileBytes, originalFilename, contentType, normalizedParentId, originId);
            
        } catch (Exception e) {
            logger.error("[ASYNC UPLOAD ERROR] 비동기 파일 업로드 실패: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public DocumentCreateResponse createFolder(String title, String parentId) {
        try {
            // 1. 동기적으로 폴더 정보 생성
            String documentId = UUID.randomUUID().toString();
            
            // 빈 문자열을 null로 변환
            String normalizedParentId = (parentId != null && parentId.trim().isEmpty()) ? null : parentId;
            String normalizedTitle = Normalizer.normalize(title, Normalizer.Form.NFC);

            // 2. DB에 폴더 정보 저장 (동기)
            DocumentEntity folderEntity = DocumentEntity.builder()
                .id(documentId)
                .parentId(normalizedParentId)
                .title(normalizedTitle)
                .bucket(storageSettings.bucket())
                .filePath(null) // 폴더는 파일 경로가 없음
                .extension(null) // 폴더는 확장자가 없음
                .isFolder(true)  // 폴더임
                .status(DocumentStatus.COMPLETE) // 폴더는 바로 완료 상태
                .build();
            
            docsRepository.save(folderEntity);
            logger.info("[CREATE FOLDER] Folder created: {} ({})", documentId, title);
            liveEvents.publish(LiveEventType.DOCUMENT_CREATED, documentId, normalizedTitle, normalizedParentId,
                DocumentStatus.COMPLETE.name(), null);
            
            // 3. 동기 응답 반환 (폴더는 extension이 null, 상태는 COMPLETE)
            return new DocumentCreateResponse(documentId, title, true, null, DocumentStatus.COMPLETE);
            
        } catch (Exception e) {
            logger.error("[CREATE FOLDER ERROR] Failed to create folder: {}", e.getMessage(), e);
            throw new RuntimeException("폴더 생성에 실패했습니다: " + e.getMessage());
        }
    }
    
    @Override
    public List<DocumentCreateResponse> createDocuments(MultipartFile[] files, String parentId) {
        List<DocumentCreateResponse> responses = new ArrayList<>();
        
        // 빈 문자열을 null로 변환
        String normalizedParentId = (parentId != null && parentId.trim().isEmpty()) ? null : parentId;
        String originId = OriginIdHolder.current();
        
        try {
            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    continue; // 빈 파일은 건너뛰기
                }
                
                // 1. 동기적으로 문서 정보 생성
                String originalFilename = file.getOriginalFilename();
                String documentId = UUID.randomUUID().toString();
                String title = removeFileExtension(originalFilename);
                String extension = getFileExtension(originalFilename);
                String normalizedTitle = Normalizer.normalize(title, Normalizer.Form.NFC);
                
                // 2. 동기 응답 리스트에 추가 (extension과 status 포함)
                responses.add(new DocumentCreateResponse(documentId, normalizedTitle, false, extension, DocumentStatus.UPLOADING));
                
                // 3. 비동기적으로 실제 파일 처리 (기존 로직 재사용)
                createDocumentAsync(file, normalizedParentId, documentId, normalizedTitle, originId);
            }
            
            logger.info("[CREATE DOCUMENTS] Created {} document entries", responses.size());
            return responses;
            
        } catch (Exception e) {
            logger.error("[CREATE DOCUMENTS ERROR] Failed to create documents: {}", e.getMessage(), e);
            throw new RuntimeException("문서 생성에 실패했습니다: " + e.getMessage());
        }
    }
    
    @Async("fileUploadExecutor")
    private void createDocumentAsync(MultipartFile file, String parentId, String documentId, String title,
                                     String originId) {
        try {
            // 파일 정보를 미리 추출
            String originalFilename = file.getOriginalFilename();
            String contentType = file.getContentType();
            String extension = getFileExtension(originalFilename);
            byte[] fileBytes = file.getBytes();
            
            // 실제 업로드 처리
            uploadDocumentWithId(fileBytes, contentType, parentId, documentId, title, extension, originId);
            
        } catch (Exception e) {
            logger.error("[ASYNC CREATE ERROR] 비동기 문서 생성 실패: {}", e.getMessage(), e);
        }
    }
    
    private DocumentResponse uploadDocumentWithId(byte[] fileBytes, String contentType, String parentId, String documentId, String title, String extension,
                                                   String originId) {
        try {
            
            storage.ensureBucket();
            String filePath = storageSettings.documentKey(documentId, title, extension);
            
            DocumentEntity documentEntity = DocumentEntity.builder()
                .id(documentId)
                .parentId(parentId)
                .title(title)
                .bucket(storageSettings.bucket())
                .filePath(filePath)
                .extension(extension)
                .isFolder(false) // 파일이므로 폴더가 아님
                .status(DocumentStatus.UPLOADING) // 초기 상태: 업로드 중
                .build();
            
            DocumentEntity savedEntity = docsRepository.save(documentEntity);
            
            storage.put(filePath, fileBytes, contentType);
            logger.info("[STORAGE] File uploaded successfully: {}", filePath);

            // 업로드 완료 후 상태를 INDEXING으로 변경
            savedEntity.updateStatus(DocumentStatus.INDEXING);
            docsRepository.save(savedEntity);
            logger.info("[STATUS] Document status updated to INDEXING: {}", documentId);
            liveEvents.publish(LiveEventType.DOCUMENT_CREATED, documentId, title, parentId,
                DocumentStatus.INDEXING.name(), null, originId);

            externalService.notifyDocumentUploaded(storageSettings.bucket(), filePath, title);
            
            return new DocumentResponse(savedEntity);
            
        } catch (Exception e) {
            logger.error("[UPLOAD ERROR] Failed to upload document: {}", e.getMessage(), e);
            throw new RuntimeException("문서 업로드에 실패했습니다: " + e.getMessage());
        }
    }
    
    @Override
    public DocumentTreeResponse getDocumentTree() {
        try {
            // 최상위 부모들만 조회하되, childrenCount와 isFolder 정보는 포함
            List<DocumentEntity> rootDocuments = docsRepository.findByParentIdIsNullOrderByCreatedAtAsc();
            List<DocumentTreeNode> treeNodes = new ArrayList<>();
            
            for (DocumentEntity entity : rootDocuments) {
                DocumentTreeNode node = new DocumentTreeNode(entity);
                
                // 문서 개수 계산 (재귀적으로 모든 하위 문서만 포함, 폴더 제외)
                Long documentCount = docsRepository.countDocumentsRecursively(entity.getId());
                node.setChildrenCount(documentCount.intValue());
                
                // folder 여부 설정 (DB의 isFolder 필드 또는 확장자 없음으로 판단)
                boolean isFolder = (entity.getIsFolder() != null && entity.getIsFolder()) || 
                                 (entity.getExtension() == null || entity.getExtension().isEmpty());
                node.setIsFolder(isFolder);
                
                // children 배열은 비워둠 (지연 로딩을 위해)
                node.setChildren(new ArrayList<>());
                
                treeNodes.add(node);
            }
                
            return new DocumentTreeResponse(treeNodes);
        } catch (Exception e) {
            logger.error("[TREE ERROR] Failed to get document tree: {}", e.getMessage(), e);
            throw new RuntimeException("문서 트리 조회에 실패했습니다: " + e.getMessage());
        }
    }
    
    @Override
    public DocumentTreeResponse getFullDocumentTree() {
        try {
            // 전체 계층 구조를 포함한 완전한 트리 조회
            List<DocumentEntity> allDocuments = docsRepository.findAllNotDeletedOrderByCreatedAt();
            List<DocumentTreeNode> treeNodes = buildDocumentTree(allDocuments);
            return new DocumentTreeResponse(treeNodes);
        } catch (Exception e) {
            logger.error("[FULL TREE ERROR] Failed to get full document tree: {}", e.getMessage(), e);
            throw new RuntimeException("전체 문서 트리 조회에 실패했습니다: " + e.getMessage());
        }
    }
    
    @Override
    public DocumentTreeResponse getDocumentTreeByParent(String parentId) {
        try {
            List<DocumentEntity> documents;
            if (parentId == null || parentId.trim().isEmpty()) {
                documents = docsRepository.findByParentIdIsNullOrderByCreatedAtAsc();
            } else {
                documents = docsRepository.findByParentIdOrderByCreatedAtAsc(parentId);
            }
            
            List<DocumentTreeNode> treeNodes = documents.stream()
                .map(entity -> {
                    DocumentTreeNode node = new DocumentTreeNode(entity);
                    // 문서 개수만 계산 (폴더 제외)
                    Long documentCount = docsRepository.countDocumentsRecursively(entity.getId());
                    node.setChildrenCount(documentCount.intValue());
                    
                    // folder 여부 설정
                    boolean isFolder = (entity.getIsFolder() != null && entity.getIsFolder()) || 
                                     (entity.getExtension() == null || entity.getExtension().isEmpty());
                    node.setIsFolder(isFolder);
                    
                    return node;
                })
                .collect(Collectors.toList());
                
            return new DocumentTreeResponse(treeNodes);
        } catch (Exception e) {
            logger.error("[TREE ERROR] Failed to get document tree by parent {}: {}", parentId, e.getMessage(), e);
            throw new RuntimeException("문서 트리 조회에 실패했습니다: " + e.getMessage());
        }
    }
    
    @Override
    public List<DocumentResponse> getRootDocuments() {
        try {
            List<DocumentEntity> rootDocuments = docsRepository.findByParentIdIsNullOrderByCreatedAtAsc();
            return rootDocuments.stream()
                .map(DocumentResponse::new)
                .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("[ROOT ERROR] Failed to get root documents: {}", e.getMessage(), e);
            throw new RuntimeException("루트 문서 조회에 실패했습니다: " + e.getMessage());
        }
    }
    
    @Override
    public List<DocumentResponse> getChildDocuments(String parentId) {
        try {
            List<DocumentEntity> childDocuments = docsRepository.findByParentIdOrderByCreatedAtAsc(parentId);
            return childDocuments.stream()
                .map(DocumentResponse::new)
                .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("[CHILD ERROR] Failed to get child documents for parent {}: {}", parentId, e.getMessage(), e);
            throw new RuntimeException("자식 문서 조회에 실패했습니다: " + e.getMessage());
        }
    }
    
    private List<DocumentTreeNode> buildDocumentTree(List<DocumentEntity> allDocuments) {
        // 1. 모든 문서를 TreeNode로 변환
        Map<String, DocumentTreeNode> nodeMap = new HashMap<>();
        for (DocumentEntity entity : allDocuments) {
            DocumentTreeNode node = new DocumentTreeNode(entity);
            // 문서 개수만 계산 (폴더 제외)
            Long documentCount = docsRepository.countDocumentsRecursively(entity.getId());
            node.setChildrenCount(documentCount.intValue());
            
            // folder 여부 설정
            boolean isFolder = (entity.getIsFolder() != null && entity.getIsFolder()) || 
                             (entity.getExtension() == null || entity.getExtension().isEmpty());
            node.setIsFolder(isFolder);
            
            nodeMap.put(entity.getId(), node);
        }
        
        // 2. 부모-자식 관계 구성
        List<DocumentTreeNode> rootNodes = new ArrayList<>();
        for (DocumentTreeNode node : nodeMap.values()) {
            if (node.getParentId() == null || node.getParentId().trim().isEmpty()) {
                // 루트 노드
                rootNodes.add(node);
            } else {
                // 자식 노드 - 부모에게 추가
                DocumentTreeNode parentNode = nodeMap.get(node.getParentId());
                if (parentNode != null) {
                    parentNode.addChild(node);
                } else {
                    // 부모가 없는 경우 루트로 처리 (orphan)
                    rootNodes.add(node);
                    logger.warn("Orphan node found: {} with parent: {}", node.getId(), node.getParentId());
                }
            }
        }
        
        return rootNodes;
    }
    
    @Override
    public void deleteDocument(String documentId) {
        try {
            // 1. DB에서 문서 조회
            DocumentEntity document = docsRepository.findById(documentId)
                .orElse(null);
            
            if (document == null) {
                throw new IllegalArgumentException("문서를 찾을 수 없습니다: " + documentId);
            }
            
            // 2. 삭제될 모든 파일명 수집 (재귀적으로)
            List<String> deletedFileNames = new ArrayList<>();
            collectDeletedFileNames(documentId, deletedFileNames);
            
            // 3. 재귀적으로 하위 문서들 먼저 삭제
            List<DocumentEntity> deleted = new ArrayList<>();
            deleteDocumentRecursively(documentId, deleted);
            for (DocumentEntity gone : deleted) {
                liveEvents.publish(LiveEventType.DOCUMENT_DELETED, gone.getId(), gone.getTitle(), gone.getParentId(),
                    gone.getStatus() == null ? null : gone.getStatus().name(), null);
            }
            
            // 4. 외부 API에 삭제 알림 (비동기)
            if (!deletedFileNames.isEmpty()) {
                externalService.notifyDocumentsDeleted(deletedFileNames);
                logger.info("[DELETE] Notified external API about {} deleted files", deletedFileNames.size());
            }
            
        } catch (Exception e) {
            logger.error("[DELETE ERROR] Failed to delete document {}: {}", documentId, e.getMessage(), e);
            throw new RuntimeException("문서 삭제에 실패했습니다: " + e.getMessage());
        }
    }
    
    /**
     * 삭제될 문서와 하위 문서들의 파일명을 재귀적으로 수집합니다.
     * 파일명은 "documentId.extension" 형태로 생성됩니다.
     */
    private void collectDeletedFileNames(String documentId, List<String> deletedFileNames) {
        try {
            // 1. 현재 문서 조회
            DocumentEntity document = docsRepository.findById(documentId)
                .orElse(null);
            
            if (document == null || document.getDeleted()) {
                // 이미 삭제되었거나 존재하지 않는 문서
                return;
            }
            
            // 2. 현재 문서가 파일(폴더가 아님)이고 확장자가 있으면 파일명 추가
            if ((document.getIsFolder() == null || !document.getIsFolder()) && 
                document.getExtension() != null && !document.getExtension().trim().isEmpty()) {
                String fileName = document.getId() + "." + document.getExtension();
                deletedFileNames.add(fileName);
                logger.debug("[DELETE COLLECT] Added file: {}", fileName);
            }
            
            // 3. 하위 문서들 재귀적으로 처리
            List<DocumentEntity> childDocuments = docsRepository.findAllByParentId(documentId);
            for (DocumentEntity child : childDocuments) {
                collectDeletedFileNames(child.getId(), deletedFileNames);
            }
            
        } catch (Exception e) {
            logger.error("[DELETE COLLECT ERROR] Failed to collect deleted file names for document {}: {}", 
                documentId, e.getMessage(), e);
        }
    }
    
    private void deleteDocumentRecursively(String documentId, List<DocumentEntity> deleted) {
        try {
            // 1. 현재 문서 조회
            DocumentEntity document = docsRepository.findById(documentId)
                .orElse(null);
            
            if (document == null || document.getDeleted()) {
                // 이미 삭제되었거나 존재하지 않는 문서
                return;
            }
            
            // 2. 하위 문서들 먼저 재귀적으로 삭제
            List<DocumentEntity> childDocuments = docsRepository.findAllByParentId(documentId);
            for (DocumentEntity child : childDocuments) {
                deleteDocumentRecursively(child.getId(), deleted);
            }
            
            // 3. 현재 문서 soft delete
            document.delete();
            docsRepository.save(document);
            deleted.add(document);
            logger.info("[DELETE] Document soft deleted in DB: {} ({})", documentId, document.getTitle());
            
            // 4. 저장소에서 파일 삭제
            String objectName = document.getFilePath();
            if (objectName != null && !objectName.isEmpty()) {
                storage.delete(objectName);
                logger.info("[DELETE] File removed from storage: {}", objectName);
            }
            
        } catch (Exception e) {
            logger.error("[DELETE ERROR] Failed to recursively delete document {}: {}", documentId, e.getMessage(), e);
            throw new RuntimeException("재귀적 문서 삭제에 실패했습니다: " + e.getMessage());
        }
    }
    
    @Override
    public void moveDocuments(List<String> documentIds, String targetParentId) {
        try {
            if (documentIds == null || documentIds.isEmpty()) {
                throw new IllegalArgumentException("이동할 문서 ID가 없습니다.");
            }
            
            // 대상 부모 폴더 유효성 검사 (null이면 루트로 이동)
            if (targetParentId != null && !targetParentId.trim().isEmpty()) {
                DocumentEntity targetParent = docsRepository.findById(targetParentId).orElse(null);
                if (targetParent == null || targetParent.getDeleted()) {
                    throw new IllegalArgumentException("대상 부모 폴더를 찾을 수 없습니다: " + targetParentId);
                }
                
                // 대상이 폴더인지 확인
                if (targetParent.getIsFolder() == null || !targetParent.getIsFolder()) {
                    throw new IllegalArgumentException("대상은 폴더여야 합니다: " + targetParentId);
                }
            }
            
            int movedCount = 0;
            for (String documentId : documentIds) {
                // 순환 참조 검사
                if (targetParentId != null && isCircularReference(documentId, targetParentId)) {
                    logger.warn("[MOVE WARNING] Circular reference detected, skipping document: {}", documentId);
                    continue;
                }
                
                // 문서 조회 및 이동
                DocumentEntity document = docsRepository.findById(documentId).orElse(null);
                if (document == null || document.getDeleted()) {
                    logger.warn("[MOVE WARNING] Document not found or deleted, skipping: {}", documentId);
                    continue;
                }
                
                // 부모 ID 변경
                String oldParentId = document.getParentId();
                document.updateParentId(targetParentId);
                docsRepository.save(document);
                
                liveEvents.publish(LiveEventType.DOCUMENT_MOVED, document.getId(), document.getTitle(), targetParentId,
                    document.getStatus() == null ? null : document.getStatus().name(), null);

                movedCount++;
                logger.info("[MOVE] Document moved: {} from [{}] to [{}]", 
                    documentId, 
                    oldParentId != null ? oldParentId : "ROOT", 
                    targetParentId != null ? targetParentId : "ROOT");
            }
            
            logger.info("[MOVE COMPLETED] Successfully moved {} out of {} documents", movedCount, documentIds.size());
            
        } catch (Exception e) {
            logger.error("[MOVE ERROR] Failed to move documents: {}", e.getMessage(), e);
            throw new RuntimeException("문서 이동에 실패했습니다: " + e.getMessage());
        }
    }
    
    /**
     * 순환 참조 검사: documentId를 targetParentId로 이동시킬 때 순환 참조가 발생하는지 확인
     */
    private boolean isCircularReference(String documentId, String targetParentId) {
        if (documentId.equals(targetParentId)) {
            return true; // 자기 자신으로 이동
        }
        
        // targetParentId의 조상들을 따라가면서 documentId가 있는지 확인
        String currentParentId = targetParentId;
        while (currentParentId != null) {
            if (currentParentId.equals(documentId)) {
                return true; // 순환 참조 발견
            }
            
            DocumentEntity parent = docsRepository.findById(currentParentId).orElse(null);
            if (parent == null || parent.getDeleted()) {
                break;
            }
            currentParentId = parent.getParentId();
        }
        
        return false;
    }
    
    @Override
    public DocumentStatisticsResponse getDocumentStatistics() {
        try {
            // 각 통계 정보를 병렬로 조회
            Long totalFolders = docsRepository.countTotalFolders();
            Long totalDocuments = docsRepository.countTotalDocuments();
            Long uploadingDocuments = docsRepository.countUploadingDocuments();
            Long indexingDocuments = docsRepository.countIndexingDocuments();
            Long completedDocuments = docsRepository.countCompletedDocuments();
            
            logger.info("[STATISTICS] Folders: {}, Documents: {}, Uploading: {}, Indexing: {}, Completed: {}", 
                totalFolders, totalDocuments, uploadingDocuments, indexingDocuments, completedDocuments);
            
            return new DocumentStatisticsResponse(
                totalFolders != null ? totalFolders : 0L,
                totalDocuments != null ? totalDocuments : 0L,
                uploadingDocuments != null ? uploadingDocuments : 0L,
                indexingDocuments != null ? indexingDocuments : 0L,
                completedDocuments != null ? completedDocuments : 0L
            );
            
        } catch (Exception e) {
            logger.error("[STATISTICS ERROR] Failed to get document statistics: {}", e.getMessage(), e);
            throw new RuntimeException("문서 통계 조회에 실패했습니다: " + e.getMessage());
        }
    }
    
    @Override
    public List<FolderListResponse> getAllFolders() {
        try {
            // 삭제되지 않은 모든 폴더를 조회
            List<DocumentEntity> folders = docsRepository.findAllFoldersOrderByTitleAsc();
            
            // DocumentEntity를 FolderListResponse로 변환하고 Java에서 정렬
            List<FolderListResponse> folderList = folders.stream()
                .map(folder -> new FolderListResponse(folder.getId(), folder.getTitle()))
                .sorted((f1, f2) -> f1.getTitle().compareToIgnoreCase(f2.getTitle()))  // 대소문자 무시하고 정렬
                .collect(Collectors.toList());
            
            logger.info("[FOLDER LIST] Retrieved {} folders, sorted by title", folderList.size());
            
            return folderList;
            
        } catch (Exception e) {
            logger.error("[FOLDER LIST ERROR] Failed to get folder list: {}", e.getMessage(), e);
            throw new RuntimeException("폴더 목록 조회에 실패했습니다: " + e.getMessage());
        }
    }
    
    @Override
    public void updateDocumentStatus(String documentId, DocumentStatus status) {
        try {
            if (documentId == null || documentId.trim().isEmpty()) {
                throw new IllegalArgumentException("문서 ID가 필요합니다.");
            }
            
            if (status == null) {
                throw new IllegalArgumentException("상태가 필요합니다.");
            }
            
            // 문서 조회
            DocumentEntity document = docsRepository.findById(documentId).orElse(null);
            if (document == null || document.getDeleted()) {
                throw new IllegalArgumentException("문서를 찾을 수 없습니다: " + documentId);
            }
            
            // 상태 업데이트
            DocumentStatus previousStatus = document.getStatus();
            document.updateStatus(status);
            docsRepository.save(document);
            liveEvents.publish(LiveEventType.DOCUMENT_STATUS, document.getId(), document.getTitle(), document.getParentId(),
                status.name(), null);
            
            logger.info("[STATUS UPDATE] Document {} status updated from {} to {}", 
                documentId, previousStatus, status);
            
        } catch (Exception e) {
            logger.error("[STATUS UPDATE ERROR] Failed to update document status: {}", e.getMessage(), e);
            throw new RuntimeException("문서 상태 업데이트에 실패했습니다: " + e.getMessage());
        }
    }
}