package com.docsportal.docs.service;

import java.util.Arrays;
import java.util.EnumSet;
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

    /** PRD 본문이 승인된 상태들. 새 버전은 이 상태에서만 만들 수 있다. */
    public static final Set<SpecStatus> PRD_APPROVED_STATES =
        EnumSet.of(SpecStatus.PRD_APPROVED, SpecStatus.SPEC_APPROVED, SpecStatus.IMPLEMENTED);

    /** 기술문서가 검토 승인을 지난 상태들. 새 PRD 버전 뒤에는 PRD_APPROVED 아래에서도 이 상태들이 공존한다. */
    static final Set<SpecStatus> APPROVED_OR_LATER =
        EnumSet.of(SpecStatus.APPROVED, SpecStatus.IN_IMPLEMENTATION, SpecStatus.IMPL_IN_REVIEW, SpecStatus.IMPLEMENTED);

    /** 구현 단계. 새 PRD 버전이 승인돼도 상태를 바꾸지 않고 "PRD 변경" 표시만 받는다. */
    static final Set<SpecStatus> IMPLEMENTATION_STATES =
        EnumSet.of(SpecStatus.IN_IMPLEMENTATION, SpecStatus.IMPL_IN_REVIEW, SpecStatus.IMPLEMENTED);

    /** 기술문서가 가질 수 있는 모든 상태. 새 PRD 버전 승인(PRD_REVISED)은 이 모든 상태에 행이 있다. */
    static final List<SpecStatus> TECH_STATES = List.of(SpecStatus.DRAFT, SpecStatus.IN_REVIEW, SpecStatus.CHANGES_REQUESTED,
        SpecStatus.APPROVED, SpecStatus.IN_IMPLEMENTATION, SpecStatus.IMPL_IN_REVIEW, SpecStatus.IMPLEMENTED);

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

        boolean allDeclaredAreasIn(Set<SpecStatus> statuses) {
            return !areas.isEmpty() && areas.stream().allMatch(a -> statuses.contains(techDocs.get(a.kind())));
        }
    }

    /** guard 가 보는 스냅샷 전체. 대상 문서 자신의 PR 링크 수와 그 문서가 속한 PRD 쪽 사실이다. */
    public record Facts(PrdFacts prd, int prLinks) {
    }

    public enum Guard {
        PRD_APPROVED("부모 PRD 가 PRD_APPROVED 상태가 아닙니다.",
            (kind, f) -> f.prd().status() == SpecStatus.PRD_APPROVED),
        AREA_DECLARED("PRD 의 tech_areas 에 선언되지 않은 영역입니다.",
            (kind, f) -> TechArea.of(kind).filter(f.prd().areas()::contains).isPresent()),
        AREA_VACANT("이 영역의 기술문서가 이미 있습니다.",
            (kind, f) -> !f.prd().techDocs().containsKey(kind)),
        ALL_AREAS_APPROVED("선언된 모든 영역의 기술문서가 승인되지 않았습니다.",
            (kind, f) -> f.prd().allDeclaredAreasIn(APPROVED_OR_LATER)),
        PRD_SPEC_APPROVED("부모 PRD 가 SPEC_APPROVED 상태가 아닙니다.",
            (kind, f) -> f.prd().status() == SpecStatus.SPEC_APPROVED),
        HAS_PR("연결된 PR 이 없습니다. 먼저 PR 링크를 추가하세요.",
            (kind, f) -> f.prLinks() > 0),
        ALL_AREAS_IMPLEMENTED("선언된 모든 영역의 기술문서가 구현 완료되지 않았습니다.",
            (kind, f) -> f.prd().allDeclaredAreasIn(EnumSet.of(SpecStatus.IMPLEMENTED)));

        private final String failure;
        private final BiPredicate<SpecKind, Facts> holds;

        Guard(String failure, BiPredicate<SpecKind, Facts> holds) {
            this.failure = failure;
            this.holds = holds;
        }

        public String failure() {
            return failure;
        }

        boolean holds(SpecKind kind, Facts facts) {
            return holds.test(kind, facts);
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

    public static final List<Transition> TABLE = Stream.of(
        Stream.of(
            row(SpecKind.PRD, null,                    SpecAction.CREATE,         List.of(Roles.PLANNER), SpecStatus.DRAFT),
            row(SpecKind.PRD, SpecStatus.DRAFT,        SpecAction.EDIT,           List.of(Roles.PLANNER), SpecStatus.DRAFT),
            row(SpecKind.PRD, SpecStatus.DRAFT,        SpecAction.APPROVE,        List.of(Roles.PLANNER), SpecStatus.PRD_APPROVED),
            row(SpecKind.PRD, SpecStatus.PRD_APPROVED, SpecAction.SET_TECH_AREAS, List.of(Roles.PLANNER), SpecStatus.PRD_APPROVED),
            row(SpecKind.PRD, SpecStatus.PRD_APPROVED, SpecAction.AGGREGATE,      List.of(Roles.SYSTEM),  SpecStatus.SPEC_APPROVED,
                Guard.ALL_AREAS_APPROVED),
            row(SpecKind.PRD, SpecStatus.SPEC_APPROVED, SpecAction.AGGREGATE,     List.of(Roles.SYSTEM),  SpecStatus.IMPLEMENTED,
                Guard.ALL_AREAS_IMPLEMENTED)),
        PRD_APPROVED_STATES.stream().flatMap(SpecTransitions::versionRows),
        Arrays.stream(TechArea.values()).flatMap(SpecTransitions::techRows)
    ).flatMap(rows -> rows).toList();

    private static Stream<Transition> versionRows(SpecStatus approved) {
        return Stream.of(
            row(SpecKind.PRD, approved, SpecAction.NEW_VERSION, List.of(Roles.PLANNER), approved),
            row(SpecKind.PRD, approved, SpecAction.SUPERSEDE,   List.of(Roles.SYSTEM),  SpecStatus.SUPERSEDED));
    }

    private static Stream<Transition> techRows(TechArea area) {
        SpecKind kind = area.kind();
        List<String> writers = List.of(Roles.PLANNER, area.role());
        List<String> areaRole = List.of(area.role());
        List<String> codeReviewer = List.of(Roles.REVIEWER);
        Stream<Transition> rows = Stream.of(
            row(kind, null,                         SpecAction.CREATE,                 writers,      SpecStatus.DRAFT,
                Guard.PRD_APPROVED, Guard.AREA_DECLARED, Guard.AREA_VACANT),
            row(kind, SpecStatus.DRAFT,             SpecAction.EDIT,                   writers,      SpecStatus.DRAFT),
            row(kind, SpecStatus.CHANGES_REQUESTED, SpecAction.EDIT,                   writers,      SpecStatus.DRAFT),
            row(kind, SpecStatus.DRAFT,             SpecAction.SUBMIT,                 writers,      SpecStatus.IN_REVIEW),
            row(kind, SpecStatus.IN_REVIEW,         SpecAction.APPROVE,                areaRole,     SpecStatus.APPROVED),
            row(kind, SpecStatus.IN_REVIEW,         SpecAction.REQUEST_CHANGES,        areaRole,     SpecStatus.CHANGES_REQUESTED),
            row(kind, SpecStatus.APPROVED,          SpecAction.START_IMPLEMENTATION,   areaRole,     SpecStatus.IN_IMPLEMENTATION,
                Guard.PRD_SPEC_APPROVED),
            row(kind, SpecStatus.IN_IMPLEMENTATION, SpecAction.LINK_PR,                areaRole,     SpecStatus.IN_IMPLEMENTATION),
            row(kind, SpecStatus.IN_IMPLEMENTATION, SpecAction.UNLINK_PR,              areaRole,     SpecStatus.IN_IMPLEMENTATION),
            row(kind, SpecStatus.IN_IMPLEMENTATION, SpecAction.SUBMIT_IMPLEMENTATION,  areaRole,     SpecStatus.IMPL_IN_REVIEW,
                Guard.HAS_PR),
            row(kind, SpecStatus.IMPL_IN_REVIEW,    SpecAction.APPROVE_IMPLEMENTATION, codeReviewer, SpecStatus.IMPLEMENTED),
            row(kind, SpecStatus.IMPL_IN_REVIEW,    SpecAction.REQUEST_IMPL_CHANGES,   codeReviewer, SpecStatus.IN_IMPLEMENTATION));
        // 새 PRD 버전 승인은 구현 전 승인만 무효로 돌리고, 나머지는 상태를 두고 "PRD 변경" 표시만 남긴다
        Stream<Transition> prdRevised = TECH_STATES.stream().map(from -> row(kind, from, SpecAction.PRD_REVISED,
            List.of(Roles.SYSTEM), from == SpecStatus.APPROVED ? SpecStatus.IN_REVIEW : from));
        Stream<Transition> acknowledge = IMPLEMENTATION_STATES.stream().map(from -> row(kind, from,
            SpecAction.ACKNOWLEDGE_PRD_CHANGE, areaRole, from));
        return Stream.of(rows, prdRevised, acknowledge).flatMap(r -> r);
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
                                  Facts facts) {
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
            .filter(g -> !g.holds(kind, facts))
            .findFirst()
            .map(g -> new Decision(Verdict.PRECONDITION_FAILED, row, null, g))
            .orElseGet(() -> new Decision(Verdict.ALLOWED, row, actorRole, null));
    }
}
