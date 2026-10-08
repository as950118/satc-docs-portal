package com.docsportal.docs.format.response;

/**
 * 폴더 목록 응답 DTO
 */
public class FolderListResponse {
    private String id;       // 폴더 ID
    private String title;    // 폴더 제목

    public FolderListResponse() {
    }

    public FolderListResponse(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
