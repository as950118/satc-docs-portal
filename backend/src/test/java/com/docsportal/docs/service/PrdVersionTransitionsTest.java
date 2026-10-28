package com.docsportal.docs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.docsportal.docs.common.Roles;
import com.docsportal.docs.persistence.SpecAction;
import com.docsportal.docs.persistence.SpecKind;
import com.docsportal.docs.persistence.SpecStatus;
import com.docsportal.docs.persistence.TechArea;
import com.docsportal.docs.service.SpecTransitions.Decision;
import com.docsportal.docs.service.SpecTransitions.Facts;
import com.docsportal.docs.service.SpecTransitions.Guard;
import com.docsportal.docs.service.SpecTransitions.PrdFacts;
import com.docsportal.docs.service.SpecTransitions.Transition;
import com.docsportal.docs.service.SpecTransitions.Verdict;

class PrdVersionTransitionsTest {

    private static final List<String> PLANNER = List.of(Roles.PLANNER, Roles.USER);
    private static final List<String> SYSTEM = List.of(Roles.SYSTEM);
    private static final List<String> EVERY_ROLE = List.of(Roles.ADMIN, Roles.USER, Roles.PLANNER, Roles.BACKEND,
        Roles.FRONTEND, Roles.DATA, Roles.REVIEWER, Roles.SYSTEM);
    private static final Facts NO_FACTS = new Facts(PrdFacts.NONE, 0);

    private static Decision prd(SpecStatus current, SpecAction action, List<String> roles) {
        return SpecTransitions.decide(SpecKind.PRD, current, action, roles, NO_FACTS);
    }

    private static Decision tech(TechArea area, SpecStatus current, SpecAction action, List<String> roles) {
        return SpecTransitions.decide(area.kind(), current, action, roles, NO_FACTS);
    }

    @Test
    void plannerCreatesNewVersionOnlyFromAnApprovedPrdWhichKeepsItsStatus() {
        for (SpecStatus approved : List.of(SpecStatus.PRD_APPROVED, SpecStatus.SPEC_APPROVED, SpecStatus.IMPLEMENTED)) {
            Decision d = prd(approved, SpecAction.NEW_VERSION, PLANNER);
            assertThat(d.verdict()).as("%s", approved).isEqualTo(Verdict.ALLOWED);
            assertThat(d.transition().to()).as("%s", approved).isEqualTo(approved);
        }
        for (SpecStatus other : List.of(SpecStatus.DRAFT, SpecStatus.SUPERSEDED)) {
            assertThat(prd(other, SpecAction.NEW_VERSION, PLANNER).verdict()).as("%s", other).isEqualTo(Verdict.INVALID_STATE);
        }
        for (String role : List.of(Roles.BACKEND, Roles.REVIEWER, Roles.ADMIN, Roles.SYSTEM)) {
            assertThat(prd(SpecStatus.SPEC_APPROVED, SpecAction.NEW_VERSION, List.of(role)).verdict())
                .as(role).isEqualTo(Verdict.FORBIDDEN);
        }
        assertThat(tech(TechArea.BE, SpecStatus.APPROVED, SpecAction.NEW_VERSION, List.of(Roles.PLANNER, Roles.BACKEND))
            .verdict()).isEqualTo(Verdict.INVALID_STATE);
    }

    @Test
    void onlyTheSystemSupersedesAndOnlyAnApprovedPrd() {
        for (SpecStatus approved : SpecTransitions.PRD_APPROVED_STATES) {
            Decision d = prd(approved, SpecAction.SUPERSEDE, SYSTEM);
            assertThat(d.verdict()).isEqualTo(Verdict.ALLOWED);
            assertThat(d.transition().to()).isEqualTo(SpecStatus.SUPERSEDED);
        }
        assertThat(prd(SpecStatus.DRAFT, SpecAction.SUPERSEDE, SYSTEM).verdict()).isEqualTo(Verdict.INVALID_STATE);
        assertThat(prd(SpecStatus.SPEC_APPROVED, SpecAction.SUPERSEDE, PLANNER).verdict()).isEqualTo(Verdict.FORBIDDEN);
    }

    @Test
    void supersededPrdHasNoWayOut() {
        assertThat(SpecTransitions.TABLE).noneMatch(t -> t.from() == SpecStatus.SUPERSEDED);
        for (SpecAction action : SpecAction.values()) {
            assertThat(prd(SpecStatus.SUPERSEDED, action, EVERY_ROLE).verdict())
                .as("%s", action).isIn(Verdict.INVALID_STATE, Verdict.FORBIDDEN);
        }
    }

