package com.docsportal.docs.storage;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 테스트용 ObjectStorage. failWith 로 넘긴 예외는 put 에서 던진다. */
public class InMemoryObjectStorage implements ObjectStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private volatile RuntimeException putFailure;

    public static StorageSettings settings(boolean asciiKeys) {
        return new StorageSettings(URI.create("http://127.0.0.1:9000"), "us-east-1", "docs", "test-access", "test-secret",
            true, true, true, asciiKeys);
    }

    public void failWith(RuntimeException failure) {
        this.putFailure = failure;
    }

    public Set<String> keys() {
        return Set.copyOf(objects.keySet());
    }

    @Override
    public void ensureBucket() {
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        if (putFailure != null) {
            throw putFailure;
        }
        objects.put(key, content.clone());
    }

    @Override
    public byte[] get(String key) {
        byte[] content = objects.get(key);
        if (content == null) {
            throw new IllegalStateException("no such key: " + key);
        }
        return content.clone();
    }

    @Override
    public boolean exists(String key) {
        return objects.containsKey(key);
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
    }
}
