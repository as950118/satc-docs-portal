package com.docsportal.docs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.docsportal.docs.common.Roles;
import com.docsportal.docs.persistence.SpecAction;
import com.docsportal.docs.persistence.SpecKind;
import com.docsportal.docs.persistence.SpecStatus;
import com.docsportal.docs.persistence.TechArea;
import com.docsportal.docs.service.SpecTransitions.Decision;
import com.docsportal.docs.service.SpecTransitions.Guard;
import com.docsportal.docs.service.SpecTransitions.PrdFacts;
import com.docsportal.docs.service.SpecTransitions.Verdict;

class TechTransitionsTest {

    private static final List<String> PLANNER = List.of(Roles.PLANNER, Roles.USER);
    private static final List<String> SYSTEM = List.of(Roles.SYSTEM);

    private static List<String> roleOf(TechArea area) {
        return List.of(area.role(), Roles.USER);
    }

    private static TechArea otherThan(TechArea area) {
        return area == TechArea.BE ? TechArea.FE : TechArea.BE;
    }

    private static PrdFacts approvedPrd(Set<TechArea> areas, Map<SpecKind, SpecStatus> techDocs) {
        return new PrdFacts(SpecStatus.PRD_APPROVED, areas, techDocs);
    }

    private static Decision decide(TechArea area, SpecStatus current, SpecAction action, List<String> roles) {
        return SpecTransitions.decide(area.kind(), current, action, roles, approvedPrd(EnumSet.of(area), Map.of()));
    }

    @ParameterizedTest
    @EnumSource(TechArea.class)
    void plannerAndAreaRoleCreateDraftUnderApprovedPrdWithDeclaredVacantArea(TechArea area) {
        for (List<String> roles : List.of(PLANNER, roleOf(area))) {
            Decision d = decide(area, null, SpecAction.CREATE, roles);
            assertThat(d.verdict()).isEqualTo(Verdict.ALLOWED);
            assertThat(d.transition().to()).isEqualTo(SpecStatus.DRAFT);
        }
        assertThat(decide(area, null, SpecAction.CREATE, roleOf(area)).actorRole()).isEqualTo(area.role());
    }

    @ParameterizedTest
    @EnumSource(TechArea.class)
    void otherAreaRoleCannotWriteOrReview(TechArea area) {
        List<String> other = roleOf(otherThan(area));
        assertThat(decide(area, null, SpecAction.CREATE, other).verdict()).isEqualTo(Verdict.FORBIDDEN);
        assertThat(decide(area, SpecStatus.DRAFT, SpecAction.EDIT, other).verdict()).isEqualTo(Verdict.FORBIDDEN);
        assertThat(decide(area, SpecStatus.DRAFT, SpecAction.SUBMIT, other).verdict()).isEqualTo(Verdict.FORBIDDEN);
        assertThat(decide(area, SpecStatus.IN_REVIEW, SpecAction.APPROVE, other).verdict()).isEqualTo(Verdict.FORBIDDEN);
        assertThat(decide(area, SpecStatus.IN_REVIEW, SpecAction.REQUEST_CHANGES, other).verdict()).isEqualTo(Verdict.FORBIDDEN);
    }

    @ParameterizedTest
    @EnumSource(TechArea.class)
    void createGuardsRejectUnapprovedPrdUndeclaredAreaAndOccupiedArea(TechArea area) {
        List<String> writer = roleOf(area);
        Map<PrdFacts, Guard> cases = Map.of(
            new PrdFacts(SpecStatus.DRAFT, EnumSet.of(area), Map.of()), Guard.PRD_APPROVED,
            new PrdFacts(SpecStatus.SPEC_APPROVED, EnumSet.of(area), Map.of()), Guard.PRD_APPROVED,
            approvedPrd(EnumSet.of(otherThan(area)), Map.of()), Guard.AREA_DECLARED,
            approvedPrd(EnumSet.noneOf(TechArea.class), Map.of()), Guard.AREA_DECLARED,
            approvedPrd(EnumSet.of(area), Map.of(area.kind(), SpecStatus.CHANGES_REQUESTED)), Guard.AREA_VACANT);

        cases.forEach((prd, guard) -> {
            Decision d = SpecTransitions.decide(area.kind(), null, SpecAction.CREATE, writer, prd);
            assertThat(d.verdict()).as("%s", prd).isEqualTo(Verdict.PRECONDITION_FAILED);
            assertThat(d.failedGuard()).as("%s", prd).isEqualTo(guard);
        });
    }

