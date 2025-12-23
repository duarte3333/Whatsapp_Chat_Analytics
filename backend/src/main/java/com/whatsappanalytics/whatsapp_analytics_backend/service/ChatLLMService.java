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

    //this is the constructor for the ChatLLMService class
    public ChatLLMService(@Qualifier("pythonRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.userRepository = userRepository;
    }

    //this is the method that will be used to analyze the chat
    public Map<String, Object> analyzeChat(Long chatId, Long userId, String question) {
        var payload = Map.of(
                "chat_id", chatId,
                "user_id", userId,
                "question", question
        );

        //this is the request that will be sent to the python microservice
        var request = new HttpEntity<>(payload);

        //this is the response that will be returned from the python microservice
        return restTemplate.exchange(
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
