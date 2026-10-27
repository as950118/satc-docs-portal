package com.docsportal.docs.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.docsportal.docs.persistence.SpecAction;
import com.docsportal.docs.persistence.SpecEvent;
import com.docsportal.docs.persistence.SpecStatus;

class SpecImplementationApiTest extends SpecApiTestSupport {

    private static final String PR = "https://github.com/as950118/satc-docs-portal/pull/7";

    private ResultActions act(String id, String action, String bearer) throws Exception {
        return mvc.perform(post("/api/v1/specs/" + id + "/" + action).header("Authorization", bearer));
    }

    private ResultActions link(String id, String bearer, String url) throws Exception {
        return postJson("/api/v1/specs/" + id + "/pr-links", bearer, "{\"url\":\"" + url + "\"}");
    }

    private ResultActions requestImplChanges(String id, String bearer, String body) throws Exception {
        return postJson("/api/v1/specs/" + id + "/request-implementation-changes", bearer, body);
    }

    private String prdWithAreas(String areasJson) throws Exception {
        String planner = login("planner");
        String prd = idOf(postJson("/api/v1/specs", planner, "{\"title\":\"P\",\"body\":\"B\"}"));
        act(prd, "approve", planner).andExpect(status().isOk());
        mvc.perform(put("/api/v1/specs/" + prd + "/tech-areas").header("Authorization", planner)
            .contentType(MediaType.APPLICATION_JSON).content("{\"areas\":" + areasJson + "}")).andExpect(status().isOk());
        return prd;
    }

    private String approvedTech(String prd, String area, String user) throws Exception {
        String bearer = login(user);
        String id = idOf(postJson("/api/v1/specs/" + prd + "/tech-docs", bearer,
            "{\"area\":\"" + area + "\",\"title\":\"t\",\"body\":\"b\"}"));
        act(id, "submit", bearer).andExpect(status().isOk());
        act(id, "approve", bearer).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"));
        return id;
    }

