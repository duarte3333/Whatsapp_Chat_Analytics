package com.whatsappanalytics.whatsapp_analytics_backend.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.whatsappanalytics.whatsapp_analytics_backend.dto.CheckoutSessionResponseDto;
import com.whatsappanalytics.whatsapp_analytics_backend.dto.PaymentRequestDto;
import com.whatsappanalytics.whatsapp_analytics_backend.model.User;
import com.whatsappanalytics.whatsapp_analytics_backend.service.PaymentService;
import com.whatsappanalytics.whatsapp_analytics_backend.service.StripeWebhookService;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final StripeWebhookService webhookService;

    public PaymentController(PaymentService paymentService, StripeWebhookService webhookService) {
        this.paymentService = paymentService;
        this.webhookService = webhookService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> createCheckoutSession(@RequestBody PaymentRequestDto request) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = (User) authentication.getPrincipal();

            CheckoutSessionResponseDto response = paymentService.createCheckoutSession(request, currentUser);
            return ResponseEntity.ok(response);
        } catch (StripeException e) {
            log.error("Stripe exception occurred", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Stripe error: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.error("Invalid package ID", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid package: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error in checkout", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Unexpected error: " + e.getMessage()));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signatureHeader) {
        log.info("=== WEBHOOK RECEIVED ===");
        log.info("Signature Header: {}", signatureHeader);
        log.info("Payload length: {}", payload.length());
        log.info("Payload: {}", payload);
        
        try {
            webhookService.handleWebhookEvent(payload, signatureHeader);
            log.info("Webhook processed successfully");
            return ResponseEntity.ok("Webhook processed successfully");
        } catch (SignatureVerificationException e) {
            log.error("Signature verification failed", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Signature verification failed");
        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing webhook");
        }
    }
}
