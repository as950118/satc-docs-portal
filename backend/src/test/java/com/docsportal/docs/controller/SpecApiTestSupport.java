package com.docsportal.docs.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.docsportal.docs.format.request.LoginRequest;
import com.docsportal.docs.persistence.SpecDocument;
import com.docsportal.docs.persistence.SpecEvent;
import com.docsportal.docs.repository.SpecDocumentRepository;
import com.docsportal.docs.repository.SpecEventRepository;
import com.docsportal.docs.config.AuthConfig;
import com.docsportal.docs.service.AccountSeeder;
import com.docsportal.docs.service.AuthService;
import com.docsportal.docs.service.AuthServiceImpl;
import com.docsportal.docs.service.SpecExportService;
import com.docsportal.docs.service.SpecService;
import com.jayway.jsonpath.JsonPath;

/**
 * 실제 AuthServiceImpl(개발용 계정 시드)·SpecService·저장소를 쓰고 DB 만 H2 로 바꾼다.
 * 테스트 트랜잭션을 끄므로 서비스 트랜잭션이 운영과 같이 커밋된다.
 */
@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:spec;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "docs.auth.token-secret=test-signing-secret-at-least-32-bytes!!",
    "docs.auth.dev-accounts=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SpecService.class, AuthServiceImpl.class, AuthConfig.class, AccountSeeder.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
abstract class SpecApiTestSupport {

    @Autowired
    protected SpecDocumentRepository documents;
    @Autowired
    protected SpecEventRepository events;
    @Autowired
    protected SpecService specService;

    @Autowired
    protected AuthService auth;
    protected MockMvc mvc;

    @BeforeEach
    void resetStoreAndMvc() {
        events.deleteAll();
        documents.deleteAll();
        mvc = MockMvcBuilders.standaloneSetup(new SpecController(specService, new SpecExportService(documents), auth)).build();
    }

    protected String login(String username) {
        return "Bearer " + auth.login(new LoginRequest(username, username + "123", false)).getToken();
    }

    protected ResultActions postJson(String path, String bearer, String json) throws Exception {
        return mvc.perform(post(path).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected static String idOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id");
    }

    protected SpecDocument doc(String id) {
        return documents.findById(id).orElseThrow();
    }

    protected List<SpecEvent> eventsOf(String id) {
        return events.findBySpecIdOrderByAtAscIdAsc(id);
    }

    protected List<SpecEvent> allEvents() {
        return events.findAll(Sort.by("id"));
    }
}
