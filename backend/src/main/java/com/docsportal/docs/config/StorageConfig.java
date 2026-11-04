package com.docsportal.docs.config;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertyResolver;

import com.docsportal.docs.storage.ObjectStorage;
import com.docsportal.docs.storage.S3ObjectStorage;
import com.docsportal.docs.storage.StorageSettings;

/**
 * 문서 파일 저장소 설정. storage.*(환경변수 STORAGE_*)의 기본값은 여기서 정하고, 주소·버킷·계정이 비어 있으면
 * 예전 minio.*(MINIO_*, 기본값은 application.yml) 값을 써서 기존 배포는 설정을 바꾸지 않아도 같은 MinIO 에 붙는다.
 * 잘못된 값은 기동을 멈춘다.
 */
@Configuration
public class StorageConfig {

    private static final Pattern BUCKET_NAME = Pattern.compile("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$");

    @Bean
    public StorageSettings storageSettings(Environment environment) {
        return settings(environment);
    }

    @Bean
    public ObjectStorage objectStorage(StorageSettings settings) {
        return new S3ObjectStorage(settings);
    }

    static StorageSettings settings(PropertyResolver properties) {
        String legacyEndpoint = "http://" + text(properties, "minio.host", "") + ":" + text(properties, "minio.port", "");
        URI endpoint = endpoint(text(properties, "storage.endpoint", legacyEndpoint));
        String region = text(properties, "storage.region", "us-east-1");
        String bucket = text(properties, "storage.bucket", text(properties, "minio.bucket-name", ""));
        if (!BUCKET_NAME.matcher(bucket).matches()) {
            throw new IllegalStateException("storage.bucket(환경변수 STORAGE_BUCKET)은 S3 버킷 이름 규칙(소문자·숫자·점·하이픈, 3~63자)을 따라야 합니다.");
        }
        String accessKey = required(properties, "storage.access-key", "minio.client-id", "STORAGE_ACCESS_KEY");
        String secretKey = required(properties, "storage.secret-key", "minio.client-secret", "STORAGE_SECRET_KEY");
        return new StorageSettings(endpoint, region, bucket, accessKey, secretKey,
            flag(properties, "storage.path-style", "STORAGE_PATH_STYLE", true),
            flag(properties, "storage.manage-bucket", "STORAGE_MANAGE_BUCKET", true),
            flag(properties, "storage.public-read", "STORAGE_PUBLIC_READ", true),
            flag(properties, "storage.ascii-keys", "STORAGE_ASCII_KEYS", false));
    }

    private static URI endpoint(String raw) {
        String name = "storage.endpoint(환경변수 STORAGE_ENDPOINT)";
        URI uri;
        try {
            uri = URI.create(raw.replaceAll("/+$", ""));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + "이 URL 형식이 아닙니다.");
        }
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
            || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalStateException(name + "은 쿼리·프래그먼트 없는 http(s) 절대 주소여야 합니다 (예: https://<ref>.storage.supabase.co/storage/v1/s3).");
        }
        return uri;
    }

    private static String required(PropertyResolver properties, String name, String legacyName, String envName) {
        String value = text(properties, name, text(properties, legacyName, ""));
        if (value.isEmpty()) {
            throw new IllegalStateException(name + "(환경변수 " + envName + ")이 비어 있습니다.");
        }
        return value;
    }

    private static boolean flag(PropertyResolver properties, String name, String envName, boolean fallback) {
        String value = text(properties, name, "").toLowerCase(Locale.ROOT);
        return switch (value) {
            case "" -> fallback;
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalStateException(name + "(환경변수 " + envName + ")은 true 또는 false 여야 합니다.");
        };
    }

    /** 비어 있는 값은 설정하지 않은 것으로 본다 (compose 는 빈 환경변수를 넘긴다). */
    private static String text(PropertyResolver properties, String name, String fallback) {
        String value = properties.getProperty(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
