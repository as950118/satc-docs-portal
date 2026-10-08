package com.docsportal.docs.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.minio.MinioClient;

@Configuration
public class MinioConfig {

    @Value("${minio.host}")
    private String minioHost;

    @Value("${minio.port}")
    private int minioPort;

    @Value("${minio.client-id}")
    private String minioClientId;

    @Value("${minio.client-secret}")
    private String minioClientSecret;

    @Bean
    MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(minioHost, minioPort, false)
                .credentials(minioClientId, minioClientSecret)
                .build();
    }

}
