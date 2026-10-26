package com.docsportal.docs.controller;

import static com.docsportal.docs.common.Path.API_SPECS;
import static com.docsportal.docs.common.Path.API_SPECS_APPROVE;
import static com.docsportal.docs.common.Path.API_SPECS_ID;
import static com.docsportal.docs.common.Path.API_SPECS_TEMPLATE_PRD;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.docsportal.docs.common.RestResponse;
import com.docsportal.docs.format.request.SpecApproveRequest;
import com.docsportal.docs.format.request.SpecWriteRequest;
import com.docsportal.docs.format.response.SpecDetailResponse;
import com.docsportal.docs.format.response.SpecSummaryResponse;
import com.docsportal.docs.format.response.SpecTemplateResponse;
import com.docsportal.docs.format.response.UserInfoResponse;
import com.docsportal.docs.persistence.SpecKind;
import com.docsportal.docs.persistence.SpecStatus;
import com.docsportal.docs.service.AuthService;
import com.docsportal.docs.service.SpecActor;
import com.docsportal.docs.service.SpecException;
import com.docsportal.docs.service.SpecService;

/**
 * 스펙 워크플로우 API (ARCH-0004). 세션 인터셉터가 없으므로 모든 요청에서 Bearer 세션을 직접 검증하고,
 * 기존 컨트롤러와 달리 실제 HTTP 상태 코드(401/403/409)를 반환한다.
 */
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
public class SpecController {

    private static final String BEARER = "Bearer ";

    private final SpecService specService;
    private final AuthService authService;

    public SpecController(SpecService specService, AuthService authService) {
        this.specService = specService;
        this.authService = authService;
    }

    @GetMapping(API_SPECS_TEMPLATE_PRD)
    public ResponseEntity<RestResponse<SpecTemplateResponse>> prdTemplate(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        authenticate(authorization);
        return ok(new SpecTemplateResponse(SpecKind.PRD.name(), specService.prdTemplate()));
    }

    @PostMapping(API_SPECS)
    public ResponseEntity<RestResponse<SpecDetailResponse>> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody SpecWriteRequest request) {
        SpecActor actor = authenticate(authorization);
        return ok(specService.create(actor, request.getTitle(), request.getBody()));
    }

    @GetMapping(API_SPECS)
    public ResponseEntity<RestResponse<List<SpecSummaryResponse>>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "kind", required = false) String kind,
            @RequestParam(value = "status", required = false) String status) {
        authenticate(authorization);
        return ok(specService.list(parseEnum(SpecKind.class, kind), parseEnum(SpecStatus.class, status)));
    }

    @GetMapping(API_SPECS_ID)
    public ResponseEntity<RestResponse<SpecDetailResponse>> get(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("specId") String specId) {
        authenticate(authorization);
        return ok(specService.get(specId));
    }

    @PutMapping(API_SPECS_ID)
    public ResponseEntity<RestResponse<SpecDetailResponse>> edit(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("specId") String specId,
            @RequestBody SpecWriteRequest request) {
        SpecActor actor = authenticate(authorization);
        return ok(specService.edit(actor, specId, request.getTitle(), request.getBody()));
    }

    @PostMapping(API_SPECS_APPROVE)
    public ResponseEntity<RestResponse<SpecDetailResponse>> approve(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("specId") String specId,
            @RequestBody(required = false) SpecApproveRequest request) {
        SpecActor actor = authenticate(authorization);
        return ok(specService.approve(actor, specId, request == null ? null : request.getComment()));
    }

    @ExceptionHandler(SpecException.class)
    public ResponseEntity<RestResponse<String>> handleSpecException(SpecException e) {
        return error(e.getStatus(), e.getMessage());
    }

    // 같은 문서에 대한 동시 쓰기. 재시도하면 최신 상태 기준으로 수렴한다
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<RestResponse<String>> handleConflict(ObjectOptimisticLockingFailureException e) {
        return error(HttpStatus.CONFLICT, "다른 요청이 먼저 문서를 변경했습니다. 다시 조회한 뒤 시도하세요.");
    }

    private SpecActor authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER)) {
            throw new SpecException(HttpStatus.UNAUTHORIZED, "세션이 필요합니다.");
        }
        try {
            UserInfoResponse user = authService.getCurrentUser(authorization.substring(BEARER.length()));
            return new SpecActor(user.getUsername(), user.getRoles());
        } catch (IllegalArgumentException e) {
            throw new SpecException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw new SpecException(HttpStatus.BAD_REQUEST, "알 수 없는 " + type.getSimpleName() + " 값입니다: " + value);
        }
    }

    private static <T> ResponseEntity<RestResponse<T>> ok(T data) {
        return ResponseEntity.ok(RestResponse.responseOk(data));
    }

    private static ResponseEntity<RestResponse<String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(RestResponse.responseError(message, status));
    }
}
