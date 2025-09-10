package com.okestro.docs.format.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 외부 검색 API 응답을 받기 위한 DTO
 */
public class ExternalSearchResponse {
    
    private String query;
    
    @JsonProperty("total_results")
    private Integer totalResults;
    
    @JsonProperty("returned_results")
    private Integer returnedResults;
    
    @JsonProperty("max_score")
    private Double maxScore;
    
    private List<ExternalSearchResultItem> results;
    
    public ExternalSearchResponse() {
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
    
    public List<ExternalSearchResultItem> getResults() {
        return results;
    }
    
    public void setResults(List<ExternalSearchResultItem> results) {
        this.results = results;
    }
}
