package com.docsportal.docs.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.zaxxer.hikari.HikariDataSource;

class DbSchemaConfigTest {

    private final DbSchemaConfig config = new DbSchemaConfig();

    private Map<String, Object> hibernateProperties(MockEnvironment environment) {
        Map<String, Object> properties = new HashMap<>();
        config.dbSchemaHibernateProperties(environment).customize(properties);
        return properties;
    }

    private String searchPathSchema(MockEnvironment environment) {
        try (HikariDataSource dataSource = new HikariDataSource()) {
            DbSchemaConfig.dbSchemaSearchPath(environment).postProcessBeforeInitialization(dataSource, "dataSource");
            return dataSource.getSchema();
        }
    }

    @Test
    void unsetSchemaKeepsTheDefaults() {
        MockEnvironment environment = new MockEnvironment();

        assertThat(hibernateProperties(environment)).isEmpty();
        assertThat(searchPathSchema(environment)).isNull();
    }

    @Test
    void schemaGoesToHibernateAndTheConnectionSearchPath() {
        MockEnvironment environment = new MockEnvironment().withProperty("docs.db.schema", " docs_portal ");

        assertThat(hibernateProperties(environment))
            .containsEntry(AvailableSettings.DEFAULT_SCHEMA, "docs_portal")
            .containsEntry(AvailableSettings.JAKARTA_HBM2DDL_CREATE_SCHEMAS, true);
        assertThat(searchPathSchema(environment)).isEqualTo("docs_portal");
    }

    @Test
    void invalidSchemaNameFailsWithTheEnvName() {
        for (String invalid : new String[] {"Docs", "docs-portal", "docs;drop", "1docs"}) {
            MockEnvironment environment = new MockEnvironment().withProperty("docs.db.schema", invalid);
            assertThatThrownBy(() -> hibernateProperties(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DOCS_DB_SCHEMA");
        }
    }
}
