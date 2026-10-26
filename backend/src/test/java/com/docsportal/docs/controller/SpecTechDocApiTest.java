package com.docsportal.docs.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.docsportal.docs.persistence.SpecAction;
import com.docsportal.docs.persistence.SpecEvent;
import com.docsportal.docs.persistence.SpecStatus;

class SpecTechDocApiTest extends SpecApiTestSupport {

    private String approvedPrd() throws Exception {
        String planner = login("planner");
        String id = idOf(postJson("/api/v1/specs", planner, "{\"title\":\"P\",\"body\":\"B\"}"));
        mvc.perform(post("/api/v1/specs/" + id + "/approve").header("Authorization", planner)).andExpect(status().isOk());
        return id;
    }

    private ResultActions setAreas(String prdId, String bearer, String areasJson) throws Exception {
        return mvc.perform(put("/api/v1/specs/" + prdId + "/tech-areas").header("Authorization", bearer)
            .contentType(MediaType.APPLICATION_JSON).content("{\"areas\":" + areasJson + "}"));
    }

    private ResultActions createTech(String prdId, String bearer, String area) throws Exception {
        return postJson("/api/v1/specs/" + prdId + "/tech-docs", bearer,
            "{\"area\":\"" + area + "\",\"title\":\"" + area + " 설계\",\"body\":\"본문\"}");
    }

    private ResultActions act(String id, String action, String bearer) throws Exception {
        return mvc.perform(post("/api/v1/specs/" + id + "/" + action).header("Authorization", bearer));
    }

    private ResultActions requestChanges(String id, String bearer, String body) throws Exception {
        return postJson("/api/v1/specs/" + id + "/request-changes", bearer, body);
    }

