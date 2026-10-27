package com.docsportal.docs.common;

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
    /** 자동 집계 같은 시스템 전이의 행위자. 어떤 사용자에게도 부여하지 않는다. */
    public static final String SYSTEM   = "SYSTEM";

    private Roles() {
    }
}