    /** start → PR 연결 → 제출까지 진행한 기술문서. */
    private void toImplReview(String id, String user) throws Exception {
        String bearer = login(user);
        act(id, "start-implementation", bearer).andExpect(status().isOk());
        link(id, bearer, PR + "/" + id).andExpect(status().isOk());
        act(id, "submit-implementation", bearer).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("IMPL_IN_REVIEW"));
    }

    private ResultActions prLinksOf(String id) throws Exception {
        return mvc.perform(get("/api/v1/specs/" + id).header("Authorization", login("viewer")));
    }

    private List<SpecEvent> aggregateEvents(String prdId) {
        return eventsOf(prdId).stream().filter(e -> e.getAction() == SpecAction.AGGREGATE).toList();
    }

    @Test
    void newEndpointsRequireSession() throws Exception {
        String prd = prdWithAreas("[\"BE\"]");
        String be = approvedTech(prd, "BE", "backend");
        for (String action : List.of("start-implementation", "submit-implementation", "approve-implementation")) {
            act(be, action, "Bearer nope").andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/specs/" + be + "/" + action)).andExpect(status().isUnauthorized());
        }
        link(be, "Bearer nope", PR).andExpect(status().isUnauthorized());
        requestImplChanges(be, "Bearer nope", "{\"comment\":\"c\"}").andExpect(status().isUnauthorized());
        assertThat(doc(be).getStatus()).isEqualTo(SpecStatus.APPROVED);
    }

    @Test
    void fullFlowImplementsEachDocAndAggregatesPrdOnceAfterTheLastArea() throws Exception {
        String prd = prdWithAreas("[\"BE\",\"FE\"]");
        String be = approvedTech(prd, "BE", "backend");
        String fe = approvedTech(prd, "FE", "frontend");
        String backend = login("backend");
        String reviewer = login("reviewer");
        assertThat(doc(prd).getStatus()).isEqualTo(SpecStatus.SPEC_APPROVED);

        act(be, "start-implementation", backend).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("IN_IMPLEMENTATION"))
            .andExpect(jsonPath("$.data.prLinks").isEmpty());
        link(be, backend, "  " + PR + "  ").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("IN_IMPLEMENTATION"))
            .andExpect(jsonPath("$.data.prLinks[0].url").value(PR))
            .andExpect(jsonPath("$.data.prLinks[0].linkedBy").value("backend"))
            .andExpect(jsonPath("$.data.prLinks[0].linkedAt").isNotEmpty());
        act(be, "submit-implementation", backend).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("IMPL_IN_REVIEW"));
        act(be, "approve-implementation", reviewer).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("IMPLEMENTED"));

        assertThat(doc(prd).getStatus()).isEqualTo(SpecStatus.SPEC_APPROVED);
        assertThat(aggregateEvents(prd)).hasSize(1);

        toImplReview(fe, "frontend");
        act(fe, "approve-implementation", reviewer).andExpect(status().isOk());
        act(fe, "approve-implementation", reviewer).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("IMPLEMENTED"));

        assertThat(doc(prd).getStatus()).isEqualTo(SpecStatus.IMPLEMENTED);
        List<SpecEvent> aggregated = aggregateEvents(prd);
        assertThat(aggregated).extracting(SpecEvent::getToStatus)
            .containsExactly(SpecStatus.SPEC_APPROVED, SpecStatus.IMPLEMENTED);
        assertThat(aggregated.get(1).getFromStatus()).isEqualTo(SpecStatus.SPEC_APPROVED);
        assertThat(aggregated.get(1).getActor()).isEqualTo("system");
        assertThat(aggregated.get(1).getActorRole()).isEqualTo("SYSTEM");

        List<SpecEvent> beHistory = eventsOf(be);
        assertThat(beHistory).extracting(e -> e.getAction().name()).containsExactly(
            "CREATE", "SUBMIT", "APPROVE", "START_IMPLEMENTATION", "LINK_PR", "SUBMIT_IMPLEMENTATION", "APPROVE_IMPLEMENTATION");
        assertThat(beHistory.get(4).getComment()).isEqualTo(PR);
        assertThat(beHistory.get(6).getActor()).isEqualTo("reviewer");
        assertThat(beHistory.get(6).getActorRole()).isEqualTo("REVIEWER");

        mvc.perform(get("/api/v1/specs/" + prd).header("Authorization", login("viewer")))
            .andExpect(jsonPath("$.data.status").value("IMPLEMENTED"))
            .andExpect(jsonPath("$.data.prLinks").isEmpty())
            .andExpect(jsonPath("$.data.events[-1].action").value("AGGREGATE"))
            .andExpect(jsonPath("$.data.events[-1].toStatus").value("IMPLEMENTED"));
    }

    @Test
    void onlyAreaRoleStartsLinksAndSubmitsAndOnlyReviewerApproves() throws Exception {
        String prd = prdWithAreas("[\"BE\"]");
        String be = approvedTech(prd, "BE", "backend");
        String backend = login("backend");
        String reviewer = login("reviewer");

        for (String other : List.of(reviewer, login("planner"), login("frontend"))) {
            act(be, "start-implementation", other).andExpect(status().isForbidden());
        }
        act(be, "start-implementation", backend).andExpect(status().isOk());
        link(be, reviewer, PR).andExpect(status().isForbidden());
        link(be, login("frontend"), PR).andExpect(status().isForbidden());
        link(be, backend, PR).andExpect(status().isOk());
        act(be, "submit-implementation", reviewer).andExpect(status().isForbidden());
        act(be, "submit-implementation", backend).andExpect(status().isOk());

        act(be, "approve-implementation", backend).andExpect(status().isForbidden());
        act(be, "approve-implementation", login("planner")).andExpect(status().isForbidden());
        requestImplChanges(be, backend, "{\"comment\":\"c\"}").andExpect(status().isForbidden());
        assertThat(doc(be).getStatus()).isEqualTo(SpecStatus.IMPL_IN_REVIEW);
    }

    @Test
    void roleIsCheckedBeforeState() throws Exception {
        String prd = prdWithAreas("[\"BE\"]");
        String draft = idOf(postJson("/api/v1/specs/" + prd + "/tech-docs", login("backend"),
            "{\"area\":\"BE\",\"title\":\"t\",\"body\":\"b\"}"));

        act(draft, "start-implementation", login("reviewer")).andExpect(status().isForbidden());
        act(draft, "approve-implementation", login("backend")).andExpect(status().isForbidden());
        act(draft, "start-implementation", login("backend")).andExpect(status().isConflict());
        act(draft, "approve-implementation", login("reviewer")).andExpect(status().isConflict());
        act(prd, "start-implementation", login("backend")).andExpect(status().isConflict());
    }

    @Test
    void startNeedsEveryDeclaredAreaApprovedFirst() throws Exception {
        String prd = prdWithAreas("[\"BE\",\"FE\"]");
        String be = approvedTech(prd, "BE", "backend");

        act(be, "start-implementation", login("backend")).andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorMessage").value(Matchers.containsString("SPEC_APPROVED")));
        assertThat(doc(be).getStatus()).isEqualTo(SpecStatus.APPROVED);

        approvedTech(prd, "FE", "frontend");
        act(be, "start-implementation", login("backend")).andExpect(status().isOk());
    }

    @Test
    void submitWithoutPrIsConflictAndStartAndSubmitAreIdempotent() throws Exception {
        String prd = prdWithAreas("[\"BE\"]");
        String be = approvedTech(prd, "BE", "backend");
        String backend = login("backend");

        act(be, "start-implementation", backend).andExpect(status().isOk());
        act(be, "start-implementation", backend).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("IN_IMPLEMENTATION"));
        act(be, "submit-implementation", backend).andExpect(status().isConflict());
        assertThat(doc(be).getStatus()).isEqualTo(SpecStatus.IN_IMPLEMENTATION);

        link(be, backend, PR).andExpect(status().isOk());
        act(be, "submit-implementation", backend).andExpect(status().isOk());
        act(be, "submit-implementation", backend).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("IMPL_IN_REVIEW"));

        assertThat(eventsOf(be)).extracting(e -> e.getAction().name())
            .containsExactly("CREATE", "SUBMIT", "APPROVE", "START_IMPLEMENTATION", "LINK_PR", "SUBMIT_IMPLEMENTATION");
    }

    @Test
    void prLinkMustBeHttpUrlWithinLength() throws Exception {
        String prd = prdWithAreas("[\"BE\"]");
        String be = approvedTech(prd, "BE", "backend");
        String backend = login("backend");
        act(be, "start-implementation", backend).andExpect(status().isOk());

        for (String bad : List.of("", "   ", "github.com/x/pull/1", "ftp://github.com/x", "javascript:alert(1)",
                "https://", "https:///pull/1", "https://git hub.com/x", "https://github.com/" + "a".repeat(500))) {
            link(be, backend, bad).andExpect(status().isBadRequest());
        }
        postJson("/api/v1/specs/" + be + "/pr-links", backend, "{}").andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/specs/" + be + "/pr-links").header("Authorization", backend))
            .andExpect(status().isBadRequest());

        String longest = "http://example.com/" + "a".repeat(500 - "http://example.com/".length());
        link(be, backend, longest).andExpect(status().isOk());
        link(be, backend, "HTTPS://github.com/x/pull/2").andExpect(status().isOk());
        prLinksOf(be).andExpect(jsonPath("$.data.prLinks[*].url").value(Matchers.contains(longest, "HTTPS://github.com/x/pull/2")));
    }

    @Test
    void duplicateLinkIsIdempotentAndLinksAreCappedPerDoc() throws Exception {
        String prd = prdWithAreas("[\"BE\"]");
        String be = approvedTech(prd, "BE", "backend");
        String backend = login("backend");
        act(be, "start-implementation", backend).andExpect(status().isOk());

        link(be, backend, PR).andExpect(status().isOk());
        link(be, backend, PR).andExpect(status().isOk()).andExpect(jsonPath("$.data.prLinks.length()").value(1));
        assertThat(eventsOf(be)).filteredOn(e -> e.getAction() == SpecAction.LINK_PR).hasSize(1);

        for (int i = 2; i <= 20; i++) {
            link(be, backend, PR + "-" + i).andExpect(status().isOk());
        }
        link(be, backend, PR + "-21").andExpect(status().isConflict());
        link(be, backend, PR).andExpect(status().isOk()).andExpect(jsonPath("$.data.prLinks.length()").value(20));
        prLinksOf(be)
            .andExpect(jsonPath("$.data.prLinks[0].url").value(PR))
            .andExpect(jsonPath("$.data.prLinks[19].url").value(PR + "-20"));
    }

    @Test
    void linkingIsOnlyAllowedWhileInImplementation() throws Exception {
        String prd = prdWithAreas("[\"BE\"]");
        String be = approvedTech(prd, "BE", "backend");
        String backend = login("backend");

        link(be, backend, PR).andExpect(status().isConflict());
        toImplReview(be, "backend");
        link(be, backend, PR + "/other").andExpect(status().isConflict());
        act(be, "approve-implementation", login("reviewer")).andExpect(status().isOk());
        link(be, backend, PR + "/other").andExpect(status().isConflict());
        prLinksOf(be).andExpect(jsonPath("$.data.prLinks.length()").value(1));
    }

    @Test
    void requestImplementationChangesNeedsCommentReopensImplementationAndRepeatIsConflict() throws Exception {
        String prd = prdWithAreas("[\"BE\"]");
        String be = approvedTech(prd, "BE", "backend");
        String backend = login("backend");
        String reviewer = login("reviewer");
        toImplReview(be, "backend");

        act(be, "request-implementation-changes", reviewer).andExpect(status().isBadRequest());
        requestImplChanges(be, reviewer, "{\"comment\":\" \"}").andExpect(status().isBadRequest());
        assertThat(doc(be).getStatus()).isEqualTo(SpecStatus.IMPL_IN_REVIEW);

        requestImplChanges(be, reviewer, "{\"comment\":\"테스트 누락\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("IN_IMPLEMENTATION"));
        requestImplChanges(be, reviewer, "{\"comment\":\"again\"}").andExpect(status().isConflict());

        link(be, backend, PR + "/tests").andExpect(status().isOk());
        act(be, "submit-implementation", backend).andExpect(status().isOk());
        act(be, "approve-implementation", reviewer).andExpect(status().isOk());
        assertThat(doc(prd).getStatus()).isEqualTo(SpecStatus.IMPLEMENTED);

        List<SpecEvent> changes = eventsOf(be).stream()
            .filter(e -> e.getAction() == SpecAction.REQUEST_IMPL_CHANGES).toList();
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).getComment()).isEqualTo("테스트 누락");
        assertThat(changes.get(0).getActorRole()).isEqualTo("REVIEWER");
    }

    @Test
    void techDocPastApprovalIsLockedForEdit() throws Exception {
        String prd = prdWithAreas("[\"BE\"]");
        String be = approvedTech(prd, "BE", "backend");
        String backend = login("backend");
        act(be, "start-implementation", backend).andExpect(status().isOk());

        for (String user : List.of("backend", "planner")) {
            mvc.perform(put("/api/v1/specs/" + be).header("Authorization", login(user))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"구현 중 수정\"}"))
                .andExpect(status().isConflict());
        }
        assertThat(doc(be).getBody()).isEqualTo("b");
    }
}
