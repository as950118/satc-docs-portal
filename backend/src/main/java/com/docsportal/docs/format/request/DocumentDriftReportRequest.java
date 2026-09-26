package com.docsportal.docs.format.request;

import java.util.List;

/**
 * CI 드리프트 검사 결과 보고 (POST /api/v1/docs/{id}/drift, ARCH-0003)
 *
 * @param sourceKey       비교에 사용한 코드 소스 ({repo}:{path}#{lang})
 * @param sourceCommit    비교 기준 커밋
 * @param referenced      문서 본문에서 찾은 API 언급 수
 * @param missingInCode   문서에는 있는데 코드에 없는 "METHOD /path"
 * @param missingInDoc    문서가 다루는 경로 범위 안에서 코드에만 있는 "METHOD /path"
 * @param unresolvedPaths 코드 추출 시 해석하지 못한 경로 수 (오판 가능성 표시용)
 */
public record DocumentDriftReportRequest(
        String sourceKey,
        String sourceCommit,
        Integer referenced,
        List<String> missingInCode,
        List<String> missingInDoc,
        Integer unresolvedPaths) {
}
