package com.docsportal.docs.persistence;

/**
 * 전이 표의 행동 축.
 *
 * convergent 가 true 이면 현재 상태가 이미 목표 상태일 때 오류 대신 성공(무동작)으로 응답한다.
 * commentRequired 가 true 이면 비어 있지 않은 코멘트가 있어야 한다.
 */
public enum SpecAction {
    CREATE(false, false),
    EDIT(false, false),
    SUBMIT(true, false),
    APPROVE(true, false),
    // 재요청을 무동작 성공으로 받으면 새 코멘트가 조용히 버려진다
    REQUEST_CHANGES(false, true),
    SET_TECH_AREAS(false, false),
    AGGREGATE(true, false),
    START_IMPLEMENTATION(true, false),
    // 상태가 바뀌지 않는 행이라 수렴할 목표가 없다. 같은 URL 중복은 서비스가 링크 단위로 멱등 처리한다
    LINK_PR(false, false),
    // 연결되지 않은 URL 삭제는 서비스가 링크 단위로 멱등 처리한다
    UNLINK_PR(false, false),
    SUBMIT_IMPLEMENTATION(true, false),
    APPROVE_IMPLEMENTATION(true, false),
    REQUEST_IMPL_CHANGES(false, true);

    private final boolean convergent;
    private final boolean commentRequired;

    SpecAction(boolean convergent, boolean commentRequired) {
        this.convergent = convergent;
        this.commentRequired = commentRequired;
    }

    public boolean isConvergent() {
        return convergent;
    }

    public boolean isCommentRequired() {
        return commentRequired;
    }
}
