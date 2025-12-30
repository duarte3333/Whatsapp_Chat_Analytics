package com.whatsappanalytics.whatsapp_analytics_backend.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting configuration using Bucket4j
 */
@Configuration
public class RateLimitConfig {

    // Cache for storing rate limit buckets per IP/user
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    /**
     * Get or create rate limit bucket for anonymous users (by IP)
     */
    public Bucket resolveBucket(String key) {
        return cache.computeIfAbsent(key, k -> createAnonymousBucket());
    }

    /**
     * Get or create rate limit bucket for authenticated users
     */
    public Bucket resolveAuthenticatedBucket(String key) {
        return cache.computeIfAbsent(key, k -> createAuthenticatedBucket());
    }

    /**
     * Create bucket for anonymous users (relaxed limits)
     * - 100 requests per minute
     * - 1000 requests per hour
     */
    private Bucket createAnonymousBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1))))
                .addLimit(Bandwidth.classic(1000, Refill.intervally(1000, Duration.ofHours(1))))
                .build();
    }

    /**
     * Create bucket for authenticated users (very relaxed limits)
     * - 200 requests per minute
     * - 5000 requests per hour
     */
    private Bucket createAuthenticatedBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(200, Refill.intervally(200, Duration.ofMinutes(1))))
                .addLimit(Bandwidth.classic(5000, Refill.intervally(5000, Duration.ofHours(1))))
                .build();
    }

    /**
     * Create bucket for file uploads (relaxed)
     * - 50 uploads per hour
     */
    public Bucket createUploadBucket(String key) {
        return cache.computeIfAbsent("upload:" + key, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(50, Refill.intervally(50, Duration.ofHours(1))))
                .build());
    }

    /**
     * Create bucket for authentication endpoints (relaxed to prevent brute force)
     * - 20 attempts per 15 minutes
     */
    public Bucket createAuthBucket(String key) {
        return cache.computeIfAbsent("auth:" + key, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(20, Refill.intervally(20, Duration.ofMinutes(15))))
                .build());
    }

    /**
     * Create bucket for resend verification code (relaxed to prevent email spam)
     * - 10 attempts per hour
     * - 3 attempts per 5 minutes
     */
    public Bucket createResendCodeBucket(String key) {
        return cache.computeIfAbsent("resend:" + key, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofHours(1))))
                .addLimit(Bandwidth.classic(3, Refill.intervally(3, Duration.ofMinutes(5))))
                .build());
    }
}