    @Test
    void createGuardsDoNotOverrideForbidden() {
        PrdFacts draftPrd = new PrdFacts(SpecStatus.DRAFT, Set.of(), Map.of());
        assertThat(SpecTransitions.decide(SpecKind.TECH_FE, null, SpecAction.CREATE, roleOf(TechArea.BE), draftPrd).verdict())
            .isEqualTo(Verdict.FORBIDDEN);
    }

    @ParameterizedTest
    @EnumSource(TechArea.class)
    void editIsAllowedFromDraftAndChangesRequestedAndReturnsToDraft(TechArea area) {
        for (SpecStatus from : List.of(SpecStatus.DRAFT, SpecStatus.CHANGES_REQUESTED)) {
            for (List<String> roles : List.of(PLANNER, roleOf(area))) {
                Decision d = decide(area, from, SpecAction.EDIT, roles);
                assertThat(d.verdict()).as("%s", from).isEqualTo(Verdict.ALLOWED);
                assertThat(d.transition().to()).isEqualTo(SpecStatus.DRAFT);
            }
        }
        for (SpecStatus locked : List.of(SpecStatus.IN_REVIEW, SpecStatus.APPROVED)) {
            assertThat(decide(area, locked, SpecAction.EDIT, roleOf(area)).verdict()).as("%s", locked)
                .isEqualTo(Verdict.INVALID_STATE);
        }
    }

    @ParameterizedTest
    @EnumSource(TechArea.class)
    void submitMovesDraftToReviewAndIsIdempotent(TechArea area) {
        Decision d = decide(area, SpecStatus.DRAFT, SpecAction.SUBMIT, PLANNER);
        assertThat(d.verdict()).isEqualTo(Verdict.ALLOWED);
        assertThat(d.transition().to()).isEqualTo(SpecStatus.IN_REVIEW);

        assertThat(decide(area, SpecStatus.IN_REVIEW, SpecAction.SUBMIT, roleOf(area)).verdict()).isEqualTo(Verdict.ALREADY_DONE);
        assertThat(decide(area, SpecStatus.CHANGES_REQUESTED, SpecAction.SUBMIT, roleOf(area)).verdict())
            .isEqualTo(Verdict.INVALID_STATE);
        assertThat(decide(area, SpecStatus.APPROVED, SpecAction.SUBMIT, roleOf(area)).verdict())
            .isEqualTo(Verdict.INVALID_STATE);
    }

    @ParameterizedTest
    @EnumSource(TechArea.class)
    void onlyAreaRoleApprovesAndApproveIsIdempotent(TechArea area) {
        Decision d = decide(area, SpecStatus.IN_REVIEW, SpecAction.APPROVE, roleOf(area));
        assertThat(d.verdict()).isEqualTo(Verdict.ALLOWED);
        assertThat(d.transition().to()).isEqualTo(SpecStatus.APPROVED);
        assertThat(d.actorRole()).isEqualTo(area.role());

        assertThat(decide(area, SpecStatus.IN_REVIEW, SpecAction.APPROVE, PLANNER).verdict()).isEqualTo(Verdict.FORBIDDEN);
        assertThat(decide(area, SpecStatus.APPROVED, SpecAction.APPROVE, roleOf(area)).verdict()).isEqualTo(Verdict.ALREADY_DONE);
        assertThat(decide(area, SpecStatus.DRAFT, SpecAction.APPROVE, roleOf(area)).verdict()).isEqualTo(Verdict.INVALID_STATE);
        assertThat(decide(area, SpecStatus.CHANGES_REQUESTED, SpecAction.APPROVE, roleOf(area)).verdict())
            .isEqualTo(Verdict.INVALID_STATE);
    }

    @ParameterizedTest
    @EnumSource(TechArea.class)
    void onlyAreaRoleRequestsChangesAndRepeatIsRejected(TechArea area) {
        Decision d = decide(area, SpecStatus.IN_REVIEW, SpecAction.REQUEST_CHANGES, roleOf(area));
        assertThat(d.verdict()).isEqualTo(Verdict.ALLOWED);
        assertThat(d.transition().to()).isEqualTo(SpecStatus.CHANGES_REQUESTED);

        assertThat(decide(area, SpecStatus.IN_REVIEW, SpecAction.REQUEST_CHANGES, PLANNER).verdict()).isEqualTo(Verdict.FORBIDDEN);
        assertThat(decide(area, SpecStatus.CHANGES_REQUESTED, SpecAction.REQUEST_CHANGES, roleOf(area)).verdict())
            .isEqualTo(Verdict.INVALID_STATE);
        assertThat(decide(area, SpecStatus.APPROVED, SpecAction.REQUEST_CHANGES, roleOf(area)).verdict())
            .isEqualTo(Verdict.INVALID_STATE);
    }

