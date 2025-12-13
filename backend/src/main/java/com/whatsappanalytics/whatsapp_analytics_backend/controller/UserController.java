package com.whatsappanalytics.whatsapp_analytics_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.whatsappanalytics.whatsapp_analytics_backend.model.ChatMetrics;
import com.whatsappanalytics.whatsapp_analytics_backend.model.GroupChat;
import com.whatsappanalytics.whatsapp_analytics_backend.model.User;
import com.whatsappanalytics.whatsapp_analytics_backend.repository.ChatMetricsRepository;
import com.whatsappanalytics.whatsapp_analytics_backend.repository.GroupChatRepository;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequestMapping("/users")
@RestController
public class UserController {

    private final GroupChatRepository groupChatRepository;
    private final ChatMetricsRepository chatMetricsRepository;

    public UserController(GroupChatRepository groupChatRepository, ChatMetricsRepository chatMetricsRepository) {
        this.groupChatRepository = groupChatRepository;
        this.chatMetricsRepository = chatMetricsRepository;
    }

    @GetMapping
    public ResponseEntity<User> authenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) authentication.getPrincipal();
        return ResponseEntity.ok(currentUser);
    }

    @GetMapping("/")
    public ResponseEntity<?> getAllUsers() {
        // Implement your existing logic for fetching all users
        return ResponseEntity.ok().build();
    }

   @GetMapping("/groups")
    public ResponseEntity<List<Map<String, Object>>> getUserGroups() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) authentication.getPrincipal();

        List<GroupChat> userChats = groupChatRepository.findByUserId(currentUser.getId());

        List<Map<String, Object>> groups = userChats.stream()
        .map(chat -> {
            ChatMetrics metrics = chatMetricsRepository.findByGroupChatId(chat.getId());

            Map<String, Object> groupMap = new HashMap<>();
            groupMap.put("id", chat.getId());
            groupMap.put("name", chat.getTitle());
            groupMap.put("memberCount", chat.getParticipantsCount());
            groupMap.put("messageCount", metrics != null ? metrics.getTotalMessages() : 0);
            groupMap.put("lastAnalyzed", metrics != null ? metrics.getLastUpdated().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) : "N/A");

            return groupMap;
        })
        .toList();

        
        return ResponseEntity.ok(groups);
    }

    @DeleteMapping("/groups/{groupId}")
    public ResponseEntity<?> deleteGroup(@PathVariable Long groupId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) authentication.getPrincipal();
        
        // Find the group
        GroupChat group = groupChatRepository.findById(groupId).orElse(null);
        
        if (group == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Check if user owns this group
        if (!group.getUserId().equals(currentUser.getId())) {
            return ResponseEntity.status(403).body("You don't own this group");
        }
        
        // Delete associated metrics first
        ChatMetrics metrics = chatMetricsRepository.findByGroupChatId(groupId);
        if (metrics != null) {
            chatMetricsRepository.delete(metrics);
        }
        
        // Delete the group
        groupChatRepository.delete(group);
        
        return ResponseEntity.ok().build();
    }
    }
