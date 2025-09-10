package com.okestro.docs.common;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;

public class RestResponse<T> {
    private String responseTime;
    private String errorMessage;
    private T data;
    private int status;

    public RestResponse() {
    }

    public RestResponse(String responseTime, String errorMessage, T data, int status) {
        this.responseTime = responseTime;
        this.errorMessage = errorMessage;
        this.data = data;
        this.status = status;
    }


    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getResponseTime() {
        return responseTime;
    }

    public void setResponseTime(String responseTime) {
        this.responseTime = responseTime;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public static <T> RestResponse<T> responseOk(T data) {
        return new RestResponse<>(LocalDateTime.now().toString(), null, data, HttpStatus.OK.value());
    }

    public static RestResponse<String> responseError(String errorMessage, HttpStatus status) {
        return new RestResponse<>(LocalDateTime.now().toString(), errorMessage, null, status.value());
    }
}