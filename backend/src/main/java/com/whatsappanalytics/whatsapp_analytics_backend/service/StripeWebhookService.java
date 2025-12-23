package com.whatsappanalytics.whatsapp_analytics_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import com.whatsappanalytics.whatsapp_analytics_backend.model.User;
import com.whatsappanalytics.whatsapp_analytics_backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class StripeWebhookService {

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    private final UserRepository userRepository;

    public StripeWebhookService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void handleWebhookEvent(String payload, String signatureHeader) throws SignatureVerificationException {
        Stripe.apiKey = stripeApiKey;
        Event event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        
        log.info("Processing event type: {}", event.getType());
        
        if ("checkout.session.completed".equals(event.getType())) {
            try {
                // Extract values from raw payload JSON
                String userEmail = extractJsonValue(payload, "customer_email");
                String type = extractJsonValue(payload, "\"type\"");
                String creditsStr = extractJsonValue(payload, "\"credits\"");
                String packageId = extractJsonValue(payload, "\"packageId\"");
                String userEmailFromMetadata = extractJsonValue(payload, "\"userEmail\"");
                
                if ((userEmail == null || userEmail.isEmpty()) && userEmailFromMetadata != null) {
                    userEmail = userEmailFromMetadata;
                }
                
                log.info("Extracted - Email: {} | Type: {} | Credits: {} | Package: {}", 
                    userEmail, type, creditsStr, packageId);
                
                if (userEmail != null && !userEmail.isEmpty()) {
                    User user = userRepository.findByEmail(userEmail).orElse(null);
                    
                    if (user != null) {
                        log.info("✓ Found user: {}", userEmail);
                        
                        if ("credits".equals(type) && creditsStr != null) {
                            int credits = Integer.parseInt(creditsStr);
                            user.setRemainingCredits((user.getRemainingCredits() != null ? user.getRemainingCredits() : 0) + credits);
                            log.info("✓ Added {} credits to user {}", credits, userEmail);
                        } else if ("lifetime".equals(type)) {
                            user.setHasLifetimeAccess(true);
                            log.info("✓ Granted lifetime access to user {}", userEmail);
                        }
                        
                        userRepository.save(user);
                        log.info("✓✓ Successfully updated user {} in database", userEmail);
                    } else {
                        log.error("✗ User not found with email: {}", userEmail);
                    }
                } else {
                    log.error("✗ No email found");
                }
            } catch (Exception e) {
                log.error("✗ Error processing checkout session", e);
            }
        }
    }
    
    private String extractJsonValue(String json, String key) {
        try {
            String searchKey = key.contains("\"") ? key : "\"" + key + "\"";
            int index = json.indexOf(searchKey);
            if (index == -1) return null;
            
            int colonIndex = json.indexOf(":", index);
            if (colonIndex == -1) return null;
            
            int startIndex = colonIndex + 1;
            while (startIndex < json.length() && (json.charAt(startIndex) == ' ' || json.charAt(startIndex) == ':')) {
                startIndex++;
            }
            
            if (startIndex >= json.length()) return null;
            
            if (json.charAt(startIndex) == '"') {
                int endIndex = json.indexOf('"', startIndex + 1);
                if (endIndex == -1) return null;
                return json.substring(startIndex + 1, endIndex);
            } else {
                int endIndex = startIndex;
                while (endIndex < json.length() && json.charAt(endIndex) != ',' && json.charAt(endIndex) != '}' && json.charAt(endIndex) != ']') {
                    endIndex++;
                }
                return json.substring(startIndex, endIndex).trim();
            }
        } catch (Exception e) {
            log.warn("Failed to extract JSON value for key: {}", key);
            return null;
        }
    }
}
