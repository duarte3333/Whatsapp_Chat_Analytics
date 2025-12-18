package com.whatsappanalytics.whatsapp_analytics_backend.config;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.whatsappanalytics.whatsapp_analytics_backend.model.User;
import com.whatsappanalytics.whatsapp_analytics_backend.repository.UserRepository;
import com.whatsappanalytics.whatsapp_analytics_backend.service.JwtService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public OAuth2AuthenticationSuccessHandler(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

        // Extract email - Microsoft may use different attributes
        String extractedEmail = oauth2User.getAttribute("email");
        if (extractedEmail == null || extractedEmail.isEmpty()) {
            extractedEmail = oauth2User.getAttribute("mail");
        }
        if (extractedEmail == null || extractedEmail.isEmpty()) {
            extractedEmail = oauth2User.getAttribute("preferred_username");
        }

        String extractedName = oauth2User.getAttribute("name");
        if (extractedName == null || extractedName.isEmpty()) {
            extractedName = oauth2User.getAttribute("given_name");
        }

        if (extractedEmail == null || extractedEmail.isEmpty()) {
            getRedirectStrategy().sendRedirect(request, response, frontendUrl + "?error=email_not_found");
            return;
        }

        final String email = extractedEmail;
        final String name = extractedName;

        try {
            // Find or create user
            User user = userRepository.findByEmail(email).orElseGet(() -> {
                User newUser = new User();
                newUser.setEmail(email);
                newUser.setUsername(name != null ? name : email.split("@")[0]);
                newUser.setPassword(""); // OAuth users don't have passwords
                newUser.setEnabled(true);
                newUser.setGroupIds(List.of()); // Empty list initially
                return userRepository.save(newUser);
            });

            // Generate JWT token
            String token = jwtService.generateToken(user);

            System.out.println("OAuth2 login successful for user: " + email);

            // Redirect to frontend with token
            getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/oauth-callback?token=" + token);

        } catch (Exception e) {
            System.err.println("Error in OAuth2 authentication: " + e.getMessage());
            e.printStackTrace();
            getRedirectStrategy().sendRedirect(request, response,
                    frontendUrl + "?error=authentication_failed");
        }
    }
}
