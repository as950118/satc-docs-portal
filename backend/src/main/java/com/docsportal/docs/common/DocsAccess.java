package com.docsportal.docs.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * /api/v1/docs/** 핸들러를 부를 수 있는 호출자. DocsAccessInterceptor 가 집행하고,
 * DocsAccessCoverageTest 가 이 선언 없이 추가된 문서 매핑을 막는다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DocsAccess {

    Level value();

    enum Level {
        /** 로그인한 사용자 누구나 */
        READ,
        /** Roles.DOCUMENT_WRITERS 중 하나를 가진 사용자 */
        WRITE,
        /** X-Internal-Token 을 가진 내부 서비스(indexing 의 상태 콜백). 사용자 세션으로는 부를 수 없다 */
        INTERNAL,
        /** X-Docs-Sync-Token 을 가진 CI. DocumentSyncController 가 직접 검증하므로 인터셉터는 통과시킨다 */
        SYNC
    }
}
