package com.whatsappanalytics.whatsapp_analytics_backend.responses;

import com.whatsappanalytics.whatsapp_analytics_backend.model.User;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginResponse {
    private String token;
    private long expiresIn;
    private User user;

    public LoginResponse(String token, long expiresIn, User user) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.user = user;
    }    
}
