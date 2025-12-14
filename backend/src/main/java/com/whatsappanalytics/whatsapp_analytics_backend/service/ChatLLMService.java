package com.whatsappanalytics.whatsapp_analytics_backend.service;

import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestTemplate;

@Service
public class ChatLLMService {

    private final RestTemplate restTemplate;
    private final String pythonUrl = "http://localhost:5050/llm/analyze";

    public ChatLLMService(@Qualifier("pythonRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Map<String, Object> analyzeChat(Long chatId, Long userId, String question) {
        var payload = Map.of(
                "chat_id", chatId,
                "user_id", userId,
                "question", question
        );

        var request = new HttpEntity<>(payload);

        return restTemplate.exchange(
                pythonUrl,
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        ).getBody();
    }
}
