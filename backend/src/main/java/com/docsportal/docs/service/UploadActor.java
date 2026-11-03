package com.docsportal.docs.service;

/**
 * 문서를 등록·수정한 로그인 사용자. userId 는 AppUser 식별자, username 은 조회 편의용이다.
 */
public record UploadActor(Long userId, String username) {
}
