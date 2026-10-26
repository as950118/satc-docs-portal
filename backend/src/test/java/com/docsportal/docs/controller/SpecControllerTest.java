package com.docsportal.docs.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.docsportal.docs.persistence.SpecEvent;

class SpecControllerTest extends SpecApiTestSupport {

    private ResultActions createAs(String bearer) throws Exception {
        return postJson("/api/v1/specs", bearer, "{\"title\":\"T\",\"body\":\"B\"}");
    }

    private String createDraft() throws Exception {
        return idOf(createAs(login("planner")));
    }

    @Test
    void missingOrUnknownSessionIs401() throws Exception {
        mvc.perform(get("/api/v1/specs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/specs").header("Authorization", "Bearer nope")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/specs/templates/prd")).andExpect(status().isUnauthorized());
        createAs("Bearer nope").andExpect(status().isUnauthorized());
        assertThat(documents.count()).isZero();
    }

    @Test
    void nonPlannerCannotCreateEditOrApprove() throws Exception {
        String id = createDraft();
        for (String user : List.of("admin", "editor", "viewer", "backend", "frontend", "data")) {
            String bearer = login(user);
            createAs(bearer).andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/specs/" + id).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"x\"}")).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/specs/" + id + "/approve").header("Authorization", bearer))
                .andExpect(status().isForbidden());
        }
        assertThat(documents.count()).isEqualTo(1);
        assertThat(events.count()).isEqualTo(1);
    }

    @Test
    void anyLoggedInUserCanReadTemplateListAndDetail() throws Exception {
        String id = createDraft();
        String viewer = login("viewer");

        mvc.perform(get("/api/v1/specs/templates/prd").header("Authorization", viewer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.kind").value("PRD"))
            .andExpect(jsonPath("$.data.markdown").value(org.hamcrest.Matchers.containsString("승인 기준")));
        mvc.perform(get("/api/v1/specs?kind=PRD&status=DRAFT").header("Authorization", viewer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(id));
        mvc.perform(get("/api/v1/specs?status=PRD_APPROVED").header("Authorization", viewer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/v1/specs?status=BOGUS").header("Authorization", viewer))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/specs/" + id).header("Authorization", viewer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.body").value("B"))
            .andExpect(jsonPath("$.data.events[0].action").value("CREATE"));
        mvc.perform(get("/api/v1/specs/missing").header("Authorization", viewer)).andExpect(status().isNotFound());
    }

    @Test
    void editingApprovedSpecIs409AndKeepsBody() throws Exception {
        String id = createDraft();
        String planner = login("planner");
        mvc.perform(post("/api/v1/specs/" + id + "/approve").header("Authorization", planner)).andExpect(status().isOk());

        mvc.perform(put("/api/v1/specs/" + id).header("Authorization", planner)
            .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"changed\"}")).andExpect(status().isConflict());

        assertThat(doc(id).getBody()).isEqualTo("B");
    }

    @Test
    void approveIsIdempotentAndDoesNotDuplicateEvent() throws Exception {
        String id = createDraft();
        String planner = login("planner");

        mvc.perform(post("/api/v1/specs/" + id + "/approve").header("Authorization", planner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"ok\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PRD_APPROVED"));
        mvc.perform(post("/api/v1/specs/" + id + "/approve").header("Authorization", planner))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PRD_APPROVED"));

        List<SpecEvent> history = eventsOf(id);
        assertThat(history).extracting(e -> e.getAction().name()).containsExactly("CREATE", "APPROVE");
        assertThat(history.get(1).getFromStatus().name()).isEqualTo("DRAFT");
        assertThat(history.get(1).getComment()).isEqualTo("ok");
    }

    @Test
    void everyTransitionLeavesAnEventWithActor() throws Exception {
        String id = createDraft();
        String planner = login("planner");
        mvc.perform(put("/api/v1/specs/" + id).header("Authorization", planner)
            .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"T2\",\"body\":\"B2\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("T2"));
        mvc.perform(post("/api/v1/specs/" + id + "/approve").header("Authorization", planner));

        assertThat(eventsOf(id)).extracting(e -> e.getAction().name()).containsExactly("CREATE", "EDIT", "APPROVE");
        assertThat(eventsOf(id)).allSatisfy(e -> {
            assertThat(e.getActor()).isEqualTo("planner");
            assertThat(e.getActorRole()).isEqualTo("PLANNER");
            assertThat(e.getAt()).isNotNull();
        });
    }

    @Test
    void blankBodyIs400() throws Exception {
        mvc.perform(post("/api/v1/specs").header("Authorization", login("planner"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"T\",\"body\":\" \"}"))
            .andExpect(status().isBadRequest());
        assertThat(documents.count()).isZero();
    }
}
