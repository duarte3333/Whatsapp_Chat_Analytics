package com.whatsappanalytics.whatsapp_analytics_backend.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.whatsappanalytics.whatsapp_analytics_backend.model.User;
import com.whatsappanalytics.whatsapp_analytics_backend.service.ChatMetricsService;
import com.whatsappanalytics.whatsapp_analytics_backend.util.SecurityValidator;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/metrics")
@Slf4j
public class ChatMetricsController {

    private final ChatMetricsService chatMetricsService;

    public ChatMetricsController(ChatMetricsService chatMetricsService) {
        this.chatMetricsService = chatMetricsService;
    }

    /**
     * Validate uploaded file (ZIP or TXT) using SecurityValidator
     * @param file The multipart file to validate
     * @return Error message if validation fails, null if valid
     */
    private String validateChatFile(MultipartFile file) {
        return SecurityValidator.validateFile(file);
    }

    // Authenticated user uploads metrics
    @PostMapping("/chat/upload")
    public ResponseEntity<Map<String, Object>> uploadForUser(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) {

        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "User not authenticated"));
        }

        try {
            // Validate file (checks size, type, and ZIP contents if ZIP)
            String validationError = validateChatFile(file);
            if (validationError != null) {
                return ResponseEntity.badRequest().body(Map.of("error", validationError));
            }

            // Returns full stats including chart data + saved chat ID
            Map<String, Object> stats = chatMetricsService.processAndSaveMetrics(file, currentUser);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error processing file upload for user {}: {}", currentUser.getEmail(), e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }


    // Anonymous upload (no user)
    @PostMapping("/chat/upload-anonymous")
    public ResponseEntity<Map<String, Object>> uploadAnonymous(@RequestParam("file") MultipartFile file) {
        try {
            // Validate file (checks size, type, and ZIP contents if ZIP)
            String validationError = validateChatFile(file);
            if (validationError != null) {
                log.warn("File validation failed for anonymous upload: {}", validationError);
                return ResponseEntity.badRequest().body(Map.of("error", validationError));
            }

            Map<String, Object> stats = chatMetricsService.processAnonymousFile(file);
            
            // Add a temporary ID for the frontend
            stats.put("id", "guest-" + System.currentTimeMillis());
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error processing anonymous file upload: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
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

