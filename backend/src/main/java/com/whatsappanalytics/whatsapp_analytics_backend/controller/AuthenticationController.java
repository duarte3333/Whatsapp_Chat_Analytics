package com.whatsappanalytics.whatsapp_analytics_backend.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.whatsappanalytics.whatsapp_analytics_backend.dto.ForgotPasswordRequestDto;
import com.whatsappanalytics.whatsapp_analytics_backend.dto.LoginUserDto;
import com.whatsappanalytics.whatsapp_analytics_backend.dto.RegisterUserDto;
import com.whatsappanalytics.whatsapp_analytics_backend.dto.ResendVerificationRequestDto;
import com.whatsappanalytics.whatsapp_analytics_backend.dto.ResetPasswordRequestDto;
import com.whatsappanalytics.whatsapp_analytics_backend.dto.VerifyUserDto;
import com.whatsappanalytics.whatsapp_analytics_backend.model.User;
import com.whatsappanalytics.whatsapp_analytics_backend.responses.LoginResponse;
import com.whatsappanalytics.whatsapp_analytics_backend.service.AuthenticationService;
import com.whatsappanalytics.whatsapp_analytics_backend.service.JwtService;

@RequestMapping("/auth")
@RestController
public class AuthenticationController {
    private final JwtService jwtService;

    private final AuthenticationService authenticationService;
    
    @Autowired
    private ObjectMapper objectMapper;

    public AuthenticationController(JwtService jwtService, AuthenticationService authenticationService) {
        this.jwtService = jwtService;
        this.authenticationService = authenticationService;
    }
    @PostMapping("/signup")
    public ResponseEntity<User> register(@RequestBody RegisterUserDto registerUserDto) {
        User registeredUser = authenticationService.signup(registerUserDto);
        // Return user without token - user must verify email first
        return ResponseEntity.ok(registeredUser);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> authenticate(@RequestBody LoginUserDto loginUserDto) {
        User authenticatedUser = authenticationService.authenticate(loginUserDto);
        String token = jwtService.generateToken(authenticatedUser);
        long expiresIn = jwtService.getExpirationTime();
        LoginResponse loginResponse = new LoginResponse(token, expiresIn, authenticatedUser);
        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyUser(@RequestBody VerifyUserDto verifyUserDto) {
        try {
            // Verify user email
            User verifiedUser = authenticationService.verifyUser(verifyUserDto);
            
            // After successful verification, automatically login the user
            String token = jwtService.generateToken(verifiedUser);
            long expiresIn = jwtService.getExpirationTime();
            LoginResponse loginResponse = new LoginResponse(token, expiresIn, verifiedUser);
            
            return ResponseEntity.ok(loginResponse);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/resend")
    public ResponseEntity<?> resendVerificationCode(@RequestBody Object request) {
        try {
            System.out.println("Resend request received. Type: " + (request != null ? request.getClass().getName() : "null"));
            System.out.println("Resend request value: " + request);
            
            String email = null;
            
            // Handle both JSON object {"email": "..."} and plain string "email@example.com"
            if (request instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> requestMap = (Map<String, Object>) request;
                System.out.println("Request is a Map. Keys: " + requestMap.keySet());
                Object emailObj = requestMap.get("email");
                if (emailObj != null) {
                    email = emailObj.toString().trim();
                    System.out.println("Extracted email from Map: " + email);
                } else {
                    System.out.println("No 'email' key found in Map");
                }
            } else if (request instanceof String) {
                // Handle plain string (with or without quotes)
                String str = (String) request;
                System.out.println("Request is a String: " + str);
                email = str.trim().replaceAll("^\"|\"$", ""); // Remove surrounding quotes if present
                System.out.println("Extracted email from String: " + email);
            } else {
                System.out.println("Request is neither Map nor String. Type: " + request.getClass().getName());
                // Try to deserialize as ResendVerificationRequestDto
                try {
                    ResendVerificationRequestDto dto = objectMapper.convertValue(request, ResendVerificationRequestDto.class);
                    email = dto.getEmail();
                    System.out.println("Extracted email from DTO conversion: " + email);
                } catch (Exception e) {
                    System.out.println("Failed to convert to DTO: " + e.getMessage());
                    // Ignore and try to extract email from toString
                    String requestStr = request.toString();
                    if (requestStr.contains("@")) {
                        // Try to extract email from string representation
                        email = requestStr.trim().replaceAll("^\"|\"$", "");
                        System.out.println("Extracted email from toString: " + email);
                    }
                }
            }
            
            if (email == null || email.trim().isEmpty()) {
                System.out.println("Email is null or empty after extraction");
                return ResponseEntity.badRequest().body(Map.of("error", "Email is required. Expected format: {\"email\": \"your@email.com\"} or \"your@email.com\""));
            }
            
            System.out.println("Calling resendVerificationCode for email: " + email);
            authenticationService.resendVerificationCode(email.trim());
            System.out.println("Verification code resent successfully");
            return ResponseEntity.ok(Map.of("message", "Verification code resent successfully."));
        } catch (RuntimeException e) {
            System.err.println("Error in resendVerificationCode: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.err.println("Unexpected error in resendVerificationCode: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", "An error occurred: " + e.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequestDto request) {
        try {
            authenticationService.requestPasswordReset(request.getEmail());
            // Always return 200 for security (don't reveal if email exists)
            return ResponseEntity.ok(Map.of("message", "If the email exists, a password reset code has been sent."));
        } catch (Exception e) {
            // Still return 200 to avoid email enumeration attacks
            return ResponseEntity.ok(Map.of("message", "If the email exists, a password reset code has been sent."));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequestDto request) {
        try {
            authenticationService.resetPassword(
                request.getEmail(),
                request.getVerificationCode(),
                request.getNewPassword()
            );
            return ResponseEntity.ok(Map.of("message", "Password reset successfully."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/user")
    public ResponseEntity<User> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        try {
            User user = (User) authentication.getPrincipal();

            if (user == null) {
                return ResponseEntity.status(401).build();
            }

            System.out.println("Fetching user info for: " + user.getEmail());
            return ResponseEntity.ok(user);

        } catch (Exception e) {
            System.err.println("Error fetching current user: " + e.getMessage());
            return ResponseEntity.status(401).build();
        }
    }

    

}
