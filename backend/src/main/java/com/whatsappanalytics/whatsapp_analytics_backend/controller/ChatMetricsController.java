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
    @PostMapping("/chat/upload")
    public ResponseEntity<Map<String, Object>> uploadForUser(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {

        if (file.isEmpty() || !file.getOriginalFilename().endsWith(".zip")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid file"));
        }

        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "User not authenticated"));
        }

        try {
            // Returns full stats including chart data + saved chat ID
            Map<String, Object> stats = chatMetricsService.processAndSaveMetrics(file, currentUser);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            System.err.println("Error processing file upload: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }


    // Anonymous upload (no user)
    @PostMapping("/chat/upload-anonymous")
    public ResponseEntity<Map<String, Object>> uploadAnonymous(@RequestParam("file") MultipartFile file) {
        System.out.println("Test Controller");
        if (file.isEmpty() || !file.getOriginalFilename().endsWith(".zip")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid file"));
        }

        Map<String, Object> stats = chatMetricsService.processAnonymousFile(file);
        
        // Add a temporary ID for the frontend
        stats.put("id", "guest-" + System.currentTimeMillis());
        
        return ResponseEntity.ok(stats);
    }

    // Get metrics for a chat (authenticated users)
    @GetMapping("/chat/{chatId}")
    public ResponseEntity<Map<String, Object>> getMetrics(
            @PathVariable Long chatId,
            @AuthenticationPrincipal User currentUser) {
        
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "User not authenticated"));
        }

        Map<String, Object> stats = chatMetricsService.getStatsByGroupId(chatId, currentUser.getId());
        
        if (stats == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(stats);
    }

}

