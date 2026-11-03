package com.docsportal.docs.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Hibernate 가 만든 H2 스키마에서 기존 문서 백필의 동작(null 인 행만, idempotent, 관리자 없으면 건너뜀)을 확인한다.
 */
@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:backfill;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DocumentActorBackfillTest {

    @Autowired
    JdbcTemplate jdbc;

    private void addUser(String username) {
        jdbc.update("insert into app_user (username, password_hash, enabled, created_at) values (?, 'x', true, current_timestamp)", username);
    }

    private void addDocument(String id, String createdBy, Long createdById, String updatedBy, Long updatedById) {
        jdbc.update("insert into document (id, title, is_folder, created_by, created_by_id, updated_by, updated_by_id) values (?, ?, false, ?, ?, ?, ?)",
            id, id, createdBy, createdById, updatedBy, updatedById);
    }

    private Map<String, Object> row(String id) {
        return jdbc.queryForMap("select created_by, created_by_id, updated_by, updated_by_id from document where id = ?", id);
    }

    @Test
    void fillsOnlyRowsWithoutActorAndIsIdempotent() {
        addUser("admin");
        addUser("alice");
        Long adminId = jdbc.queryForObject("select id from app_user where username = 'admin'", Long.class);
        Long aliceId = jdbc.queryForObject("select id from app_user where username = 'alice'", Long.class);
        addDocument("legacy", null, null, null, null);
        addDocument("mine", "alice", aliceId, "alice", aliceId);

        new DocumentActorBackfill(jdbc, "").backfill();
        new DocumentActorBackfill(jdbc, "").backfill();

        Map<String, Object> legacy = row("legacy");
        assertThat(legacy.get("CREATED_BY")).isEqualTo("admin");
        assertThat(((Number) legacy.get("CREATED_BY_ID")).longValue()).isEqualTo(adminId);
        assertThat(legacy.get("UPDATED_BY")).isEqualTo("admin");
        assertThat(((Number) legacy.get("UPDATED_BY_ID")).longValue()).isEqualTo(adminId);
        Map<String, Object> mine = row("mine");
        assertThat(mine.get("CREATED_BY")).isEqualTo("alice");
        assertThat(((Number) mine.get("UPDATED_BY_ID")).longValue()).isEqualTo(aliceId);
        assertThat(jdbc.queryForObject("select count(*) from document_event", Integer.class)).isZero();
    }

    @Test
    void usesConfiguredInitialAdminAndSkipsWhenMissing() {
        addDocument("legacy", null, null, null, null);

        new DocumentActorBackfill(jdbc, "root").backfill();
        assertThat(row("legacy").get("CREATED_BY")).isNull();  // 관리자 계정이 없으면 그대로 둔다

        addUser("root");
        new DocumentActorBackfill(jdbc, "root").backfill();
        assertThat(row("legacy").get("CREATED_BY")).isEqualTo("root");
    }
}
