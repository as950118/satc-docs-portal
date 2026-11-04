package com.docsportal.docs.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import com.docsportal.docs.storage.StorageSettings;

/** 실제 application.yml 위에 환경변수를 얹어 storage 설정이 어떻게 풀리는지 확인한다. */
class StorageConfigTest {

    private static StorageSettings resolve(Map<String, String> env) {
        MockEnvironment environment = new MockEnvironment();
        try {
            for (PropertySource<?> source : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))) {
                environment.getPropertySources().addLast(source);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("env", new HashMap<>(env)));
        return StorageConfig.settings(environment);
    }

    @Test
    void withoutEnvironmentItIsTheLocalMinioOfBefore() {
        StorageSettings settings = resolve(Map.of());

        assertThat(settings.endpoint()).isEqualTo(URI.create("http://127.0.0.1:9000"));
        assertThat(settings.region()).isEqualTo("us-east-1");
        assertThat(settings.bucket()).isEqualTo("docs");
        assertThat(settings.accessKey()).isEqualTo("minioadmin");
        assertThat(settings.secretKey()).isEqualTo("minioadmin");
        assertThat(settings.pathStyle()).isTrue();
        assertThat(settings.manageBucket()).isTrue();
        assertThat(settings.publicRead()).isTrue();
        assertThat(settings.asciiKeys()).isFalse();
    }

    @Test
    void legacyMinioVariablesStillWork() {
        StorageSettings settings = resolve(Map.of(
            "MINIO_HOST", "minio", "MINIO_PORT", "9100", "MINIO_CLIENT_ID", "ak", "MINIO_CLIENT_SECRET", "sk",
            "MINIO_BUCKET_NAME", "cmp-docs"));

        assertThat(settings.endpoint()).isEqualTo(URI.create("http://minio:9100"));
        assertThat(settings.bucket()).isEqualTo("cmp-docs");
        assertThat(settings.accessKey()).isEqualTo("ak");
        assertThat(settings.secretKey()).isEqualTo("sk");
    }

    @Test
    void storageVariablesWinOverMinioAndKeepTheEndpointPath() {
        StorageSettings settings = resolve(Map.ofEntries(
            Map.entry("MINIO_HOST", "minio"), Map.entry("MINIO_CLIENT_ID", "old"), Map.entry("MINIO_BUCKET_NAME", "old-bucket"),
            Map.entry("STORAGE_ENDPOINT", "https://ref.storage.supabase.co/storage/v1/s3/"),
            Map.entry("STORAGE_REGION", "ap-northeast-2"), Map.entry("STORAGE_BUCKET", "satc-docs"),
            Map.entry("STORAGE_ACCESS_KEY", "ak"), Map.entry("STORAGE_SECRET_KEY", "sk"),
            Map.entry("STORAGE_MANAGE_BUCKET", "false"), Map.entry("STORAGE_PUBLIC_READ", "FALSE"),
            Map.entry("STORAGE_ASCII_KEYS", "true")));

        assertThat(settings.endpoint()).isEqualTo(URI.create("https://ref.storage.supabase.co/storage/v1/s3"));
        assertThat(settings.region()).isEqualTo("ap-northeast-2");
        assertThat(settings.bucket()).isEqualTo("satc-docs");
        assertThat(settings.accessKey()).isEqualTo("ak");
        assertThat(settings.manageBucket()).isFalse();
        assertThat(settings.publicRead()).isFalse();
        assertThat(settings.asciiKeys()).isTrue();
        assertThat(settings.pathStyle()).isTrue();
    }

    @Test
    void blankStorageVariablesFallBackLikeUnsetOnes() {
        Map<String, String> env = new HashMap<>();
        for (String name : new String[] {"STORAGE_ENDPOINT", "STORAGE_REGION", "STORAGE_BUCKET", "STORAGE_ACCESS_KEY",
            "STORAGE_SECRET_KEY", "STORAGE_PATH_STYLE", "STORAGE_MANAGE_BUCKET", "STORAGE_PUBLIC_READ", "STORAGE_ASCII_KEYS"}) {
            env.put(name, "");
        }
        env.put("MINIO_HOST", "minio");

        StorageSettings settings = resolve(env);

        assertThat(settings.endpoint()).isEqualTo(URI.create("http://minio:9000"));
        assertThat(settings.bucket()).isEqualTo("docs");
        assertThat(settings.accessKey()).isEqualTo("minioadmin");
        assertThat(settings.manageBucket()).isTrue();
        assertThat(settings.asciiKeys()).isFalse();
    }

    @Test
    void invalidValuesStopStartupNamingTheVariable() {
        assertThatThrownBy(() -> resolve(Map.of("STORAGE_ENDPOINT", "minio:9000")))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("STORAGE_ENDPOINT");
        assertThatThrownBy(() -> resolve(Map.of("STORAGE_ENDPOINT", "https://x.example/s3?a=1")))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("STORAGE_ENDPOINT");
        assertThatThrownBy(() -> resolve(Map.of("STORAGE_BUCKET", "Docs_1")))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("STORAGE_BUCKET");
        assertThatThrownBy(() -> resolve(Map.of("STORAGE_ASCII_KEYS", "yes")))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("STORAGE_ASCII_KEYS");
        assertThatThrownBy(() -> resolve(Map.of("MINIO_CLIENT_SECRET", "")))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("STORAGE_SECRET_KEY");
    }

    @Test
    void settingsTextNeverShowsTheSecret() {
        StorageSettings settings = resolve(Map.of("STORAGE_SECRET_KEY", "very-secret-value"));

        assertThat(settings.toString()).doesNotContain("very-secret-value");
    }
}
