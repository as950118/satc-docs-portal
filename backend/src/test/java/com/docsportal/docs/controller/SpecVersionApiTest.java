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

class SpecVersionApiTest extends SpecApiTestSupport {

    private ResultActions act(String id, String action, String bearer) throws Exception {
        return mvc.perform(post("/api/v1/specs/" + id + "/" + action).header("Authorization", bearer));
    }

    private ResultActions view(String id) throws Exception {
        return mvc.perform(get("/api/v1/specs/" + id).header("Authorization", login("viewer")));
    }

    private ResultActions editBody(String id, String body) throws Exception {
        return mvc.perform(put("/api/v1/specs/" + id).header("Authorization", login("planner"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + body + "\"}"));
    }

    private String prdWithAreas(String areasJson) throws Exception {
        String planner = login("planner");
        String prd = idOf(postJson("/api/v1/specs", planner, "{\"title\":\"P\",\"body\":\"v1 본문\"}"));
        act(prd, "approve", planner).andExpect(status().isOk());
        mvc.perform(put("/api/v1/specs/" + prd + "/tech-areas").header("Authorization", planner)
            .contentType(MediaType.APPLICATION_JSON).content("{\"areas\":" + areasJson + "}")).andExpect(status().isOk());
        return prd;
    }

    private String tech(String prd, String area, String user) throws Exception {
        return idOf(postJson("/api/v1/specs/" + prd + "/tech-docs", login(user),
            "{\"area\":\"" + area + "\",\"title\":\"t\",\"body\":\"b\"}"));
    }

    private String approvedTech(String prd, String area, String user) throws Exception {
        String id = tech(prd, area, user);
        act(id, "submit", login(user)).andExpect(status().isOk());
        act(id, "approve", login(user)).andExpect(status().isOk());
        return id;
    }

    private void implement(String id, String user) throws Exception {
        String bearer = login(user);
        act(id, "start-implementation", bearer).andExpect(status().isOk());
        postJson("/api/v1/specs/" + id + "/pr-links", bearer, "{\"url\":\"https://github.com/o/r/pull/" + id + "\"}")
            .andExpect(status().isOk());
        act(id, "submit-implementation", bearer).andExpect(status().isOk());
        act(id, "approve-implementation", login("reviewer")).andExpect(status().isOk());
    }

    private String approvedNewVersion(String prd, String body) throws Exception {
        String v2 = idOf(act(prd, "new-version", login("planner")).andExpect(status().isOk()));
        editBody(v2, body).andExpect(status().isOk());
        act(v2, "approve", login("planner")).andExpect(status().isOk());
        return v2;
    }

    private List<SpecEvent> eventsOf(String id, SpecAction action) {
        return eventsOf(id).stream().filter(e -> e.getAction() == action).toList();
    }

    @Test
    void newVersionStartsAsDraftCopyWhileV1StaysLiveAndRepeatReturnsTheSameDraft() throws Exception {
        String v1 = prdWithAreas("[\"BE\"]");
        String be = approvedTech(v1, "BE", "backend");
        String planner = login("planner");

        String v2 = idOf(act(v1, "new-version", planner).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.revision").value(2))
            .andExpect(jsonPath("$.data.supersedesId").value(v1))
            .andExpect(jsonPath("$.data.title").value("P"))
            .andExpect(jsonPath("$.data.body").value("v1 본문"))
            .andExpect(jsonPath("$.data.techAreas").isEmpty())
            .andExpect(jsonPath("$.data.author").value("planner"))
            .andExpect(jsonPath("$.data.events[0].action").value("CREATE")));
        act(v1, "new-version", planner).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(v2));

        view(v1).andExpect(jsonPath("$.data.status").value("SPEC_APPROVED"))
            .andExpect(jsonPath("$.data.revision").value(1))
            .andExpect(jsonPath("$.data.supersededById").value(v2))
            .andExpect(jsonPath("$.data.events[-1].action").value("NEW_VERSION"));
        assertThat(eventsOf(v1, SpecAction.NEW_VERSION)).hasSize(1);
        assertThat(doc(be).getPrdId()).isEqualTo(v1);

        mvc.perform(get("/api/v1/specs").param("kind", "PRD").header("Authorization", planner))
            .andExpect(jsonPath("$.data[*].id").value(Matchers.contains(v2)));
        mvc.perform(get("/api/v1/specs").param("kind", "PRD").param("allVersions", "true").header("Authorization", planner))
            .andExpect(jsonPath("$.data[*].id").value(Matchers.containsInAnyOrder(v1, v2)));
        act(be, "start-implementation", login("backend")).andExpect(status().isOk());
    }

