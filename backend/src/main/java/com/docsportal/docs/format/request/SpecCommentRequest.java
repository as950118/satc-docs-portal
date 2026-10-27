package com.docsportal.docs.format.request;

/**
 * 승인·제출·변경 요청의 코멘트. 변경 요청(request-changes, request-implementation-changes)에서만 필수다.
 */
public class SpecCommentRequest {
    private String comment;

    public SpecCommentRequest() {
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
