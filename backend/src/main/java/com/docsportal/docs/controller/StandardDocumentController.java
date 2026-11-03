package com.docsportal.docs.controller;

import static com.docsportal.docs.common.Path.API_DOCS_STANDARD;
import static com.docsportal.docs.common.Path.API_DOCS_STANDARD_HISTORY;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.docsportal.docs.common.RestResponse;
import com.docsportal.docs.common.Roles;
import com.docsportal.docs.format.request.DocumentSyncRequest;
import com.docsportal.docs.format.response.StandardDocumentResponse;
import com.docsportal.docs.format.response.UserInfoResponse;
import com.docsportal.docs.persistence.AppUser;
import com.docsportal.docs.repository.AppUserRepository;
import com.docsportal.docs.service.AuthService;
import com.docsportal.docs.service.DocumentSyncStorageException;
import com.docsportal.docs.service.SpecException;
import com.docsportal.docs.service.StandardDocumentService;
import com.docsportal.docs.service.UploadActor;

/**
 * 표준문서 등록·이력 API. 공용 동기화 토큰 대신 로그인 사용자의 Bearer 세션(MCP OAuth 토큰 포함)으로 호출하며,
 * 등록자/수정자가 기록된다. SpecController 와 같이 실제 HTTP 상태 코드를 반환한다.
 */
@RestController
public class StandardDocumentController {

    private static final String BEARER = "Bearer ";
    /** 조회 전용 계정(USER 만 가진 계정)은 등록할 수 없다. */
    static final Set<String> WRITER_ROLES = Set.of(
        Roles.ADMIN, Roles.PLANNER, Roles.BACKEND, Roles.FRONTEND, Roles.DATA, Roles.REVIEWER, Roles.UPLOADER);

    private final StandardDocumentService service;
    private final AuthService authService;
    private final AppUserRepository users;

    public StandardDocumentController(StandardDocumentService service, AuthService authService,
                                      AppUserRepository users) {
        this.service = service;
        this.authService = authService;
        this.users = users;
    }

    @PutMapping(value = API_DOCS_STANDARD, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RestResponse<StandardDocumentResponse.Registered>> register(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("file") MultipartFile file,
            @RequestParam("sourceKey") String sourceKey,
            @RequestParam("sourceRepo") String sourceRepo,
            @RequestParam("sourcePath") String sourcePath,
            @RequestParam("sourceCommit") String sourceCommit,
            @RequestParam("contentHash") String contentHash,
            @RequestParam(value = "parentId", required = false) String parentId) throws IOException {
        UserInfoResponse user = authenticate(authorization);
        List<String> roles = user.getRoles() == null ? List.of() : user.getRoles();
        if (roles.stream().noneMatch(WRITER_ROLES::contains)) {
            throw new SpecException(HttpStatus.FORBIDDEN, "표준문서를 등록할 권한(역할)이 없습니다.");
        }
        DocumentSyncRequest request = new DocumentSyncRequest(sourceKey, sourceRepo, sourcePath, sourceCommit,
            contentHash, parentId, file.getOriginalFilename(), file.getContentType(), file.getBytes());
        return ResponseEntity.ok(RestResponse.responseOk(service.register(request, uploadActor(user))));
    }

    @GetMapping(API_DOCS_STANDARD_HISTORY)
    public ResponseEntity<RestResponse<StandardDocumentResponse.History>> history(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("sourceKey") String sourceKey) {
        authenticate(authorization);
        return ResponseEntity.ok(RestResponse.responseOk(service.history(sourceKey)));
    }

    @ExceptionHandler(SpecException.class)
    public ResponseEntity<RestResponse<String>> handleSpec(SpecException e) {
        return error(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<RestResponse<String>> handleBadRequest(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<RestResponse<String>> handleConflict(Exception e) {
        return error(HttpStatus.CONFLICT, "같은 문서에 대한 동시 등록이 충돌했습니다. 다시 시도하세요.");
    }

    @ExceptionHandler(DocumentSyncStorageException.class)
    public ResponseEntity<RestResponse<String>> handleStorage(DocumentSyncStorageException e) {
        return error(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    /** 세션 응답의 id 는 username 이라, 추적용 숫자 식별자는 AppUser 에서 찾는다. */
    private UploadActor uploadActor(UserInfoResponse user) {
        Long id = users.findByUsername(user.getUsername()).map(AppUser::getId)
            .orElseThrow(() -> new SpecException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
        return new UploadActor(id, user.getUsername());
    }

    private UserInfoResponse authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER)) {
            throw new SpecException(HttpStatus.UNAUTHORIZED, "세션이 필요합니다.");
        }
        try {
            return authService.getCurrentUser(authorization.substring(BEARER.length()));
        } catch (IllegalArgumentException e) {
            throw new SpecException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    private static ResponseEntity<RestResponse<String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(RestResponse.responseError(message, status));
    }
}
