package com.whatsappanalytics.whatsapp_analytics_backend.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.whatsappanalytics.whatsapp_analytics_backend.dto.CheckoutSessionResponseDto;
import com.whatsappanalytics.whatsapp_analytics_backend.dto.PaymentRequestDto;
import com.whatsappanalytics.whatsapp_analytics_backend.dto.AvailablePackagesResponseDto;
import com.whatsappanalytics.whatsapp_analytics_backend.dto.PackageDto;
import com.whatsappanalytics.whatsapp_analytics_backend.dto.LifetimePackageDto;
import com.whatsappanalytics.whatsapp_analytics_backend.model.User;
import com.whatsappanalytics.whatsapp_analytics_backend.service.PaymentService;
import com.whatsappanalytics.whatsapp_analytics_backend.service.StripeWebhookService;
import lombok.extern.slf4j.Slf4j;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payments")
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final StripeWebhookService webhookService;

    @Value("${stripe.price.prompts-5}")
    private String stripePricePrompts5;

    @Value("${stripe.price.prompts-10}")
    private String stripePricePrompts10;

    @Value("${stripe.price.prompts-20}")
    private String stripePricePrompts20;

    @Value("${stripe.price.lifetime}")
    private String stripePriceLifetime;

    public PaymentController(PaymentService paymentService, StripeWebhookService webhookService) {
        this.paymentService = paymentService;
        this.webhookService = webhookService;
    }

    @GetMapping("/packages")
    public ResponseEntity<AvailablePackagesResponseDto> getAvailablePackages() {
        log.info("===== GET AVAILABLE PACKAGES REQUEST =====");
        
        try {
            // Credit packages
            List<PackageDto> creditPackages = Arrays.asList(
                new PackageDto(
                    "prompts-5",
                    5,
                    99,
                    "0.99€",
                    false
                ),
                new PackageDto(
                    "prompts-10",
                    10,
                    149,
                    "1.49€",
                    true
                ),
                new PackageDto(
                    "prompts-20",
                    20,
                    229,
                    "2.29€",
                    false
                )
            );
            
            // Lifetime package
            LifetimePackageDto lifetimePackage = new LifetimePackageDto(
                "lifetime",
                2599,
                "25.99€",
                "Lifetime Access",
                Arrays.asList(
                    "Unlimited prompts forever",
                    "No prompt usage or renewal",
                    "Access to all RAG features",
                    "Priority support"
                )
            );
            
            AvailablePackagesResponseDto response = new AvailablePackagesResponseDto(
                creditPackages,
                lifetimePackage
            );
            
            log.info("===== AVAILABLE PACKAGES RETRIEVED SUCCESSFULLY =====");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("✗ Error retrieving available packages", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> createCheckoutSession(@RequestBody PaymentRequestDto request) {
        try {
            log.info("Step 1: Attempting to retrieve authentication context");
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated()) {
                log.error("User not authenticated");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
            }
            
            User currentUser = (User) authentication.getPrincipal();
            
            if (currentUser == null) {
                log.error("Current user is null");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not found"));
            }

            CheckoutSessionResponseDto response = paymentService.createCheckoutSession(request, currentUser);
            
            log.info("Step 7: Checkout session created successfully - SessionId: {}", response.getSessionId());
            log.info("Step 8: Returning checkout URL to frontend");
            log.info("===== CHECKOUT REQUEST COMPLETED SUCCESSFULLY =====");
            
            return ResponseEntity.ok(response);
        } catch (StripeException e) {
            log.error("✗ Stripe exception occurred - Error message: {}, Status code: {}", e.getMessage(), e.getStatusCode(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Payment processing failed", "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.error("✗ Invalid package ID - Error message: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Invalid request", "message", e.getMessage()));
        } catch (ClassCastException e) {
            log.error("✗ ClassCastException - Failed to cast principal to User: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication error", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("✗ Unexpected exception in checkout endpoint", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An unexpected error occurred", "message", e.getMessage()));
        }
    }

    @PostMapping("/payment-intent")
    public ResponseEntity<?> createPaymentIntent(
            @RequestBody Map<String, Object> request,
            Principal principal) {
        log.info("===== PAYMENT INTENT REQUEST RECEIVED =====");
        log.info("1️⃣ Request body: {}", request);
        
        try {
            // 2️⃣ Validate request body
            String type = (String) request.get("type");
            String packageId = (String) request.get("packageId");
            Long amount = null;
            
            Object amountObj = request.get("amount");
            if (amountObj != null) {
                if (amountObj instanceof Number) {
                    amount = ((Number) amountObj).longValue();
                }
            }
            
            log.info("2️⃣ Extracted type: {}, packageId: {}, amount: {}", type, packageId, amount);
            
            if (type == null || packageId == null || amount == null) {
                log.warn("⚠ Missing required fields in request");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Missing required fields", "required", Arrays.asList("type", "packageId", "amount")));
            }
            
            // 3️⃣ Extract authenticated user
            Authentication authentication = (Authentication) principal;
            User user = (User) authentication.getPrincipal();
            log.info("3️⃣ Authenticated user: {}, Email: {}", user.getUsername(), user.getEmail());
            
            // 4️⃣ Call payment service
            log.info("4️⃣ Calling paymentService.createPaymentIntent()");
            Map<String, String> result = paymentService.createPaymentIntent(type, packageId, amount, user);
            
            // 5️⃣ Extract response
            String paymentIntentId = result.get("paymentIntentId");
            String clientSecret = result.get("clientSecret");
            log.info("5️⃣ Payment intent created - ID: {}, Secret obtained: {}", paymentIntentId, clientSecret != null ? "✓" : "✗");
            
            // 6️⃣ Build response
            Map<String, Object> response = new HashMap<>();
            response.put("paymentIntentId", paymentIntentId);
            response.put("clientSecret", clientSecret);
            log.info("6️⃣ Response built successfully");
            
            // 7️⃣ Log security info
            log.info("7️⃣ Frontend will use clientSecret: {} for card confirmation", clientSecret != null ? "✓ Set" : "✗ Missing");
            
            // 8️⃣ Log completion
            log.info("8️⃣ Returning response with payment intent details");
            
            // 9️⃣ Return response
            log.info("===== PAYMENT INTENT REQUEST COMPLETED SUCCESSFULLY =====");
            return ResponseEntity.ok(response);
            
        } catch (ClassCastException e) {
            log.error("✗ ClassCastException - Failed to cast principal to User: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication error", "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.error("✗ Invalid package ID - Error message: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Invalid request", "message", e.getMessage()));
        } catch (StripeException e) {
            log.error("✗ Stripe exception occurred - Error message: {}, Status code: {}", e.getMessage(), e.getStatusCode(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Payment processing failed", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("✗ Unexpected exception in payment intent endpoint", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An unexpected error occurred", "message", e.getMessage()));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signatureHeader) {
        log.info("===== WEBHOOK ENDPOINT HIT =====");
        log.info("Signature header present: {}", signatureHeader != null);
        log.info("Payload length: {}", payload != null ? payload.length() : "null");
        log.info("===== WEBHOOK RECEIVED =====");
        
        if (signatureHeader == null) {
            log.warn("⚠ No Stripe-Signature header found!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"error\": \"No signature header\"}");
        }
        
        try {
            webhookService.handleWebhookEvent(payload, signatureHeader);
            log.info("===== WEBHOOK PROCESSED SUCCESSFULLY =====");
            return ResponseEntity.ok("{\"status\": \"success\"}");
        } catch (SignatureVerificationException e) {
            log.error("✗ Signature verification failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("{\"error\": \"Invalid signature\"}");
        } catch (Exception e) {
            log.error("✗ Error processing webhook: {}", e.getMessage(), e);
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"error\": \"Processing failed\"}");
        }
    }
}
