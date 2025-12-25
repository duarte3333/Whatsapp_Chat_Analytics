package com.whatsappanalytics.whatsapp_analytics_backend.service;

import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestTemplate;
import com.whatsappanalytics.whatsapp_analytics_backend.model.User;
import com.whatsappanalytics.whatsapp_analytics_backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ChatLLMService {

    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final String pythonUrl = "http://localhost:5050/llm/analyze";

    public ChatLLMService(@Qualifier("pythonRestTemplate") RestTemplate restTemplate, UserRepository userRepository) {
        this.restTemplate = restTemplate;
        this.userRepository = userRepository;
    }

    public Map<String, Object> analyzeChat(Long chatId, Long userId, String question, User user) {
        // Check if user has access (either credits or lifetime access)
        if (!user.getHasLifetimeAccess() && user.getRemainingCredits() <= 0) {
            log.warn("User {} has no credits and no lifetime access", userId);
            throw new InsufficientCreditsException("No credits available");
        }

        var payload = Map.of(
                "chat_id", chatId,
                "user_id", userId,
                "question", question
        );

        var request = new HttpEntity<>(payload);

        Map<String, Object> response = restTemplate.exchange(
                pythonUrl,
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        ).getBody();

        // Deduct credit if user doesn't have lifetime access
        if (!user.getHasLifetimeAccess()) {
            user.setRemainingCredits(user.getRemainingCredits() - 1);
            userRepository.save(user);
            log.info("Deducted 1 credit from user {}. Remaining: {}", userId, user.getRemainingCredits());
        }

        // Add remaining credits to response for frontend
        if (response != null) {
            response.put("remainingCredits", user.getRemainingCredits());
            response.put("hasLifetimeAccess", user.getHasLifetimeAccess());
        }

        return response;
    }

    public static class InsufficientCreditsException extends RuntimeException {
        public InsufficientCreditsException(String message) {
            super(message);
        }
    }
}