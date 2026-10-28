package com.docsportal.docs.controller;

import static com.docsportal.docs.common.Path.API_DOCS;
import static com.docsportal.docs.common.Path.API_DOCS_CHILDREN;
import static com.docsportal.docs.common.Path.API_DOCS_FOLDER;
import static com.docsportal.docs.common.Path.API_DOCS_FOLDERS;
import static com.docsportal.docs.common.Path.API_DOCS_ID;
import static com.docsportal.docs.common.Path.API_DOCS_MOVE;
import static com.docsportal.docs.common.Path.API_DOCS_MULTIPLE;
import static com.docsportal.docs.common.Path.API_DOCS_ROOT;
import static com.docsportal.docs.common.Path.API_DOCS_SEARCH;
import static com.docsportal.docs.common.Path.API_DOCS_STATISTICS;
import static com.docsportal.docs.common.Path.API_DOCS_STATUS;
import static com.docsportal.docs.common.Path.API_DOCS_TREE;
import static com.docsportal.docs.common.Path.API_DOCS_TREE_FULL;
import static com.docsportal.docs.common.Path.API_DOCS_TREE_PARENT;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.docsportal.docs.common.RestResponse;
import com.docsportal.docs.format.request.DocumentMoveRequest;
import com.docsportal.docs.format.request.DocumentStatusUpdateRequest;
import com.docsportal.docs.format.response.DocumentCreateResponse;
import com.docsportal.docs.format.response.DocumentResponse;
import com.docsportal.docs.format.response.DocumentStatisticsResponse;
import com.docsportal.docs.format.response.DocumentTreeResponse;
import com.docsportal.docs.format.response.FolderListResponse;
import com.docsportal.docs.format.response.SearchResultResponse;
import com.docsportal.docs.service.DocumentService;
import com.docsportal.docs.service.ExternalService;

@RestController
public class DocumentController {

    private final DocumentService documentService;
    private final ExternalService externalService;

    public DocumentController(DocumentService documentService, ExternalService externalService) {
        this.documentService = documentService;
        this.externalService = externalService;
    }

