package com.docsportal.docs.format.request;

/**
 * 승인 요청. 본문 전체가 선택 사항이다.
 */
public class SpecApproveRequest {
    private String comment;

    public SpecApproveRequest() {
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
