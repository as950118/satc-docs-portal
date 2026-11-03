package com.docsportal.docs.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docsportal.docs.format.response.BreadcrumbItem;
import com.docsportal.docs.format.response.SearchResultItem;
import com.docsportal.docs.persistence.SpecDocument;
import com.docsportal.docs.persistence.SpecKind;
import com.docsportal.docs.repository.SpecDocumentRepository;

/**
 * PRD·기술문서(BE/FE/DATA) 검색. 이 문서들은 spec_document 테이블에만 있고 ES 에 색인되지 않으므로
 * 업로드 문서 검색(ExternalService)과 별개로 DB 에서 찾아 같은 응답 형식으로 돌려준다.
 */
@Service
public class SpecSearchService {

    private static final int MAX_RESULTS = 10;
    private static final int SNIPPET_RADIUS = 60;
    private static final int MAX_SNIPPETS = 3;

    private final SpecDocumentRepository documents;

    public SpecSearchService(SpecDocumentRepository documents) {
        this.documents = documents;
    }

    /** 공백으로 나눈 모든 단어가 제목 또는 본문에 들어 있는 문서. 제목에 걸린 문서가 앞선다. */
    @Transactional(readOnly = true)
    public List<SearchResultItem> search(String query) {
        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return List.of();
        }
        List<SpecDocument> candidates = documents.searchText("%" + escapeLike(tokens.get(0)) + "%");

        List<SearchResultItem> titleHits = new ArrayList<>();
        List<SearchResultItem> bodyHits = new ArrayList<>();
        for (SpecDocument doc : candidates) {
            String title = doc.getTitle().toLowerCase(Locale.ROOT);
            String body = doc.getBody().toLowerCase(Locale.ROOT);
            boolean all = tokens.stream().allMatch(t -> title.contains(t) || body.contains(t));
            if (!all) {
                continue;
            }
            SearchResultItem item = toItem(doc, tokens);
            (tokens.stream().anyMatch(title::contains) ? titleHits : bodyHits).add(item);
        }
        titleHits.addAll(bodyHits);
        return titleHits.size() > MAX_RESULTS ? titleHits.subList(0, MAX_RESULTS) : titleHits;
    }

    private SearchResultItem toItem(SpecDocument doc, List<String> tokens) {
        SearchResultItem item = new SearchResultItem(doc.getId(), doc.getTitle(),
            new ArrayList<BreadcrumbItem>(), label(doc.getKind()), snippets(doc.getBody(), tokens));
        item.setSpecKind(doc.getKind().name());
        return item;
    }

    private static String label(SpecKind kind) {
        return switch (kind) {
            case PRD -> "PRD";
            case TECH_BE -> "BE 기술문서";
            case TECH_FE -> "FE 기술문서";
            case TECH_DATA -> "DATA 기술문서";
        };
    }

    /** 검색어가 걸린 줄 주변을 잘라 <em> 으로 강조한다. 본문의 HTML 은 이스케이프한다. */
    static List<String> snippets(String body, List<String> tokens) {
        Pattern hit = Pattern.compile(String.join("|", tokens.stream().map(Pattern::quote).toList()),
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        List<String> result = new ArrayList<>();
        Matcher m = hit.matcher(body);
        int lastEnd = -1;
        while (m.find() && result.size() < MAX_SNIPPETS) {
            if (m.start() < lastEnd) {
                continue;
            }
            int from = Math.max(0, m.start() - SNIPPET_RADIUS);
            int to = Math.min(body.length(), m.end() + SNIPPET_RADIUS);
            String piece = body.substring(from, to).replace('\n', ' ');
            Matcher inner = hit.matcher(piece);
            StringBuilder out = new StringBuilder(from > 0 ? "…" : "");
            int pos = 0;
            while (inner.find()) {
                out.append(escapeHtml(piece.substring(pos, inner.start())))
                    .append("<em>").append(escapeHtml(inner.group())).append("</em>");
                pos = inner.end();
            }
            out.append(escapeHtml(piece.substring(pos))).append(to < body.length() ? "…" : "");
            result.add(out.toString());
            lastEnd = to;
        }
        return result;
    }

    private static List<String> tokenize(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return List.of(query.trim().toLowerCase(Locale.ROOT).split("\\s+"));
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