    @ParameterizedTest
    @EnumSource(TechArea.class)
    void prdRevisionReopensOnlyApprovedDocsAndFlagsTheRest(TechArea area) {
        Map<SpecStatus, SpecStatus> expected = Stream.of(SpecStatus.values())
            .filter(SpecTransitions.TECH_STATES::contains)
            .collect(Collectors.toMap(s -> s, s -> s == SpecStatus.APPROVED ? SpecStatus.IN_REVIEW : s));
        expected.forEach((from, to) -> {
            Decision d = tech(area, from, SpecAction.PRD_REVISED, SYSTEM);
            assertThat(d.verdict()).as("%s", from).isEqualTo(Verdict.ALLOWED);
            assertThat(d.transition().to()).as("%s", from).isEqualTo(to);
        });
        assertThat(tech(area, SpecStatus.APPROVED, SpecAction.PRD_REVISED, List.of(area.role())).verdict())
            .isEqualTo(Verdict.FORBIDDEN);
    }

    @Test
    void everyStatusATechDocCanReachHasAPrdRevisedRow() {
        Set<SpecStatus> reachable = SpecTransitions.TABLE.stream()
            .filter(t -> t.kind() != SpecKind.PRD)
            .flatMap(t -> Stream.of(t.from(), t.to()))
            .filter(s -> s != null)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(SpecStatus.class)));
        Set<SpecStatus> revised = SpecTransitions.TABLE.stream()
            .filter(t -> t.kind() == SpecKind.TECH_BE && t.action() == SpecAction.PRD_REVISED)
            .map(Transition::from)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(SpecStatus.class)));
        assertThat(revised).isEqualTo(reachable);
    }

    @ParameterizedTest
    @EnumSource(TechArea.class)
    void areaRoleAcknowledgesPrdChangeOnlyDuringImplementation(TechArea area) {
        for (SpecStatus impl : List.of(SpecStatus.IN_IMPLEMENTATION, SpecStatus.IMPL_IN_REVIEW, SpecStatus.IMPLEMENTED)) {
            Decision d = tech(area, impl, SpecAction.ACKNOWLEDGE_PRD_CHANGE, List.of(area.role()));
            assertThat(d.verdict()).as("%s", impl).isEqualTo(Verdict.ALLOWED);
            assertThat(d.transition().to()).as("%s", impl).isEqualTo(impl);
            for (List<String> other : List.of(PLANNER, List.of(Roles.REVIEWER), List.of(area == TechArea.BE ? Roles.FRONTEND : Roles.BACKEND))) {
                assertThat(tech(area, impl, SpecAction.ACKNOWLEDGE_PRD_CHANGE, other).verdict())
                    .as("%s %s", impl, other).isEqualTo(Verdict.FORBIDDEN);
            }
        }
        for (SpecStatus early : List.of(SpecStatus.DRAFT, SpecStatus.IN_REVIEW, SpecStatus.CHANGES_REQUESTED, SpecStatus.APPROVED)) {
            assertThat(tech(area, early, SpecAction.ACKNOWLEDGE_PRD_CHANGE, List.of(area.role())).verdict())
                .as("%s", early).isEqualTo(Verdict.INVALID_STATE);
        }
    }

    private static Decision aggregate(Set<TechArea> areas, Map<SpecKind, SpecStatus> docs) {
        return SpecTransitions.decide(SpecKind.PRD, SpecStatus.PRD_APPROVED, SpecAction.AGGREGATE, SYSTEM,
            new Facts(new PrdFacts(SpecStatus.PRD_APPROVED, areas, docs), 0));
    }

    @Test
    void specApprovalCountsDocsAlreadyPastReviewAfterARevision() {
        Set<TechArea> beFe = EnumSet.of(TechArea.BE, TechArea.FE);
        for (SpecStatus later : List.of(SpecStatus.APPROVED, SpecStatus.IN_IMPLEMENTATION, SpecStatus.IMPL_IN_REVIEW,
                SpecStatus.IMPLEMENTED)) {
            Decision d = aggregate(beFe, Map.of(SpecKind.TECH_BE, later, SpecKind.TECH_FE, SpecStatus.APPROVED));
            assertThat(d.verdict()).as("%s", later).isEqualTo(Verdict.ALLOWED);
            assertThat(d.transition().to()).isEqualTo(SpecStatus.SPEC_APPROVED);
        }
        Decision reReview = aggregate(beFe, Map.of(SpecKind.TECH_BE, SpecStatus.IN_IMPLEMENTATION, SpecKind.TECH_FE,
            SpecStatus.IN_REVIEW));
        assertThat(reReview.failedGuard()).isEqualTo(Guard.ALL_AREAS_APPROVED);
    }
}
