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
        
        if (currentUser == null) {
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
     * Get shared stats by token (public endpoint)
     * GET /shared/{token}
     * Auth: None (public)
     */
    @GetMapping("/shared/{token}")
    public ResponseEntity<?> getSharedStats(@PathVariable String token) {
        try {
            Map<String, Object> stats = shareLinkService.getSharedStats(token);
            return ResponseEntity.ok(stats);
        } catch (IllegalArgumentException e) {
            log.error("Error accessing shared stats: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error accessing shared stats: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to retrieve shared stats"));
        }
    }
}


