package com.whatsappanalytics.whatsapp_analytics_backend.service;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.core.ParameterizedTypeReference;

@Service
public class AnalyticsService {

    private final WebClient webClient = WebClient.create("http://localhost:5000");

    public Map<String, Object> processAnonymousFile(MultipartFile file) {
        try {
            return webClient.post()
                .uri("/analyze")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData("file", file.getResource()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
        } catch (Exception e) {
            throw new RuntimeException("Failed to process file", e);
        }
    }
}
