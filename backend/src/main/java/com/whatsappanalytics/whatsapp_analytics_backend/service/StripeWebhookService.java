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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
        
        // Handle both checkout.session.completed and payment_intent.succeeded
        if ("checkout.session.completed".equals(event.getType())) {
            handleCheckoutSessionCompleted(payload);
        } else if ("payment_intent.succeeded".equals(event.getType())) {
            handlePaymentIntentSucceeded(payload);
        } else {
            log.info("Ignoring event type: {}", event.getType());
        }
    }
    
    private void handleCheckoutSessionCompleted(String payload) {
        try {
            log.info("Processing checkout.session.completed webhook");
            
            // Parse JSON properly
            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(payload);
            JsonNode dataObject = json.get("data").get("object");
            JsonNode metadata = dataObject.get("metadata");
            
            String type = metadata != null && metadata.has("type") ? metadata.get("type").asText() : null;
            String userEmail = metadata != null && metadata.has("userEmail") ? metadata.get("userEmail").asText() : null;
            String creditsStr = metadata != null && metadata.has("credits") ? metadata.get("credits").asText() : null;
            
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
            log.error("✗ Error processing checkout.session.completed webhook", e);
            e.printStackTrace();
        }
    }
    
    private void handlePaymentIntentSucceeded(String payload) {
        try {
            log.info("Processing payment_intent.succeeded webhook");
            
            // Parse JSON properly
            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(payload);
            JsonNode dataObject = json.get("data").get("object");
            JsonNode metadata = dataObject.get("metadata");
            
            String type = metadata != null && metadata.has("type") ? metadata.get("type").asText() : null;
            String userEmail = metadata != null && metadata.has("userEmail") ? metadata.get("userEmail").asText() : null;
            long amount = dataObject.get("amount").asLong();
            
            log.info("Extracted - Type: {}, Email: {}, Amount (cents): {}", type, userEmail, amount);
            
            if (userEmail != null && !userEmail.isEmpty() && type != null) {
                User user = userRepository.findByEmail(userEmail).orElse(null);
                
                if (user != null) {
                    log.info("✓ Found user: {} (ID: {})", userEmail, user.getId());
                    
                    if ("credits".equals(type)) {
                        // Determine credits based on amount (in cents)
                        int credits = getCreditsForAmount(amount);
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
            log.error("✗ Error processing payment_intent.succeeded webhook", e);
            e.printStackTrace();
        }
    }
    
    private int getCreditsForAmount(long amountInCents) {
        return switch ((int) amountInCents) {
            case 99 -> 5;      // €0.99
            case 149 -> 10;    // €1.49
            case 229 -> 20;    // €2.29
            default -> {
                log.warn("Unknown amount: {}", amountInCents);
                yield 0;
            }
        };
    }
}
