package com.docsportal.docs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.docsportal.docs.common.Roles;
import com.docsportal.docs.persistence.SpecAction;
import com.docsportal.docs.persistence.SpecKind;
import com.docsportal.docs.persistence.SpecStatus;
import com.docsportal.docs.service.SpecTransitions.Decision;
import com.docsportal.docs.service.SpecTransitions.Verdict;

class SpecTransitionsTest {

    private static final List<String> PLANNER = List.of(Roles.PLANNER, Roles.USER);
    private static final List<String> ADMIN = List.of(Roles.ADMIN, Roles.USER);

    private static Decision decide(SpecStatus current, SpecAction action, List<String> roles) {
        return SpecTransitions.decide(SpecKind.PRD, current, action, roles);
    }

    @Test
    void plannerCreatesDraft() {
        Decision d = decide(null, SpecAction.CREATE, PLANNER);

        assertThat(d.verdict()).isEqualTo(Verdict.ALLOWED);
        assertThat(d.transition().to()).isEqualTo(SpecStatus.DRAFT);
        assertThat(d.actorRole()).isEqualTo(Roles.PLANNER);
    }

    @Test
    void plannerEditsDraftAndStaysDraft() {
        Decision d = decide(SpecStatus.DRAFT, SpecAction.EDIT, PLANNER);

        assertThat(d.verdict()).isEqualTo(Verdict.ALLOWED);
        assertThat(d.transition().to()).isEqualTo(SpecStatus.DRAFT);
    }

    @Test
    void plannerApprovesDraft() {
        Decision d = decide(SpecStatus.DRAFT, SpecAction.APPROVE, PLANNER);

        assertThat(d.verdict()).isEqualTo(Verdict.ALLOWED);
        assertThat(d.transition().to()).isEqualTo(SpecStatus.PRD_APPROVED);
    }

    @Test
    void approveIsIdempotentOnApproved() {
        Decision d = decide(SpecStatus.PRD_APPROVED, SpecAction.APPROVE, PLANNER);

        assertThat(d.verdict()).isEqualTo(Verdict.ALREADY_DONE);
        assertThat(d.transition()).isNull();
    }

    @Test
    void editingApprovedIsInvalidState() {
        assertThat(decide(SpecStatus.PRD_APPROVED, SpecAction.EDIT, PLANNER).verdict())
            .isEqualTo(Verdict.INVALID_STATE);
    }

    @Test
    void createIsNotConvergent() {
        assertThat(decide(SpecStatus.DRAFT, SpecAction.CREATE, PLANNER).verdict())
            .isEqualTo(Verdict.INVALID_STATE);
    }

    @Test
    void nonPlannerIsForbiddenForEveryAction() {
        for (SpecAction action : SpecAction.values()) {
            for (SpecStatus status : new SpecStatus[] {null, SpecStatus.DRAFT, SpecStatus.PRD_APPROVED}) {
                assertThat(decide(status, action, ADMIN).verdict())
                    .as("%s from %s", action, status)
                    .isEqualTo(Verdict.FORBIDDEN);
            }
        }
    }

    @Test
    void roleIsCheckedBeforeState() {
        assertThat(decide(SpecStatus.PRD_APPROVED, SpecAction.EDIT, List.of(Roles.USER)).verdict())
            .isEqualTo(Verdict.FORBIDDEN);
    }

    @Test
    void tableHasNoDuplicateRows() {
        long distinct = SpecTransitions.TABLE.stream()
            .map(t -> List.of(t.kind().name(), String.valueOf(t.from()), t.action().name()))
            .distinct()
            .count();

        assertThat(distinct).isEqualTo(SpecTransitions.TABLE.size());
    }
}
