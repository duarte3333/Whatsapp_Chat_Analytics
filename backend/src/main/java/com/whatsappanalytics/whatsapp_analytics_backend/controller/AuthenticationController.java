package com.whatsappanalytics.whatsapp_analytics_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.whatsappanalytics.whatsapp_analytics_backend.dto.LoginUserDto;
import com.whatsappanalytics.whatsapp_analytics_backend.dto.RegisterUserDto;
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

    public AuthenticationController(JwtService jwtService, AuthenticationService authenticationService) {
        this.jwtService = jwtService;
        this.authenticationService = authenticationService;
    }
    @PostMapping("/signup")
    public ResponseEntity<User> register(@RequestBody RegisterUserDto registerUserDto) {
        User registeredUser = authenticationService.signup(registerUserDto);
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
    public ResponseEntity<String> verifyUser(@RequestBody VerifyUserDto verifyUserDto) {
        try {
            authenticationService.verifyUser(verifyUserDto);
            return ResponseEntity.ok("Account verified successfully.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/resend")
    public ResponseEntity<?> resendVerificationCode(@RequestBody String email) {
        try {
            authenticationService.resendVerificationCode(email);
            return ResponseEntity.ok("Verification code resent successfully.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
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
