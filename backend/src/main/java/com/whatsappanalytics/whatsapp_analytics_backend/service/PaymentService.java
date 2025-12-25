package com.whatsappanalytics.whatsapp_analytics_backend.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.whatsappanalytics.whatsapp_analytics_backend.dto.CheckoutSessionResponseDto;
import com.whatsappanalytics.whatsapp_analytics_backend.dto.PaymentRequestDto;
import com.whatsappanalytics.whatsapp_analytics_backend.model.User;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PaymentService {

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${stripe.price.prompts-5}")
    private String stripePricePrompts5;

    @Value("${stripe.price.prompts-10}")
    private String stripePricePrompts10;

    @Value("${stripe.price.prompts-20}")
    private String stripePricePrompts20;

    @Value("${stripe.price.lifetime}")
    private String stripePriceLifetime;

    public CheckoutSessionResponseDto createCheckoutSession(PaymentRequestDto request, User user) throws StripeException {
        Stripe.apiKey = stripeApiKey;

        String successUrl = frontendUrl + "/payment-success?session_id={CHECKOUT_SESSION_ID}";
        String cancelUrl = frontendUrl + "/payment-success?status=cancelled";

        SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .setCustomerEmail(user.getEmail())
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPrice(getPriceId(request.getPackageId()))
                                .setQuantity(1L)
                                .build()
                )
                .putMetadata("type", request.getType())
                .putMetadata("packageId", request.getPackageId())
                .putMetadata("userEmail", user.getEmail());

        if ("credits".equals(request.getType()) && request.getCredits() != null) {
            paramsBuilder.putMetadata("credits", request.getCredits().toString());
        }

        Session session = Session.create(paramsBuilder.build());

        log.info("Created Stripe checkout session: {} for user: {}", session.getId(), user.getEmail());

        return new CheckoutSessionResponseDto(session.getId(), session.getUrl());
    }

    private String getPriceId(String packageId) {
        log.info("Getting Stripe Price ID for package: {}", packageId);
        
        // Map package IDs to Stripe price IDs
        // These should be configured in your Stripe dashboard
        // Support both "prompts-X" and "credits-X" naming conventions
        String priceId = switch (packageId) {
            case "prompts-5", "credits-5" -> stripePricePrompts5;
            case "prompts-10", "credits-10" -> stripePricePrompts10;
            case "prompts-20", "credits-20" -> stripePricePrompts20;
            case "lifetime" -> stripePriceLifetime;
            default -> throw new IllegalArgumentException("Unknown package: " + packageId);
        };
        
        log.info("Resolved Price ID for package '{}': {}", packageId, priceId);
        
        // Validate that a real Stripe price ID is configured (not a placeholder)
        if (priceId == null || priceId.contains("placeholder") || !priceId.startsWith("price_")) {
            String envVarName;
            if (packageId.equals("lifetime")) {
                envVarName = "STRIPE_PRICE_LIFETIME";
            } else {
                String number = packageId.replace("credits-", "").replace("prompts-", "");
                envVarName = "STRIPE_PRICE_PROMPTS_" + number;
            }
            
            String errorMessage = String.format(
                "Stripe Price ID not configured for package '%s'. " +
                "Current value: '%s'. " +
                "Please set the %s environment variable with a valid Stripe Price ID (starts with 'price_'). " +
                "Get Price IDs from: Stripe Dashboard → Products → Select product → Copy Price ID",
                packageId, priceId, envVarName
            );
            
            log.error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
        
        return priceId;
    }
}
