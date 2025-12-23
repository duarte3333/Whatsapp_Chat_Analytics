package com.whatsappanalytics.whatsapp_analytics_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequestDto {
    private String type; // "credits" or "lifetime"
    private Long amount; // Amount in cents
    private String packageId; // e.g., "credits-50"
    private Integer credits; // Number of credits (only for credits type)
}
