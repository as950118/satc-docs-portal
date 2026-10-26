package com.docsportal.docs.persistence;

/**
 * 전이 표의 행동 축.
 *
 * convergent 가 true 이면 현재 상태가 이미 목표 상태일 때 오류 대신 성공(무동작)으로 응답한다.
 */
public enum SpecAction {
    CREATE(false),
    EDIT(false),
    APPROVE(true);

    private final boolean convergent;

    SpecAction(boolean convergent) {
        this.convergent = convergent;
    }

    public boolean isConvergent() {
        return convergent;
    }
}
