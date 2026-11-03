package com.docsportal.docs.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 등록자/수정자 정보가 없는 기존 문서(created_by, updated_by 가 null 인 행)를 관리자 계정으로 채운다.
 * 값이 이미 있는 행은 건드리지 않으므로 매 기동마다 실행해도 결과가 같다. 이력(document_event)은 만들지 않는다.
 * 관리자 계정은 docs.auth.initial-admin.username 이 있으면 그 계정, 없으면 'admin' 이다. 없으면 건너뛰고 로그만 남긴다.
 * 계정 시드(AccountSeeder) 뒤에 돈다.
 */
@Component
@DependsOn({"entityManagerFactory", "accountSeeder", "specSchemaUpgrade"})
public class DocumentActorBackfill {

    private static final Logger log = LoggerFactory.getLogger(DocumentActorBackfill.class);
    private static final String DEFAULT_ADMIN = "admin";

    private final JdbcTemplate jdbc;
    private final String adminUsername;

    public DocumentActorBackfill(JdbcTemplate jdbc, @Value("${docs.auth.initial-admin.username:}") String initialAdmin) {
        this.jdbc = jdbc;
        this.adminUsername = initialAdmin.isBlank() ? DEFAULT_ADMIN : initialAdmin;
    }

    @PostConstruct
    void backfill() {
        var admins = jdbc.query("select id, username from app_user where username = ?",
            (rs, i) -> new Object[] {rs.getLong(1), rs.getString(2)}, adminUsername);
        if (admins.isEmpty()) {
            log.warn("관리자 계정 '{}' 이(가) 없어 기존 문서의 등록자 채우기를 건너뜁니다.", adminUsername);
            return;
        }
        Object id = admins.get(0)[0];
        Object name = admins.get(0)[1];
        int created = jdbc.update("update document set created_by = ?, created_by_id = ? where created_by is null", name, id);
        int updated = jdbc.update("update document set updated_by = ?, updated_by_id = ? where updated_by is null", name, id);
        if (created + updated > 0) {
            log.info("등록자 없는 기존 문서를 '{}' 로 채움: 등록자 {}건, 수정자 {}건", name, created, updated);
        }
    }
}
