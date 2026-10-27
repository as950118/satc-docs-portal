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
import com.docsportal.docs.service.SpecTransitions.Facts;
import com.docsportal.docs.service.SpecTransitions.Guard;
import com.docsportal.docs.service.SpecTransitions.PrdFacts;
import com.docsportal.docs.service.SpecTransitions.Verdict;

class ImplementationTransitionsTest {

    private static final List<String> PLANNER = List.of(Roles.PLANNER, Roles.USER);
    private static final List<String> REVIEWER = List.of(Roles.REVIEWER, Roles.USER);
    private static final List<String> SYSTEM = List.of(Roles.SYSTEM);

    private static List<String> roleOf(TechArea area) {
        return List.of(area.role(), Roles.USER);
    }

    private static TechArea otherThan(TechArea area) {
        return area == TechArea.BE ? TechArea.FE : TechArea.BE;
    }

    private static Facts specApproved(TechArea area, int prLinks) {
        return new Facts(new PrdFacts(SpecStatus.SPEC_APPROVED, EnumSet.of(area), Map.of(area.kind(), SpecStatus.APPROVED)),
            prLinks);
    }

    private static Decision decide(TechArea area, SpecStatus current, SpecAction action, List<String> roles, int prLinks) {
        return SpecTransitions.decide(area.kind(), current, action, roles, specApproved(area, prLinks));
    }

    private static Decision decide(TechArea area, SpecStatus current, SpecAction action, List<String> roles) {
        return decide(area, current, action, roles, 1);
    }

    @ParameterizedTest
    @EnumSource(TechArea.class)
    void areaRoleStartsImplementationOfApprovedDocUnderSpecApprovedPrd(TechArea area) {
        Decision d = decide(area, SpecStatus.APPROVED, SpecAction.START_IMPLEMENTATION, roleOf(area));
        assertThat(d.verdict()).isEqualTo(Verdict.ALLOWED);
        assertThat(d.transition().to()).isEqualTo(SpecStatus.IN_IMPLEMENTATION);
        assertThat(d.actorRole()).isEqualTo(area.role());

        assertThat(decide(area, SpecStatus.IN_IMPLEMENTATION, SpecAction.START_IMPLEMENTATION, roleOf(area)).verdict())
            .isEqualTo(Verdict.ALREADY_DONE);
        for (SpecStatus early : List.of(SpecStatus.DRAFT, SpecStatus.IN_REVIEW, SpecStatus.CHANGES_REQUESTED)) {
            assertThat(decide(area, early, SpecAction.START_IMPLEMENTATION, roleOf(area)).verdict())
                .as("%s", early).isEqualTo(Verdict.INVALID_STATE);
        }
        for (SpecStatus later : List.of(SpecStatus.IMPL_IN_REVIEW, SpecStatus.IMPLEMENTED)) {
            assertThat(decide(area, later, SpecAction.START_IMPLEMENTATION, roleOf(area)).verdict())
                .as("%s", later).isEqualTo(Verdict.INVALID_STATE);
        }
    }

    @ParameterizedTest
    @EnumSource(TechArea.class)
    void startNeedsParentPrdSpecApproved(TechArea area) {
        for (SpecStatus prdStatus : List.of(SpecStatus.PRD_APPROVED, SpecStatus.IMPLEMENTED)) {
            Facts facts = new Facts(new PrdFacts(prdStatus, EnumSet.of(area), Map.of(area.kind(), SpecStatus.APPROVED)), 0);
            Decision d = SpecTransitions.decide(area.kind(), SpecStatus.APPROVED, SpecAction.START_IMPLEMENTATION,
                roleOf(area), facts);
            assertThat(d.verdict()).as("%s", prdStatus).isEqualTo(Verdict.PRECONDITION_FAILED);
            assertThat(d.failedGuard()).as("%s", prdStatus).isEqualTo(Guard.PRD_SPEC_APPROVED);
        }
    }