    @Test
    void plannerSetsTechAreasOnlyWhilePrdApproved() {
        PrdFacts none = PrdFacts.NONE;
        Decision d = SpecTransitions.decide(SpecKind.PRD, SpecStatus.PRD_APPROVED, SpecAction.SET_TECH_AREAS, PLANNER, none);
        assertThat(d.verdict()).isEqualTo(Verdict.ALLOWED);
        assertThat(d.transition().to()).isEqualTo(SpecStatus.PRD_APPROVED);

        for (SpecStatus other : List.of(SpecStatus.DRAFT, SpecStatus.SPEC_APPROVED)) {
            assertThat(SpecTransitions.decide(SpecKind.PRD, other, SpecAction.SET_TECH_AREAS, PLANNER, none).verdict())
                .as("%s", other).isEqualTo(Verdict.INVALID_STATE);
        }
        assertThat(SpecTransitions.decide(SpecKind.PRD, SpecStatus.PRD_APPROVED, SpecAction.SET_TECH_AREAS,
            roleOf(TechArea.BE), none).verdict()).isEqualTo(Verdict.FORBIDDEN);
    }

    private static Decision aggregate(SpecStatus prdStatus, Set<TechArea> areas, Map<SpecKind, SpecStatus> techDocs) {
        return SpecTransitions.decide(SpecKind.PRD, prdStatus, SpecAction.AGGREGATE, SYSTEM,
            new PrdFacts(prdStatus, areas, techDocs));
    }

    @Test
    void aggregateNeedsEveryDeclaredAreaApproved() {
        Set<TechArea> beFe = EnumSet.of(TechArea.BE, TechArea.FE);

        assertThat(aggregate(SpecStatus.PRD_APPROVED, EnumSet.noneOf(TechArea.class), Map.of()).failedGuard())
            .isEqualTo(Guard.ALL_AREAS_APPROVED);
        assertThat(aggregate(SpecStatus.PRD_APPROVED, beFe, Map.of(SpecKind.TECH_BE, SpecStatus.APPROVED)).failedGuard())
            .isEqualTo(Guard.ALL_AREAS_APPROVED);
        assertThat(aggregate(SpecStatus.PRD_APPROVED, beFe,
            Map.of(SpecKind.TECH_BE, SpecStatus.APPROVED, SpecKind.TECH_FE, SpecStatus.IN_REVIEW)).failedGuard())
            .isEqualTo(Guard.ALL_AREAS_APPROVED);

        Decision all = aggregate(SpecStatus.PRD_APPROVED, beFe,
            Map.of(SpecKind.TECH_BE, SpecStatus.APPROVED, SpecKind.TECH_FE, SpecStatus.APPROVED, SpecKind.TECH_DATA, SpecStatus.DRAFT));
        assertThat(all.verdict()).isEqualTo(Verdict.ALLOWED);
        assertThat(all.transition().to()).isEqualTo(SpecStatus.SPEC_APPROVED);
        assertThat(all.actorRole()).isEqualTo(Roles.SYSTEM);
    }

    @Test
    void aggregateIsIdempotentAndNotBeforePrdApproval() {
        Map<SpecKind, SpecStatus> beApproved = Map.of(SpecKind.TECH_BE, SpecStatus.APPROVED);
        assertThat(aggregate(SpecStatus.SPEC_APPROVED, EnumSet.of(TechArea.BE), beApproved).verdict())
            .isEqualTo(Verdict.ALREADY_DONE);
        assertThat(aggregate(SpecStatus.DRAFT, EnumSet.of(TechArea.BE), beApproved).verdict())
            .isEqualTo(Verdict.INVALID_STATE);
    }

    @Test
    void noUserRoleCanAggregate() {
        for (String role : List.of(Roles.PLANNER, Roles.ADMIN, Roles.BACKEND, Roles.FRONTEND, Roles.DATA)) {
            assertThat(SpecTransitions.decide(SpecKind.PRD, SpecStatus.PRD_APPROVED, SpecAction.AGGREGATE, List.of(role),
                approvedPrd(EnumSet.of(TechArea.BE), Map.of(SpecKind.TECH_BE, SpecStatus.APPROVED))).verdict())
                .as(role).isEqualTo(Verdict.FORBIDDEN);
        }
    }
}