    // 문서 업로드 (비동기)
    @PostMapping(value = API_DOCS, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RestResponse<Boolean> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "parentId", required = false) String parentId) {
        
        try {
            if (file.isEmpty()) {
                return new RestResponse<>(
                    java.time.LocalDateTime.now().toString(),
                    "파일이 비어있습니다.", 
                    null, 
                    HttpStatus.BAD_REQUEST.value()
                );
            }
            
            // Service에서 비동기 처리
            documentService.uploadDocumentAsync(file, parentId);
            
            // 즉시 true 응답 반환
            return RestResponse.responseOk(true);
            
        } catch (Exception e) {
            return new RestResponse<>(
                java.time.LocalDateTime.now().toString(),
                "파일 업로드 처리 중 오류가 발생했습니다.", 
                null, 
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            );
        }
    }

    // 최상위 문서 트리 조회 (children 없이)
    @GetMapping(value = API_DOCS_TREE)
    public RestResponse<DocumentTreeResponse> getDocumentTree() {
        try {
            DocumentTreeResponse treeResponse = documentService.getDocumentTree();
            return RestResponse.responseOk(treeResponse);
        } catch (Exception e) {
            return new RestResponse<>(
                java.time.LocalDateTime.now().toString(),
                "문서 트리 조회에 실패했습니다.", 
                null, 
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            );
        }
    }

    // 전체 계층 구조 포함 트리 조회
    @GetMapping(value = API_DOCS_TREE_FULL)
    public RestResponse<DocumentTreeResponse> getFullDocumentTree() {
        try {
            DocumentTreeResponse treeResponse = documentService.getFullDocumentTree();
            return RestResponse.responseOk(treeResponse);
        } catch (Exception e) {
            return new RestResponse<>(
                java.time.LocalDateTime.now().toString(),
                "전체 문서 트리 조회에 실패했습니다.", 
                null, 
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            );
        }
    }

    // 특정 부모의 자식 트리 조회
    @GetMapping(value = API_DOCS_TREE_PARENT)
    public RestResponse<DocumentTreeResponse> getDocumentTreeByParent(@PathVariable("parentId") String parentId) {
        try {
            DocumentTreeResponse treeResponse = documentService.getDocumentTreeByParent(parentId);
            return RestResponse.responseOk(treeResponse);
        } catch (Exception e) {
            return new RestResponse<>(
                java.time.LocalDateTime.now().toString(),
                "문서 트리 조회에 실패했습니다.", 
                null, 
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            );
        }
    }

    // 루트 문서들 조회
    @GetMapping(value = API_DOCS_ROOT)
    public RestResponse<List<DocumentResponse>> getRootDocuments() {
        try {
            List<DocumentResponse> rootDocuments = documentService.getRootDocuments();
            return RestResponse.responseOk(rootDocuments);
        } catch (Exception e) {
            return new RestResponse<>(
                java.time.LocalDateTime.now().toString(),
                "루트 문서 조회에 실패했습니다.", 
                null, 
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            );
        }
    }

    // 특정 부모의 직접적인 자식들 조회
    @GetMapping(value = API_DOCS_CHILDREN)
    public RestResponse<List<DocumentResponse>> getChildDocuments(@PathVariable String parentId) {
        try {
            List<DocumentResponse> childDocuments = documentService.getChildDocuments(parentId);
            return RestResponse.responseOk(childDocuments);
        } catch (Exception e) {
            return new RestResponse<>(
                java.time.LocalDateTime.now().toString(),
                "자식 문서 조회에 실패했습니다.", 
                null, 
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            );
        }
    }

    // 새 폴더 생성
    @PostMapping(value = API_DOCS_FOLDER)
    public RestResponse<DocumentCreateResponse> createFolder(
            @RequestParam("title") String title,
            @RequestParam(value = "parentId", required = false) String parentId) {
        try {
            DocumentCreateResponse response = documentService.createFolder(title, parentId);
            return RestResponse.responseOk(response);
        } catch (Exception e) {
            return new RestResponse<>(
                java.time.LocalDateTime.now().toString(),
                "폴더 생성 중 오류가 발생했습니다: " + e.getMessage(),
                null,
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            );
        }
    }

    // 다중 문서 생성
    @PostMapping(value = API_DOCS_MULTIPLE, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RestResponse<List<DocumentCreateResponse>> createMultipleDocuments(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "parentId", required = false) String parentId) {
        try {
            if (files == null || files.length == 0) {
                return new RestResponse<>(
                    java.time.LocalDateTime.now().toString(),
                    "업로드할 파일이 없습니다.",
                    null,
                    HttpStatus.BAD_REQUEST.value()
                );
            }

            List<DocumentCreateResponse> responses = documentService.createDocuments(files, parentId);
            return RestResponse.responseOk(responses);
        } catch (Exception e) {
            return new RestResponse<>(
                java.time.LocalDateTime.now().toString(),
                "다중 문서 생성 중 오류가 발생했습니다: " + e.getMessage(),
                null,
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            );
        }
    }

    // 문서/폴더 이동
    @PostMapping(value = API_DOCS_MOVE)
    public RestResponse<Boolean> moveDocuments(@RequestBody DocumentMoveRequest request) {
        try {
            if (request.getDocumentIds() == null || request.getDocumentIds().isEmpty()) {
                return new RestResponse<>(
                    java.time.LocalDateTime.now().toString(),
                    "이동할 문서 ID가 없습니다.",
                    null,
                    HttpStatus.BAD_REQUEST.value()
                );
            }

            documentService.moveDocuments(request.getDocumentIds(), request.getTargetParentId());
            return RestResponse.responseOk(true);
        } catch (IllegalArgumentException e) {
            return new RestResponse<>(
                java.time.LocalDateTime.now().toString(),
                e.getMessage(),
                null,
                HttpStatus.BAD_REQUEST.value()
            );
        } catch (Exception e) {
            return new RestResponse<>(
                java.time.LocalDateTime.now().toString(),
                "문서 이동 중 오류가 발생했습니다: " + e.getMessage(),
                null,
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            );
        }
    }

    // 문서 통계 조회
    @GetMapping(value = API_DOCS_STATISTICS)
    public RestResponse<DocumentStatisticsResponse> getDocumentStatistics() {
        try {
            DocumentStatisticsResponse statistics = documentService.getDocumentStatistics();
            return RestResponse.responseOk(statistics);
        } catch (Exception e) {
            return new RestResponse<>(
                java.time.LocalDateTime.now().toString(),
                "문서 통계 조회 중 오류가 발생했습니다: " + e.getMessage(),
                null,
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            );
        }
    }

    // 폴더 목록 조회
    @GetMapping(value = API_DOCS_FOLDERS)
    public RestResponse<List<FolderListResponse>> getAllFolders() {
        try {
            List<FolderListResponse> folders = documentService.getAllFolders();
            return RestResponse.responseOk(folders);
        } catch (Exception e) {
            return new RestResponse<>(
                java.time.LocalDateTime.now().toString(),
                "폴더 목록 조회 중 오류가 발생했습니다: " + e.getMessage(),
                null,
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            );
        }
    }

    // 문서 삭제
    @DeleteMapping(value = API_DOCS_ID)
    public RestResponse<Boolean> deleteDocument(@PathVariable("documentId") String documentId) {
        try {
            documentService.deleteDocument(documentId);
            return RestResponse.responseOk(true);
        } catch (RuntimeException e) {
            // RuntimeException의 원인이 IllegalArgumentException인지 확인
            if (e.getCause() instanceof IllegalArgumentException || e.getMessage().contains("문서를 찾을 수 없습니다")) {
                return new RestResponse<>(
                    java.time.LocalDateTime.now().toString(),
                    e.getMessage(),
                    null,
                    HttpStatus.NOT_FOUND.value()
                );
            }
            return new RestResponse<>(
                java.time.LocalDateTime.now().toString(),
                "문서 삭제 중 오류가 발생했습니다: " + e.getMessage(),
                null,
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            );
        } catch (Exception e) {
            return new RestResponse<>(
                java.time.LocalDateTime.now().toString(),
                "문서 삭제 중 오류가 발생했습니다: " + e.getMessage(),
                null,
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            );
        }
    }

    // 문서 상태 업데이트
    @PostMapping(value = API_DOCS_STATUS)
    public RestResponse<Boolean> updateDocumentStatus(@RequestBody DocumentStatusUpdateRequest request) {
        try {
            if (request.getDocumentId() == null || request.getDocumentId().trim().isEmpty()) {
                return new RestResponse<>(
                    java.time.LocalDateTime.now().toString(),
                    "문서 ID가 필요합니다.",
                    null,
                    HttpStatus.BAD_REQUEST.value()
                );
            }
            
            if (request.getStatus() == null) {
                return new RestResponse<>(
                    java.time.LocalDateTime.now().toString(),
                    "상태가 필요합니다.",
                    null,
                    HttpStatus.BAD_REQUEST.value()
                );
            }

            documentService.updateDocumentStatus(request.getDocumentId(), request.getStatus());
            return RestResponse.responseOk(true);
        } catch (IllegalArgumentException e) {
            return new RestResponse<>(
                java.time.LocalDateTime.now().toString(),
                e.getMessage(),
                null,
                HttpStatus.BAD_REQUEST.value()
            );
        } catch (Exception e) {
            return new RestResponse<>(
                java.time.LocalDateTime.now().toString(),
                "문서 상태 업데이트 중 오류가 발생했습니다: " + e.getMessage(),
                null,
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            );
        }
    }

    // 문서 검색
    @GetMapping(value = API_DOCS_SEARCH)
    public RestResponse<SearchResultResponse> searchDocuments(@RequestParam("q") String query) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return new RestResponse<>(
                    java.time.LocalDateTime.now().toString(),
                    "검색 쿼리가 필요합니다.",
                    null,
                    HttpStatus.BAD_REQUEST.value()
                );
            }

            SearchResultResponse searchResult = externalService.searchDocuments(query.trim());
            return RestResponse.responseOk(searchResult);
        } catch (Exception e) {
            return new RestResponse<>(
                java.time.LocalDateTime.now().toString(),
                "문서 검색 중 오류가 발생했습니다: " + e.getMessage(),
                null,
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            );
        }
    }

    // Test
    @RequestMapping(value = "/hello")
    public String greeting() {
        return "HELLO, WORLD!";
    }
}
