package com.docsportal.docs.service;

import java.util.List;

/**
 * 검증된 세션의 호출자.
 */
public record SpecActor(String username, List<String> roles) {
}
