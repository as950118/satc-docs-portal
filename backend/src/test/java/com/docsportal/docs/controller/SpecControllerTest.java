package com.docsportal.docs.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.docsportal.docs.format.request.LoginRequest;
import com.docsportal.docs.persistence.SpecDocument;
import com.docsportal.docs.persistence.SpecEvent;
import com.docsportal.docs.repository.SpecDocumentRepository;
import com.docsportal.docs.repository.SpecEventRepository;
import com.docsportal.docs.service.AuthServiceImpl;
import com.docsportal.docs.service.SpecService;

/**
 * 실제 AuthServiceImpl·SpecService·전이 표를 쓰고 저장소만 메모리로 대체한다.
 */
class SpecControllerTest {

    private final Map<String, SpecDocument> documents = new HashMap<>();
    private final List<SpecEvent> events = new ArrayList<>();
    private final AuthServiceImpl auth = new AuthServiceImpl();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SpecDocumentRepository documentRepo = mock(SpecDocumentRepository.class);
        when(documentRepo.saveAndFlush(any(SpecDocument.class))).thenAnswer(inv -> {
            SpecDocument d = inv.getArgument(0);
            documents.put(d.getId(), d);
            return d;
        });
        when(documentRepo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(documents.get(inv.<String>getArgument(0))));
        when(documentRepo.findAll(any(Sort.class))).thenAnswer(inv -> new ArrayList<>(documents.values()));

        SpecEventRepository eventRepo = mock(SpecEventRepository.class);
        when(eventRepo.save(any(SpecEvent.class))).thenAnswer(inv -> {
            events.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(eventRepo.findBySpecIdOrderByAtAscIdAsc(anyString())).thenAnswer(inv ->
            events.stream().filter(e -> e.getSpecId().equals(inv.getArgument(0))).toList());

        mvc = MockMvcBuilders.standaloneSetup(new SpecController(new SpecService(documentRepo, eventRepo), auth)).build();
    }

    private String login(String username) {
        return "Bearer " + auth.login(new LoginRequest(username, username + "123", false)).getToken();
    }

    private ResultActions createAs(String bearer) throws Exception {
        return mvc.perform(post("/api/v1/specs").header("Authorization", bearer)
            .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"T\",\"body\":\"B\"}"));
    }

    private String createDraft() throws Exception {
        String json = createAs(login("planner")).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(json, "$.data.id");
    }

    @Test
    void missingOrUnknownSessionIs401() throws Exception {
        mvc.perform(get("/api/v1/specs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/specs").header("Authorization", "Bearer nope")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/specs/templates/prd")).andExpect(status().isUnauthorized());
        createAs("Bearer nope").andExpect(status().isUnauthorized());
        assertThat(documents).isEmpty();
    }

    @Test
    void nonPlannerCannotCreateEditOrApprove() throws Exception {
        String id = createDraft();
        for (String user : List.of("admin", "editor", "viewer")) {
            String bearer = login(user);
            createAs(bearer).andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/specs/" + id).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"x\"}")).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/specs/" + id + "/approve").header("Authorization", bearer))
                .andExpect(status().isForbidden());
        }
        assertThat(documents).hasSize(1);
        assertThat(events).hasSize(1);
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

        assertThat(documents.get(id).getBody()).isEqualTo("B");
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

        assertThat(events).extracting(e -> e.getAction().name()).containsExactly("CREATE", "APPROVE");
        assertThat(events.get(1).getFromStatus().name()).isEqualTo("DRAFT");
        assertThat(events.get(1).getComment()).isEqualTo("ok");
    }

    @Test
    void everyTransitionLeavesAnEventWithActor() throws Exception {
        String id = createDraft();
        String planner = login("planner");
        mvc.perform(put("/api/v1/specs/" + id).header("Authorization", planner)
            .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"T2\",\"body\":\"B2\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("T2"));
        mvc.perform(post("/api/v1/specs/" + id + "/approve").header("Authorization", planner));

        assertThat(events).extracting(e -> e.getAction().name()).containsExactly("CREATE", "EDIT", "APPROVE");
        assertThat(events).allSatisfy(e -> {
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
        assertThat(documents).isEmpty();
    }
}
