package com.docsportal.docs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.docsportal.docs.format.response.SpecExportResponse;
import com.docsportal.docs.persistence.TechArea;

class OpenSpecConverterTest {

    private static final String ROOT = "16b41143-88a9-4088-aaa2-6fb22692f601";

    private static String sample(String name) throws IOException {
        return new ClassPathResource("openspec/" + name + ".md").getContentAsString(StandardCharsets.UTF_8);
    }

    private static String file(SpecExportResponse r, String path) {
        return r.files().stream().filter(f -> f.path().equals(path)).findFirst().orElseThrow().content();
    }

    private static SpecExportResponse convert(String prd, String prev, Map<TechArea, String> tech) {
        return OpenSpecConverter.convert(ROOT, prev == null ? 1 : 2, "라벨 권한", prd, prev, tech);
    }

    @Test
    void newFormatPrdWithApprovedTechDocsYieldsFourFilesAndNumberedTasks() throws IOException {
        Map<TechArea, String> tech = new EnumMap<>(TechArea.class);
        tech.put(TechArea.FE, sample("sample-tech-fe"));
        tech.put(TechArea.BE, sample("sample-tech-be"));

        SpecExportResponse r = convert(sample("sample-prd-r1-new-format"), null, tech);

        assertThat(r.lint()).isEmpty();
        assertThat(r.changeName()).isEqualTo("prd-16b41143-r1");
        assertThat(r.files()).extracting(SpecExportResponse.File::path)
            .containsExactly("proposal.md", "specs/inventory-work-label/spec.md", "design.md", "tasks.md");

        String spec = file(r, "specs/inventory-work-label/spec.md");
        assertThat(spec).contains("## ADDED Requirements")
            .contains("### Requirement: AC-1 업무 라벨 할당 여부 표시")
            .contains("#### Scenario: 라벨이 할당된 자원 표시")
            .contains("- **GIVEN** 업무 라벨이 할당된 자원이 있다")
            .contains("- **WHEN** 사용자가 클라우드 자원 탭을 연다")
            .contains("- **THEN** 해당 자원에 업무 라벨 할당 표시가 공통 컴포넌트로 나타난다")
            .doesNotContain("- 조건:", "- 동작:", "- 결과:");

        String tasks = file(r, "tasks.md");
        assertThat(tasks).contains("## 1. BE", "- [ ] 1.1 챗봇 조회 구분 방식 확정 (검증: 기술문서 승인)", "- [ ] 1.4 ", "## 2. FE", "- [ ] 2.1 ");
        assertThat(file(r, "design.md")).contains("## BE", "### 2. API 변경").doesNotContain("작업");
        assertThat(file(r, "proposal.md")).contains("## Why", "<!-- docs-portal PRD " + ROOT + " revision 1 -->");
    }

    @Test
    void withoutTechDocsOnlyProposalAndSpecAreProduced() throws IOException {
        SpecExportResponse r = convert(sample("sample-prd-r1-new-format"), null, Map.of());

        assertThat(r.files()).extracting(SpecExportResponse.File::path).containsExactly("proposal.md", "specs/inventory-work-label/spec.md");
    }

    @Test
    void oldFormatPrdReportsMissingCriteriaAndWritesNoFiles() {
        String old = "# PRD: 옛 형식\n\n| 항목 | 값 |\n|---|---|\n| 스펙 영역 | old-area |\n\n## 4. 승인 기준\n\n- [ ] AC-1: 한 문장이다.\n";

        SpecExportResponse r = convert(old, null, Map.of());

        assertThat(r.lint()).containsExactly("4절에 `### AC-n: 이름` 형식의 승인 기준이 없다");
        assertThat(r.files()).isEmpty();
    }

    @Test
    void lintsMissingKeywordScenarioStepsAndBadCapability() {
        String prd = """
            # PRD: x

            | 항목 | 값 |
            |---|---|
            | 스펙 영역 | 한글 영역 |

            ## 4. 승인 기준

            ### AC-1: 키워드 없음
            그냥 표시한다.

            #### 시나리오: 결과 없음
            - 동작: 연다

            ### AC-2: 시나리오 없음
            표시해야 한다(SHALL).
            """;

        assertThat(convert(prd, null, Map.of()).lint()).containsExactly(
            "AC-1 문장에 (SHALL) 또는 (MUST NOT)이 없다",
            "AC-1 시나리오 '결과 없음'에 결과가 없다",
            "AC-2 시나리오가 없다",
            "스펙 영역 '한글 영역'는 영문 kebab-case가 아니다");
    }

    @Test
    void techDocWithoutTaskItemsIsLinted() throws IOException {
        String tech = sample("sample-tech-be").replaceAll("(?s)## 8\\. 작업.*", "## 8. 작업\n\n");

        SpecExportResponse r = convert(sample("sample-prd-r1-new-format"), null, Map.of(TechArea.BE, tech));

        assertThat(r.lint()).containsExactly("BE 기술문서에 `## 8. 작업`의 `- [ ] 작업` 항목이 없다");
    }

    @Test
    void revisionTwoDiffsAgainstPreviousByAcNumber() throws IOException {
        SpecExportResponse r = convert(sample("sample-prd-r2-new-format"), sample("sample-prd-r1-new-format"), Map.of());

        assertThat(r.lint()).isEmpty();
        assertThat(r.changeName()).isEqualTo("prd-16b41143-r2");
        String spec = file(r, "specs/inventory-work-label/spec.md");
        assertThat(spec).doesNotContain("## Purpose")
            .contains("## ADDED Requirements", "### Requirement: AC-11 라벨 필터 응답 시간")
            .contains("## MODIFIED Requirements", "### Requirement: AC-8 ")
            .contains("## REMOVED Requirements", "### Requirement: AC-9 모든 지원 플랫폼 적용\n**Reason**")
            .contains("## RENAMED Requirements", "- FROM: `### Requirement: AC-5 라벨 미지정 전체 조회`",
                "- TO: `### Requirement: AC-5 라벨 필터 없는 전체 조회`");
        assertThat(file(r, "proposal.md")).contains("### Modified Capabilities");
    }

    @Test
    void renameAndBodyChangeOnTheSameAcEmitBoth() throws IOException {
        SpecExportResponse r = convert(sample("sample-prd-r3-rename-and-modify"), sample("sample-prd-r1-new-format"), Map.of());

        assertThat(r.lint()).isEmpty();
        String spec = file(r, "specs/inventory-work-label/spec.md");
        assertThat(spec).contains("## RENAMED Requirements").contains("## MODIFIED Requirements");
    }

    @Test
    void droppingAScenarioFromAModifiedRequirementIsLinted() throws IOException {
        SpecExportResponse r = convert(sample("sample-prd-r2-drops-scenario"), sample("sample-prd-r1-new-format"), Map.of());

        assertThat(r.lint()).hasSize(1);
        assertThat(r.lint().get(0)).startsWith("AC-8 시나리오 삭제(").contains("MODIFIED");
        assertThat(r.files()).isEmpty();
    }

    @Test
    void identicalRevisionProducesNoDeltaBlocks() throws IOException {
        String prd = sample("sample-prd-r1-new-format");

        SpecExportResponse r = convert(prd, prd, Map.of());

        assertThat(file(r, "specs/inventory-work-label/spec.md").strip()).isEmpty();
        assertThat(List.of("ADDED", "MODIFIED", "REMOVED", "RENAMED")).noneMatch(k -> file(r, "specs/inventory-work-label/spec.md").contains(k));
    }
}
