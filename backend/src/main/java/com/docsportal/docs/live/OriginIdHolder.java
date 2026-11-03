package com.docsportal.docs.live;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 현재 요청의 X-Client-Id 헤더. 변경을 일으킨 화면이 자기 이벤트를 걸러내는 용도일 뿐 보안 수단이 아니다.
 * 요청 스레드가 아니거나(@Async 등) 헤더가 없으면 null 이다.
 */
public final class OriginIdHolder {

    public static final String HEADER = "X-Client-Id";
    static final int MAX_LENGTH = 128;

    private OriginIdHolder() {
    }

    public static String current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) {
            return null;
        }
        String value = servlet.getRequest().getHeader(HEADER);
        if (value == null || value.isBlank()) {
            return null;
        }
        value = value.strip();
        return value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
    }
}
