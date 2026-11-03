package com.docsportal.docs.format.response;

import java.util.List;

/**
 * PRD 를 OpenSpec change 폴더로 내보낸 결과. files 의 path 는 change 폴더 기준 상대 경로이고,
 * lint 가 비어 있지 않으면 files 는 비어 있다.
 */
public record SpecExportResponse(String changeName, List<File> files, List<String> lint) {

    public record File(String path, String content) {
    }
}
