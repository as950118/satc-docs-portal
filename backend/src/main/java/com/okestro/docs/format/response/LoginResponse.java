package com.okestro.docs.format.response;

public class LoginResponse {
    private String token;
    private UserInfoResponse userInfo;
    private Long expiresIn; // 토큰 만료 시간 (초)

    public LoginResponse() {}

    public LoginResponse(String token, UserInfoResponse userInfo, Long expiresIn) {
        this.token = token;
        this.userInfo = userInfo;
        this.expiresIn = expiresIn;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UserInfoResponse getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(UserInfoResponse userInfo) {
        this.userInfo = userInfo;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }

    @Override
    public String toString() {
        return "LoginResponse{" +
                "token='[PROTECTED]'" +
                ", userInfo=" + userInfo +
                ", expiresIn=" + expiresIn +
                '}';
    }
}
