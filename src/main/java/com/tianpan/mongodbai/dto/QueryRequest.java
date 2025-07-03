package com.tianpan.mongodbai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class QueryRequest {
    
    @JsonProperty("query")
    private String query;
    
    public QueryRequest() {}
    
    public QueryRequest(String query) {
        this.query = query;
    }
    
    public String getQuery() {
        return query;
    }
    
    public void setQuery(String query) {
        this.query = query;
    }
} 