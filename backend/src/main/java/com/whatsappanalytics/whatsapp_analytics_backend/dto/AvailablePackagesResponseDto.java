package com.whatsappanalytics.whatsapp_analytics_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailablePackagesResponseDto {
    private List<PackageDto> creditPackages;
    private LifetimePackageDto lifetimePackage;
}
