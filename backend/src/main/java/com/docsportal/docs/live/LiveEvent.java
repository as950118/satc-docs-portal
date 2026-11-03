package com.docsportal.docs.live;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 변경 알림 한 건. 본문(body/content)·파일 경로는 의도적으로 필드가 없다 (AC-6).
 *
 * @param status 문서는 DocumentStatus, 스펙은 SpecStatus 문자열. 해당 없으면 null
 * @param actor  스펙 변경만 사용자명. 업로드 문서는 인증이 없어 null
 * @param originId 변경을 일으킨 클라이언트가 보낸 X-Client-Id. 없으면 null
 */
public record LiveEvent(LiveEventType type, String id, String title, String parentId, String status,
                        String actor, String originId, Instant at) {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @JsonIgnore
    public String eventName() {
        return type.wireName();
    }

    /** SSE data 로 쓰는 JSON 한 줄. */
    public String toJson() {
        return write(this);
    }

    /** 연결 직후 보내는 hello 이벤트의 data. */
    public static String helloJson(Instant at) {
        return write(new Hello("hello", at));
    }

    private record Hello(String type, Instant at) {
    }

    private static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
