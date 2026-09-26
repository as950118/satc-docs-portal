package com.docsportal.docs.format.dto;

import java.util.List;

/**
 * 외부 검색 API 응답의 하이라이트 DTO
 */
public class ExternalSearchHighlight {
    
    private List<String> content;
    
    public ExternalSearchHighlight() {
    }
    
    public List<String> getContent() {
        return content;
    }
    
    public void setContent(List<String> content) {
        this.content = content;
    }
}