    @Test
    void newVersionChecksRoleBeforeState() throws Exception {
        String planner = login("planner");
        String draft = idOf(postJson("/api/v1/specs", planner, "{\"title\":\"P\",\"body\":\"B\"}"));
        String v1 = prdWithAreas("[\"BE\"]");
        String be = tech(v1, "BE", "backend");

        for (String user : List.of("backend", "reviewer", "admin")) {
            act(draft, "new-version", login(user)).andExpect(status().isForbidden());
            act(v1, "new-version", login(user)).andExpect(status().isForbidden());
        }
        act(draft, "new-version", planner).andExpect(status().isConflict());
        act(be, "new-version", planner).andExpect(status().isNotFound());
        act("nope", "new-version", planner).andExpect(status().isNotFound());
        act(v1, "new-version", "Bearer nope").andExpect(status().isUnauthorized());
        assertThat(documents.findBySupersedesId(v1)).isEmpty();
    }

    @Test
    void approvingV2ReReviewsApprovedDocsAndOnlyFlagsDocsInImplementation() throws Exception {
        String v1 = prdWithAreas("[\"BE\",\"FE\"]");
        String be = approvedTech(v1, "BE", "backend");
        String fe = approvedTech(v1, "FE", "frontend");
        String backend = login("backend");
        act(be, "start-implementation", backend).andExpect(status().isOk());

        String v2 = approvedNewVersion(v1, "v2 본문");

        view(v1).andExpect(jsonPath("$.data.status").value("SUPERSEDED"))
            .andExpect(jsonPath("$.data.body").value("v1 본문"))
            .andExpect(jsonPath("$.data.supersededById").value(v2));
        SpecEvent superseded = eventsOf(v1, SpecAction.SUPERSEDE).get(0);
        assertThat(superseded.getFromStatus()).isEqualTo(SpecStatus.SPEC_APPROVED);
        assertThat(superseded.getActorRole()).isEqualTo("SYSTEM");
        assertThat(superseded.getComment()).contains(v2);

        view(v2).andExpect(jsonPath("$.data.status").value("PRD_APPROVED"))
            .andExpect(jsonPath("$.data.body").value("v2 본문"))
            .andExpect(jsonPath("$.data.techAreas").value(Matchers.containsInAnyOrder("BE", "FE")));
        view(be).andExpect(jsonPath("$.data.status").value("IN_IMPLEMENTATION"))
            .andExpect(jsonPath("$.data.prdId").value(v2))
            .andExpect(jsonPath("$.data.prdRevision").value(1))
            .andExpect(jsonPath("$.data.prdChanged").value(true))
            .andExpect(jsonPath("$.data.events[-1].action").value("PRD_REVISED"))
            .andExpect(jsonPath("$.data.events[-1].comment").value(Matchers.containsString("영향 확인")));
        view(fe).andExpect(jsonPath("$.data.status").value("IN_REVIEW"))
            .andExpect(jsonPath("$.data.prdChanged").value(true))
            .andExpect(jsonPath("$.data.events[-1].fromStatus").value("APPROVED"))
            .andExpect(jsonPath("$.data.events[-1].comment").value(Matchers.containsString("재검토")));
        mvc.perform(get("/api/v1/specs/" + v1 + "/tech-docs").header("Authorization", backend))
            .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/v1/specs/" + v2 + "/tech-docs").header("Authorization", backend))
            .andExpect(jsonPath("$.data[*].prdChanged").value(Matchers.contains(true, true)));

        act(be, "acknowledge-prd-change", login("frontend")).andExpect(status().isForbidden());
        act(fe, "acknowledge-prd-change", login("frontend")).andExpect(status().isConflict());
        act(fe, "start-implementation", login("frontend")).andExpect(status().isConflict());

        act(fe, "approve", login("frontend")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.prdChanged").value(false))
            .andExpect(jsonPath("$.data.prdRevision").value(2));
        view(v2).andExpect(jsonPath("$.data.status").value("SPEC_APPROVED"));

        act(be, "acknowledge-prd-change", backend).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("IN_IMPLEMENTATION"))
            .andExpect(jsonPath("$.data.prdChanged").value(false));
        act(be, "acknowledge-prd-change", backend).andExpect(status().isOk());
        assertThat(eventsOf(be, SpecAction.ACKNOWLEDGE_PRD_CHANGE)).hasSize(1);

        postJson("/api/v1/specs/" + be + "/pr-links", backend, "{\"url\":\"https://github.com/o/r/pull/1\"}")
            .andExpect(status().isOk());
        act(be, "submit-implementation", backend).andExpect(status().isOk());
        act(be, "approve-implementation", login("reviewer")).andExpect(status().isOk());
        implement(fe, "frontend");
        view(v2).andExpect(jsonPath("$.data.status").value("IMPLEMENTED"));
        assertThat(eventsOf(v1, SpecAction.AGGREGATE)).extracting(SpecEvent::getToStatus)
            .containsExactly(SpecStatus.SPEC_APPROVED);
    }

