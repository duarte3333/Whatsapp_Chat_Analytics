package com.whatsappanalytics.whatsapp_analytics_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreditsResponseDto {
    private Integer remainingCredits;
    private Boolean hasLifetimeAccess;
}
