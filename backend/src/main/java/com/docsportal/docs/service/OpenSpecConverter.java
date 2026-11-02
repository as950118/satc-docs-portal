package com.docsportal.docs.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.docsportal.docs.format.response.SpecExportResponse;
import com.docsportal.docs.persistence.TechArea;

/**
 * PRD 와 기술문서 본문(마크다운)을 OpenSpec change 폴더 파일로 바꾸는 순수 변환기. 저장소를 모른다.
 * 4절의 `### AC-n: 이름` 블록이 요구사항, `#### 시나리오:` 가 시나리오이고, 조건/동작/결과는 GIVEN/WHEN/THEN 으로 옮긴다.
 * 형식이 어긋나면 파일을 만들지 않고 lint 만 돌려준다.
 */
final class OpenSpecConverter {

    private static final Pattern H2 = Pattern.compile("^## (\\d+)\\.\\s*(.*)$");
    private static final Pattern AC = Pattern.compile("^### AC-(\\d+):\\s*(.+?)\\s*$");
    private static final Pattern SCENARIO = Pattern.compile("^#### (?:시나리오|Scenario):\\s*(.+?)\\s*$");
    private static final Pattern STEP_LINE = Pattern.compile("^- (조건|동작|결과|그리고):\\s*(.*)$");
    private static final Pattern NORMATIVE = Pattern.compile("(?<![A-Za-z])(SHALL|MUST)(?![A-Za-z])");
    private static final Pattern KEBAB = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*(/[a-z][a-z0-9]*(-[a-z0-9]+)*)*");
    private static final Map<String, String> STEP = Map.of("조건", "GIVEN", "동작", "WHEN", "결과", "THEN", "그리고", "AND");
    private static final int TASKS_SECTION = 8;

    record Scenario(String name, List<String> steps) {
    }

    record Criterion(int id, String title, String text, List<Scenario> scenarios) {
        String name() {
            return "AC-" + id + " " + title;
        }
    }

    private record Line(String text, boolean fenced) {
    }

    private record Document(List<String> head, Map<Integer, String> sections, Map<Integer, String> titles) {
        String section(int n) {
            return sections.getOrDefault(n, "");
        }
    }

    private record Criteria(String preamble, List<Criterion> list) {
    }

    private OpenSpecConverter() {
    }

    static SpecExportResponse convert(String rootId, int revision, String title, String prdBody, String prevBody,
                                      Map<TechArea, String> techBodies) {
        Document prd = parse(prdBody);
        Criteria criteria = parseCriteria(prd.section(4));
        List<String> lint = new ArrayList<>(lint(criteria.list()));
        String capability = meta(prd.head(), "스펙 영역");
        if (capability == null || capability.isBlank()) {
            capability = "prd-" + shortId(rootId);
        }
        if (!KEBAB.matcher(capability).matches()) {
            lint.add("스펙 영역 '" + capability + "'는 영문 kebab-case가 아니다");
        }
        List<Criterion> previous = prevBody == null ? null : parseCriteria(parse(prevBody).section(4)).list();
        String purpose = (prd.section(2) + (criteria.preamble().isEmpty() ? "" : "\n\n" + criteria.preamble())).strip();
        String specMd = previous == null
            ? "## Purpose\n\n" + purpose + "\n\n## ADDED Requirements\n\n" + renderAll(criteria.list())
            : delta(criteria.list(), previous, lint);

        String changeName = "prd-" + shortId(rootId) + "-r" + revision;
        String source = "<!-- docs-portal PRD " + rootId + " revision " + revision + " -->";
        String proposal = String.join("\n\n",
            "# " + title, source,
            "## Why\n\n" + prd.section(1),
            "## What Changes\n\n" + prd.section(2) + "\n\n### 비목표\n\n" + prd.section(3)
                + "\n\n### 범위\n\n" + demote(prd.section(5)),
            "## Capabilities\n\n### " + (previous == null ? "New" : "Modified") + " Capabilities\n- `" + capability + "`: " + title,
            "## Impact\n\n" + demote(prd.section(8))) + "\n";

        List<String> design = new ArrayList<>(List.of("# Design: " + title, source,
            "## PRD 맥락\n\n### 가정\n\n" + prd.section(6) + "\n\n### 비기능 요구사항\n\n" + prd.section(7)
                + "\n\n### 열린 질문\n\n" + prd.section(9)));
        List<String> tasks = new ArrayList<>(List.of("# Tasks", source));
        int group = 0;
        for (TechArea area : TechArea.values()) {
            String body = techBodies.get(area);
            if (body == null) {
                continue;
            }
            group++;
            Document tech = parse(body);
            design.add("## " + area + "\n\n" + tech.sections().keySet().stream()
                .filter(k -> k != TASKS_SECTION)
                .map(k -> "### " + k + ". " + tech.titles().get(k) + "\n\n" + demote(demote(tech.section(k))))
                .collect(Collectors.joining("\n\n")));
            List<String> items = tech.section(TASKS_SECTION).lines()
                .filter(l -> l.startsWith("- [ ] ") && !l.substring(6).isBlank())
                .map(l -> l.substring(6).strip()).toList();
            if (items.isEmpty()) {
                lint.add(area + " 기술문서에 `## 8. 작업`의 `- [ ] 작업` 항목이 없다");
            }
            StringBuilder block = new StringBuilder("## " + group + ". " + area + "\n\n");
            for (int i = 0; i < items.size(); i++) {
                block.append(i == 0 ? "" : "\n").append("- [ ] ").append(group).append('.').append(i + 1).append(' ').append(items.get(i));
            }
            tasks.add(block.toString());
        }

        if (!lint.isEmpty()) {
            return new SpecExportResponse(changeName, List.of(), List.copyOf(lint));
        }
        List<SpecExportResponse.File> files = new ArrayList<>();
        files.add(new SpecExportResponse.File("proposal.md", proposal));
        files.add(new SpecExportResponse.File("specs/" + capability + "/spec.md", specMd.stripTrailing() + "\n"));
        if (!techBodies.isEmpty()) {
            files.add(new SpecExportResponse.File("design.md", String.join("\n\n", design) + "\n"));
            files.add(new SpecExportResponse.File("tasks.md", String.join("\n\n", tasks) + "\n"));
        }
        return new SpecExportResponse(changeName, List.copyOf(files), List.of());
    }

