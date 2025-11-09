package com.whatsappanalytics.whatsapp_analytics_backend.service;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.swing.GroupLayout.Group;

import org.springframework.beans.factory.annotation.Autowired;
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

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.client5.http.ssl.TrustSelfSignedStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;

import com.whatsappanalytics.whatsapp_analytics_backend.model.ChatMetrics;
import com.whatsappanalytics.whatsapp_analytics_backend.model.GroupChat;
import com.whatsappanalytics.whatsapp_analytics_backend.model.User;
import com.whatsappanalytics.whatsapp_analytics_backend.repository.ChatMetricsRepository;
import com.whatsappanalytics.whatsapp_analytics_backend.repository.GroupChatRepository;

@Service
public class ChatMetricsService {

    private final RestTemplate restTemplate;
    private final String pythonServiceUrl = "https://127.0.0.1:5050/analyze";
    private final ChatMetricsRepository chatMetricsRepository;
    private final GroupChatRepository groupChatRepository;

    @Autowired
    public ChatMetricsService(ChatMetricsRepository chatMetricsRepository,
                              GroupChatRepository groupChatRepository,
                              RestTemplate restTemplate) {
        this.chatMetricsRepository = chatMetricsRepository;
        this.groupChatRepository = groupChatRepository;
        this.restTemplate = restTemplate;
    }

    public ChatMetricsService(
            ChatMetricsRepository chatMetricsRepository,
            GroupChatRepository groupChatRepository,
            @Value("${python.truststore.path}") String truststorePath,
            @Value("${python.truststore.password}") String truststorePassword
    ) throws Exception {
        this.chatMetricsRepository = chatMetricsRepository;
        this.groupChatRepository = groupChatRepository;

        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (InputStream fis = new ClassPathResource(truststorePath).getInputStream()) {
            trustStore.load(fis, truststorePassword.toCharArray());
        }
        SSLContext sslContext = SSLContexts.custom()
                .loadTrustMaterial(trustStore, new TrustSelfSignedStrategy())
                .build();

        var sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                .setSslContext(sslContext)
                .build();

        var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(sslSocketFactory)
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        this.restTemplate = new RestTemplate(requestFactory);
    }

    public Map<String, Object> processAnonymousFile(MultipartFile file) {
        Map<String, Object> stats = sendFileToPython(file);
        System.out.println("Received stats from Python service: " + stats);
        return stats != null ? stats : Collections.emptyMap();
    }

    public ChatMetrics processAndSaveMetrics(MultipartFile file, User currentUser) {
        Map<String, Object> stats = sendFileToPython(file);
        if (stats == null || stats.isEmpty()) {
            throw new RuntimeException("Python service returned no data");
        }

        // 1. Fetch or create GroupChat
        GroupChat newChat = new GroupChat();
        newChat.setUserId(currentUser);
        newChat.setTitle("Chat " + System.currentTimeMillis()); //TODO: change to appropriate name
        newChat.setParticipantsCount((int) stats.getOrDefault("participants_count", 0)); //TODO: adjust according to stats
        newChat.setCreatedAt(LocalDateTime.now());
        groupChatRepository.save(newChat);

        // 2. Create ChatMetrics entity
        ChatMetrics chatMetrics = new ChatMetrics();
        chatMetrics.setGroupChatId(newChat);
        chatMetrics.setTotalMessages(stats.get("total_messages") != null ? (int) stats.get("total_messages") : 0);
        chatMetrics.setTopUser((String) stats.get("top_user"));
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
