package com.whatsappanalytics.whatsapp_analytics_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.whatsappanalytics.whatsapp_analytics_backend.dto.ChatRequestDto;
import com.whatsappanalytics.whatsapp_analytics_backend.model.User;
import com.whatsappanalytics.whatsapp_analytics_backend.service.ChatLLMService;

@RestController
@RequestMapping("/chat-llm")
public class ChatLLMController {

    private final ChatLLMService chatLLMService;

    public ChatLLMController(ChatLLMService chatLLMService) {
        this.chatLLMService = chatLLMService;
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

        var response = chatLLMService.analyzeChat(chatId, currentUser.getId(), request.question);
        return ResponseEntity.ok(response);
    }

}
