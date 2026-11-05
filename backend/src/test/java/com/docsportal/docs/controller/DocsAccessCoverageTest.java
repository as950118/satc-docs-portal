package com.docsportal.docs.controller;

import static com.docsportal.docs.common.Path.API_DOCS;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import com.docsportal.docs.common.DocsAccess;

/**
 * 모든 컨트롤러의 매핑을 리플렉션으로 모아, 보호 선언 없이 추가된 매핑이 있으면 실패한다.
 * 문서 매핑은 @DocsAccess 와 DocsEndpoints 표에 있어야 하고, 그 밖의 매핑은 Authorization 헤더를 직접 받거나 아래 공개 목록에 있어야 한다.
 */
class DocsAccessCoverageTest {

    private static final Map<String, String> OPEN_WITHOUT_DOCS_ACCESS = Map.ofEntries(
        Map.entry("ANY /hello", "헬스 체크(Render healthCheckPath, compose healthcheck)"),
        Map.entry("GET /api/v1/events", "SSE. EventSource 는 헤더를 못 보내고 이벤트에 본문이 없다(LiveEventsController)"),
        Map.entry("POST /api/v1/auth/login", "로그인"),
        Map.entry("POST /api/v1/auth/logout", "HttpServletRequest 의 Authorization 을 읽는다"),
        Map.entry("GET /api/v1/auth/me", "HttpServletRequest 의 Authorization 을 검증한다"),
        Map.entry("GET /api/v1/auth/validate", "HttpServletRequest 의 Authorization 을 검증한다"),
        Map.entry("GET /.well-known/oauth-authorization-server", "OAuth 메타데이터(RFC 8414)"),
        Map.entry("POST /oauth/register", "OAuth 동적 등록(RFC 7591)"),
        Map.entry("GET /oauth/authorize", "OAuth 인가 요청. 동의 화면으로 보낸다"),
        Map.entry("GET /oauth/authorize/requests/{requestId}", "동의 화면이 대기 요청을 읽는다"),
        Map.entry("POST /oauth/authorize/requests/{requestId}/deny", "동의 거절"),
        Map.entry("POST /oauth/token", "OAuth 코드 교환(PKCE)")
    );

    @Test
    void everyDocsMappingDeclaresAccessAndIsInTheEndpointTable() {
        Map<String, Method> docs = mappings().entrySet().stream()
            .filter(e -> isDocsPath(e.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, TreeMap::new));

        List<String> undeclared = docs.entrySet().stream()
            .filter(e -> e.getValue().getAnnotation(DocsAccess.class) == null)
            .map(Map.Entry::getKey).toList();
        assertThat(undeclared).as("@DocsAccess 가 없는 문서 매핑").isEmpty();

        assertThat(docs.keySet()).as("DocsEndpoints.ALL 에 없는(또는 사라진) 문서 매핑")
            .containsExactlyInAnyOrderElementsOf(DocsEndpoints.ALL.stream().map(DocsEndpoints.Endpoint::key).toList());
    }

    @Test
    void everyOtherMappingChecksAuthorizationOrIsListedOpen() {
        Map<String, Method> others = mappings().entrySet().stream()
            .filter(e -> !isDocsPath(e.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, TreeMap::new));

        List<String> unprotected = others.entrySet().stream()
            .filter(e -> !takesAuthorizationHeader(e.getValue()) && !OPEN_WITHOUT_DOCS_ACCESS.containsKey(e.getKey()))
            .map(Map.Entry::getKey).toList();
        assertThat(unprotected).as("Authorization 을 받지 않고 공개 목록에도 없는 매핑").isEmpty();
        assertThat(others.keySet()).as("공개 목록에만 있고 컨트롤러에는 없는 매핑").containsAll(OPEN_WITHOUT_DOCS_ACCESS.keySet());
    }

    private static boolean isDocsPath(String key) {
        String path = key.substring(key.indexOf(' ') + 1);
        return path.equals(API_DOCS) || path.startsWith(API_DOCS + "/");
    }

    private static boolean takesAuthorizationHeader(Method method) {
        for (Parameter parameter : method.getParameters()) {
            RequestHeader header = AnnotatedElementUtils.findMergedAnnotation(parameter, RequestHeader.class);
            if (header != null && "Authorization".equalsIgnoreCase(header.name())) {
                return true;
            }
        }
        return false;
    }

    /** "GET /api/v1/docs/tree" 형태의 키와 핸들러 메서드. method 가 없는 @RequestMapping 은 ANY 로 적는다. */
    private static Map<String, Method> mappings() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        Map<String, Method> found = new TreeMap<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.docsportal.docs")) {
            Class<?> type = ClassUtils.resolveClassName(definition.getBeanClassName(), DocsAccessCoverageTest.class.getClassLoader());
            RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            List<String> prefixes = classMapping == null ? List.of("") : orEmpty(classMapping.path());
            for (Method method : type.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                List<String> verbs = mapping.method().length == 0 ? List.of("ANY")
                    : Arrays.stream(mapping.method()).map(Enum::name).toList();
                for (String prefix : prefixes) {
                    for (String path : orEmpty(mapping.path())) {
                        for (String verb : verbs) {
                            Method previous = found.put(verb + " " + prefix + path, method);
                            assertThat(previous).as("같은 매핑이 두 번: " + verb + " " + prefix + path).isNull();
                        }
                    }
                }
            }
        }
        assertThat(found).as("컨트롤러 스캔 결과").isNotEmpty();
        return found;
    }

    private static List<String> orEmpty(String[] paths) {
        return paths.length == 0 ? List.of("") : List.of(paths);
    }
}
