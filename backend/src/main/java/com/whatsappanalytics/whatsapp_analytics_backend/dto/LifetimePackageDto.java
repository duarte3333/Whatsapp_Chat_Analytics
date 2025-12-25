package com.whatsappanalytics.whatsapp_analytics_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LifetimePackageDto {
    private String id;
    private Integer priceInCents;
    private String displayPrice;
    private String name;
    private List<String> features;
}