    @ParameterizedTest
    @EnumSource(TechArea.class)
    void plannerReviewerAndOtherAreaCannotStartLinkOrSubmit(TechArea area) {
        for (List<String> roles : List.of(PLANNER, REVIEWER, roleOf(otherThan(area)))) {
            assertThat(decide(area, SpecStatus.APPROVED, SpecAction.START_IMPLEMENTATION, roles).verdict())
                .as("%s", roles).isEqualTo(Verdict.FORBIDDEN);
            assertThat(decide(area, SpecStatus.IN_IMPLEMENTATION, SpecAction.LINK_PR, roles).verdict())
                .as("%s", roles).isEqualTo(Verdict.FORBIDDEN);
            assertThat(decide(area, SpecStatus.IN_IMPLEMENTATION, SpecAction.UNLINK_PR, roles).verdict())
                .as("%s", roles).isEqualTo(Verdict.FORBIDDEN);
            assertThat(decide(area, SpecStatus.IN_IMPLEMENTATION, SpecAction.SUBMIT_IMPLEMENTATION, roles).verdict())
                .as("%s", roles).isEqualTo(Verdict.FORBIDDEN);
        }
    }

    @ParameterizedTest
    @EnumSource(TechArea.class)
    void linkPrOnlyWhileInImplementationAndKeepsState(TechArea area) {
        Decision d = decide(area, SpecStatus.IN_IMPLEMENTATION, SpecAction.LINK_PR, roleOf(area), 0);
        assertThat(d.verdict()).isEqualTo(Verdict.ALLOWED);
        assertThat(d.transition().to()).isEqualTo(SpecStatus.IN_IMPLEMENTATION);

        for (SpecStatus other : List.of(SpecStatus.APPROVED, SpecStatus.IMPL_IN_REVIEW, SpecStatus.IMPLEMENTED)) {
            assertThat(decide(area, other, SpecAction.LINK_PR, roleOf(area)).verdict())
                .as("%s", other).isEqualTo(Verdict.INVALID_STATE);
        }
    }

    @ParameterizedTest
    @EnumSource(TechArea.class)
    void unlinkPrOnlyWhileInImplementationSoSubmittedEvidenceStays(TechArea area) {
        Decision d = decide(area, SpecStatus.IN_IMPLEMENTATION, SpecAction.UNLINK_PR, roleOf(area), 1);
        assertThat(d.verdict()).isEqualTo(Verdict.ALLOWED);
        assertThat(d.transition().to()).isEqualTo(SpecStatus.IN_IMPLEMENTATION);
        assertThat(d.actorRole()).isEqualTo(area.role());

        for (SpecStatus other : List.of(SpecStatus.APPROVED, SpecStatus.IMPL_IN_REVIEW, SpecStatus.IMPLEMENTED)) {
            assertThat(decide(area, other, SpecAction.UNLINK_PR, roleOf(area)).verdict())
                .as("%s", other).isEqualTo(Verdict.INVALID_STATE);
        }
        assertThat(decide(area, SpecStatus.IMPL_IN_REVIEW, SpecAction.UNLINK_PR, REVIEWER).verdict())
            .isEqualTo(Verdict.FORBIDDEN);
    }

    @ParameterizedTest
    @EnumSource(TechArea.class)
    void submitImplementationNeedsAtLeastOnePrAndIsIdempotent(TechArea area) {
        Decision none = decide(area, SpecStatus.IN_IMPLEMENTATION, SpecAction.SUBMIT_IMPLEMENTATION, roleOf(area), 0);
        assertThat(none.verdict()).isEqualTo(Verdict.PRECONDITION_FAILED);
        assertThat(none.failedGuard()).isEqualTo(Guard.HAS_PR);

        Decision d = decide(area, SpecStatus.IN_IMPLEMENTATION, SpecAction.SUBMIT_IMPLEMENTATION, roleOf(area), 1);
        assertThat(d.verdict()).isEqualTo(Verdict.ALLOWED);
        assertThat(d.transition().to()).isEqualTo(SpecStatus.IMPL_IN_REVIEW);

        assertThat(decide(area, SpecStatus.IMPL_IN_REVIEW, SpecAction.SUBMIT_IMPLEMENTATION, roleOf(area), 0).verdict())
            .isEqualTo(Verdict.ALREADY_DONE);
        assertThat(decide(area, SpecStatus.APPROVED, SpecAction.SUBMIT_IMPLEMENTATION, roleOf(area)).verdict())
            .isEqualTo(Verdict.INVALID_STATE);
    }

