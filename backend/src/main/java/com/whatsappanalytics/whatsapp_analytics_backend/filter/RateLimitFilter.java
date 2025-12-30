package com.whatsappanalytics.whatsapp_analytics_backend.filter;

import com.whatsappanalytics.whatsapp_analytics_backend.config.RateLimitConfig;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.whatsappanalytics.whatsapp_analytics_backend.model.User;

import java.io.IOException;

/**
 * Rate limiting filter that applies different limits based on endpoint and authentication status
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitConfig rateLimitConfig;

    public RateLimitFilter(RateLimitConfig rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        // Skip rate limiting for OPTIONS requests
        if ("OPTIONS".equals(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = null;
        String key = getRateLimitKey(request);

        // Apply different rate limits based on endpoint
        if (isResendEndpoint(path)) {
            // Very strict rate limiting for resend verification code (prevent email spam)
            bucket = rateLimitConfig.createResendCodeBucket(key);
        } else if (isAuthEndpoint(path)) {
            // Strict rate limiting for authentication endpoints (but still allow them)
            bucket = rateLimitConfig.createAuthBucket(key);
        } else if (isUploadEndpoint(path)) {
            // Very strict rate limiting for file uploads
            bucket = rateLimitConfig.createUploadBucket(key);
        } else if (isAuthenticated(request)) {
            // Authenticated users get more lenient limits
            bucket = rateLimitConfig.resolveAuthenticatedBucket(key);
        } else {
            // Anonymous users get stricter limits
            bucket = rateLimitConfig.resolveBucket(key);
        }

        // Check if request is allowed
        if (bucket.tryConsume(1)) {
            // Add rate limit headers
            response.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            filterChain.doFilter(request, response);
        } else {
            // Rate limit exceeded - return 429, not 401
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\": \"Rate limit exceeded. Please try again later.\"}");
        }
    }

    /**
     * Get rate limit key (IP for anonymous, email for authenticated)
     */
    private String getRateLimitKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.isAuthenticated() && 
            authentication.getPrincipal() instanceof User) {
            User user = (User) authentication.getPrincipal();
            return user.getEmail();
        }
        
        // Use IP address for anonymous users
        String ip = getClientIpAddress(request);
        return ip != null ? ip : "unknown";
    }

    /**
     * Get client IP address (handles proxies)
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    /**
     * Check if request is authenticated
     */
    private boolean isAuthenticated(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && 
               authentication.isAuthenticated() && 
               authentication.getPrincipal() instanceof User;
    }

    /**
     * Check if endpoint is authentication-related
     */
    private boolean isAuthEndpoint(String path) {
        return path.startsWith("/api/auth/") || 
               path.startsWith("/auth/") ||
               path.startsWith("/oauth2/") ||
               path.startsWith("/login/");
    }

    /**
     * Check if endpoint is file upload
     */
    private boolean isUploadEndpoint(String path) {
        return path.contains("/upload") || 
               path.contains("/analyze") ||
               path.contains("/rag/upload");
    }

    /**
     * Check if endpoint is resend verification code
     */
    private boolean isResendEndpoint(String path) {
        return path.equals("/auth/resend") || path.equals("/api/auth/resend");
    }
}

