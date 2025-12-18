package com.whatsappanalytics.whatsapp_analytics_backend.config;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        
        System.err.println("OAuth2 authentication failed: " + exception.getMessage());
        exception.printStackTrace();
        
        // Redirect to frontend with error
        getRedirectStrategy().sendRedirect(request, response,
                frontendUrl + "?error=oauth2_authentication_failed&message=" + 
                exception.getMessage().replaceAll(" ", "+"));
    }
}
