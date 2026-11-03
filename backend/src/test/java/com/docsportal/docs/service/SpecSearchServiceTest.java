package com.docsportal.docs.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.docsportal.docs.persistence.SpecDocument;
import com.docsportal.docs.persistence.SpecKind;
import com.docsportal.docs.persistence.SpecStatus;
import com.docsportal.docs.repository.SpecDocumentRepository;

class SpecSearchServiceTest {

    private SpecDocument doc(String id, SpecKind kind, String title, String body) {
        SpecDocument d = mock(SpecDocument.class);
        when(d.getId()).thenReturn(id);
        when(d.getKind()).thenReturn(kind);
        when(d.getTitle()).thenReturn(title);
        when(d.getBody()).thenReturn(body);
        when(d.getStatus()).thenReturn(SpecStatus.DRAFT);
        return d;
    }

    @Test
    void 모든_단어가_걸린_문서만_돌려주고_제목_일치가_앞선다() {
        SpecDocumentRepository repo = mock(SpecDocumentRepository.class);
        SpecDocument a = doc("a", SpecKind.TECH_BE, "인증", "로그인 API 에서 토큰을 발급한다");
        SpecDocument b = doc("b", SpecKind.PRD, "로그인 개선", "세션 토큰 정책");
        SpecDocument c = doc("c", SpecKind.TECH_FE, "화면", "로그인 화면");
        when(repo.searchText("%로그인%")).thenReturn(List.of(a, b, c));

        var result = new SpecSearchService(repo).search("로그인 토큰");

        assertEquals(List.of("b", "a"), result.stream().map(r -> r.getDocumentId()).toList());
        assertEquals("PRD", result.get(0).getSpecKind());
        assertEquals("BE 기술문서", result.get(1).getObjectName());
    }

    @Test
    void 스니펫은_검색어를_강조하고_HTML은_이스케이프한다() {
        var snippets = SpecSearchService.snippets("앞 <script>x</script> 로그인 뒤", List.of("로그인"));
        assertEquals(1, snippets.size());
        assertTrue(snippets.get(0).contains("<em>로그인</em>"));
        assertTrue(snippets.get(0).contains("&lt;script&gt;"));
    }

    @Test
    void 빈_검색어는_빈_결과() {
        assertTrue(new SpecSearchService(mock(SpecDocumentRepository.class)).search("  ").isEmpty());
    }
}