    private static String shortId(String id) {
        return id.substring(0, Math.min(8, id.length()));
    }

    /** 코드 펜스 안의 줄에는 fenced 표시를 붙인다. 펜스 줄 자신도 fenced 다. */
    private static List<Line> lines(String md) {
        List<Line> out = new ArrayList<>();
        boolean fenced = false;
        for (String line : md.replace("\r\n", "\n").split("\n", -1)) {
            if (line.stripLeading().startsWith("```")) {
                fenced = !fenced;
            }
            out.add(new Line(line, fenced));
        }
        return out;
    }

    private static Document parse(String md) {
        List<String> head = new ArrayList<>();
        Map<Integer, List<String>> raw = new TreeMap<>();
        Map<Integer, String> titles = new TreeMap<>();
        List<String> current = null;
        for (Line line : lines(md)) {
            Matcher m = line.fenced() ? null : H2.matcher(line.text());
            if (m != null && m.matches()) {
                int n = Integer.parseInt(m.group(1));
                current = new ArrayList<>();
                raw.put(n, current);
                titles.put(n, m.group(2).strip());
            } else {
                (current == null ? head : current).add(line.text());
            }
        }
        Map<Integer, String> sections = new TreeMap<>();
        raw.forEach((k, v) -> sections.put(k, String.join("\n", v).strip()));
        return new Document(head, sections, titles);
    }

    private static String meta(List<String> head, String key) {
        for (String line : head) {
            String[] cells = line.strip().replaceAll("^\\||\\|$", "").split("\\|", -1);
            if (cells.length == 2 && cells[0].strip().equals(key)) {
                return cells[1].strip();
            }
        }
        return null;
    }

    private static Criteria parseCriteria(String section4) {
        List<String> preamble = new ArrayList<>();
        List<Criterion> done = new ArrayList<>();
        String title = null;
        int id = 0;
        List<String> text = new ArrayList<>();
        List<Scenario> scenarios = new ArrayList<>();
        String scenarioName = null;
        List<String> steps = null;
        boolean open = false;
        for (Line line : lines(section4)) {
            String s = line.text();
            if (!line.fenced() && s.startsWith("### ")) {
                Matcher m = AC.matcher(s);
                if (open && steps != null) {
                    scenarios.add(new Scenario(scenarioName, steps));
                    steps = null;
                }
                if (open) {
                    done.add(new Criterion(id, title, String.join("\n", text), List.copyOf(scenarios)));
                    open = false;
                }
                if (!m.matches()) {
                    return new Criteria(String.join("\n", preamble).strip(), done);
                }
                id = Integer.parseInt(m.group(1));
                title = m.group(2);
                text = new ArrayList<>();
                scenarios = new ArrayList<>();
                open = true;
                continue;
            }
            if (!open) {
                preamble.add(s);
                continue;
            }
            Matcher sc = line.fenced() ? null : SCENARIO.matcher(s);
            if (sc != null && sc.matches()) {
                if (steps != null) {
                    scenarios.add(new Scenario(scenarioName, steps));
                }
                scenarioName = sc.group(1);
                steps = new ArrayList<>();
                continue;
            }
            if (steps == null) {
                if (!s.isBlank()) {
                    text.add(s.strip());
                }
                continue;
            }
            Matcher st = STEP_LINE.matcher(s);
            if (st.matches()) {
                steps.add("- **" + STEP.get(st.group(1)) + "** " + st.group(2));
            } else if (!s.isBlank()) {
                steps.add(s.stripTrailing());
            }
        }
        if (open) {
            if (steps != null) {
                scenarios.add(new Scenario(scenarioName, steps));
            }
            done.add(new Criterion(id, title, String.join("\n", text), List.copyOf(scenarios)));
        }
        return new Criteria(String.join("\n", preamble).strip(), done);
    }

