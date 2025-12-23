package com.whatsappanalytics.whatsapp_analytics_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutSessionResponseDto {
    private String sessionId;
    private String url;
}
