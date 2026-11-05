package com.docsportal.docs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.docsportal.docs.format.dto.ExternalSearchResponse;
import com.docsportal.docs.format.dto.ExternalSearchResultItem;
import com.docsportal.docs.format.request.ExternalApiRequest;
import com.docsportal.docs.format.response.BreadcrumbItem;
import com.docsportal.docs.format.response.SearchResultItem;
import com.docsportal.docs.format.response.SearchResultResponse;
import com.docsportal.docs.persistence.DocumentEntity;
import com.docsportal.docs.repository.DocsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 통합 외부 API 호출 서비스 구현체
 */
@Service
public class ExternalServiceImpl implements ExternalService {
    
    private static final Logger logger = LoggerFactory.getLogger(ExternalServiceImpl.class);
    
    @Value("${external.api.host:http://127.0.0.1}")
    private String apiHost;
    
    @Value("${external.api.port:8000}")
    private int apiPort;
    
    @Value("${external.api.enabled:true}")
    private boolean externalApiEnabled;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final DocsRepository docsRepository;
    
    public ExternalServiceImpl(RestTemplate restTemplate, ObjectMapper objectMapper, DocsRepository docsRepository) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.docsRepository = docsRepository;
    }

    // Render 의 fromService host 처럼 스킴 없는 호스트 이름도 받는다
    private String indexingUrl(String path) {
        String base = apiHost.contains("://") ? apiHost : "http://" + apiHost;
        return base + ":" + apiPort + path;
    }
    
    @Override
    @Async("fileUploadExecutor")
    public void notifyDocumentUploaded(String bucketName, String objectName, String title) {
        if (!externalApiEnabled) {
            logger.info("[EXTERNAL API] External API is disabled, skipping notification");
            return;
        }
        
        try {
            ExternalApiRequest request = new ExternalApiRequest(bucketName, objectName, title);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<ExternalApiRequest> httpEntity = new HttpEntity<>(request, headers);
            
            String url = indexingUrl("/api/v1/process-document");
            
            logger.info("[EXTERNAL API] Calling external API: {} with request: {}", url, request);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                httpEntity,
                String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("[EXTERNAL API] Successfully called external API for bucket: {}, object: {}", 
                    bucketName, objectName);
            } else {
                logger.warn("[EXTERNAL API] External API returned non-success status: {} for bucket: {}, object: {}", 
                    response.getStatusCode(), bucketName, objectName);
            }
            
        } catch (Exception e) {
            logger.error("[EXTERNAL API ERROR] Failed to call external API for bucket: {}, object: {}, error: {}", 
                bucketName, objectName, e.getMessage(), e);
        }
    }
    
    @Override
    public SearchResultResponse searchDocuments(String query) {
        try {
            if (query == null || query.trim().isEmpty()) {
                logger.warn("[SEARCH] Empty query provided");
                return new SearchResultResponse("", 0, 0, 0.0, Collections.emptyList());
            }
            
            // 외부 검색 API 호출
            String baseUrl = indexingUrl("/api/v1/search-documents");
            String url = UriComponentsBuilder
                    .fromUriString(baseUrl)
                    .queryParam("q", query.trim())
                    .build()
                    .toUriString();
            
            logger.info("[SEARCH] Base URL: {}", baseUrl);
            logger.info("[SEARCH] Full URL: {}", url);
            logger.info("[SEARCH] Original query: '{}'", query);
            logger.info("[SEARCH] Trimmed query: '{}'", query.trim());
            
            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            headers.set("Content-Type", "application/json; charset=UTF-8");
            headers.set("User-Agent", "DocsBackend/1.0");
            
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            logger.info("[SEARCH] Request headers: {}", headers);
            
            // 먼저 원본 JSON 응답을 문자열로 받아서 로깅
            ResponseEntity<String> rawResponseEntity = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                entity, 
                String.class
            );
            
            String rawResponse = rawResponseEntity.getBody();
            logger.info("[SEARCH] Raw response body: {}", rawResponse);
            
            // 원본 응답에서 중복 문서 ID 검사
            if (rawResponse != null && rawResponse.contains("document_id")) {
                checkRawResponseForDuplicates(rawResponse);
            }
            
            // JSON을 객체로 파싱
            ExternalSearchResponse externalResponse = null;
            try {
                externalResponse = objectMapper.readValue(rawResponse, ExternalSearchResponse.class);
                logger.info("[SEARCH] Successfully parsed JSON response");
            } catch (Exception parseException) {
                logger.error("[SEARCH] Failed to parse JSON response: {}", parseException.getMessage());
                throw parseException;
            }
            
            logger.info("[SEARCH] Response status: {}", rawResponseEntity.getStatusCode());
            logger.info("[SEARCH] Response headers: {}", rawResponseEntity.getHeaders());
            
            if (externalResponse == null) {
                logger.warn("[SEARCH] External API returned null response");
                return new SearchResultResponse(query, 0, 0, 0.0, Collections.emptyList());
            }
            
            logger.info("[SEARCH] External API response - query: '{}', total: {}, returned: {}", 
                externalResponse.getQuery(), externalResponse.getTotalResults(), externalResponse.getReturnedResults());
            
            // 응답 데이터 포맷팅
            logger.info("[SEARCH] Raw external results count: {}", 
                externalResponse.getResults() != null ? externalResponse.getResults().size() : 0);
            
            List<SearchResultItem> formattedResults = formatSearchResults(externalResponse.getResults());
            
            logger.info("[SEARCH] Formatted results count: {}", formattedResults.size());
            
            SearchResultResponse response = new SearchResultResponse(
                externalResponse.getQuery(),
                externalResponse.getTotalResults(),
                externalResponse.getReturnedResults(),
                externalResponse.getMaxScore(),
                formattedResults
            );
            
            logger.info("[SEARCH] Final response - query: '{}', total: {}, returned: {}, formatted: {}", 
                response.getQuery(), response.getTotalResults(), response.getReturnedResults(), formattedResults.size());
            
            return response;
            
        } catch (Exception e) {
            logger.error("[SEARCH ERROR] Failed to search documents for query '{}': {}", query, e.getMessage(), e);
            // 오류 발생 시 빈 결과 반환
            return new SearchResultResponse(query, 0, 0, 0.0, Collections.emptyList());
        }
    }
    
    /**
     * 외부 API 응답을 프론트엔드용 형식으로 변환
     */
    private List<SearchResultItem> formatSearchResults(List<ExternalSearchResultItem> externalResults) {
        if (externalResults == null || externalResults.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 모든 문서 ID 추출
        List<String> documentIds = externalResults.stream()
                .map(ExternalSearchResultItem::getDocumentId)
                .filter(id -> id != null && !id.trim().isEmpty())
                .distinct()
                .collect(Collectors.toList());
        
        // 한 번에 모든 문서 정보 조회 (성능 최적화)
        Map<String, DocumentEntity> documentMap = fetchDocumentMap(documentIds);
        
        // breadcrumb 구성을 위해 모든 관련 부모 문서들도 조회
        Map<String, DocumentEntity> allDocumentsMap = fetchAllRelatedDocuments(documentMap);
        
        List<SearchResultItem> results = externalResults.stream()
                .map(item -> convertToSearchResultItem(item, documentMap, allDocumentsMap))
                .collect(Collectors.toList());
        
        // 중복 highlight 검사
        checkForDuplicateHighlights(results);
        
        return results;
    }
    
    /**
     * 문서 ID 목록으로 문서 엔티티들을 한 번에 조회 (성능 최적화)
     */
    private Map<String, DocumentEntity> fetchDocumentMap(List<String> documentIds) {
        Map<String, DocumentEntity> documentMap = new HashMap<>();
        
        if (documentIds.isEmpty()) {
            return documentMap;
        }
        
        try {
            List<DocumentEntity> documents = docsRepository.findAllById(documentIds);
            
            for (DocumentEntity doc : documents) {
                if (!doc.getDeleted()) {
                    documentMap.put(doc.getId(), doc);
                }
            }
            
            logger.debug("[SEARCH CONVERT] Fetched {} documents from DB for {} document IDs", 
                documentMap.size(), documentIds.size());
            
        } catch (Exception e) {
            logger.error("[SEARCH CONVERT] Failed to fetch documents: {}", e.getMessage(), e);
        }
        
        return documentMap;
    }
    
    /**
     * breadcrumb 구성을 위해 모든 관련 부모 문서들을 배치로 조회 (성능 최적화)
     */
    private Map<String, DocumentEntity> fetchAllRelatedDocuments(Map<String, DocumentEntity> initialDocuments) {
        Map<String, DocumentEntity> allDocuments = new HashMap<>(initialDocuments);
        Set<String> parentIdsToFetch = new HashSet<>();
        
        // 1단계: 초기 문서들의 부모 ID 수집
        for (DocumentEntity doc : initialDocuments.values()) {
            if (doc.getParentId() != null && !doc.getParentId().trim().isEmpty()) {
                parentIdsToFetch.add(doc.getParentId());
            }
        }
        
        // 2단계: 재귀적으로 모든 조상 문서들 조회 (최대 깊이 제한)
        int maxDepth = 10; // 순환 참조 및 성능 보호
        int currentDepth = 0;
        
        while (!parentIdsToFetch.isEmpty() && currentDepth < maxDepth) {
            // 아직 조회하지 않은 부모 ID들만 필터링
            List<String> newParentIds = parentIdsToFetch.stream()
                    .filter(id -> !allDocuments.containsKey(id))
                    .collect(Collectors.toList());
            
            if (newParentIds.isEmpty()) {
                break; // 더 이상 조회할 부모가 없음
            }
            
            try {
                // 배치로 부모 문서들 조회
                List<DocumentEntity> parentDocuments = docsRepository.findAllById(newParentIds);
                
                Set<String> nextLevelParentIds = new HashSet<>();
                for (DocumentEntity parent : parentDocuments) {
                    if (!parent.getDeleted()) {
                        allDocuments.put(parent.getId(), parent);
                        
                        // 다음 레벨의 부모 ID 수집
                        if (parent.getParentId() != null && !parent.getParentId().trim().isEmpty()) {
                            nextLevelParentIds.add(parent.getParentId());
                        }
                    }
                }
                
                parentIdsToFetch = nextLevelParentIds;
                currentDepth++;
                
                logger.debug("[BREADCRUMB] Fetched {} parent documents at depth {}", 
                    parentDocuments.size(), currentDepth);
                
            } catch (Exception e) {
                logger.error("[BREADCRUMB] Failed to fetch parent documents at depth {}: {}", 
                    currentDepth, e.getMessage(), e);
                break;
            }
        }
        
        if (currentDepth >= maxDepth) {
            logger.warn("[BREADCRUMB] Reached maximum depth limit ({}), stopping breadcrumb traversal", maxDepth);
        }
        
        logger.debug("[BREADCRUMB] Total documents collected: {}, max depth reached: {}", 
            allDocuments.size(), currentDepth);
        
        return allDocuments;
    }
    
    /**
     * 서로 다른 문서에서 동일한 highlight가 나오는지 검사
     */
    private void checkForDuplicateHighlights(List<SearchResultItem> results) {
        Map<String, List<String>> highlightToDocuments = new HashMap<>();
        
        for (SearchResultItem result : results) {
            if (result.getHighlightContent() != null) {
                for (String highlight : result.getHighlightContent()) {
                    String normalizedHighlight = normalizeHighlightContent(highlight);
                    
                    highlightToDocuments.computeIfAbsent(normalizedHighlight, k -> new ArrayList<>())
                            .add(result.getDocumentId());
                }
            }
        }
        
        // 동일한 highlight를 가진 서로 다른 문서들 찾기
        for (Map.Entry<String, List<String>> entry : highlightToDocuments.entrySet()) {
            List<String> documentIds = entry.getValue();
            if (documentIds.size() > 1) {
                String highlightPreview = entry.getKey().length() > 100 ? 
                    entry.getKey().substring(0, 100) + "..." : entry.getKey();
                
                logger.warn("[SEARCH DUPLICATE] Same highlight found in {} different documents: {} - Preview: '{}'", 
                    documentIds.size(), documentIds, highlightPreview);
            }
        }
    }
    
    /**
     * 개별 검색 결과 항목 변환 (미리 조회된 문서 맵 사용)
     */
    private SearchResultItem convertToSearchResultItem(ExternalSearchResultItem externalItem, 
                                                       Map<String, DocumentEntity> documentMap, 
                                                       Map<String, DocumentEntity> allDocumentsMap) {
        String documentId = externalItem.getDocumentId();
        DocumentEntity document = documentMap.get(documentId);
        // DB 에 없는 문서(색인 전용)는 색인에 저장된 제목을 쓴다
        String title = document != null ? document.getTitle()
                : (externalItem.getTitle() != null && !externalItem.getTitle().isBlank() ? externalItem.getTitle() : documentId);
        List<BreadcrumbItem> breadcrumb = document != null ? buildBreadcrumb(document, allDocumentsMap) : new ArrayList<>();
        String objectName = null;
        List<String> highlightContent = new ArrayList<>();
        
        logger.debug("[SEARCH CONVERT] Processing item - documentId: {}, title: {}, breadcrumb size: {}", 
            documentId, title, breadcrumb.size());
        
        // metadata에서 object_name 추출
        if (externalItem.getMetadata() != null) {
            objectName = externalItem.getMetadata().getObjectName();
            logger.debug("[SEARCH CONVERT] Extracted objectName: {}", objectName);
        } else {
            logger.debug("[SEARCH CONVERT] No metadata found for documentId: {}", documentId);
        }
        
        // highlight에서 content 추출 및 중복 제거
        if (externalItem.getHighlight() != null && externalItem.getHighlight().getContent() != null) {
            List<String> rawHighlights = externalItem.getHighlight().getContent();
            logger.debug("[SEARCH CONVERT] Raw highlight items: {} for documentId: {}", 
                rawHighlights.size(), documentId);
            
            // 중복 제거 및 빈 문자열 필터링
            highlightContent = rawHighlights.stream()
                    .filter(content -> content != null && !content.trim().isEmpty())
                    .distinct()  // 정확한 중복 제거
                    .collect(Collectors.toList());
            
            // 유사한 내용의 중복 제거 (HTML 태그 제거 후 비교)
            highlightContent = removeSimilarHighlights(highlightContent);
            
            logger.debug("[SEARCH CONVERT] After deduplication: {} highlight items for documentId: {}", 
                highlightContent.size(), documentId);
                
            // 중복이 제거된 경우 로깅
            if (rawHighlights.size() != highlightContent.size()) {
                logger.info("[SEARCH CONVERT] Removed {} duplicate highlights for documentId: {}", 
                    rawHighlights.size() - highlightContent.size(), documentId);
            }
        } else {
            logger.debug("[SEARCH CONVERT] No highlight content found for documentId: {}", documentId);
        }
        
        SearchResultItem result = new SearchResultItem(documentId, title, breadcrumb, objectName, highlightContent);
        result.setIndexOnly(document == null);
        logger.debug("[SEARCH CONVERT] Created result item: documentId={}, title={}, breadcrumb size={}, objectName={}, highlightCount={}", 
            result.getDocumentId(), result.getTitle(), result.getBreadcrumb() != null ? result.getBreadcrumb().size() : 0,
            result.getObjectName(), result.getHighlightContent() != null ? result.getHighlightContent().size() : 0);
        
        return result;
    }
    
    /**
     * 문서의 breadcrumb 경로를 생성합니다 (루트부터 현재 문서까지의 경로)
     */
    private List<BreadcrumbItem> buildBreadcrumb(DocumentEntity document, Map<String, DocumentEntity> allDocumentsMap) {
        List<BreadcrumbItem> breadcrumb = new ArrayList<>();
        
        // 현재 문서부터 루트까지 역순으로 경로 수집
        List<DocumentEntity> pathToRoot = new ArrayList<>();
        DocumentEntity current = document;
        Set<String> visitedIds = new HashSet<>(); // 순환 참조 방지
        
        while (current != null) {
            // 순환 참조 검사
            if (visitedIds.contains(current.getId())) {
                logger.warn("[BREADCRUMB] Circular reference detected for document: {}", current.getId());
                break;
            }
            visitedIds.add(current.getId());
            
            // 폴더만 breadcrumb에 포함 (문서 자체는 제외)
            if (current.getIsFolder() != null && current.getIsFolder()) {
                pathToRoot.add(current);
            }
            
            // 부모로 이동
            if (current.getParentId() != null && !current.getParentId().trim().isEmpty()) {
                current = allDocumentsMap.get(current.getParentId());
            } else {
                // 루트에 도달
                break;
            }
        }
        
        // 역순 정렬 (루트부터 현재 폴더까지)
        Collections.reverse(pathToRoot);
        
        // BreadcrumbItem으로 변환
        for (DocumentEntity folder : pathToRoot) {
            String type = folder.getParentId() == null || folder.getParentId().trim().isEmpty() ? "root" : "folder";
            breadcrumb.add(new BreadcrumbItem(folder.getId(), folder.getTitle(), type));
        }
        
        logger.debug("[BREADCRUMB] Built breadcrumb with {} items for document: {}", 
            breadcrumb.size(), document.getId());
        
        return breadcrumb;
    }
    
    /**
     * 유사한 highlight content를 제거합니다.
     * HTML 태그를 제거하고 공백을 정규화한 후 비교합니다.
     */
    private List<String> removeSimilarHighlights(List<String> highlights) {
        if (highlights == null || highlights.size() <= 1) {
            return highlights;
        }
        
        List<String> result = new ArrayList<>();
        Set<String> normalizedContents = new HashSet<>();
        
        for (String highlight : highlights) {
            // HTML 태그 제거 및 공백 정규화
            String normalized = normalizeHighlightContent(highlight);
            
            // 정규화된 내용이 중복되지 않는 경우만 추가
            if (normalizedContents.add(normalized)) {
                result.add(highlight);
            } else {
                logger.debug("[SEARCH CONVERT] Removed similar highlight: {}", 
                    highlight.length() > 100 ? highlight.substring(0, 100) + "..." : highlight);
            }
        }
        
        return result;
    }
    
    /**
     * highlight content를 정규화합니다.
     * - HTML 태그 제거
     * - 연속된 공백을 하나로 변환
     * - 앞뒤 공백 제거
     */
    private String normalizeHighlightContent(String content) {
        if (content == null) {
            return "";
        }
        
        return content
                .replaceAll("<[^>]+>", "")  // HTML 태그 제거
                .replaceAll("\\s+", " ")    // 연속된 공백을 하나로
                .trim()                     // 앞뒤 공백 제거
                .toLowerCase();             // 대소문자 통일
    }
    
    /**
     * 원본 JSON 응답에서 중복 문서 ID나 이상한 패턴을 검사
     */
    private void checkRawResponseForDuplicates(String rawResponse) {
        try {
            // document_id 패턴 추출
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"document_id\"\\s*:\\s*\"([^\"]+)\"");
            java.util.regex.Matcher matcher = pattern.matcher(rawResponse);
            
            Map<String, Integer> documentIdCounts = new HashMap<>();
            while (matcher.find()) {
                String documentId = matcher.group(1);
                documentIdCounts.put(documentId, documentIdCounts.getOrDefault(documentId, 0) + 1);
            }
            
            // 중복된 document_id 찾기
            for (Map.Entry<String, Integer> entry : documentIdCounts.entrySet()) {
                if (entry.getValue() > 1) {
                    logger.warn("[SEARCH RAW] Duplicate document_id found in raw response: {} appears {} times", 
                        entry.getKey(), entry.getValue());
                }
            }
            
            logger.info("[SEARCH RAW] Total unique document_ids in response: {}", documentIdCounts.size());
            
        } catch (Exception e) {
            logger.debug("[SEARCH RAW] Failed to analyze raw response for duplicates: {}", e.getMessage());
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getIndexedDocument(String documentId) {
        try {
            String url = UriComponentsBuilder.fromUriString(indexingUrl("/api/v1/documents"))
                    .pathSegment(documentId).build().encode().toUriString();
            return restTemplate.getForObject(url, Map.class);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            return null;
        } catch (Exception e) {
            logger.error("[INDEXED DOC] Failed to fetch {}: {}", documentId, e.getMessage());
            return null;
        }
    }

    @Override
    @Async("fileUploadExecutor")
    public void notifyDocumentsDeleted(List<String> deletedFileNames) {
        if (!externalApiEnabled) {
            logger.info("[EXTERNAL API] External API is disabled, skipping delete notification");
            return;
        }
        
        if (deletedFileNames == null || deletedFileNames.isEmpty()) {
            logger.warn("[EXTERNAL API] No deleted file names provided for delete notification");
            return;
        }
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // 요청 본문을 객체로 감싸서 전송
            Map<String, List<String>> requestBody = new HashMap<>();
            requestBody.put("files", deletedFileNames);
            
            HttpEntity<Map<String, List<String>>> httpEntity = new HttpEntity<>(requestBody, headers);
            
            String url = indexingUrl("/api/v1/documents");
            logger.info("[EXTERNAL API] Calling delete notification API: {} with {} files: {}", 
                url, deletedFileNames.size(), deletedFileNames);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.DELETE,
                httpEntity,
                String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("[EXTERNAL API] Successfully notified document deletion for {} files", 
                    deletedFileNames.size());
            } else {
                logger.warn("[EXTERNAL API] Delete notification API returned non-success status: {} for files: {}", 
                    response.getStatusCode(), deletedFileNames);
            }
            
        } catch (Exception e) {
            logger.error("[EXTERNAL API ERROR] Failed to notify document deletion for files: {}, error: {}", 
                deletedFileNames, e.getMessage(), e);
        }
    }

    @Override
    @Async("fileUploadExecutor")
    public void reindexDocument(String bucketName, String documentFileName, String objectName, String title) {
        // self-invocation 이므로 아래 두 호출은 @Async 프록시를 거치지 않고 이 스레드에서 순서대로 실행된다
        notifyDocumentsDeleted(List.of(documentFileName));
        notifyDocumentUploaded(bucketName, objectName, title);
    }
}
