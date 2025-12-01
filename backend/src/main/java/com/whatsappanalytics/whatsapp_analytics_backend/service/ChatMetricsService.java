package com.whatsappanalytics.whatsapp_analytics_backend.service;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import javax.net.ssl.SSLContext;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.annotation.Qualifier;

import com.whatsappanalytics.whatsapp_analytics_backend.model.ChatMetrics;
import com.whatsappanalytics.whatsapp_analytics_backend.model.GroupChat;
import com.whatsappanalytics.whatsapp_analytics_backend.model.User;
import com.whatsappanalytics.whatsapp_analytics_backend.repository.ChatMetricsRepository;
import com.whatsappanalytics.whatsapp_analytics_backend.repository.GroupChatRepository;

@Service
public class ChatMetricsService {

    private final RestTemplate restTemplate;
    private final String pythonServiceUrl = "https://localhost:5050/analyze";
    private final ChatMetricsRepository chatMetricsRepository;
    private final GroupChatRepository groupChatRepository;
    

    public ChatMetricsService(
        ChatMetricsRepository chatMetricsRepository,
        GroupChatRepository groupChatRepository,
        @Qualifier("pythonRestTemplate") RestTemplate restTemplate
    ) {
    this.chatMetricsRepository = chatMetricsRepository;
    this.groupChatRepository = groupChatRepository;
    this.restTemplate = restTemplate;
    }

    public Map<String, Object> processAnonymousFile(MultipartFile file) {
        System.out.println("=== processAnonymousFile called ===");
        System.out.println("File name: " + file.getOriginalFilename());
        System.out.println("File size: " + file.getSize());
        
        Map<String, Object> stats = sendFileToPython(file);
        System.out.println("Received stats from Python service: " + stats);
        System.out.println("Stats keys: " + (stats != null ? stats.keySet() : "null"));
        return stats != null ? stats : Collections.emptyMap();
    }

    public ChatMetrics processAndSaveMetrics(MultipartFile file, User currentUser) {
        Map<String, Object> stats = sendFileToPython(file);
        if (stats == null || stats.isEmpty()) {
            throw new RuntimeException("Python service returned no data");
        }

        // 1. Fetch or create GroupChat
        GroupChat newChat = new GroupChat();
        // Extract title from stats (Python service now provides this)
        String title = (String) stats.getOrDefault("title", "Chat " + System.currentTimeMillis());
        newChat.setTitle(title);
        newChat.setUserId(currentUser.getId());

        // Extract participants_count from stats
        Object participantsCountObj = stats.get("participants_count");
        int participantsCount = 0;
        if (participantsCountObj instanceof Integer) {
            participantsCount = (Integer) participantsCountObj;
        } else if (participantsCountObj instanceof Number) {
            participantsCount = ((Number) participantsCountObj).intValue();
        }
        newChat.setParticipantsCount(participantsCount);
        newChat.setCreatedAt(LocalDateTime.now());
        groupChatRepository.save(newChat);

        // 2. Create ChatMetrics from stats returned by Python service
        ChatMetrics chatMetrics = new ChatMetrics();
        chatMetrics.setGroupChatId(newChat.getId());

        // Extract total_messages from stats
        Object totalMessagesObj = stats.get("total_messages");
        int totalMessages = 0;
        if (totalMessagesObj instanceof Integer) {
            totalMessages = (Integer) totalMessagesObj;
        } else if (totalMessagesObj instanceof Number) {
            totalMessages = ((Number) totalMessagesObj).intValue();
        }
        chatMetrics.setTotalMessages(totalMessages);

        // Extract top_user from stats (Python service now provides this)
        String topUser = (String) stats.getOrDefault("top_user", "Unknown");
        chatMetrics.setTopUser(topUser);

        chatMetrics.setCreatedAt(LocalDateTime.now());
        chatMetrics.setLastUpdated(LocalDateTime.now());

        // 3. Persist ChatMetrics
        return chatMetricsRepository.save(chatMetrics);
    }


    private Map<String, Object> sendFileToPython(MultipartFile file) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });

            MultiValueMap<String, Object> multiValueMap = new LinkedMultiValueMap<>();
            builder.build().forEach((key, value) -> multiValueMap.addAll(key, value));

            HttpEntity<MultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(multiValueMap);

            return restTemplate.exchange(
                    pythonServiceUrl,
                    HttpMethod.POST,
                    requestEntity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    }
            ).getBody();
        } catch (IOException e) {
            throw new RuntimeException("Failed to send file to Python service", e);
        }
    }

}
