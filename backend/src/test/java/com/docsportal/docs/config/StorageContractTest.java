package com.docsportal.docs.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.env.StandardEnvironment;

import com.docsportal.docs.storage.S3ObjectStorage;
import com.docsportal.docs.storage.StorageSettings;

import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * 실제 S3 호환 저장소에 붙어 S3ObjectStorage 를 확인한다. STORAGE_CONTRACT_TEST=true 와 STORAGE_* 환경변수가 있을 때만 돈다.
 * 객체는 compat-test/ 아래에만 만들고 지운다. STORAGE_CONTRACT_REJECTS_NON_ASCII=true 면 한글 키가 거부되는 저장소로 본다.
 */
@EnabledIfEnvironmentVariable(named = "STORAGE_CONTRACT_TEST", matches = "true")
class StorageContractTest {

    private static final String PREFIX = "compat-test/" + UUID.randomUUID() + "/";
    private static final byte[] BODY = "# 계약 테스트\nbody".getBytes(StandardCharsets.UTF_8);

    private final StorageSettings settings = StorageConfig.settings(new StandardEnvironment());
    private final S3ObjectStorage storage = new S3ObjectStorage(settings);
    private final List<String> written = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String key : written) {
            try {
                storage.delete(key);
            } catch (RuntimeException ignored) {
                // 거부된 키는 처음부터 없다
            }
        }
        storage.close();
    }

    private StorageSettings withAsciiKeys(boolean asciiKeys) {
        return new StorageSettings(settings.endpoint(), settings.region(), settings.bucket(), settings.accessKey(),
            settings.secretKey(), settings.pathStyle(), settings.manageBucket(), settings.publicRead(), asciiKeys);
    }

    private String key(boolean asciiKeys) {
        String id = UUID.randomUUID().toString();
        String key = PREFIX + withAsciiKeys(asciiKeys).documentKey(id, "한글 제목 (계약)", "md");
        written.add(key);
        return key;
    }

    @Test
    void putGetExistsDeleteWithAsciiKeys() {
        storage.ensureBucket();
        String key = key(true);
        assertThat(key).matches("[\\x21-\\x7E]+");

        storage.put(key, BODY, "text/markdown");

        assertThat(storage.exists(key)).isTrue();
        assertThat(storage.get(key)).isEqualTo(BODY);
        storage.delete(key);
        assertThat(storage.exists(key)).isFalse();
        assertThatThrownBy(() -> storage.get(key)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void titleKeysWithHangulDependOnTheProvider() {
        String key = key(false);
        assertThat(key).contains("한글 제목 (계약)");

        if ("true".equals(System.getenv("STORAGE_CONTRACT_REJECTS_NON_ASCII"))) {
            // 거부되는 키는 HEAD 도 404 가 아니라 400 이라 exists 로 확인할 수 없다
            assertThatThrownBy(() -> storage.put(key, BODY, "text/markdown"))
                .isInstanceOfSatisfying(S3Exception.class, e -> {
                    assertThat(e.statusCode()).isEqualTo(400);
                    assertThat(e.awsErrorDetails().errorCode()).isEqualTo("InvalidKey");
                });
        } else {
            storage.put(key, BODY, "text/markdown");
            assertThat(storage.get(key)).isEqualTo(BODY);
        }
    }
}
