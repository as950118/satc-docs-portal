package com.docsportal.docs.format.request;

/**
 * 기술문서 생성. area 는 BE / FE / DATA.
 */
public class SpecTechDocRequest {
    private String area;
    private String title;
    private String body;

    public SpecTechDocRequest() {
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
