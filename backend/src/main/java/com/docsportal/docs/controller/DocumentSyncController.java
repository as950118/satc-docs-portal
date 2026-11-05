package com.docsportal.docs.controller;

import static com.docsportal.docs.common.Path.API_DOCS_SYNC;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.docsportal.docs.common.DocsAccess;
import com.docsportal.docs.common.DocsAccess.Level;
import com.docsportal.docs.common.RestResponse;
import com.docsportal.docs.format.request.DocumentSyncRequest;
import com.docsportal.docs.format.response.DocumentSyncResponse;
import com.docsportal.docs.service.DocumentSyncService;
import com.docsportal.docs.service.DocumentSyncStorageException;

/**
 * 코드 → 문서 자동 동기화 엔드포인트 (ARCH-0001, ADR-0003)
 *
 * 기존 업로드 API와 달리 동기 처리하고 실제 HTTP 상태 코드를 반환한다 (CI가 실패를 인지해야 함).
 */
@RestController
public class DocumentSyncController {

    static final String TOKEN_HEADER = "X-Docs-Sync-Token";
    private static final Logger logger = LoggerFactory.getLogger(DocumentSyncController.class);

    private final DocumentSyncService documentSyncService;
    private final byte[] syncToken;

    public DocumentSyncController(DocumentSyncService documentSyncService,
                                  @Value("${docs.sync.token:}") String syncToken) {
        this.documentSyncService = documentSyncService;
        this.syncToken = syncToken.getBytes(StandardCharsets.UTF_8);
    }

    @DocsAccess(Level.SYNC)
    @PutMapping(value = API_DOCS_SYNC, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RestResponse<DocumentSyncResponse>> sync(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @RequestParam("file") MultipartFile file,
            @RequestParam("sourceKey") String sourceKey,
            @RequestParam("sourceRepo") String sourceRepo,
            @RequestParam("sourcePath") String sourcePath,
            @RequestParam("sourceCommit") String sourceCommit,
            @RequestParam("contentHash") String contentHash,
            @RequestParam(value = "parentId", required = false) String parentId) {

        if (syncToken.length == 0) {
            // 토큰 미설정 시 엔드포인트 비활성 (무인증 개방 방지)
            return error(HttpStatus.SERVICE_UNAVAILABLE, "문서 동기화가 비활성화되어 있습니다 (docs.sync.token 미설정).");
        }
        if (token == null || !MessageDigest.isEqual(syncToken, token.getBytes(StandardCharsets.UTF_8))) {
            return error(HttpStatus.UNAUTHORIZED, "유효하지 않은 동기화 토큰입니다.");
        }

        try {
            DocumentSyncRequest request = new DocumentSyncRequest(
                sourceKey, sourceRepo, sourcePath, sourceCommit, contentHash, parentId,
                file.getOriginalFilename(), file.getContentType(), file.getBytes());
            return ResponseEntity.ok(RestResponse.responseOk(documentSyncService.sync(request)));
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException e) {
            // 같은 sourceKey 동시 요청 — 재시도하면 updated/unchanged 로 수렴한다
            logger.warn("[SYNC] Conflict for {}: {}", sourceKey, e.getMessage());
            return error(HttpStatus.CONFLICT, "같은 소스에 대한 동시 동기화가 충돌했습니다. 다시 시도하세요.");
        } catch (DocumentSyncStorageException e) {
            return error(HttpStatus.BAD_GATEWAY, e.getMessage());
        } catch (Exception e) {
            logger.error("[SYNC] Unexpected error for {}: {}", sourceKey, e.getMessage(), e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "문서 동기화 중 오류가 발생했습니다.");
        }
    }

    private static ResponseEntity<RestResponse<DocumentSyncResponse>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
            .body(new RestResponse<>(LocalDateTime.now().toString(), message, null, status.value()));
    }
}
