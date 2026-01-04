package com.whatsappanalytics.whatsapp_analytics_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.whatsappanalytics.whatsapp_analytics_backend.dto.ShareLinkResponseDto;
import com.whatsappanalytics.whatsapp_analytics_backend.model.User;
import com.whatsappanalytics.whatsapp_analytics_backend.service.ShareLinkService;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
public class ShareController {

    private final ShareLinkService shareLinkService;

    public ShareController(ShareLinkService shareLinkService) {
        this.shareLinkService = shareLinkService;
    }

    /**
     * Create a share link for a group chat
     * POST /groups/{groupId}/share
     * Auth: Required (user must own the group)
     */
    @PostMapping("/groups/{groupId}/share")
    public ResponseEntity<?> createShareLink(
            @PathVariable Long groupId,
            @RequestBody(required = false) Map<String, Integer> request,
            @AuthenticationPrincipal User currentUser) {
        
        log.info("===== CREATE SHARE LINK REQUEST =====");
        log.info("Group ID: {}", groupId);
        log.info("Current user: {}", currentUser != null ? currentUser.getEmail() : "null");
        log.info("Request body: {}", request);
        
        if (currentUser == null) {
            log.error("User not authenticated for share link creation");
            return ResponseEntity.status(401).body(Map.of("error", "User not authenticated"));
        }

        try {
            // Extract expiration days from request (optional)
            Integer expiresInDays = null;
            if (request != null && request.containsKey("expiresInDays")) {
                expiresInDays = request.get("expiresInDays");
            }

            ShareLinkResponseDto response = shareLinkService.createShareLink(groupId, currentUser, expiresInDays);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Error creating share link: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error creating share link: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to create share link"));
        }
    }

    /**
     * Create a share link for anonymous/guest stats
     * POST /shared/guest
     * Auth: None (public)
     */
    @PostMapping("/shared/guest")
    public ResponseEntity<?> createGuestShareLink(@RequestBody Map<String, Object> request) {
        try {
            log.info("Received guest share link request with keys: {}", request.keySet());
            
            // Extract stats data - check if wrapped in "stats" key or sent directly
            @SuppressWarnings("unchecked")
            Map<String, Object> stats = (Map<String, Object>) request.get("stats");
            
            // If stats is not found in "stats" key, assume the entire request is the stats
            if (stats == null || stats.isEmpty()) {
                // Check if request contains stats-like keys (e.g., "title", "total_messages", etc.)
                if (request.containsKey("title") || request.containsKey("total_messages") || request.containsKey("dailyMessages")) {
                    stats = request; // Use the entire request as stats
                } else {
                    log.error("No stats data found in request. Request keys: {}", request.keySet());
                    return ResponseEntity.badRequest().body(Map.of("error", "Stats data is required"));
                }
            }
            
            // Extract expiration days from request (optional)
            Integer expiresInDays = null;
            if (request.containsKey("expiresInDays")) {
                Object expDays = request.get("expiresInDays");
                if (expDays instanceof Integer) {
                    expiresInDays = (Integer) expDays;
                } else if (expDays instanceof Number) {
                    expiresInDays = ((Number) expDays).intValue();
                }
            }

            if (stats == null || stats.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Stats data is required"));
            }

            log.info("Creating guest share link with stats containing {} keys", stats.size());
            ShareLinkResponseDto response = shareLinkService.createGuestShareLink(stats, expiresInDays);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Error creating guest share link: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error creating guest share link: {}", e.getMessage(), e);
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to create share link: " + e.getMessage()));
        }
    }

    /**
     * Get shared stats by token (public endpoint)
     * GET /shared/{token}
     * Auth: None (public)
     */
    @GetMapping("/shared/{token}")
    public ResponseEntity<?> getSharedStats(@PathVariable String token) {
        log.info("===== GET SHARED STATS REQUEST =====");
        log.info("Token: {}", token);
        log.info("Request URI: /shared/{}", token);
        try {
            Map<String, Object> stats = shareLinkService.getSharedStats(token);
            log.info("Successfully retrieved shared stats for token: {}", token);
            return ResponseEntity.ok(stats);
        } catch (IllegalArgumentException e) {
            log.error("Error accessing shared stats for token {}: {}", token, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error accessing shared stats for token {}: {}", token, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to retrieve shared stats"));
        }
    }
}


