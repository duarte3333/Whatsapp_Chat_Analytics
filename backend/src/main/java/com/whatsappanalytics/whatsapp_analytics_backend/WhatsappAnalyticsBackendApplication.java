package com.whatsappanalytics.whatsapp_analytics_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.http.client.HttpClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication(
        exclude = {
                HttpClientAutoConfiguration.class,
                RestClientAutoConfiguration.class
        }
)
public class WhatsappAnalyticsBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(WhatsappAnalyticsBackendApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
