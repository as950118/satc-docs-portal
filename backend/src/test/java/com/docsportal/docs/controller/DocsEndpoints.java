package com.docsportal.docs.controller;

import static com.docsportal.docs.common.Path.API_DOCS;
import static com.docsportal.docs.common.Path.API_DOCS_CHILDREN;
import static com.docsportal.docs.common.Path.API_DOCS_CONTENT;
import static com.docsportal.docs.common.Path.API_DOCS_FOLDER;
import static com.docsportal.docs.common.Path.API_DOCS_FOLDERS;
import static com.docsportal.docs.common.Path.API_DOCS_ID;
import static com.docsportal.docs.common.Path.API_DOCS_INDEXED;
import static com.docsportal.docs.common.Path.API_DOCS_MOVE;
import static com.docsportal.docs.common.Path.API_DOCS_MULTIPLE;
import static com.docsportal.docs.common.Path.API_DOCS_ROOT;
import static com.docsportal.docs.common.Path.API_DOCS_SEARCH;
import static com.docsportal.docs.common.Path.API_DOCS_STANDARD;
import static com.docsportal.docs.common.Path.API_DOCS_STANDARD_HISTORY;
import static com.docsportal.docs.common.Path.API_DOCS_STATISTICS;
import static com.docsportal.docs.common.Path.API_DOCS_STATUS;
import static com.docsportal.docs.common.Path.API_DOCS_SYNC;
import static com.docsportal.docs.common.Path.API_DOCS_TREE;
import static com.docsportal.docs.common.Path.API_DOCS_TREE_FULL;
import static com.docsportal.docs.common.Path.API_DOCS_TREE_PARENT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.docsportal.docs.common.DocsAccess.Level;

/**
 * 문서 API 매핑 전체와 각 매핑이 허용하는 호출자. DocsAccessCoverageTest 는 컨트롤러의 /api/v1/docs 매핑이 이 표와
 * 정확히 같은지 보고, DocsApiAuthTest 는 표의 매핑마다 실제 요청으로 401/403/200 을 확인한다.
 */
final class DocsEndpoints {

    record Endpoint(String method, String pattern, Level level, Supplier<MockHttpServletRequestBuilder> request) {
        String key() {
            return method + " " + pattern;
        }

        @Override
        public String toString() {
            return key();
        }
    }

    static final List<Endpoint> ALL = List.of(
        new Endpoint("POST", API_DOCS, Level.WRITE, () -> multipart(API_DOCS).file(file("file"))),
        new Endpoint("GET", API_DOCS_TREE, Level.READ, () -> get(API_DOCS_TREE)),
        new Endpoint("GET", API_DOCS_TREE_FULL, Level.READ, () -> get(API_DOCS_TREE_FULL)),
        new Endpoint("GET", API_DOCS_TREE_PARENT, Level.READ, () -> get(API_DOCS_TREE_PARENT, "p1")),
        new Endpoint("GET", API_DOCS_ROOT, Level.READ, () -> get(API_DOCS_ROOT)),
        new Endpoint("GET", API_DOCS_CHILDREN, Level.READ, () -> get(API_DOCS_CHILDREN, "p1")),
        new Endpoint("POST", API_DOCS_FOLDER, Level.WRITE, () -> post(API_DOCS_FOLDER).param("title", "t")),
        new Endpoint("POST", API_DOCS_MULTIPLE, Level.WRITE, () -> multipart(API_DOCS_MULTIPLE).file(file("files"))),
        new Endpoint("POST", API_DOCS_MOVE, Level.WRITE, () -> post(API_DOCS_MOVE)
            .contentType(MediaType.APPLICATION_JSON).content("{\"documentIds\":[\"d1\"],\"targetParentId\":null}")),
        new Endpoint("GET", API_DOCS_STATISTICS, Level.READ, () -> get(API_DOCS_STATISTICS)),
        new Endpoint("GET", API_DOCS_FOLDERS, Level.READ, () -> get(API_DOCS_FOLDERS)),
        new Endpoint("DELETE", API_DOCS_ID, Level.WRITE, () -> delete(API_DOCS_ID, "d1")),
        new Endpoint("POST", API_DOCS_STATUS, Level.INTERNAL, () -> post(API_DOCS_STATUS)
            .contentType(MediaType.APPLICATION_JSON).content("{\"documentId\":\"d1\",\"status\":\"COMPLETE\"}")),
        new Endpoint("GET", API_DOCS_CONTENT, Level.READ, () -> get(API_DOCS_CONTENT, "d1")),
        new Endpoint("GET", API_DOCS_INDEXED, Level.READ, () -> get(API_DOCS_INDEXED, "d1")),
        new Endpoint("GET", API_DOCS_SEARCH, Level.READ, () -> get(API_DOCS_SEARCH).param("q", "x")),
        new Endpoint("PUT", API_DOCS_STANDARD, Level.WRITE, () -> sourceForm(API_DOCS_STANDARD)),
        new Endpoint("GET", API_DOCS_STANDARD_HISTORY, Level.READ, () -> get(API_DOCS_STANDARD_HISTORY).param("sourceKey", "k")),
        new Endpoint("PUT", API_DOCS_SYNC, Level.SYNC, () -> sourceForm(API_DOCS_SYNC))
    );

    private DocsEndpoints() {
    }

    private static MockHttpServletRequestBuilder sourceForm(String path) {
        return multipart(HttpMethod.PUT, path).file(file("file"))
            .param("sourceKey", "k").param("sourceRepo", "r").param("sourcePath", "p")
            .param("sourceCommit", "c").param("contentHash", "h");
    }

    private static MockMultipartFile file(String name) {
        return new MockMultipartFile(name, "a.md", "text/markdown", "# a".getBytes());
    }
}
