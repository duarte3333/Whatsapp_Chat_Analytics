package com.whatsappanalytics.whatsapp_analytics_backend.config;

import com.whatsappanalytics.whatsapp_analytics_backend.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final HandlerExceptionResolver handlerExceptionResolver;

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserDetailsService userDetailsService,
            HandlerExceptionResolver handlerExceptionResolver
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }
        
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {


        final String authHeader = request.getHeader("Authorization");
        final String requestPath = request.getRequestURI();

        // Check if this is a public endpoint - if so, skip JWT validation
        if (isPublicEndpoint(requestPath)) {
            System.out.println("JWT Filter: Public endpoint detected: " + requestPath + ", skipping JWT validation");
            filterChain.doFilter(request, response);
            return;
        }
        
        System.out.println("JWT Filter: Protected endpoint: " + requestPath);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // Only log for non-OPTIONS requests to reduce noise
            if (!"OPTIONS".equals(request.getMethod())) {
                System.out.println("No Bearer token found for " + request.getMethod() + " " + requestPath + ", passing through without auth");
            }
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String jwt = authHeader.substring(7);
            final String username = jwtService.extractUsername(jwt);

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
         

            if (username != null && authentication == null) {

                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                boolean valid = jwtService.isTokenValid(jwt, userDetails);

                if (valid) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    System.out.println("JWT token invalid for user: " + username);
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid JWT token");
                    return;
                }
            }

            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            System.err.println("JWT authentication failed: " + ex.getMessage());
            ex.printStackTrace();
            // Only return 401 if this is not a public endpoint
            if (!isPublicEndpoint(requestPath)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "JWT authentication failed");
            } else {
                filterChain.doFilter(request, response);
            }
        }
    }

    /**
     * Check if the request path is a public endpoint that doesn't require authentication
     */
    private boolean isPublicEndpoint(String path) {
        // Exclude /auth/user from public endpoints - it requires authentication
        if (path.equals("/auth/user") || path.equals("/api/auth/user")) {
            return false;
        }
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
    