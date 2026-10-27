package com.docsportal.docs.persistence;

/**
 * 스펙 문서 종류와 그 양식 파일 (ARCH-0004). 기술문서 종류와 영역의 대응은 TechArea 가 갖는다.
 */
public enum SpecKind {
    PRD("spec-templates/prd.md"),
    TECH_BE("spec-templates/tech-be.md"),
    TECH_FE("spec-templates/tech-fe.md"),
    TECH_DATA("spec-templates/tech-data.md");

    private final String templatePath;

    SpecKind(String templatePath) {
        this.templatePath = templatePath;
    }

    public String templatePath() {
        return templatePath;
    }
}