    @ParameterizedTest
    @EnumSource(TechArea.class)
    void onlyReviewerApprovesImplementationAndApproveIsIdempotent(TechArea area) {
        Decision d = decide(area, SpecStatus.IMPL_IN_REVIEW, SpecAction.APPROVE_IMPLEMENTATION, REVIEWER);
        assertThat(d.verdict()).isEqualTo(Verdict.ALLOWED);
        assertThat(d.transition().to()).isEqualTo(SpecStatus.IMPLEMENTED);
        assertThat(d.actorRole()).isEqualTo(Roles.REVIEWER);

        for (List<String> roles : List.of(roleOf(area), PLANNER)) {
            assertThat(decide(area, SpecStatus.IMPL_IN_REVIEW, SpecAction.APPROVE_IMPLEMENTATION, roles).verdict())
                .as("%s", roles).isEqualTo(Verdict.FORBIDDEN);
        }
        assertThat(decide(area, SpecStatus.IMPLEMENTED, SpecAction.APPROVE_IMPLEMENTATION, REVIEWER).verdict())
            .isEqualTo(Verdict.ALREADY_DONE);
        assertThat(decide(area, SpecStatus.IN_IMPLEMENTATION, SpecAction.APPROVE_IMPLEMENTATION, REVIEWER).verdict())
            .isEqualTo(Verdict.INVALID_STATE);
    }

    @ParameterizedTest
    @EnumSource(TechArea.class)
    void onlyReviewerRequestsImplementationChangesAndRepeatIsRejected(TechArea area) {
        Decision d = decide(area, SpecStatus.IMPL_IN_REVIEW, SpecAction.REQUEST_IMPL_CHANGES, REVIEWER);
        assertThat(d.verdict()).isEqualTo(Verdict.ALLOWED);
        assertThat(d.transition().to()).isEqualTo(SpecStatus.IN_IMPLEMENTATION);
        assertThat(SpecAction.REQUEST_IMPL_CHANGES.isCommentRequired()).isTrue();

        assertThat(decide(area, SpecStatus.IMPL_IN_REVIEW, SpecAction.REQUEST_IMPL_CHANGES, roleOf(area)).verdict())
            .isEqualTo(Verdict.FORBIDDEN);
        assertThat(decide(area, SpecStatus.IN_IMPLEMENTATION, SpecAction.REQUEST_IMPL_CHANGES, REVIEWER).verdict())
            .isEqualTo(Verdict.INVALID_STATE);
        assertThat(decide(area, SpecStatus.IMPLEMENTED, SpecAction.REQUEST_IMPL_CHANGES, REVIEWER).verdict())
            .isEqualTo(Verdict.INVALID_STATE);
    }

    @ParameterizedTest
    @EnumSource(TechArea.class)
    void docsPastApprovalAreLockedForEdit(TechArea area) {
        for (SpecStatus locked : List.of(SpecStatus.IN_IMPLEMENTATION, SpecStatus.IMPL_IN_REVIEW, SpecStatus.IMPLEMENTED)) {
            for (List<String> roles : List.of(PLANNER, roleOf(area))) {
                assertThat(decide(area, locked, SpecAction.EDIT, roles).verdict())
                    .as("%s", locked).isEqualTo(Verdict.INVALID_STATE);
            }
        }
    }

    @Test
    void reviewerHasNoPrdOrTechWritingRights() {
        assertThat(SpecTransitions.decide(SpecKind.PRD, null, SpecAction.CREATE, REVIEWER, new Facts(PrdFacts.NONE, 0))
            .verdict()).isEqualTo(Verdict.FORBIDDEN);
        assertThat(decide(TechArea.BE, SpecStatus.IN_REVIEW, SpecAction.APPROVE, REVIEWER).verdict())
            .isEqualTo(Verdict.FORBIDDEN);
    }

