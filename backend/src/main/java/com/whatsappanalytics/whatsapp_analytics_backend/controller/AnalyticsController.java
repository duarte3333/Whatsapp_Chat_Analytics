package com.whatsappanalytics.whatsapp_analytics_backend.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.whatsappanalytics.whatsapp_analytics_backend.service.AnalyticsService;


@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @PostMapping("/upload-anonymous")
    public ResponseEntity<Map<String, Object>> uploadAnonymous(@RequestParam("file") MultipartFile file) {
        // minimal validation
        if (file.isEmpty() || !file.getOriginalFilename().endsWith(".zip")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid file"));
        }

        // call service
        Map<String, Object> stats = analyticsService.processAnonymousFile(file);
        return ResponseEntity.ok(stats);
    }
}
