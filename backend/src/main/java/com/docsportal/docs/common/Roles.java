package com.docsportal.docs.common;

import java.util.Set;

/**
 * 역할 문자열 상수의 단일 정의 위치.
 * 계정(AccountSeeder)과 스펙 전이 표(SpecTransitions)가 같은 값을 참조한다.
 */
public final class Roles {

    public static final String ADMIN    = "ADMIN";
    public static final String USER     = "USER";
    public static final String PLANNER  = "PLANNER";
    public static final String BACKEND  = "BACKEND";
    public static final String FRONTEND = "FRONTEND";
    public static final String DATA     = "DATA";
    public static final String REVIEWER = "REVIEWER";
    /** 표준문서(개발 가이드 등) 등록·갱신만 허용하는 역할. 다른 역할과 함께 가질 수 있다. */
    public static final String UPLOADER = "UPLOADER";
    /** 자동 집계 같은 시스템 전이의 행위자. 어떤 사용자에게도 부여하지 않는다. */
    public static final String SYSTEM   = "SYSTEM";

    /** 문서를 올리고 옮기고 지울 수 있는 역할. USER 만 가진 계정은 조회 전용이다. */
    public static final Set<String> DOCUMENT_WRITERS = Set.of(ADMIN, PLANNER, BACKEND, FRONTEND, DATA, REVIEWER, UPLOADER);

    private Roles() {
    }
}
