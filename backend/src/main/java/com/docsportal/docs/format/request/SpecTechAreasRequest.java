package com.docsportal.docs.format.request;

import java.util.List;

/**
 * PRD 필요 영역 선언. 값은 BE / FE / DATA.
 */
public class SpecTechAreasRequest {
    private List<String> areas;

    public SpecTechAreasRequest() {
    }

    public List<String> getAreas() {
        return areas;
    }

    public void setAreas(List<String> areas) {
        this.areas = areas;
    }
}
