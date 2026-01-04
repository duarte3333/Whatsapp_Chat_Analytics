package com.whatsappanalytics.whatsapp_analytics_backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequestDto {
    private String email;
    private String verificationCode;
    private String newPassword;
}



