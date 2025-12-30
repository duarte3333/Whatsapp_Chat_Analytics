package com.whatsappanalytics.whatsapp_analytics_backend.config;

import java.io.InputStream;
import java.security.KeyStore;

import javax.net.ssl.SSLContext;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.client5.http.ssl.TrustSelfSignedStrategy;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.client5.http.config.RequestConfig;
import java.util.concurrent.TimeUnit;

@Configuration
public class PythonRestTemplateConfig {

    @Bean("pythonRestTemplate")
    public RestTemplate pythonRestTemplate(
            @Value("${python.truststore.path}") String truststorePath,
            @Value("${python.truststore.password}") String truststorePassword
    ) throws Exception {

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

        // Configure request timeouts (5 minutes for response)
        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.of(5, TimeUnit.MINUTES))
                .setConnectionRequestTimeout(Timeout.of(30, TimeUnit.SECONDS))
                .setConnectTimeout(Timeout.of(30, TimeUnit.SECONDS))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictIdleConnections(Timeout.of(30, TimeUnit.SECONDS))
                .evictExpiredConnections()
                .build();

        var requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        // Set timeouts: connection timeout, read timeout (for file processing)
        // 5 minutes total timeout for large file processing
        // Convert Timeout to milliseconds (int) for HttpComponentsClientHttpRequestFactory
        requestFactory.setConnectTimeout((int) Timeout.of(30, TimeUnit.SECONDS).toMilliseconds());
        requestFactory.setConnectionRequestTimeout((int) Timeout.of(30, TimeUnit.SECONDS).toMilliseconds());
        requestFactory.setReadTimeout((int) Timeout.of(5, TimeUnit.MINUTES).toMilliseconds()); // 5 minutes for response

        return new RestTemplate(requestFactory);
    }
}
