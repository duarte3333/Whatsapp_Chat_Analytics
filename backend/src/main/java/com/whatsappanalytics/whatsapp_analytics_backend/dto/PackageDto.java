package com.whatsappanalytics.whatsapp_analytics_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PackageDto {
    private String id;
    private Integer credits;
    private Integer priceInCents;
    private String displayPrice;
    private Boolean popular;
}
