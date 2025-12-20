package com.whatsappanalytics.whatsapp_analytics_backend.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.annotation.Qualifier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.whatsappanalytics.whatsapp_analytics_backend.model.ChatMetrics;
import com.whatsappanalytics.whatsapp_analytics_backend.model.GroupChat;
import com.whatsappanalytics.whatsapp_analytics_backend.model.User;
import com.whatsappanalytics.whatsapp_analytics_backend.repository.ChatMetricsRepository;
import com.whatsappanalytics.whatsapp_analytics_backend.repository.GroupChatRepository;

@Service
public class ChatMetricsService {

    private final RestTemplate restTemplate;
    private final String pythonServiceUrl = "http://localhost:5050/analyze";
    private final ChatMetricsRepository chatMetricsRepository;
    private final GroupChatRepository groupChatRepository;
    private final ObjectMapper objectMapper;
    

    public ChatMetricsService(
        ChatMetricsRepository chatMetricsRepository,
        GroupChatRepository groupChatRepository,
        @Qualifier("pythonRestTemplate") RestTemplate restTemplate,
        ObjectMapper objectMapper
    ) {
    this.chatMetricsRepository = chatMetricsRepository;
    this.groupChatRepository = groupChatRepository;
    this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
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

    public Map<String, Object> processAndSaveMetrics(MultipartFile file, User currentUser) {
        Map<String, Object> stats = sendFileToPython(file);
        if (stats == null || stats.isEmpty()) {
            throw new RuntimeException("Python service returned no data");
        }

        // 1. Create GroupChat
        GroupChat newChat = new GroupChat();
        String title = (String) stats.getOrDefault("title", "Chat " + System.currentTimeMillis());
        newChat.setTitle(title);
        newChat.setUserId(currentUser.getId());

        int participantsCount = extractInt(stats.get("participants_count"));
        newChat.setParticipantsCount(participantsCount);
        newChat.setCreatedAt(LocalDateTime.now());
        groupChatRepository.save(newChat);

        // 2. Create ChatMetrics with all data including chart data
        ChatMetrics chatMetrics = new ChatMetrics();
        chatMetrics.setGroupChatId(newChat.getId());
        chatMetrics.setTotalMessages(extractInt(stats.get("total_messages")));
        chatMetrics.setTopUser((String) stats.getOrDefault("top_user", "Unknown"));
        chatMetrics.setParticipantsCount(participantsCount);
        chatMetrics.setMostActiveDay((String) stats.get("mostActiveDay"));
        chatMetrics.setPeakHour((String) stats.get("peakHour"));

        // Save chart data as JSON
        try {
            // Legacy chart data
            if (stats.get("dailyMessages") != null) {
                chatMetrics.setDailyMessagesJson(objectMapper.writeValueAsString(stats.get("dailyMessages")));
            }
            if (stats.get("hourlyData") != null) {
                chatMetrics.setHourlyDataJson(objectMapper.writeValueAsString(stats.get("hourlyData")));
            }
            if (stats.get("participantData") != null) {
                chatMetrics.setParticipantDataJson(objectMapper.writeValueAsString(stats.get("participantData")));
            }
            if (stats.get("emojiUsage") != null) {
                chatMetrics.setEmojiUsageJson(objectMapper.writeValueAsString(stats.get("emojiUsage")));
            }
            // New chart data
            if (stats.get("messagesPerUser") != null) {
                chatMetrics.setMessagesPerUserJson(objectMapper.writeValueAsString(stats.get("messagesPerUser")));
            }
            if (stats.get("weeklyVolume") != null) {
                chatMetrics.setWeeklyVolumeJson(objectMapper.writeValueAsString(stats.get("weeklyVolume")));
            }
            if (stats.get("heatmapData") != null) {
                chatMetrics.setHeatmapDataJson(objectMapper.writeValueAsString(stats.get("heatmapData")));
            }
            if (stats.get("messageTypeBreakdown") != null) {
                chatMetrics.setMessageTypeBreakdownJson(objectMapper.writeValueAsString(stats.get("messageTypeBreakdown")));
            }
            if (stats.get("dateRange") != null) {
                chatMetrics.setDateRangeJson(objectMapper.writeValueAsString(stats.get("dateRange")));
            }
            // Emoji stats
            if (stats.get("topEmojisOverall") != null) {
                chatMetrics.setTopEmojisOverallJson(objectMapper.writeValueAsString(stats.get("topEmojisOverall")));
            }
            if (stats.get("topEmojisPerUser") != null) {
                chatMetrics.setTopEmojisPerUserJson(objectMapper.writeValueAsString(stats.get("topEmojisPerUser")));
            }
            // Streak stats
            if (stats.get("longestStreak") != null) {
                chatMetrics.setLongestStreakJson(objectMapper.writeValueAsString(stats.get("longestStreak")));
            }
            if (stats.get("longestStreakPerUser") != null) {
                chatMetrics.setLongestStreakPerUserJson(objectMapper.writeValueAsString(stats.get("longestStreakPerUser")));
            }
        } catch (JsonProcessingException e) {
            System.err.println("Error serializing chart data: " + e.getMessage());
        }

        chatMetrics.setCreatedAt(LocalDateTime.now());
        chatMetrics.setLastUpdated(LocalDateTime.now());

        // 3. Persist ChatMetrics
        chatMetricsRepository.save(chatMetrics);
        
        // 4. Add the saved chat ID to stats for frontend navigation
        stats.put("id", newChat.getId());
        stats.put("chatId", newChat.getId());
        
        return stats;
    }

    /**
     * Get stats for a group chat by ID
     */
    public Map<String, Object> getStatsByGroupId(Long groupId, Long userId) {
        // Verify group belongs to user
        GroupChat group = groupChatRepository.findByIdAndUserId(groupId, userId);
        if (group == null) {
            return null;
        }

        ChatMetrics metrics = chatMetricsRepository.findByGroupChatId(groupId);
        if (metrics == null) {
            return null;
        }

        // Build response map
        Map<String, Object> stats = new HashMap<>();
        stats.put("id", groupId);
        stats.put("chatId", groupId);
        stats.put("title", group.getTitle());
        stats.put("total_messages", metrics.getTotalMessages());
        stats.put("totalMessages", metrics.getTotalMessages());
        stats.put("participants_count", metrics.getParticipantsCount());
        stats.put("top_user", metrics.getTopUser());
        stats.put("topUser", metrics.getTopUser());
        stats.put("mostActiveDay", metrics.getMostActiveDay());
        stats.put("peakHour", metrics.getPeakHour());

        // Deserialize JSON chart data
        try {
            // Legacy chart data
            if (metrics.getDailyMessagesJson() != null) {
                stats.put("dailyMessages", objectMapper.readValue(
                    metrics.getDailyMessagesJson(), 
                    new TypeReference<List<Map<String, Object>>>() {}
                ));
            }
            if (metrics.getHourlyDataJson() != null) {
                stats.put("hourlyData", objectMapper.readValue(
                    metrics.getHourlyDataJson(), 
                    new TypeReference<List<Map<String, Object>>>() {}
                ));
            }
            if (metrics.getParticipantDataJson() != null) {
                stats.put("participantData", objectMapper.readValue(
                    metrics.getParticipantDataJson(), 
                    new TypeReference<List<Map<String, Object>>>() {}
                ));
            }
            if (metrics.getEmojiUsageJson() != null) {
                stats.put("emojiUsage", objectMapper.readValue(
                    metrics.getEmojiUsageJson(), 
                    new TypeReference<Map<String, Object>>() {}
                ));
            }
            // New chart data
            if (metrics.getMessagesPerUserJson() != null) {
                stats.put("messagesPerUser", objectMapper.readValue(
                    metrics.getMessagesPerUserJson(), 
                    new TypeReference<List<Map<String, Object>>>() {}
                ));
            }
            if (metrics.getWeeklyVolumeJson() != null) {
                stats.put("weeklyVolume", objectMapper.readValue(
                    metrics.getWeeklyVolumeJson(), 
                    new TypeReference<List<Map<String, Object>>>() {}
                ));
            }
            if (metrics.getHeatmapDataJson() != null) {
                stats.put("heatmapData", objectMapper.readValue(
                    metrics.getHeatmapDataJson(), 
                    new TypeReference<List<Map<String, Object>>>() {}
                ));
            }
            if (metrics.getMessageTypeBreakdownJson() != null) {
                stats.put("messageTypeBreakdown", objectMapper.readValue(
                    metrics.getMessageTypeBreakdownJson(), 
                    new TypeReference<List<Map<String, Object>>>() {}
                ));
            }
            if (metrics.getDateRangeJson() != null) {
                stats.put("dateRange", objectMapper.readValue(
                    metrics.getDateRangeJson(), 
                    new TypeReference<Map<String, Object>>() {}
                ));
            }
            // Emoji stats
            if (metrics.getTopEmojisOverallJson() != null) {
                stats.put("topEmojisOverall", objectMapper.readValue(
                    metrics.getTopEmojisOverallJson(), 
                    new TypeReference<List<Map<String, Object>>>() {}
                ));
            }
            if (metrics.getTopEmojisPerUserJson() != null) {
                stats.put("topEmojisPerUser", objectMapper.readValue(
                    metrics.getTopEmojisPerUserJson(), 
                    new TypeReference<List<Map<String, Object>>>() {}
                ));
            }
            // Streak stats
            if (metrics.getLongestStreakJson() != null) {
                stats.put("longestStreak", objectMapper.readValue(
                    metrics.getLongestStreakJson(), 
                    new TypeReference<Map<String, Object>>() {}
                ));
            }
            if (metrics.getLongestStreakPerUserJson() != null) {
                stats.put("longestStreakPerUser", objectMapper.readValue(
                    metrics.getLongestStreakPerUserJson(), 
                    new TypeReference<List<Map<String, Object>>>() {}
                ));
            }
        } catch (JsonProcessingException e) {
            System.err.println("Error deserializing chart data: " + e.getMessage());
        }

        return stats;
    }

    private int extractInt(Object obj) {
        if (obj instanceof Integer) {
            return (Integer) obj;
        } else if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        return 0;
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
