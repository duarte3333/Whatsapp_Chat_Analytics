package com.whatsappanalytics.whatsapp_analytics_backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.whatsappanalytics.whatsapp_analytics_backend.model.ChatMetrics;
import com.whatsappanalytics.whatsapp_analytics_backend.model.User;
import com.whatsappanalytics.whatsapp_analytics_backend.service.ChatMetricsService;

@RestController
@RequestMapping("/metrics")
public class ChatMetricsController {

    private final ChatMetricsService chatMetricsService;

    public ChatMetricsController(ChatMetricsService chatMetricsService) {
        this.chatMetricsService = chatMetricsService;
    }

    // Authenticated user uploads metrics
    // @PostMapping("/chat/{chatId}/upload")
    // public ResponseEntity<ChatMetrics> uploadForUser(
    //         @PathVariable Long chatId,
    //         @RequestParam("file") MultipartFile file,
    //         @AuthenticationPrincipal User currentUser) {

    //     if (file.isEmpty() || !file.getOriginalFilename().endsWith(".zip")) {
    //         return ResponseEntity.badRequest().build();
    //     }

    //     ChatMetrics saved = chatMetricsService.processAndSaveMetrics(file, chatId, currentUser);
    //     return ResponseEntity.ok(saved);
    // }

    // Anonymous upload (no user)
    @PostMapping("/chat/upload-anonymous")
    public ResponseEntity<Map<String, Object>> uploadAnonymous(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || !file.getOriginalFilename().endsWith(".zip")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid file"));
        }

        // Call service to send file to Python microservice
        Map<String, Object> stats = chatMetricsService.processAnonymousFile(file);
        return ResponseEntity.ok(stats);
    }

    // // Get metrics for a chat
    // @GetMapping("/chat/{chatId}")
    // public ResponseEntity<List<ChatMetrics>> getMetrics(@PathVariable Long chatId) {
    //     List<ChatMetrics> metrics = chatMetricsService.getMetricsByChatId(chatId);
    //     return ResponseEntity.ok(metrics);
    // }

}