    /** 선언 → 생성 → 제출까지 진행한 기술문서 id. */
    private String inReview(String prdId, String area, String user) throws Exception {
        String bearer = login(user);
        String id = idOf(createTech(prdId, bearer, area).andExpect(status().isOk()));
        act(id, "submit", bearer).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("IN_REVIEW"));
        return id;
    }

    private SpecStatus prdStatus(String prdId) {
        return doc(prdId).getStatus();
    }

    private List<SpecEvent> aggregateEvents(String prdId) {
        return eventsOf(prdId).stream().filter(e -> e.getAction() == SpecAction.AGGREGATE).toList();
    }

    @Test
    void newEndpointsRequireSession() throws Exception {
        String prd = approvedPrd();
        mvc.perform(get("/api/v1/specs/templates/tech/BE")).andExpect(status().isUnauthorized());
        setAreas(prd, "Bearer nope", "[\"BE\"]").andExpect(status().isUnauthorized());
        createTech(prd, "Bearer nope", "BE").andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/specs/" + prd + "/tech-docs")).andExpect(status().isUnauthorized());
        act(prd, "submit", "Bearer nope").andExpect(status().isUnauthorized());
        requestChanges(prd, "Bearer nope", "{\"comment\":\"c\"}").andExpect(status().isUnauthorized());
        assertThat(doc(prd).getTechAreas()).isEmpty();
    }

    @Test
    void techTemplatesAreServedPerArea() throws Exception {
        String viewer = login("viewer");
        mvc.perform(get("/api/v1/specs/templates/tech/BE").header("Authorization", viewer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.kind").value("TECH_BE"))
            .andExpect(jsonPath("$.data.markdown").value(containsString("API 변경")));
        mvc.perform(get("/api/v1/specs/templates/tech/fe").header("Authorization", viewer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.kind").value("TECH_FE"))
            .andExpect(jsonPath("$.data.markdown").value(containsString("화면과 흐름")));
        mvc.perform(get("/api/v1/specs/templates/tech/DATA").header("Authorization", viewer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.kind").value("TECH_DATA"))
            .andExpect(jsonPath("$.data.markdown").value(containsString("마이그레이션")));
        mvc.perform(get("/api/v1/specs/templates/tech/OPS").header("Authorization", viewer))
            .andExpect(status().isBadRequest());
    }

    @Test
    void onlyPlannerDeclaresAreasOnApprovedPrdWithAtLeastOneKnownArea() throws Exception {
        String planner = login("planner");
        String draft = idOf(postJson("/api/v1/specs", planner, "{\"title\":\"P\",\"body\":\"B\"}"));
        setAreas(draft, planner, "[\"BE\"]").andExpect(status().isConflict());

        String prd = approvedPrd();
        setAreas(prd, login("backend"), "[\"BE\"]").andExpect(status().isForbidden());
        setAreas(prd, planner, "[]").andExpect(status().isBadRequest());
        setAreas(prd, planner, "[\"OPS\"]").andExpect(status().isBadRequest());
        assertThat(doc(prd).getTechAreas()).isEmpty();

        setAreas(prd, planner, "[\"FE\",\"BE\",\"BE\"]")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.techAreas").value(org.hamcrest.Matchers.contains("BE", "FE")))
            .andExpect(jsonPath("$.data.status").value("PRD_APPROVED"));
        SpecEvent declared = eventsOf(prd).get(eventsOf(prd).size() - 1);
        assertThat(declared.getAction()).isEqualTo(SpecAction.SET_TECH_AREAS);
        assertThat(declared.getActor()).isEqualTo("planner");
        assertThat(declared.getComment()).isEqualTo("BE,FE");

        String techId = idOf(createTech(prd, login("backend"), "BE"));
        setAreas(techId, planner, "[\"BE\"]").andExpect(status().isNotFound());
        setAreas("missing", planner, "[\"BE\"]").andExpect(status().isNotFound());
    }

    @Test
    void techDocIsCreatedOnlyUnderApprovedPrdForDeclaredVacantArea() throws Exception {
        String planner = login("planner");
        String backend = login("backend");
        String draft = idOf(postJson("/api/v1/specs", planner, "{\"title\":\"P\",\"body\":\"B\"}"));
        createTech(draft, backend, "BE").andExpect(status().isConflict());

        String prd = approvedPrd();
        createTech(prd, backend, "BE").andExpect(status().isConflict());
        setAreas(prd, planner, "[\"BE\"]").andExpect(status().isOk());
        createTech(prd, login("frontend"), "FE").andExpect(status().isConflict());
        createTech(prd, login("frontend"), "BE").andExpect(status().isForbidden());
        createTech(prd, backend, "OPS").andExpect(status().isBadRequest());
        createTech("missing", backend, "BE").andExpect(status().isNotFound());

        createTech(prd, backend, "BE")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.kind").value("TECH_BE"))
            .andExpect(jsonPath("$.data.prdId").value(prd))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.author").value("backend"))
            .andExpect(jsonPath("$.data.events[0].action").value("CREATE"))
            .andExpect(jsonPath("$.data.events[0].actorRole").value("BACKEND"));
        createTech(prd, planner, "BE").andExpect(status().isConflict());

        assertThat(documents.count()).isEqualTo(3);
    }

    @Test
    void backendCannotApproveFrontendDocAndPlannerCannotReview() throws Exception {
        String prd = approvedPrd();
        setAreas(prd, login("planner"), "[\"FE\"]").andExpect(status().isOk());
        String fe = inReview(prd, "FE", "frontend");

        act(fe, "approve", login("backend")).andExpect(status().isForbidden());
        act(fe, "approve", login("planner")).andExpect(status().isForbidden());
        requestChanges(fe, login("backend"), "{\"comment\":\"x\"}").andExpect(status().isForbidden());
        assertThat(doc(fe).getStatus()).isEqualTo(SpecStatus.IN_REVIEW);

        act(fe, "approve", login("frontend"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void requestChangesNeedsCommentAndSendsDocBackThroughEditing() throws Exception {
        String prd = approvedPrd();
        setAreas(prd, login("planner"), "[\"BE\"]").andExpect(status().isOk());
        String be = inReview(prd, "BE", "backend");
        String backend = login("backend");

        act(be, "request-changes", backend).andExpect(status().isBadRequest());
        requestChanges(be, backend, "{\"comment\":\"  \"}").andExpect(status().isBadRequest());
        assertThat(doc(be).getStatus()).isEqualTo(SpecStatus.IN_REVIEW);

        requestChanges(be, backend, "{\"comment\":\"API 오류 코드 보강\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CHANGES_REQUESTED"));
        requestChanges(be, backend, "{\"comment\":\"again\"}").andExpect(status().isConflict());
        act(be, "submit", backend).andExpect(status().isConflict());

        mvc.perform(put("/api/v1/specs/" + be).header("Authorization", login("planner"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"보강한 본문\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"));
        act(be, "submit", backend).andExpect(status().isOk());
        act(be, "submit", backend).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("IN_REVIEW"));
        act(be, "approve", backend).andExpect(status().isOk());
        mvc.perform(put("/api/v1/specs/" + be).header("Authorization", backend)
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"승인 후 수정\"}"))
            .andExpect(status().isConflict());

        List<SpecEvent> history = eventsOf(be);
        assertThat(history).extracting(e -> e.getAction().name())
            .containsExactly("CREATE", "SUBMIT", "REQUEST_CHANGES", "EDIT", "SUBMIT", "APPROVE");
        assertThat(history.get(2).getComment()).isEqualTo("API 오류 코드 보강");
        assertThat(history.get(2).getActorRole()).isEqualTo("BACKEND");
        assertThat(history.get(3).getFromStatus()).isEqualTo(SpecStatus.CHANGES_REQUESTED);
        assertThat(doc(be).getBody()).isEqualTo("보강한 본문");
    }

    @Test
    void reviewActionsOnDraftAreConflicts() throws Exception {
        String prd = approvedPrd();
        setAreas(prd, login("planner"), "[\"DATA\"]").andExpect(status().isOk());
        String data = idOf(createTech(prd, login("data"), "DATA"));

        act(data, "approve", login("data")).andExpect(status().isConflict());
        requestChanges(data, login("data"), "{\"comment\":\"c\"}").andExpect(status().isConflict());
        act(prd, "submit", login("planner")).andExpect(status().isConflict());
    }

    @Test
    void partialApprovalDoesNotAggregate() throws Exception {
        String prd = approvedPrd();
        setAreas(prd, login("planner"), "[\"BE\",\"FE\"]").andExpect(status().isOk());
        String be = inReview(prd, "BE", "backend");
        inReview(prd, "FE", "frontend");

        act(be, "approve", login("backend")).andExpect(status().isOk());

        assertThat(prdStatus(prd)).isEqualTo(SpecStatus.PRD_APPROVED);
        assertThat(aggregateEvents(prd)).isEmpty();
    }

    @Test
    void approvingLastDeclaredAreaAggregatesOnceWithSystemEvent() throws Exception {
        String prd = approvedPrd();
        setAreas(prd, login("planner"), "[\"BE\",\"FE\"]").andExpect(status().isOk());
        String be = inReview(prd, "BE", "backend");
        String fe = inReview(prd, "FE", "frontend");
        act(be, "approve", login("backend")).andExpect(status().isOk());

        act(fe, "approve", login("frontend")).andExpect(status().isOk());
        act(fe, "approve", login("frontend")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"));
        act(be, "approve", login("backend")).andExpect(status().isOk());

        assertThat(prdStatus(prd)).isEqualTo(SpecStatus.SPEC_APPROVED);
        List<SpecEvent> aggregated = aggregateEvents(prd);
        assertThat(aggregated).hasSize(1);
        assertThat(aggregated.get(0).getActor()).isEqualTo("system");
        assertThat(aggregated.get(0).getActorRole()).isEqualTo("SYSTEM");
        assertThat(aggregated.get(0).getFromStatus()).isEqualTo(SpecStatus.PRD_APPROVED);
        assertThat(aggregated.get(0).getToStatus()).isEqualTo(SpecStatus.SPEC_APPROVED);
        assertThat(eventsOf(fe)).extracting(e -> e.getAction().name()).containsExactly("CREATE", "SUBMIT", "APPROVE");

        setAreas(prd, login("planner"), "[\"BE\",\"FE\",\"DATA\"]").andExpect(status().isConflict());
        mvc.perform(get("/api/v1/specs/" + prd).header("Authorization", login("viewer")))
            .andExpect(jsonPath("$.data.status").value("SPEC_APPROVED"))
            .andExpect(jsonPath("$.data.events[-1].action").value("AGGREGATE"));
    }

    @Test
    void narrowingAreasToApprovedOnesAggregatesImmediately() throws Exception {
        String prd = approvedPrd();
        String planner = login("planner");
        setAreas(prd, planner, "[\"BE\",\"FE\"]").andExpect(status().isOk());
        String be = inReview(prd, "BE", "backend");
        String fe = idOf(createTech(prd, login("frontend"), "FE"));
        act(be, "approve", login("backend")).andExpect(status().isOk());
        assertThat(prdStatus(prd)).isEqualTo(SpecStatus.PRD_APPROVED);

        setAreas(prd, planner, "[\"BE\"]")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SPEC_APPROVED"));

        assertThat(aggregateEvents(prd)).hasSize(1);
        assertThat(doc(fe).getStatus()).isEqualTo(SpecStatus.DRAFT);
    }

    @Test
    void wideningAreasRequiresTheNewAreaToo() throws Exception {
        String prd = approvedPrd();
        String planner = login("planner");
        setAreas(prd, planner, "[\"BE\"]").andExpect(status().isOk());
        String be = inReview(prd, "BE", "backend");
        setAreas(prd, planner, "[\"BE\",\"DATA\"]").andExpect(status().isOk());

        act(be, "approve", login("backend")).andExpect(status().isOk());
        assertThat(prdStatus(prd)).isEqualTo(SpecStatus.PRD_APPROVED);

        String data = inReview(prd, "DATA", "data");
        act(data, "approve", login("data")).andExpect(status().isOk());
        assertThat(prdStatus(prd)).isEqualTo(SpecStatus.SPEC_APPROVED);
        assertThat(aggregateEvents(prd)).hasSize(1);
    }

    @Test
    void listFiltersByParentKindAndStatusInTheDatabase() throws Exception {
        String prd = approvedPrd();
        String other = approvedPrd();
        setAreas(prd, login("planner"), "[\"BE\",\"FE\"]").andExpect(status().isOk());
        String be = inReview(prd, "BE", "backend");
        String fe = idOf(createTech(prd, login("frontend"), "FE"));
        String viewer = login("viewer");

        mvc.perform(get("/api/v1/specs/" + prd + "/tech-docs").header("Authorization", viewer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].id").value(org.hamcrest.Matchers.contains(be, fe)))
            .andExpect(jsonPath("$.data[0].prdId").value(prd));
        mvc.perform(get("/api/v1/specs/" + other + "/tech-docs").header("Authorization", viewer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/v1/specs/" + be + "/tech-docs").header("Authorization", viewer))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/specs/missing/tech-docs").header("Authorization", viewer))
            .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/specs?prdId=" + prd + "&status=IN_REVIEW").header("Authorization", viewer))
            .andExpect(jsonPath("$.data[*].id").value(org.hamcrest.Matchers.contains(be)));
        mvc.perform(get("/api/v1/specs?kind=TECH_FE").header("Authorization", viewer))
            .andExpect(jsonPath("$.data[*].id").value(org.hamcrest.Matchers.contains(fe)));
        mvc.perform(get("/api/v1/specs?kind=PRD").header("Authorization", viewer))
            .andExpect(jsonPath("$.data[*].id").value(org.hamcrest.Matchers.containsInAnyOrder(prd, other)))
            .andExpect(jsonPath("$.data[?(@.id == '" + prd + "')].techAreas[*]").value(org.hamcrest.Matchers.contains("BE", "FE")));
        mvc.perform(get("/api/v1/specs").header("Authorization", viewer))
            .andExpect(jsonPath("$.data.length()").value(4));
    }
}
