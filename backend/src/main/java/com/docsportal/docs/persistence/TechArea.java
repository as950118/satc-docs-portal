package com.docsportal.docs.persistence;

import java.util.Arrays;
import java.util.Optional;

import com.docsportal.docs.common.Roles;

/**
 * 기술문서 영역 레지스트리. 영역, 문서 종류, 검토 역할의 대응은 여기 한 곳에만 둔다 (ARCH-0004 2단계).
 */
public enum TechArea {
    BE(SpecKind.TECH_BE, Roles.BACKEND),
    FE(SpecKind.TECH_FE, Roles.FRONTEND),
    DATA(SpecKind.TECH_DATA, Roles.DATA);

    private final SpecKind kind;
    private final String role;

    TechArea(SpecKind kind, String role) {
        this.kind = kind;
        this.role = role;
    }

    public SpecKind kind() {
        return kind;
    }

    public String role() {
        return role;
    }

    public static Optional<TechArea> of(SpecKind kind) {
        return Arrays.stream(values()).filter(a -> a.kind == kind).findFirst();
    }
}
