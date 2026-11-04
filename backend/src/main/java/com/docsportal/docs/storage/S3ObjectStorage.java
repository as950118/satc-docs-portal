package com.docsportal.docs.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** AWS SDK v2 S3 클라이언트로 S3 호환 저장소에 접근한다. */
public class S3ObjectStorage implements ObjectStorage, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(S3ObjectStorage.class);

    private final S3Client s3;
    private final StorageSettings settings;

    public S3ObjectStorage(StorageSettings settings) {
        this.settings = settings;
        // SDK 2.30 부터 기본으로 붙는 CRC 체크섬 헤더를 S3 호환 저장소 일부가 거부해서, 필요할 때만 보내게 한다
        this.s3 = S3Client.builder()
            .endpointOverride(settings.endpoint())
            .region(Region.of(settings.region()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(settings.accessKey(), settings.secretKey())))
            .forcePathStyle(settings.pathStyle())
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .build();
    }

    @Override
    public void ensureBucket() {
        if (!settings.manageBucket()) {
            return;
        }
        String bucket = settings.bucket();
        try {
            if (bucketExists(bucket)) {
                return;
            }
            s3.createBucket(b -> b.bucket(bucket));
            if (settings.publicRead()) {
                s3.putBucketPolicy(b -> b.bucket(bucket).policy(publicReadPolicy(bucket)));
            }
            logger.info("[STORAGE] Created bucket {} (public read: {})", bucket, settings.publicRead());
        } catch (SdkException e) {
            logger.error("[STORAGE] Failed to ensure bucket {}: {}", bucket, e.getMessage());
        }
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        s3.putObject(b -> b.bucket(settings.bucket()).key(key).contentType(contentType).contentLength((long) content.length),
            RequestBody.fromBytes(content));
    }

    @Override
    public byte[] get(String key) {
        return s3.getObjectAsBytes(b -> b.bucket(settings.bucket()).key(key)).asByteArray();
    }

    @Override
    public boolean exists(String key) {
        try {
            s3.headObject(b -> b.bucket(settings.bucket()).key(key));
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(b -> b.bucket(settings.bucket()).key(key));
    }

    @Override
    public void close() {
        s3.close();
    }

    private boolean bucketExists(String bucket) {
        try {
            s3.headBucket(b -> b.bucket(bucket));
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    private static String publicReadPolicy(String bucket) {
        return """
            {
              "Version":"2012-10-17",
              "Statement":[
                {
                  "Effect":"Allow",
                  "Principal":{"AWS":"*"},
                  "Action":["s3:GetObject"],
                  "Resource":["arn:aws:s3:::%s/*"]
                }
              ]
            }
            """.formatted(bucket);
    }
}
