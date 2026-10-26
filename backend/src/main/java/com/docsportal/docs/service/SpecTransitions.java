package com.docsportal.docs.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import com.docsportal.docs.common.Roles;
import com.docsportal.docs.persistence.SpecAction;
import com.docsportal.docs.persistence.SpecKind;
import com.docsportal.docs.persistence.SpecStatus;
import com.docsportal.docs.persistence.TechArea;

/**
 * 스펙 워크플로우 전이 표 (ARCH-0004). 허용 전이와 그 전제 조건은 이 표에만 선언하고 서비스에는 분기를 두지 않는다.
 */
public final class SpecTransitions {

    /** from 이 null 이면 문서가 아직 없는 상태(생성)다. guards 는 상태가 맞은 뒤 순서대로 검사한다. */
    public record Transition(SpecKind kind, SpecStatus from, SpecAction action, List<String> roles, SpecStatus to,
                             List<Guard> guards) {
    }

    /**
     * 전이 조건이 보는 PRD 쪽 사실. 문서가 속한 PRD(PRD 자신이면 자기)의 상태, 선언 영역, 영역별 기술문서 상태다.
     */
    public record PrdFacts(SpecStatus status, Set<TechArea> areas, Map<SpecKind, SpecStatus> techDocs) {
        /** 아직 PRD 가 없을 때(PRD 생성). */
        public static final PrdFacts NONE = new PrdFacts(null, Set.of(), Map.of());
    }

    public enum Guard {
        PRD_APPROVED("부모 PRD 가 PRD_APPROVED 상태가 아닙니다.",
            (kind, prd) -> prd.status() == SpecStatus.PRD_APPROVED),
        AREA_DECLARED("PRD 의 tech_areas 에 선언되지 않은 영역입니다.",
            (kind, prd) -> TechArea.of(kind).filter(prd.areas()::contains).isPresent()),
        AREA_VACANT("이 영역의 기술문서가 이미 있습니다.",
            (kind, prd) -> !prd.techDocs().containsKey(kind)),
        ALL_AREAS_APPROVED("선언된 모든 영역의 기술문서가 승인되지 않았습니다.",
            (kind, prd) -> !prd.areas().isEmpty()
                && prd.areas().stream().allMatch(a -> prd.techDocs().get(a.kind()) == SpecStatus.APPROVED));

        private final String failure;
        private final BiPredicate<SpecKind, PrdFacts> holds;

        Guard(String failure, BiPredicate<SpecKind, PrdFacts> holds) {
            this.failure = failure;
            this.holds = holds;
        }

        public String failure() {
            return failure;
        }

        boolean holds(SpecKind kind, PrdFacts prd) {
            return holds.test(kind, prd);
        }
    }

    public enum Verdict {
        ALLOWED,
        /** 이미 목표 상태. 성공으로 응답하되 이력은 남기지 않는다. */
        ALREADY_DONE,
        FORBIDDEN,
        INVALID_STATE,
        /** 상태는 맞지만 행의 guard 가 막았다. failedGuard 가 이유다. */
        PRECONDITION_FAILED
    }

    /** transition 은 ALLOWED 와 PRECONDITION_FAILED, actorRole 은 ALLOWED 와 ALREADY_DONE 일 때 채워진다. */
    public record Decision(Verdict verdict, Transition transition, String actorRole, Guard failedGuard) {
    }

    public static final List<Transition> TABLE = Stream.concat(
        Stream.of(
            row(SpecKind.PRD, null,                    SpecAction.CREATE,         List.of(Roles.PLANNER), SpecStatus.DRAFT),
            row(SpecKind.PRD, SpecStatus.DRAFT,        SpecAction.EDIT,           List.of(Roles.PLANNER), SpecStatus.DRAFT),
            row(SpecKind.PRD, SpecStatus.DRAFT,        SpecAction.APPROVE,        List.of(Roles.PLANNER), SpecStatus.PRD_APPROVED),
            row(SpecKind.PRD, SpecStatus.PRD_APPROVED, SpecAction.SET_TECH_AREAS, List.of(Roles.PLANNER), SpecStatus.PRD_APPROVED),
            row(SpecKind.PRD, SpecStatus.PRD_APPROVED, SpecAction.AGGREGATE,      List.of(Roles.SYSTEM),  SpecStatus.SPEC_APPROVED,
                Guard.ALL_AREAS_APPROVED)),
        Arrays.stream(TechArea.values()).flatMap(SpecTransitions::techRows)
    ).toList();

    private static Stream<Transition> techRows(TechArea area) {
        SpecKind kind = area.kind();
        List<String> writers = List.of(Roles.PLANNER, area.role());
        List<String> reviewers = List.of(area.role());
        return Stream.of(
            row(kind, null,                         SpecAction.CREATE,          writers,   SpecStatus.DRAFT,
                Guard.PRD_APPROVED, Guard.AREA_DECLARED, Guard.AREA_VACANT),
            row(kind, SpecStatus.DRAFT,             SpecAction.EDIT,            writers,   SpecStatus.DRAFT),
            row(kind, SpecStatus.CHANGES_REQUESTED, SpecAction.EDIT,            writers,   SpecStatus.DRAFT),
            row(kind, SpecStatus.DRAFT,             SpecAction.SUBMIT,          writers,   SpecStatus.IN_REVIEW),
            row(kind, SpecStatus.IN_REVIEW,         SpecAction.APPROVE,         reviewers, SpecStatus.APPROVED),
            row(kind, SpecStatus.IN_REVIEW,         SpecAction.REQUEST_CHANGES, reviewers, SpecStatus.CHANGES_REQUESTED));
    }

    private static Transition row(SpecKind kind, SpecStatus from, SpecAction action, List<String> roles, SpecStatus to,
                                  Guard... guards) {
        return new Transition(kind, from, action, roles, to, List.of(guards));
    }

    private SpecTransitions() {
    }

    /**
     * 역할, 상태, guard 순서로 검사한다. 역할을 먼저 보는 이유는 권한 없는 호출자에게 문서 상태를 노출하지 않기 위해서다.
     */
    public static Decision decide(SpecKind kind, SpecStatus current, SpecAction action, List<String> actorRoles,
                                  PrdFacts prd) {
        List<Transition> candidates = TABLE.stream()
            .filter(t -> t.kind() == kind && t.action() == action)
            .toList();

        String actorRole = candidates.stream()
            .flatMap(t -> t.roles().stream())
            .filter(actorRoles::contains)
            .findFirst()
            .orElse(null);
        if (actorRole == null) {
            return new Decision(candidates.isEmpty() ? Verdict.INVALID_STATE : Verdict.FORBIDDEN, null, null, null);
        }

        Transition row = candidates.stream().filter(t -> t.from() == current).findFirst().orElse(null);
        if (row == null) {
            return action.isConvergent() && candidates.stream().anyMatch(t -> t.to() == current)
                ? new Decision(Verdict.ALREADY_DONE, null, actorRole, null)
                : new Decision(Verdict.INVALID_STATE, null, null, null);
        }

        return row.guards().stream()
            .filter(g -> !g.holds(kind, prd))
            .findFirst()
            .map(g -> new Decision(Verdict.PRECONDITION_FAILED, row, null, g))
            .orElseGet(() -> new Decision(Verdict.ALLOWED, row, actorRole, null));
    }
}
