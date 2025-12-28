package com.whatsappanalytics.whatsapp_analytics_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.whatsappanalytics.whatsapp_analytics_backend.dto.ChatRequestDto;
import com.whatsappanalytics.whatsapp_analytics_backend.model.User;
import com.whatsappanalytics.whatsapp_analytics_backend.service.ChatLLMService;
import com.whatsappanalytics.whatsapp_analytics_backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;

@RestController
@RequestMapping("/chat-llm")
@Slf4j
public class ChatLLMController {

    private final ChatLLMService chatLLMService;
    private final UserRepository userRepository;

    public ChatLLMController(ChatLLMService chatLLMService, UserRepository userRepository) {
        this.chatLLMService = chatLLMService;
        this.userRepository = userRepository;
    }

    @PostMapping("/{chatId}")
    public ResponseEntity<?> analyzeChat(
            @PathVariable Long chatId,
            @RequestBody ChatRequestDto request,
            @AuthenticationPrincipal User currentUser
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body("User not authenticated");
        }

        try {
            var response = chatLLMService.analyzeChat(chatId, currentUser.getId(), request.question, currentUser);
            return ResponseEntity.ok(response);
        } catch (ChatLLMService.InsufficientCreditsException e) {
            log.warn("User {} attempted to use chat without credits", currentUser.getId());
            return ResponseEntity.status(402).body("Insufficient credits. Please purchase credits to continue.");
        } catch (Exception e) {
            log.error("Error analyzing chat", e);
            return ResponseEntity.status(500).body("Error processing your request");
        }
    }

    @GetMapping("/credits")
    public ResponseEntity<?> getCredits(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body("User not authenticated");
        }

        try {
            User user = userRepository.findById(currentUser.getId()).orElse(null);
            if (user == null) {
                return ResponseEntity.status(404).body("User not found");
            }

            int remainingCredits = user.getRemainingCredits() != null ? user.getRemainingCredits() : 0;
            boolean hasLifetimeAccess = user.getHasLifetimeAccess() != null && user.getHasLifetimeAccess();

            return ResponseEntity.ok(Map.of(
                "remainingCredits", remainingCredits,
                "hasLifetimeAccess", hasLifetimeAccess,
                "email", user.getEmail()
            ));
        } catch (Exception e) {
            log.error("Error fetching credits for user {}", currentUser.getId(), e);
            return ResponseEntity.status(500).body("Error fetching credits");
        }
    }

    @PostMapping("/credits/deduct")
    public ResponseEntity<?> deductCredits(
            @RequestBody Map<String, Integer> request,
            @AuthenticationPrincipal User currentUser
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(401).body("User not authenticated");
        }

        try {
            Integer amount = request.get("amount");
            if (amount == null || amount <= 0) {
                return ResponseEntity.status(400).body("Invalid amount. Must be greater than 0.");
            }

            User user = userRepository.findById(currentUser.getId()).orElse(null);
            if (user == null) {
                return ResponseEntity.status(404).body("User not found");
            }

            // Check if user has lifetime access - if yes, don't deduct
            if (user.getHasLifetimeAccess() != null && user.getHasLifetimeAccess()) {
                log.info("User {} has lifetime access, no credit deduction needed", currentUser.getId());
                return ResponseEntity.ok(Map.of(
                    "remainingCredits", user.getRemainingCredits() != null ? user.getRemainingCredits() : 0,
                    "hasLifetimeAccess", true,
                    "message", "Lifetime access active - no credits deducted"
                ));
            }

            // Check if user has enough credits
            int currentCredits = user.getRemainingCredits() != null ? user.getRemainingCredits() : 0;
            if (currentCredits < amount) {
                log.warn("User {} attempted to deduct {} credits but only has {}", 
                    currentUser.getId(), amount, currentCredits);
                return ResponseEntity.status(402).body(Map.of(
                    "error", "Insufficient credits",
                    "remainingCredits", currentCredits,
                    "requestedAmount", amount
                ));
            }

            // Deduct credits and save
            int newCredits = currentCredits - amount;
            user.setRemainingCredits(newCredits);
            User savedUser = userRepository.save(user);

            log.info("Deducted {} credits from user {}. New balance: {}", 
                amount, currentUser.getId(), newCredits);

            return ResponseEntity.ok(Map.of(
                "remainingCredits", newCredits,
                "hasLifetimeAccess", user.getHasLifetimeAccess() != null && user.getHasLifetimeAccess(),
                "deductedAmount", amount,
                "message", "Credits deducted successfully"
            ));
        } catch (Exception e) {
            log.error("Error deducting credits for user {}", currentUser.getId(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Error deducting credits",
                "details", e.getMessage()
            ));
        }
    }

}
