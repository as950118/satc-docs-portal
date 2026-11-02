package com.docsportal.docs.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.docsportal.docs.persistence.SpecStatus;
import com.fasterxml.jackson.databind.ObjectMapper;

class SpecExportApiTest extends SpecApiTestSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static String sample(String name) throws IOException {
        return new ClassPathResource("openspec/" + name + ".md").getContentAsString(StandardCharsets.UTF_8);
    }

    private ResultActions export(String id, String user) throws Exception {
        return mvc.perform(get("/api/v1/specs/" + id + "/export/openspec").header("Authorization", login(user)));
    }

    private ResultActions act(String id, String action, String user) throws Exception {
        return mvc.perform(post("/api/v1/specs/" + id + "/" + action).header("Authorization", login(user)));
    }

    private String newPrd(String body) throws Exception {
        return idOf(postJson("/api/v1/specs", login("planner"),
            JSON.writeValueAsString(Map.of("title", "라벨 권한", "body", body))).andExpect(status().isOk()));
    }

    private String approvedPrd(String body) throws Exception {
        String id = newPrd(body);
        act(id, "approve", "planner").andExpect(status().isOk());
        return id;
    }

    private void declareBe(String prd) throws Exception {
        mvc.perform(put("/api/v1/specs/" + prd + "/tech-areas").header("Authorization", login("planner"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"areas\":[\"BE\"]}")).andExpect(status().isOk());
    }

    private void approvedTech(String prd, String body) throws Exception {
        declareBe(prd);
        String tech = idOf(postJson("/api/v1/specs/" + prd + "/tech-docs", login("backend"),
            JSON.writeValueAsString(Map.of("area", "BE", "title", "BE 설계", "body", body))).andExpect(status().isOk()));
        act(tech, "submit", "backend").andExpect(status().isOk());
        act(tech, "approve", "backend").andExpect(status().isOk());
    }

    @Test
    void approvedNewFormatPrdExportsToAnyRoleWithoutWritingAnything() throws Exception {
        String prd = approvedPrd(sample("sample-prd-r1-new-format"));
        approvedTech(prd, sample("sample-tech-be"));
        long eventCount = events.count();
        SpecStatus before = doc(prd).getStatus();

        for (String user : List.of("viewer", "planner", "backend", "reviewer", "admin")) {
            export(prd, user).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changeName").value("prd-" + prd.substring(0, 8) + "-r1"))
                .andExpect(jsonPath("$.data.lint").isEmpty())
                .andExpect(jsonPath("$.data.files[*].path").value(contains("proposal.md",
                    "specs/inventory-work-label/spec.md", "design.md", "tasks.md")))
                .andExpect(jsonPath("$.data.files[3].content").value(containsString("- [ ] 1.1 ")));
        }

        assertThat(events.count()).isEqualTo(eventCount);
        assertThat(doc(prd).getStatus()).isEqualTo(before);
    }

    @Test
    void draftPrdIsConflict() throws Exception {
        String draft = newPrd(sample("sample-prd-r1-new-format"));

        export(draft, "planner").andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorMessage").value(containsString("DRAFT")));
    }

    @Test
    void supersededPrdIsConflictAndTheNewRevisionExportsAgainstIt() throws Exception {
        String v1 = approvedPrd(sample("sample-prd-r1-new-format"));
        String v2 = idOf(act(v1, "new-version", "planner").andExpect(status().isOk()));
        mvc.perform(put("/api/v1/specs/" + v2).header("Authorization", login("planner"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(Map.of("body", sample("sample-prd-r2-new-format"))))).andExpect(status().isOk());
        act(v2, "approve", "planner").andExpect(status().isOk());

        export(v1, "planner").andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorMessage").value(containsString("SUPERSEDED")));
        export(v2, "planner").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.changeName").value("prd-" + v1.substring(0, 8) + "-r2"))
            .andExpect(jsonPath("$.data.files[1].content").value(containsString("## RENAMED Requirements")));
    }

    @Test
    void oldFormatApprovedPrdIsUnprocessableWithLint() throws Exception {
        String prd = approvedPrd("# PRD: 옛\n\n## 4. 승인 기준\n\n- [ ] AC-1: 한 문장\n");

        export(prd, "viewer").andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.status").value(422))
            .andExpect(jsonPath("$.errorMessage").value(containsString("4절에 `### AC-n: 이름` 형식의 승인 기준이 없다")))
            .andExpect(jsonPath("$.data.lint[0]").value("4절에 `### AC-n: 이름` 형식의 승인 기준이 없다"))
            .andExpect(jsonPath("$.data.files").isEmpty());
    }

    @Test
    void requiresSessionAndKnownPrd() throws Exception {
        String prd = approvedPrd(sample("sample-prd-r1-new-format"));
        declareBe(prd);
        String tech = idOf(postJson("/api/v1/specs/" + prd + "/tech-docs", login("backend"), "{\"area\":\"BE\",\"title\":\"t\",\"body\":\"b\"}")
            .andExpect(status().isOk()));

        mvc.perform(get("/api/v1/specs/" + prd + "/export/openspec")).andExpect(status().isUnauthorized());
        export("no-such-id", "viewer").andExpect(status().isNotFound());
        export(tech, "viewer").andExpect(status().isNotFound());
    }
}
