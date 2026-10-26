package com.docsportal.docs.service;

import java.util.List;

import com.docsportal.docs.common.Roles;
import com.docsportal.docs.persistence.SpecAction;
import com.docsportal.docs.persistence.SpecKind;
import com.docsportal.docs.persistence.SpecStatus;

/**
 * 스펙 워크플로우 전이 표 (ARCH-0004). 허용 전이는 이 표에만 선언하고 서비스에는 분기를 두지 않는다.
 * 2단계에서 TECH_* 행을 이 표에 추가한다.
 */
public final class SpecTransitions {

    /** from 이 null 이면 문서가 아직 없는 상태(생성)다. */
    public record Transition(SpecKind kind, SpecStatus from, SpecAction action, List<String> roles, SpecStatus to) {
    }

    public enum Verdict {
        ALLOWED,
        /** 이미 목표 상태. 성공으로 응답하되 이력은 남기지 않는다. */
        ALREADY_DONE,
        FORBIDDEN,
        INVALID_STATE
    }

    /** transition 은 ALLOWED 일 때만, actorRole 은 ALLOWED 와 ALREADY_DONE 일 때 채워진다. */
    public record Decision(Verdict verdict, Transition transition, String actorRole) {
    }

    public static final List<Transition> TABLE = List.of(
        new Transition(SpecKind.PRD, null,                SpecAction.CREATE,  List.of(Roles.PLANNER), SpecStatus.DRAFT),
        new Transition(SpecKind.PRD, SpecStatus.DRAFT,    SpecAction.EDIT,    List.of(Roles.PLANNER), SpecStatus.DRAFT),
        new Transition(SpecKind.PRD, SpecStatus.DRAFT,    SpecAction.APPROVE, List.of(Roles.PLANNER), SpecStatus.PRD_APPROVED)
    );

    private SpecTransitions() {
    }

    /**
     * 역할 검사를 상태 검사보다 먼저 한다. 권한 없는 호출자에게 문서 상태를 노출하지 않기 위해서다.
     */
    public static Decision decide(SpecKind kind, SpecStatus current, SpecAction action, List<String> actorRoles) {
        List<Transition> candidates = TABLE.stream()
            .filter(t -> t.kind() == kind && t.action() == action)
            .toList();

        String actorRole = candidates.stream()
            .flatMap(t -> t.roles().stream())
            .filter(actorRoles::contains)
            .findFirst()
            .orElse(null);
        if (actorRole == null) {
            return new Decision(candidates.isEmpty() ? Verdict.INVALID_STATE : Verdict.FORBIDDEN, null, null);
        }

        return candidates.stream()
            .filter(t -> t.from() == current)
            .findFirst()
            .map(t -> new Decision(Verdict.ALLOWED, t, actorRole))
            .orElseGet(() -> action.isConvergent() && candidates.stream().anyMatch(t -> t.to() == current)
                ? new Decision(Verdict.ALREADY_DONE, null, actorRole)
                : new Decision(Verdict.INVALID_STATE, null, null));
    }
}