    private static List<String> lint(List<Criterion> criteria) {
        List<String> issues = new ArrayList<>();
        if (criteria.isEmpty()) {
            issues.add("4절에 `### AC-n: 이름` 형식의 승인 기준이 없다");
        }
        TreeSet<Integer> seen = new TreeSet<>();
        for (Criterion ac : criteria) {
            if (!seen.add(ac.id())) {
                issues.add("AC-" + ac.id() + " 번호가 중복된다");
            }
            if (ac.text().isEmpty()) {
                issues.add("AC-" + ac.id() + " 요구사항 문장이 없다");
            } else if (!NORMATIVE.matcher(ac.text()).find()) {
                issues.add("AC-" + ac.id() + " 문장에 (SHALL) 또는 (MUST NOT)이 없다");
            }
            if (ac.scenarios().isEmpty()) {
                issues.add("AC-" + ac.id() + " 시나리오가 없다");
            }
            for (Scenario s : ac.scenarios()) {
                String joined = String.join("\n", s.steps());
                if (!joined.contains("**WHEN**") && !joined.contains("**GIVEN**")) {
                    issues.add("AC-" + ac.id() + " 시나리오 '" + s.name() + "'에 조건/동작이 없다");
                }
                if (!joined.contains("**THEN**")) {
                    issues.add("AC-" + ac.id() + " 시나리오 '" + s.name() + "'에 결과가 없다");
                }
            }
        }
        return issues;
    }

    private static String render(Criterion ac) {
        StringBuilder out = new StringBuilder("### Requirement: " + ac.name() + "\n" + ac.text() + "\n\n");
        for (Scenario s : ac.scenarios()) {
            out.append("#### Scenario: ").append(s.name()).append('\n').append(String.join("\n", s.steps())).append("\n\n");
        }
        return out.toString().stripTrailing() + "\n";
    }

    private static String renderAll(Collection<Criterion> criteria) {
        return criteria.stream().map(OpenSpecConverter::render).collect(Collectors.joining("\n"));
    }

    /** 직전 revision 과 AC 번호로 짝을 맞춘 ADDED/MODIFIED/REMOVED/RENAMED. 시나리오를 잃는 MODIFIED 는 lint 다. */
    private static String delta(List<Criterion> current, List<Criterion> previous, List<String> lint) {
        Map<Integer, Criterion> old = byId(previous);
        Map<Integer, Criterion> now = byId(current);
        List<Criterion> added = new ArrayList<>();
        List<Criterion> modified = new ArrayList<>();
        List<Criterion> removed = new ArrayList<>();
        List<String> renamed = new ArrayList<>();
        now.forEach((id, n) -> {
            Criterion o = old.get(id);
            if (o == null) {
                added.add(n);
                return;
            }
            if (!o.name().equals(n.name())) {
                renamed.add("- FROM: `### Requirement: " + o.name() + "`\n- TO: `### Requirement: " + n.name() + "`");
            }
            if (!o.text().equals(n.text()) || !o.scenarios().equals(n.scenarios())) {
                TreeSet<String> dropped = o.scenarios().stream().map(Scenario::name).collect(Collectors.toCollection(TreeSet::new));
                n.scenarios().forEach(s -> dropped.remove(s.name()));
                if (!dropped.isEmpty()) {
                    lint.add("AC-" + id + " 시나리오 삭제(" + String.join(", ", dropped)
                        + ")는 OpenSpec MODIFIED로 표현할 수 없다. 새 AC 번호로 옮기거나 시나리오를 남겨야 한다");
                }
                modified.add(n);
            }
        });
        old.forEach((id, o) -> {
            if (!now.containsKey(id)) {
                removed.add(o);
            }
        });
        List<String> out = new ArrayList<>();
        if (!added.isEmpty()) {
            out.add("## ADDED Requirements\n\n" + renderAll(added));
        }
        if (!modified.isEmpty()) {
            out.add("## MODIFIED Requirements\n\n" + renderAll(modified));
        }
        if (!removed.isEmpty()) {
            out.add("## REMOVED Requirements\n\n" + removed.stream()
                .map(a -> "### Requirement: " + a.name() + "\n**Reason**: PRD 새 버전에서 삭제\n").collect(Collectors.joining("\n")));
        }
        if (!renamed.isEmpty()) {
            out.add("## RENAMED Requirements\n\n" + String.join("\n", renamed));
        }
        return String.join("\n\n", out);
    }

    private static Map<Integer, Criterion> byId(List<Criterion> criteria) {
        Map<Integer, Criterion> map = new TreeMap<>();
        criteria.forEach(c -> map.putIfAbsent(c.id(), c));
        return map;
    }

    private static String demote(String md) {
        return lines(md).stream().map(l -> !l.fenced() && l.text().startsWith("#") ? "#" + l.text() : l.text())
            .collect(Collectors.joining("\n"));
    }
}
