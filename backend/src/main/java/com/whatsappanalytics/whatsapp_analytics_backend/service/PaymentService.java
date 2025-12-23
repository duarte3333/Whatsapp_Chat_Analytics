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

    @Value("${stripe.price.credits-50}")
    private String stripePriceCredits50;

    @Value("${stripe.price.credits-150}")
    private String stripePriceCredits150;

    @Value("${stripe.price.credits-500}")
    private String stripePriceCredits500;

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
                .putMetadata("packageId", request.getPackageId());

        if ("credits".equals(request.getType()) && request.getCredits() != null) {
            paramsBuilder.putMetadata("credits", request.getCredits().toString());
        }

        Session session = Session.create(paramsBuilder.build());

        log.info("Created Stripe checkout session: {} for user: {}", session.getId(), user.getEmail());

        return new CheckoutSessionResponseDto(session.getId(), session.getUrl());
    }

    private String getPriceId(String packageId) {
        // Map package IDs to Stripe price IDs
        // These should be configured in your Stripe dashboard
        return switch (packageId) {
            case "credits-50" -> stripePriceCredits50;
            case "credits-150" -> stripePriceCredits150;
            case "credits-500" -> stripePriceCredits500;
            case "lifetime" -> stripePriceLifetime;
            default -> throw new IllegalArgumentException("Unknown package: " + packageId);
        };
    }
}