    private static Decision aggregate(SpecStatus prdStatus, Set<TechArea> areas, Map<SpecKind, SpecStatus> techDocs) {
        return SpecTransitions.decide(SpecKind.PRD, prdStatus, SpecAction.AGGREGATE, SYSTEM,
            new Facts(new PrdFacts(prdStatus, areas, techDocs), 0));
    }

    @Test
    void implementedAggregateNeedsEveryDeclaredAreaImplemented() {
        Set<TechArea> beFe = EnumSet.of(TechArea.BE, TechArea.FE);

        assertThat(aggregate(SpecStatus.SPEC_APPROVED, beFe,
            Map.of(SpecKind.TECH_BE, SpecStatus.IMPLEMENTED, SpecKind.TECH_FE, SpecStatus.IMPL_IN_REVIEW)).failedGuard())
            .isEqualTo(Guard.ALL_AREAS_IMPLEMENTED);
        assertThat(aggregate(SpecStatus.SPEC_APPROVED, beFe, Map.of(SpecKind.TECH_BE, SpecStatus.IMPLEMENTED)).failedGuard())
            .isEqualTo(Guard.ALL_AREAS_IMPLEMENTED);
        assertThat(aggregate(SpecStatus.SPEC_APPROVED, EnumSet.noneOf(TechArea.class), Map.of()).failedGuard())
            .isEqualTo(Guard.ALL_AREAS_IMPLEMENTED);

        Decision all = aggregate(SpecStatus.SPEC_APPROVED, beFe,
            Map.of(SpecKind.TECH_BE, SpecStatus.IMPLEMENTED, SpecKind.TECH_FE, SpecStatus.IMPLEMENTED,
                SpecKind.TECH_DATA, SpecStatus.DRAFT));
        assertThat(all.verdict()).isEqualTo(Verdict.ALLOWED);
        assertThat(all.transition().from()).isEqualTo(SpecStatus.SPEC_APPROVED);
        assertThat(all.transition().to()).isEqualTo(SpecStatus.IMPLEMENTED);
        assertThat(all.actorRole()).isEqualTo(Roles.SYSTEM);
    }

    @Test
    void aggregateClimbsOneLevelAtATime() {
        Map<SpecKind, SpecStatus> beImplemented = Map.of(SpecKind.TECH_BE, SpecStatus.IMPLEMENTED);
        Decision fromPrdApproved = aggregate(SpecStatus.PRD_APPROVED, EnumSet.of(TechArea.BE), beImplemented);
        assertThat(fromPrdApproved.verdict()).isEqualTo(Verdict.PRECONDITION_FAILED);
        assertThat(fromPrdApproved.failedGuard()).isEqualTo(Guard.ALL_AREAS_APPROVED);

        Decision first = aggregate(SpecStatus.PRD_APPROVED, EnumSet.of(TechArea.BE), Map.of(SpecKind.TECH_BE, SpecStatus.APPROVED));
        assertThat(first.transition().to()).isEqualTo(SpecStatus.SPEC_APPROVED);
        Decision second = aggregate(first.transition().to(), EnumSet.of(TechArea.BE), beImplemented);
        assertThat(second.transition().to()).isEqualTo(SpecStatus.IMPLEMENTED);
        assertThat(aggregate(SpecStatus.IMPLEMENTED, EnumSet.of(TechArea.BE), beImplemented).verdict())
            .isEqualTo(Verdict.ALREADY_DONE);
    }

    @Test
    void noUserRoleCanAggregateToImplemented() {
        for (String role : List.of(Roles.PLANNER, Roles.REVIEWER, Roles.BACKEND, Roles.ADMIN)) {
            assertThat(SpecTransitions.decide(SpecKind.PRD, SpecStatus.SPEC_APPROVED, SpecAction.AGGREGATE, List.of(role),
                new Facts(new PrdFacts(SpecStatus.SPEC_APPROVED, EnumSet.of(TechArea.BE),
                    Map.of(SpecKind.TECH_BE, SpecStatus.IMPLEMENTED)), 0)).verdict())
                .as(role).isEqualTo(Verdict.FORBIDDEN);
        }
    }
}
