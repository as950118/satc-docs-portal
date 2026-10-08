package com.docsportal.docs.format.request;

/**
 * 코드 → 문서 동기화 요청 (PUT /api/v1/docs/sync, ARCH-0001)
 *
 * @param sourceKey    동기화 대상 식별자 ({owner/repo}:{path}#{lang})
 * @param sourceRepo   owner/repo
 * @param sourcePath   레포 내 생성 대상 경로
 * @param sourceCommit 동기화 기준 커밋 SHA
 * @param contentHash  정규화된 스펙의 sha256 (서버가 재계산해 검증)
 * @param parentId     최초 생성 시 배치 폴더 (이후 호출에서는 무시)
 * @param fileName     업로드 파일명 (확장자 제외 부분이 문서 제목)
 * @param contentType  업로드 파일 Content-Type
 * @param fileBytes    스펙 내용
 */
public record DocumentSyncRequest(
        String sourceKey,
        String sourceRepo,
        String sourcePath,
        String sourceCommit,
        String contentHash,
        String parentId,
        String fileName,
        String contentType,
        byte[] fileBytes) {
}
