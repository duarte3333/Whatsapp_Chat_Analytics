package com.whatsappanalytics.whatsapp_analytics_backend.service;

import com.whatsappanalytics.whatsapp_analytics_backend.dto.ShareLinkResponseDto;
import com.whatsappanalytics.whatsapp_analytics_backend.model.GroupChat;
import com.whatsappanalytics.whatsapp_analytics_backend.model.ShareLink;
import com.whatsappanalytics.whatsapp_analytics_backend.model.User;
import com.whatsappanalytics.whatsapp_analytics_backend.repository.GroupChatRepository;
import com.whatsappanalytics.whatsapp_analytics_backend.repository.ShareLinkRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ShareLinkService {

    private final ShareLinkRepository shareLinkRepository;
    private final GroupChatRepository groupChatRepository;
    private final ChatMetricsService chatMetricsService;
    private static final SecureRandom random = new SecureRandom();
    private static final int TOKEN_LENGTH = 32;

    public ShareLinkService(
            ShareLinkRepository shareLinkRepository,
            GroupChatRepository groupChatRepository,
            ChatMetricsService chatMetricsService) {
        this.shareLinkRepository = shareLinkRepository;
        this.groupChatRepository = groupChatRepository;
        this.chatMetricsService = chatMetricsService;
    }

    /**
     * Create a share link for a group chat
     * @param groupId The group chat ID
     * @param user The user creating the share link (must own the group)
     * @param expiresInDays Optional expiration in days (null for no expiration)
     * @return ShareLinkResponseDto with token and expiration
     */
    @Transactional
    public ShareLinkResponseDto createShareLink(Long groupId, User user, Integer expiresInDays) {
        // Verify user owns the group
        GroupChat groupChat = groupChatRepository.findByIdAndUserId(groupId, user.getId());
        if (groupChat == null) {
            throw new IllegalArgumentException("Group chat not found or user does not have access");
        }

        // Generate unique token
        String token = generateUniqueToken();

        // Calculate expiration if provided
        LocalDateTime expiresAt = null;
        if (expiresInDays != null && expiresInDays > 0) {
            expiresAt = LocalDateTime.now().plusDays(expiresInDays);
        }

        // Create share link
        ShareLink shareLink = new ShareLink();
        shareLink.setToken(token);
        shareLink.setGroupId(groupId);
        shareLink.setUserId(user.getId());
        shareLink.setCreatedAt(LocalDateTime.now());
        shareLink.setExpiresAt(expiresAt);
        shareLink.setViewCount(0);

        shareLinkRepository.save(shareLink);

        log.info("Created share link for group {} by user {}", groupId, user.getId());

        return new ShareLinkResponseDto(token, expiresAt);
    }

    /**
     * Get stats for a shared link (public access)
     * @param token The share token
     * @return Map with all stats data (same format as /metrics/chat/{id})
     */
    @Transactional
    public Map<String, Object> getSharedStats(String token) {
        Optional<ShareLink> shareLinkOpt = shareLinkRepository.findByToken(token);
        
        if (shareLinkOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid share token");
        }

        ShareLink shareLink = shareLinkOpt.get();

        // Check if expired
        if (shareLink.getExpiresAt() != null && LocalDateTime.now().isAfter(shareLink.getExpiresAt())) {
            throw new IllegalArgumentException("Share link has expired");
        }

        // Increment view count
        shareLink.setViewCount(shareLink.getViewCount() + 1);
        shareLinkRepository.save(shareLink);

        // Get stats (without user verification since it's public)
        Map<String, Object> stats = chatMetricsService.getStatsByGroupId(shareLink.getGroupId(), null);
        
        if (stats == null) {
            throw new IllegalArgumentException("Stats not found for this share link");
        }

        log.info("Accessed shared stats for group {} via token (view count: {})", 
                 shareLink.getGroupId(), shareLink.getViewCount());

        return stats;
    }

    /**
     * Generate a unique token
     */
    private String generateUniqueToken() {
        String token;
        do {
            byte[] randomBytes = new byte[TOKEN_LENGTH];
            random.nextBytes(randomBytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        } while (shareLinkRepository.findByToken(token).isPresent());
        
        return token;
    }
}


