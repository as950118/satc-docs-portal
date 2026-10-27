package com.docsportal.docs.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 스펙 테이블 enum 컬럼의 CHECK 제약을 지운다 (ARCH-0004 스키마 주의).
 *
 * Hibernate 6.6 은 enum 컬럼을 만들 때마다 그 시점의 값 목록을 CHECK 로 넣고(@JdbcTypeCode(VARCHAR) 로도 막히지 않음),
 * ddl-auto: update 는 기존 제약을 넓히지 않는다. 그래서 enum 값을 추가하면 1단계에서 만든 DB 의 INSERT 가 실패한다.
 * 허용 값은 전이 표가 지키므로 DB 제약은 두지 않는다. 남은 제약만 찾아 지우므로 매 기동마다 실행해도 결과가 같다.
 * 같은 이유로, 버전 컬럼이 생기기 전에 만든 문서는 1 번 버전으로 채운다(null 인 행만).
 * Hibernate 스키마 갱신 뒤, 웹 서버가 요청을 받기 전에 돈다.
 */
@Component
@DependsOn("entityManagerFactory")
public class SpecSchemaUpgrade {

    private static final Logger log = LoggerFactory.getLogger(SpecSchemaUpgrade.class);

    private static final String ENUM_CHECKS = """
        select c.conrelid::regclass::text as table_name, c.conname
        from pg_constraint c
        join pg_attribute a on a.attrelid = c.conrelid and a.attnum = c.conkey[1]
        where c.contype = 'c'
          and cardinality(c.conkey) = 1
          and c.conrelid in (to_regclass('spec_document'), to_regclass('spec_event'))
          and a.attname in ('kind', 'status', 'action', 'from_status', 'to_status')
        """;

    private final JdbcTemplate jdbc;

    public SpecSchemaUpgrade(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void upgrade() {
        dropEnumChecks();
        backfillRevisions();
    }

    private void dropEnumChecks() {
        List<String[]> checks = jdbc.query(ENUM_CHECKS, (rs, i) -> new String[] {rs.getString(1), rs.getString(2)});
        for (String[] check : checks) {
            jdbc.execute("alter table " + check[0] + " drop constraint if exists \"" + check[1] + "\"");
            log.info("스펙 enum CHECK 제약 제거: {}.{}", check[0], check[1]);
        }
    }

    private void backfillRevisions() {
        int prds = jdbc.update("update spec_document set revision = 1 where kind = 'PRD' and revision is null");
        int techDocs = jdbc.update("update spec_document set prd_revision = 1 where kind <> 'PRD' and prd_revision is null");
        if (prds + techDocs > 0) {
            log.info("스펙 버전 기본값 채움: PRD {}건, 기술문서 {}건", prds, techDocs);
        }
    }
}
