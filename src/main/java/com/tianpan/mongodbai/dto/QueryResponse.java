package com.tianpan.mongodbai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class QueryResponse {
    
    @JsonProperty("result")
    private String result;
    
    @JsonProperty("success")
    private boolean success;
    
    @JsonProperty("message")
    private String message;
    
    public QueryResponse() {}
    
    public QueryResponse(String result, boolean success, String message) {
        this.result = result;
        this.success = success;
        this.message = message;
    }
    
    public static QueryResponse success(String result) {
        return new QueryResponse(result, true, "查询成功");
    }
    
    public static QueryResponse error(String message) {
        return new QueryResponse(null, false, message);
    }
    
    public String getResult() {
        return result;
    }
    
    public void setResult(String result) {
        this.result = result;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
} 