package com.docsportal.docs.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.docsportal.docs.persistence.SpecDocument;
import com.docsportal.docs.persistence.SpecStatus;

class SpecPageApiTest extends SpecApiTestSupport {

    private void prd(String id, String title, String author) {
        documents.saveAndFlush(SpecDocument.prd(id, title, "body", SpecStatus.DRAFT, author));
    }

    private ResultActions page(String query) throws Exception {
        return mvc.perform(get("/api/v1/specs/page" + query).header("Authorization", login("viewer")));
    }

    private ResultActions pageQ(String q) throws Exception {
        return mvc.perform(get("/api/v1/specs/page").param("q", q).header("Authorization", login("viewer")));
    }

    @Test
    void authIsRequired() throws Exception {
        mvc.perform(get("/api/v1/specs/page")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/specs/page").header("Authorization", "Bearer nope")).andExpect(status().isUnauthorized());
    }

    @Test
    void badEnumIs400() throws Exception {
        page("?status=BOGUS").andExpect(status().isBadRequest());
    }

    @Test
    void matchesTitleOrAuthorCaseInsensitively() throws Exception {
        prd("a", "Login Redesign", "alice");
        prd("b", "Billing", "BOB");
        prd("c", "Other", "carol");

        pageQ("LOGIN").andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value("a"));
        pageQ("bob").andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].id").value("b"));
        pageQ("  ").andExpect(jsonPath("$.data.total").value(3));
        page("").andExpect(jsonPath("$.data.total").value(3));
        pageQ("zzz").andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0))
            .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void likeWildcardsAreLiteral() throws Exception {
        prd("a", "100% done", "x");
        prd("b", "a_b", "x");
        prd("c", "plain", "x");
        prd("d", "axb", "x");

        pageQ("%").andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].id").value("a"));
        pageQ("a_b").andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].id").value("b"));
        pageQ("_").andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void paginatesWithTotalPageAndSizeAndClampsSize() throws Exception {
        for (int i = 0; i < 25; i++) {
            prd(String.format("p%02d", i), "T" + i, "x");
        }
        page("").andExpect(jsonPath("$.data.total").value(25)).andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(20)).andExpect(jsonPath("$.data.items.length()").value(20));
        page("?page=1").andExpect(jsonPath("$.data.items.length()").value(5)).andExpect(jsonPath("$.data.page").value(1));
        page("?page=5&size=10").andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.total").value(25));
        page("?size=0").andExpect(jsonPath("$.data.size").value(1)).andExpect(jsonPath("$.data.items.length()").value(1));
        page("?size=1000").andExpect(jsonPath("$.data.size").value(100)).andExpect(jsonPath("$.data.items.length()").value(25));
        page("?page=-3").andExpect(jsonPath("$.data.page").value(0));
    }

    @Test
    void allVersionsSemanticsMatchList() throws Exception {
        String planner = login("planner");
        String v1 = idOf(postJson("/api/v1/specs", planner, "{\"title\":\"P\",\"body\":\"B\"}"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/specs/" + v1 + "/approve")
            .header("Authorization", planner)).andExpect(status().isOk());
        String v2 = idOf(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .post("/api/v1/specs/" + v1 + "/new-version").header("Authorization", planner)).andExpect(status().isOk()));

        page("").andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].id").value(v2));
        page("?allVersions=true").andExpect(jsonPath("$.data.total").value(2));
        mvc.perform(get("/api/v1/specs").header("Authorization", planner)).andExpect(jsonPath("$.data.length()").value(1));
    }
}
