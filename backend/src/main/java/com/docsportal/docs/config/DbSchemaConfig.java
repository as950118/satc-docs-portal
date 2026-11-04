package com.docsportal.docs.config;

import java.util.regex.Pattern;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.zaxxer.hikari.HikariDataSource;

/**
 * docs.db.schema(환경변수 DOCS_DB_SCHEMA)를 정하면 모든 테이블을 그 스키마에 둔다. 비우면 지금처럼 search_path 기본값(public)을 쓴다.
 * Supabase 는 public 스키마를 REST API 로 노출하고 새 테이블은 RLS 가 꺼진 채 생기므로, 운영은 전용 스키마를 쓴다(docs/deploy.md).
 * Hibernate 가 스키마를 만들고 테이블 이름에 스키마를 붙인다. 이름을 붙이지 않는 네이티브 SQL 은 커넥션 search_path 로 같은 스키마를 찾는다.
 */
@Configuration
public class DbSchemaConfig {

    private static final Pattern SCHEMA_NAME = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

    static String schema(Environment environment) {
        String schema = environment.getProperty("docs.db.schema", "").trim();
        if (!schema.isEmpty() && !SCHEMA_NAME.matcher(schema).matches()) {
            throw new IllegalStateException(
                "docs.db.schema(환경변수 DOCS_DB_SCHEMA)는 소문자, 숫자, 밑줄로 된 PostgreSQL 스키마 이름이어야 합니다: " + schema);
        }
        return schema;
    }

    @Bean
    public HibernatePropertiesCustomizer dbSchemaHibernateProperties(Environment environment) {
        String schema = schema(environment);
        return properties -> {
            if (!schema.isEmpty()) {
                properties.put(AvailableSettings.DEFAULT_SCHEMA, schema);
                properties.put(AvailableSettings.JAKARTA_HBM2DDL_CREATE_SCHEMAS, true);
            }
        };
    }

    @Bean
    public static BeanPostProcessor dbSchemaSearchPath(Environment environment) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                String schema = schema(environment);
                if (bean instanceof HikariDataSource dataSource && !schema.isEmpty()) {
                    dataSource.setSchema(schema);
                }
                return bean;
            }
        };
    }
}
