package com.whatsappanalytics.whatsapp_analytics_backend.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import com.whatsappanalytics.whatsapp_analytics_backend.filter.RateLimitFilter;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private final AuthenticationProvider authenticationProvider;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler;
    private final OAuth2AuthenticationFailureHandler oAuth2FailureHandler;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfiguration(
            AuthenticationProvider authenticationProvider,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler,
            OAuth2AuthenticationFailureHandler oAuth2FailureHandler,
            RateLimitFilter rateLimitFilter) {
        this.authenticationProvider = authenticationProvider;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
        this.oAuth2FailureHandler = oAuth2FailureHandler;
        this.rateLimitFilter = rateLimitFilter;
    }

    

@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http, @Qualifier("corsConfigurationSource") CorsConfigurationSource corsSource) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsSource))
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers(HttpMethod.POST, "/shared/guest").permitAll()
            .requestMatchers(HttpMethod.POST, "/payments/webhook").permitAll()
            .requestMatchers(HttpMethod.GET, "/payments/webhook").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/payments/webhook").permitAll()
            .requestMatchers(
                "/auth/**",
                "/auth/signup",
                "/auth/login",
                "/auth/verify",
                "/auth/resend",
                "/auth/forgot-password",
                "/auth/reset-password",
                "/oauth2/**",
                "/login/**",
                "/v3/api-docs/**",
                "/api/payments/webhook",
		"/payments/webhook",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/metrics/chat/upload-anonymous",
                "/shared/**",
                "/api/shared/**")
            .permitAll()
            .anyRequest().authenticated())
        .oauth2Login(oauth2 -> oauth2
            .successHandler(oAuth2SuccessHandler)
            .failureHandler(oAuth2FailureHandler))
        .formLogin(form -> form.disable())  // Disable form login redirects
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(exception -> exception
            .authenticationEntryPoint((request, response, authException) -> {
                // Only return 401 for protected endpoints
                // Public endpoints should not reach here if configured correctly
                response.setContentType("application/json;charset=UTF-8");
                response.setCharacterEncoding("UTF-8");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"" + authException.getMessage() + "\"}");
            }))
        .authenticationProvider(authenticationProvider)
        .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
}

/**
 * Check if the request path is a public endpoint that doesn't require authentication
 */
private boolean isPublicEndpoint(String path) {
    return path.startsWith("/auth/") ||
           path.startsWith("/oauth2/") ||
           path.startsWith("/login/") ||
           path.startsWith("/v3/api-docs/") ||
           path.startsWith("/swagger-ui/") ||
           path.equals("/swagger-ui.html") ||
           path.startsWith("/api/payments/webhook") ||
           path.startsWith("/payments/webhook") ||
           path.startsWith("/metrics/chat/upload-anonymous") ||
           path.startsWith("/shared/") ||
           path.startsWith("/api/shared/");
}

}
