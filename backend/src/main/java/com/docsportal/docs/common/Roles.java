package com.docsportal.docs.common;

/**
 * 역할 문자열 상수의 단일 정의 위치.
 * 사용자(AuthServiceImpl)와 스펙 전이 표(SpecTransitions)가 같은 값을 참조한다.
 */
public final class Roles {

    public static final String ADMIN    = "ADMIN";
    public static final String USER     = "USER";
    public static final String PLANNER  = "PLANNER";

    private Roles() {
    }
}
