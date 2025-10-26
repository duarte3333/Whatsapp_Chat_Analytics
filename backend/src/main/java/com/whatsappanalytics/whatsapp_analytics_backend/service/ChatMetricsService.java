package com.whatsappanalytics.whatsapp_analytics_backend.service;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
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

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.client5.http.ssl.TrustSelfSignedStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;

import com.whatsappanalytics.whatsapp_analytics_backend.repository.ChatMetricsRepository;

@Service
public class ChatMetricsService {

    private final RestTemplate restTemplate;
    private final String pythonServiceUrl = "https://127.0.0.1:5050/analyze";
    private final ChatMetricsRepository chatMetricsRepository;

    public ChatMetricsService(
        ChatMetricsRepository chatMetricsRepository,
        @Value("${python.truststore.path}") String truststorePath,
        @Value("${python.truststore.password}") String truststorePassword
    ) throws Exception {
        this.chatMetricsRepository = chatMetricsRepository;
        
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
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            ).getBody();
        } catch (IOException e) {
            throw new RuntimeException("Failed to send file to Python service", e);
        }
    }
}
