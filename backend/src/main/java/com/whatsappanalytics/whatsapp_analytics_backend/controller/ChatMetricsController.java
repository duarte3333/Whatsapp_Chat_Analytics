package com.whatsappanalytics.whatsapp_analytics_backend.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
     * Validate uploaded ZIP file
     * @param file The multipart file to validate
     * @return Error message if validation fails, null if valid
     */
    private String validateZipFile(MultipartFile file) {
        // Check if file is empty
        if (file.isEmpty()) {
            return "File is empty";
        }
        
        // Check if file is a ZIP
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
            return "File must be a ZIP file";
        }
        
        // Check file size (5MB = 5 * 1024 * 1024 bytes)
        long maxSize = 5 * 1024 * 1024; // 5MB
        long fileSize = file.getSize();
        if (fileSize > maxSize) {
            return "File size exceeds 5MB limit";
        }
        
        // Check if ZIP contains _chat.txt file
        try (InputStream inputStream = file.getInputStream();
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            
            ZipEntry entry;
            boolean hasChatFile = false;
            
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                // Check for _chat.txt file (case-insensitive)
                if (entryName.toLowerCase().endsWith("_chat.txt")) {
                    hasChatFile = true;
                    break;
                }
                zipInputStream.closeEntry();
            }
            
            if (!hasChatFile) {
                return "The ZIP file does not contain a .txt file with the chat";
            }
            
        } catch (IOException e) {
            return "Error reading ZIP file: " + e.getMessage();
        }
        
        return null; // Validation passed
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
            // Validate file (checks size, type, and ZIP contents)
            String validationError = validateZipFile(file);
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
            // Validate file (checks size, type, and ZIP contents)
            String validationError = validateZipFile(file);
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

