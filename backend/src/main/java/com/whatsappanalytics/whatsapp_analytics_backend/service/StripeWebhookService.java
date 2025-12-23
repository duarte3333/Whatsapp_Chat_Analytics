package com.whatsappanalytics.whatsapp_analytics_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
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
            handleCheckoutSessionCompleted(event);
        } else {
            log.debug("Ignoring event type: {}", event.getType());
        }
    }

    private void handleCheckoutSessionCompleted(Event event) {
        try {
            Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
            
            if (session != null) {
                String userEmail = session.getCustomerEmail();
                log.info("Processing checkout session for email: {}", userEmail);
                
                User user = userRepository.findByEmail(userEmail).orElse(null);
                
                if (user != null) {
                    String packageId = session.getMetadata().get("packageId");
                    String type = session.getMetadata().get("type");
                    
                    log.info("Package: {}, Type: {}", packageId, type);
                    
                    if ("credits".equals(type)) {
                        int credits = Integer.parseInt(session.getMetadata().get("credits"));
                        user.setRemainingCredits((user.getRemainingCredits() != null ? user.getRemainingCredits() : 0) + credits);
                        log.info("Added {} credits to user {}", credits, userEmail);
                    } else if ("lifetime".equals(type)) {
                        user.setHasLifetimeAccess(true);
                        log.info("Granted lifetime access to user {}", userEmail);
                    }
                    
                    userRepository.save(user);
                    log.info("Updated user {} after successful payment", userEmail);
                } else {
                    log.warn("User not found with email: {}", userEmail);
                }
            } else {
                log.error("Session object is null in event data");
            }
        } catch (Exception e) {
            log.error("Error processing checkout session", e);
        }
    }
}
