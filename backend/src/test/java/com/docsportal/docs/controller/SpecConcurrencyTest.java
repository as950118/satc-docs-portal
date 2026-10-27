package com.docsportal.docs.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import com.docsportal.docs.persistence.SpecAction;
import com.docsportal.docs.persistence.SpecStatus;
import com.docsportal.docs.repository.SpecDocumentRepository;
import com.docsportal.docs.service.SpecService;

/**
 * 두 요청이 모두 문서를 읽은 뒤에야 진행하도록 findById 직후에 장벽을 둬서, 경합 순서를 매번 같게 만든다.
 */
class SpecConcurrencyTest extends SpecApiTestSupport {

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private MockMvc racingMvc;

    @BeforeEach
    void buildRacingMvc() {
        CyclicBarrier bothLoaded = new CyclicBarrier(2);
        SpecDocumentRepository pausing = (SpecDocumentRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[] {SpecDocumentRepository.class}, (proxy, method, args) -> {
                Object result;
                try {
                    result = method.invoke(documents, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
                if (method.getName().equals("findById")) {
                    bothLoaded.await(10, TimeUnit.SECONDS);
                }
                return result;
            });

        ProxyFactory factory = new ProxyFactory(new SpecService(pausing, events));
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        racingMvc = MockMvcBuilders.standaloneSetup(new SpecController((SpecService) factory.getProxy(), auth)).build();
    }

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    private String inReview(String prdId, String area, String user) throws Exception {
        String bearer = login(user);
        String id = idOf(postJson("/api/v1/specs/" + prdId + "/tech-docs", bearer,
            "{\"area\":\"" + area + "\",\"title\":\"t\",\"body\":\"b\"}"));
        mvc.perform(post("/api/v1/specs/" + id + "/submit").header("Authorization", bearer)).andExpect(status().isOk());
        return id;
    }

    private String prdWithAreas(String areasJson) throws Exception {
        String planner = login("planner");
        String prd = idOf(postJson("/api/v1/specs", planner, "{\"title\":\"P\",\"body\":\"B\"}"));
        mvc.perform(post("/api/v1/specs/" + prd + "/approve").header("Authorization", planner)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/specs/" + prd + "/tech-areas").header("Authorization", planner)
            .contentType(MediaType.APPLICATION_JSON).content("{\"areas\":" + areasJson + "}")).andExpect(status().isOk());
        return prd;
    }

    private String approved(String prdId, String area, String user) throws Exception {
        String bearer = login(user);
        String id = inReview(prdId, area, user);
        mvc.perform(post("/api/v1/specs/" + id + "/approve").header("Authorization", bearer)).andExpect(status().isOk());
        return id;
    }

    private void submitImplementation(String id, String user) throws Exception {
        String bearer = login(user);
        mvc.perform(post("/api/v1/specs/" + id + "/start-implementation").header("Authorization", bearer))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/specs/" + id + "/pr-links").header("Authorization", bearer)
            .contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"https://github.com/o/r/pull/" + id + "\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/specs/" + id + "/submit-implementation").header("Authorization", bearer))
            .andExpect(status().isOk());
    }

    private Callable<Integer> racing(String id, String action, String bearer) {
        return () -> racingMvc.perform(post("/api/v1/specs/" + id + "/" + action).header("Authorization", bearer))
            .andReturn().getResponse().getStatus();
    }

    private List<Integer> race(Callable<Integer> a, Callable<Integer> b) throws Exception {
        Future<Integer> first = pool.submit(a);
        Future<Integer> second = pool.submit(b);
        return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
    }

    @Test
    void secondOfTwoConcurrentApprovalsGets409AndOnlyOneEventIsKept() throws Exception {
        String prd = prdWithAreas("[\"BE\"]");
        String be = inReview(prd, "BE", "backend");
        String backend = login("backend");

        List<Integer> statuses = race(racing(be, "approve", backend), racing(be, "approve", backend));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(doc(be).getStatus()).isEqualTo(SpecStatus.APPROVED);
        assertThat(eventsOf(be)).filteredOn(e -> e.getAction() == SpecAction.APPROVE).hasSize(1);
        assertThat(doc(prd).getStatus()).isEqualTo(SpecStatus.SPEC_APPROVED);
        assertThat(eventsOf(prd)).filteredOn(e -> e.getAction() == SpecAction.AGGREGATE).hasSize(1);
    }

    @Test
    void concurrentApprovalOfTheLastTwoAreasStillAggregates() throws Exception {
        String prd = prdWithAreas("[\"BE\",\"FE\"]");
        String be = inReview(prd, "BE", "backend");
        String fe = inReview(prd, "FE", "frontend");

        List<Integer> statuses = race(racing(be, "approve", login("backend")), racing(fe, "approve", login("frontend")));

        assertThat(statuses).containsExactly(200, 200);
        assertThat(doc(prd).getStatus()).isEqualTo(SpecStatus.SPEC_APPROVED);
        assertThat(eventsOf(prd)).filteredOn(e -> e.getAction() == SpecAction.AGGREGATE).hasSize(1);
    }

    @Test
    void concurrentImplementationApprovalOfTheLastTwoAreasStillAggregates() throws Exception {
        String prd = prdWithAreas("[\"BE\",\"FE\"]");
        String be = approved(prd, "BE", "backend");
        String fe = approved(prd, "FE", "frontend");
        submitImplementation(be, "backend");
        submitImplementation(fe, "frontend");
        String reviewer = login("reviewer");

        List<Integer> statuses = race(racing(be, "approve-implementation", reviewer),
            racing(fe, "approve-implementation", reviewer));

        assertThat(statuses).containsExactly(200, 200);
        assertThat(doc(be).getStatus()).isEqualTo(SpecStatus.IMPLEMENTED);
        assertThat(doc(fe).getStatus()).isEqualTo(SpecStatus.IMPLEMENTED);
        assertThat(doc(prd).getStatus()).isEqualTo(SpecStatus.IMPLEMENTED);
        assertThat(eventsOf(prd)).filteredOn(e -> e.getAction() == SpecAction.AGGREGATE)
            .extracting(e -> e.getToStatus()).containsExactly(SpecStatus.SPEC_APPROVED, SpecStatus.IMPLEMENTED);
    }
}
