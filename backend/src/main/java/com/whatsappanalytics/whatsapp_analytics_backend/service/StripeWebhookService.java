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
                log.info("Processing checkout.session.completed webhook");
                
                // Simple regex-based extraction from metadata
                String type = extractValueByKey(payload, "\"type\"");
                String creditsStr = extractValueByKey(payload, "\"credits\"");
                String userEmail = extractValueByKey(payload, "\"userEmail\"");
                
                log.info("Extracted - Type: {}, Credits: {}, Email: {}", type, creditsStr, userEmail);
                
                if (userEmail != null && !userEmail.isEmpty() && type != null) {
                    User user = userRepository.findByEmail(userEmail).orElse(null);
                    
                    if (user != null) {
                        log.info("✓ Found user: {} (ID: {})", userEmail, user.getId());
                        
                        if ("credits".equals(type) && creditsStr != null) {
                            int credits = Integer.parseInt(creditsStr);
                            int oldCredits = user.getRemainingCredits() != null ? user.getRemainingCredits() : 0;
                            int newCredits = oldCredits + credits;
                            user.setRemainingCredits(newCredits);
                            log.info("✓ Adding {} credits: {} + {} = {}", type, oldCredits, credits, newCredits);
                        } else if ("lifetime".equals(type)) {
                            user.setHasLifetimeAccess(true);
                            log.info("✓ Setting lifetime access for user {}", userEmail);
                        }
                        
                        User savedUser = userRepository.save(user);
                        log.info("✓✓ SUCCESS - User {} updated. Credits: {}, Lifetime: {}", 
                            userEmail, savedUser.getRemainingCredits(), savedUser.getHasLifetimeAccess());
                    } else {
                        log.error("✗ User not found: {}", userEmail);
                    }
                } else {
                    log.error("✗ Missing required fields - Type: {}, Email: {}", type, userEmail);
                }
            } catch (Exception e) {
                log.error("✗ Error processing webhook", e);
                e.printStackTrace();
            }
        }
    }
    
    private String extractValueByKey(String json, String key) {
        try {
            int keyIndex = json.indexOf(key);
            if (keyIndex == -1) return null;
            
            int colonIndex = json.indexOf(":", keyIndex);
            if (colonIndex == -1) return null;
            
            int startIndex = colonIndex + 1;
            while (startIndex < json.length() && json.charAt(startIndex) == ' ') {
                startIndex++;
            }
            
            if (startIndex >= json.length()) return null;
            
            // Handle quoted strings
            if (json.charAt(startIndex) == '"') {
                int endIndex = json.indexOf('"', startIndex + 1);
                if (endIndex == -1) return null;
                return json.substring(startIndex + 1, endIndex);
            }
            
            // Handle numbers or unquoted values
            int endIndex = startIndex;
            while (endIndex < json.length() && json.charAt(endIndex) != ',' && json.charAt(endIndex) != '}') {
                endIndex++;
            }
            return json.substring(startIndex, endIndex).trim();
        } catch (Exception e) {
            log.warn("Failed to extract value for key: {}", key);
            return null;
        }
    }
    
}
