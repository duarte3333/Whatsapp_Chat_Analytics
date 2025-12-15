package com.whatsappanalytics.whatsapp_analytics_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.whatsappanalytics.whatsapp_analytics_backend.model.ChatMetrics;
import com.whatsappanalytics.whatsapp_analytics_backend.model.GroupChat;
import com.whatsappanalytics.whatsapp_analytics_backend.model.User;
import com.whatsappanalytics.whatsapp_analytics_backend.repository.ChatMetricsRepository;
import com.whatsappanalytics.whatsapp_analytics_backend.repository.GroupChatRepository;
import com.whatsappanalytics.whatsapp_analytics_backend.repository.UserRepository;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

@RequestMapping("/users")
@RestController
public class UserController {

    private final GroupChatRepository groupChatRepository;
    private final ChatMetricsRepository chatMetricsRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(GroupChatRepository groupChatRepository, ChatMetricsRepository chatMetricsRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.groupChatRepository = groupChatRepository;
        this.chatMetricsRepository = chatMetricsRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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

    @PutMapping("/{userId}/password")
    public ResponseEntity<?> updatePassword(
            @PathVariable Long userId,
            @RequestBody Map<String, String> passwordRequest,
            @org.springframework.security.core.annotation.AuthenticationPrincipal User currentUser) {
        
        if (currentUser == null) {
            return ResponseEntity.status(401).body("User not authenticated");
        }
        
        // Verify the user is updating their own password
        if (!currentUser.getId().equals(userId)) {
            return ResponseEntity.status(403).body("You can only update your own password");
        }
        
        String currentPassword = passwordRequest.get("currentPassword");
        String newPassword = passwordRequest.get("newPassword");
        
        if (currentPassword == null || newPassword == null) {
            return ResponseEntity.badRequest().body("Current password and new password are required");
        }
        
        if (currentPassword.trim().isEmpty() || newPassword.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Passwords cannot be empty");
        }
        
        if (newPassword.length() < 8) {
            return ResponseEntity.badRequest().body("New password must be at least 8 characters");
        }
        
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }
            
            // Verify current password
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                return ResponseEntity.status(401).body("Current password is incorrect");
            }
            
            // Update password
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            
            System.out.println("Password updated successfully for user: " + userId);
            return ResponseEntity.ok().body(Map.of("message", "Password updated successfully"));
        } catch (Exception e) {
            System.err.println("Error updating password: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Failed to update password: " + e.getMessage());
        }
    }

    @Transactional
    @DeleteMapping
    public ResponseEntity<?> deleteAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) authentication.getPrincipal();
        
        if (currentUser == null) {
            return ResponseEntity.status(401).body("User not authenticated");
        }

        try {
            Long userId = currentUser.getId();
            System.out.println("=== DELETE ACCOUNT REQUEST ===");
            System.out.println("User ID: " + userId);
            
            // Get all groups for this user
            List<GroupChat> userGroups = groupChatRepository.findByUserId(userId);
            System.out.println("Found " + userGroups.size() + " groups for user");
            
            // Delete metrics for each group
            for (GroupChat group : userGroups) {
                System.out.println("Deleting metrics for group: " + group.getId());
                ChatMetrics metrics = chatMetricsRepository.findByGroupChatId(group.getId());
                if (metrics != null) {
                    chatMetricsRepository.delete(metrics);
                    System.out.println("Deleted metrics for group: " + group.getId());
                }
            }
            
            // Delete all groups for this user
            System.out.println("Deleting all groups for user: " + userId);
            groupChatRepository.deleteByUserId(userId);
            
            // Delete the user account itself
            System.out.println("Deleting user account: " + userId);
            userRepository.deleteById(userId);
            System.out.println("Account deletion complete for user: " + userId);
            
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            System.err.println("Error deleting account: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Failed to delete account: " + e.getMessage());
        }
    }
}
