package com.okestro.docs.format.response;

import java.util.List;

/**
 * 프론트엔드로 전달할 검색 결과 응답 DTO
 * 외부 검색 API 결과에서 필요한 필드만 추출하여 전달
 */
public class SearchResultResponse {
    
    private String query;
    private Integer totalResults;
    private Integer returnedResults;
    private Double maxScore;
    private List<SearchResultItem> results;
    
    public SearchResultResponse() {
    }
    
    public SearchResultResponse(String query, Integer totalResults, Integer returnedResults, 
                              Double maxScore, List<SearchResultItem> results) {
        this.query = query;
        this.totalResults = totalResults;
        this.returnedResults = returnedResults;
        this.maxScore = maxScore;
        this.results = results;
    }
    
    public String getQuery() {
        return query;
    }
    
    public void setQuery(String query) {
        this.query = query;
    }
    
    public Integer getTotalResults() {
        return totalResults;
    }
    
    public void setTotalResults(Integer totalResults) {
        this.totalResults = totalResults;
    }
    
    public Integer getReturnedResults() {
        return returnedResults;
    }
    
    public void setReturnedResults(Integer returnedResults) {
        this.returnedResults = returnedResults;
    }
    
    public Double getMaxScore() {
        return maxScore;
    }
    
    public void setMaxScore(Double maxScore) {
        this.maxScore = maxScore;
    }
    
    public List<SearchResultItem> getResults() {
        return results;
    }
    
    public void setResults(List<SearchResultItem> results) {
        this.results = results;
    }
}
