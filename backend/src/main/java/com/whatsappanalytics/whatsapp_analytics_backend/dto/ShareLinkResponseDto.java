package com.whatsappanalytics.whatsapp_analytics_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShareLinkResponseDto {
    private String shareToken;
    private LocalDateTime expiresAt;
}


