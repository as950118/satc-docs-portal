package com.docsportal.docs.live;

import com.fasterxml.jackson.annotation.JsonValue;

/** SSE 로 내보내는 변경 이벤트 종류. 와이어 이름은 여기서만 만든다. */
public enum LiveEventType {

    DOCUMENT_CREATED("document.created"),
    DOCUMENT_UPDATED("document.updated"),
    DOCUMENT_STATUS("document.status"),
    DOCUMENT_DELETED("document.deleted"),
    DOCUMENT_MOVED("document.moved"),
    SPEC_CHANGED("spec.changed");

    private final String wireName;

    LiveEventType(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }
}
