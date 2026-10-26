package com.docsportal.docs.format.request;

/**
 * PRD 생성(POST)·수정(PUT) 요청. 수정 시 title 을 생략하면 기존 제목을 유지한다.
 */
public class SpecWriteRequest {
    private String title;
    private String body;

    public SpecWriteRequest() {
    }

    public SpecWriteRequest(String title, String body) {
        this.title = title;
        this.body = body;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
