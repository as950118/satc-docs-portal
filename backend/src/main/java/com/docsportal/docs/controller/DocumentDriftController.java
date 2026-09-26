package com.docsportal.docs.controller;

import static com.docsportal.docs.common.Path.API_DOCS_CONTENT;
import static com.docsportal.docs.common.Path.API_DOCS_DRIFT;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.docsportal.docs.common.RestResponse;
import com.docsportal.docs.common.SyncTokenVerifier;
import com.docsportal.docs.format.request.DocumentDriftReportRequest;
import com.docsportal.docs.format.response.DocumentDriftResponse;
import com.docsportal.docs.service.DocumentDriftException;
import com.docsportal.docs.service.DocumentDriftService;
import com.docsportal.docs.service.DocumentDriftService.DocumentContent;

/**
 * 문서-코드 드리프트 감지 (ARCH-0003)
 *
 * CORS: 이 프로젝트는 전역 CorsConfigurationSource 빈이 MVC에 연결되어 있지 않아
 * 기존 컨트롤러와 같이 @CrossOrigin 으로 선언한다 (프론트 배지가 GET /drift 를 호출).
 */
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
public class DocumentDriftController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentDriftController.class);

    private final DocumentDriftService documentDriftService;
    private final SyncTokenVerifier tokenVerifier;

    public DocumentDriftController(DocumentDriftService documentDriftService, SyncTokenVerifier tokenVerifier) {
        this.documentDriftService = documentDriftService;
        this.tokenVerifier = tokenVerifier;
    }

    // 드리프트 검사용 텍스트 본문
    @GetMapping(value = API_DOCS_CONTENT)
    public ResponseEntity<?> getContent(@PathVariable String documentId) {
        try {
            DocumentContent content = documentDriftService.getContent(documentId);
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(content.bytes());
        } catch (DocumentDriftException e) {
            return error(e.getStatus(), e.getMessage());
        }
    }

    // CI 검사 결과 보고
    @PostMapping(value = API_DOCS_DRIFT)
    public ResponseEntity<RestResponse<DocumentDriftResponse>> report(
            @RequestHeader(value = SyncTokenVerifier.HEADER, required = false) String token,
            @PathVariable String documentId,
            @RequestBody(required = false) DocumentDriftReportRequest request) {
        switch (tokenVerifier.verify(token)) {
            case DISABLED:
                return error(HttpStatus.SERVICE_UNAVAILABLE, "드리프트 보고가 비활성화되어 있습니다 (docs.sync.token 미설정).");
            case INVALID:
                return error(HttpStatus.UNAUTHORIZED, "유효하지 않은 동기화 토큰입니다.");
            default:
                break;
        }
        try {
            return ResponseEntity.ok(RestResponse.responseOk(documentDriftService.report(documentId, request)));
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (DocumentDriftException e) {
            return error(e.getStatus(), e.getMessage());
        } catch (Exception e) {
            logger.error("[DRIFT] Unexpected error for {}: {}", documentId, e.getMessage(), e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "드리프트 결과 저장 중 오류가 발생했습니다.");
        }
    }

    // 드리프트 상태 조회 (문서 화면 배지용)
    @GetMapping(value = API_DOCS_DRIFT)
    public ResponseEntity<RestResponse<DocumentDriftResponse>> get(@PathVariable String documentId) {
        try {
            return ResponseEntity.ok(RestResponse.responseOk(documentDriftService.get(documentId)));
        } catch (DocumentDriftException e) {
            return error(e.getStatus(), e.getMessage());
        }
    }

    private static <T> ResponseEntity<RestResponse<T>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
            .body(new RestResponse<>(LocalDateTime.now().toString(), message, null, status.value()));
    }
}
