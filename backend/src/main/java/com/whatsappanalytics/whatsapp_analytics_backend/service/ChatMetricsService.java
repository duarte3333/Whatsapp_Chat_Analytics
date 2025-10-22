package com.whatsappanalytics.whatsapp_analytics_backend.service;


import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.whatsappanalytics.whatsapp_analytics_backend.model.ChatMetrics;


@Service
public class ChatMetricsService {

    private final WebClient webClient = WebClient.create("http://localhost:5000");

 public ChatMetrics uploadMetrics(MultipartFile file) {
        try {
            return webClient.post()
                    .uri("/analyze")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData("file", file.getResource()))
                    .retrieve()
                    .bodyToMono(ChatMetrics.class)  // <-- deserialize directly into ChatMetrics
                    .block();
        } catch (Exception e) {
            throw new RuntimeException("Failed to process file", e);
        }
    }

}
