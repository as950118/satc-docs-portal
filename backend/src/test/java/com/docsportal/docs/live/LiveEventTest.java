package com.docsportal.docs.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class LiveEventTest {

    private static final Instant AT = Instant.parse("2026-11-03T06:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonHasExactlyTheContractFieldsAndNoBody() throws Exception {
        LiveEvent event = new LiveEvent(LiveEventType.DOCUMENT_STATUS, "d1", "제목", null, "COMPLETE", null, "abc", AT);

        JsonNode json = mapper.readTree(event.toJson());

        assertThat(json.has("body")).isFalse();
        assertThat(json.has("content")).isFalse();
        assertThat(json.has("filePath")).isFalse();
        assertThat(json.fieldNames()).toIterable()
            .containsExactlyInAnyOrder("type", "id", "title", "parentId", "status", "actor", "originId", "at");
        assertThat(json.get("type").asText()).isEqualTo("document.status");
        assertThat(json.get("parentId").isNull()).isTrue();
        assertThat(json.get("originId").asText()).isEqualTo("abc");
        assertThat(json.get("at").asText()).isEqualTo("2026-11-03T06:00:00Z");
        assertThat(event.toJson()).doesNotContain("\n");
    }

    @Test
    void typeNamesComeFromTheEnum() {
        assertThat(LiveEventType.values()).extracting(LiveEventType::wireName).containsExactly(
            "document.created", "document.updated", "document.status", "document.deleted", "document.moved",
            "spec.changed");
    }

    @Test
    void helloCarriesOnlyTypeAndTime() throws Exception {
        JsonNode json = mapper.readTree(LiveEvent.helloJson(AT));

        assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder("type", "at");
        assertThat(json.get("type").asText()).isEqualTo("hello");
    }
}