    @Test
    void supersededVersionIsReadOnly() throws Exception {
        String v1 = prdWithAreas("[\"BE\"]");
        String v2 = approvedNewVersion(v1, "v2");
        String planner = login("planner");

        editBody(v1, "고침").andExpect(status().isConflict());
        act(v1, "approve", planner).andExpect(status().isConflict());
        act(v1, "new-version", planner).andExpect(status().isConflict());
        mvc.perform(put("/api/v1/specs/" + v1 + "/tech-areas").header("Authorization", planner)
            .contentType(MediaType.APPLICATION_JSON).content("{\"areas\":[\"FE\"]}")).andExpect(status().isConflict());
        postJson("/api/v1/specs/" + v1 + "/tech-docs", planner, "{\"area\":\"BE\",\"title\":\"t\",\"body\":\"b\"}")
            .andExpect(status().isConflict());
        view(v1).andExpect(jsonPath("$.data.status").value("SUPERSEDED")).andExpect(jsonPath("$.data.body").value("v1 본문"));

        String v3 = idOf(act(v2, "new-version", planner).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.revision").value(3))
            .andExpect(jsonPath("$.data.supersedesId").value(v2)));
        act(v3, "approve", planner).andExpect(status().isOk());
        view(v2).andExpect(jsonPath("$.data.status").value("SUPERSEDED"));
        mvc.perform(get("/api/v1/specs").param("kind", "PRD").header("Authorization", planner))
            .andExpect(jsonPath("$.data[*].id").value(Matchers.contains(v3)));
    }

    @Test
    void fullyImplementedPrdClimbsBackInOneApprovalAndDocsAreOnlyFlagged() throws Exception {
        String v1 = prdWithAreas("[\"BE\"]");
        String be = approvedTech(v1, "BE", "backend");
        implement(be, "backend");
        view(v1).andExpect(jsonPath("$.data.status").value("IMPLEMENTED"));

        String v2 = approvedNewVersion(v1, "문구 수정");

        view(v2).andExpect(jsonPath("$.data.status").value("IMPLEMENTED"));
        assertThat(eventsOf(v2, SpecAction.AGGREGATE)).extracting(SpecEvent::getToStatus)
            .containsExactly(SpecStatus.SPEC_APPROVED, SpecStatus.IMPLEMENTED);
        view(be).andExpect(jsonPath("$.data.status").value("IMPLEMENTED")).andExpect(jsonPath("$.data.prdChanged").value(true));
        act(be, "acknowledge-prd-change", login("backend")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.prdChanged").value(false));
    }

    @Test
    void docsBeforeReviewKeepTheirStateAndLoseTheFlagOnApproval() throws Exception {
        String v1 = prdWithAreas("[\"BE\",\"FE\",\"DATA\"]");
        String be = tech(v1, "BE", "backend");
        String fe = tech(v1, "FE", "frontend");
        act(fe, "submit", login("frontend")).andExpect(status().isOk());
        String data = tech(v1, "DATA", "data");
        act(data, "submit", login("data")).andExpect(status().isOk());
        postJson("/api/v1/specs/" + data + "/request-changes", login("data"), "{\"comment\":\"보완\"}").andExpect(status().isOk());

        String v2 = approvedNewVersion(v1, "v2");

        view(be).andExpect(jsonPath("$.data.status").value("DRAFT")).andExpect(jsonPath("$.data.prdChanged").value(true));
        view(fe).andExpect(jsonPath("$.data.status").value("IN_REVIEW")).andExpect(jsonPath("$.data.prdChanged").value(true));
        view(data).andExpect(jsonPath("$.data.status").value("CHANGES_REQUESTED")).andExpect(jsonPath("$.data.prdChanged").value(true));
        mvc.perform(get("/api/v1/specs").param("prdId", v2).header("Authorization", login("viewer")))
            .andExpect(jsonPath("$.data.length()").value(3))
            .andExpect(jsonPath("$.data[*].prdChanged").value(Matchers.everyItem(Matchers.is(true))));

        act(be, "submit", login("backend")).andExpect(status().isOk());
        act(be, "approve", login("backend")).andExpect(status().isOk()).andExpect(jsonPath("$.data.prdChanged").value(false));
        act(fe, "approve", login("frontend")).andExpect(status().isOk());
        postJson("/api/v1/specs/" + v2 + "/tech-docs", login("planner"), "{\"area\":\"BE\",\"title\":\"t\",\"body\":\"b\"}")
            .andExpect(status().isConflict());
    }
}
