package com.whatsappanalytics.whatsapp_analytics_backend.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.whatsappanalytics.whatsapp_analytics_backend.dto.LoginUserDto;
import com.whatsappanalytics.whatsapp_analytics_backend.dto.RegisterUserDto;
import com.whatsappanalytics.whatsapp_analytics_backend.dto.VerifyUserDto;
import com.whatsappanalytics.whatsapp_analytics_backend.model.User;
import com.whatsappanalytics.whatsapp_analytics_backend.repository.UserRepository;

@Service
public class AuthenticationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    public AuthenticationService(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 AuthenticationManager authenticationManager,
                                 EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.emailService = emailService;
    }

    public User signup(RegisterUserDto input) {
        // Check if user already exists (use findFirstByEmail to handle duplicates)
        Optional<User> existingUser = userRepository.findFirstByEmail(input.getEmail());
        
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            
            // If user is already enabled (verified), throw error
            if (user.isEnabled()) {
                throw new RuntimeException("User with this email already exists. Please sign in.");
            }
            
            // If user exists but is not verified, resend verification code
            user.setVerificationCode(generateVerificationCode());
            user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(15));
            sendVerificationEmail(user);
            return userRepository.save(user);
        }
        
        // Create new user
        User user = new User(input.getUsername(), input.getEmail(),
                passwordEncoder.encode(input.getPassword()));
        user.setVerificationCode(generateVerificationCode());
        user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(15));
        user.setEnabled(false);
        user.setRemainingCredits(3); // Default credits for new users
        user.setHasLifetimeAccess(false);
        sendVerificationEmail(user);

        return userRepository.save(user);
    }

    public User authenticate(LoginUserDto input) {
        // Use findFirstByEmail to handle duplicate emails
        User user = userRepository.findFirstByEmail(input.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Check if user is OAuth user (empty password)
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            throw new RuntimeException("This account was created with Google/Microsoft. Please sign in with OAuth.");
        }
        
        if (!user.isEnabled()) {
            throw new RuntimeException("User not verified. Please verify your email.");
        }
        
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(input.getEmail(), input.getPassword()));
        return user;
    }

    public User verifyUser(VerifyUserDto input) {
        // Use findFirstByEmail to handle cases where multiple users have the same email
        Optional<User> optionalUser = userRepository.findFirstByEmail(input.getEmail());
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            
            // Check if user is already verified
            if (user.isEnabled()) {
                throw new RuntimeException("User already verified.");
            }
            
            // Check if verification code exists
            if (user.getVerificationCode() == null) {
                throw new RuntimeException("No verification code found. Please request a new code.");
            }
            
            // Check if verification code has expired
            if (user.getVerificationCodeExpiresAt() == null || 
                user.getVerificationCodeExpiresAt().isBefore(LocalDateTime.now())) {
                throw new RuntimeException("Verification code expired. Please request a new code.");
            }
            
            // Compare verification codes (trim whitespace)
            String storedCode = user.getVerificationCode().trim();
            String providedCode = input.getVerificationCode() != null ? input.getVerificationCode().trim() : "";
            
            if (storedCode.equals(providedCode)) {
                user.setEnabled(true);
                user.setVerificationCode(null);
                user.setVerificationCodeExpiresAt(null);
                User verifiedUser = userRepository.save(user);
                return verifiedUser; // Return verified user for automatic login
            } else {
                throw new RuntimeException("Invalid verification code.");
            }
        } else {
            throw new RuntimeException("User not found.");
        }
    }

public void resendVerificationCode(String email) {
        // Use findFirstByEmail to handle cases where multiple users have the same email
        Optional<User> optionalUser = userRepository.findFirstByEmail(email);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (user.isEnabled()) {
                throw new RuntimeException("User already verified.");
            }
            user.setVerificationCode(generateVerificationCode());
            user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(15));
            sendVerificationEmail(user);
            userRepository.save(user);
        } else {
            throw new RuntimeException("User not found.");
        }
    }

    public void sendVerificationEmail(User user) {
        String subject = "Thank you for joining Chatlytics";
        String verificationCode = user.getVerificationCode();
        String htmlMessage = "<!DOCTYPE html>"
            + "<html lang=\"en\">"
            + "<head>"
            + "<meta charset=\"UTF-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
            + "<style>"
            + "@media only screen and (max-width: 600px) { .container { width: 100% !important; padding: 20px !important; } }"
            + "</style>"
            + "</head>"
            + "<body style=\"margin: 0; padding: 0; background-color: #fafafa; font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'SF Pro Text', 'Helvetica Neue', Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased; -moz-osx-font-smoothing: grayscale;\">"
            
            // Outer container with subtle gradient
            + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background: linear-gradient(180deg, #fafafa 0%, #f5f5f7 100%); padding: 80px 20px; min-height: 100vh;\">"
            + "<tr>"
            + "<td align=\"center\" valign=\"top\">"
            
            // Main email container - Apple style card
            + "<table width=\"600\" cellpadding=\"0\" cellspacing=\"0\" class=\"container\" style=\"background-color: #ffffff; border-radius: 20px; overflow: hidden; box-shadow: 0 2px 16px rgba(0, 0, 0, 0.08); max-width: 600px; margin: 0 auto;\">"
            
            // Spacer top
            + "<tr>"
            + "<td style=\"padding: 64px 48px 0 48px; background-color: #ffffff;\">"
            + "</td>"
            + "</tr>"
            
            // Logo/Brand area - Minimal
            + "<tr>"
            + "<td style=\"padding: 0 48px 32px 48px; background-color: #ffffff; text-align: left;\">"
            + "<div style=\"display: inline-block; padding: 8px 16px; background-color: #f0fdf4; border-radius: 12px;\">"
            + "<span style=\"color: #10b981; font-size: 20px; font-weight: 600; letter-spacing: -0.3px;\">Chatlytics</span>"
            + "</div>"
            + "</td>"
            + "</tr>"
            
            // Main content
            + "<tr>"
            + "<td style=\"padding: 0 48px 48px 48px; background-color: #ffffff;\">"
            
            // Welcome message - Apple style typography
            + "<h1 style=\"color: #1d1d1f; margin: 0 0 16px 0; font-size: 32px; font-weight: 600; letter-spacing: -0.5px; line-height: 1.2;\">"
            + "Welcome to Chatlytics"
            + "</h1>"
            
            + "<p style=\"color: #86868b; font-size: 17px; line-height: 1.47059; margin: 0 0 48px 0; font-weight: 400;\">"
            + "We're excited to have you on board. To get started, please verify your email address using the verification code below."
            + "</p>"
            
            // Verification code container - Apple style
            + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin: 0 0 32px 0;\">"
            + "<tr>"
            + "<td align=\"center\" style=\"padding: 48px 32px; background: linear-gradient(135deg, #f0fdf4 0%, #ecfdf5 100%); border-radius: 18px; border: 1px solid #d1fae5;\">"
            
            + "<p style=\"color: #6b7280; margin: 0 0 24px 0; font-size: 13px; font-weight: 500; text-transform: uppercase; letter-spacing: 0.5px;\">Verification Code</p>"
            
            + "<div style=\"background-color: #10b981; color: #ffffff; padding: 24px 48px; border-radius: 14px; display: inline-block; font-size: 36px; font-weight: 600; letter-spacing: 6px; font-family: 'SF Mono', 'Monaco', 'Menlo', 'Courier New', monospace; margin: 0 0 20px 0; box-shadow: 0 4px 12px rgba(16, 185, 129, 0.25);\">"
            + verificationCode
            + "</div>"
            
            + "<p style=\"color: #86868b; font-size: 15px; margin: 0; line-height: 1.5; font-weight: 400;\">Enter this code in the verification form</p>"
            
            + "</td>"
            + "</tr>"
            + "</table>"
            
            // Expiration notice - Apple style subtle
            + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin: 0 0 48px 0;\">"
            + "<tr>"
            + "<td style=\"background-color: #fff7ed; border-left: 3px solid #f59e0b; padding: 16px 20px; border-radius: 0 10px 10px 0;\">"
            + "<p style=\"color: #92400e; font-size: 14px; margin: 0; font-weight: 500; line-height: 1.5;\">This code expires in 15 minutes</p>"
            + "</td>"
            + "</tr>"
            + "</table>"
            
            + "</td>"
            + "</tr>"
            
            // Divider
            + "<tr>"
            + "<td style=\"padding: 0 48px; background-color: #ffffff;\">"
            + "<div style=\"height: 1px; background-color: #f5f5f7; margin: 0;\"></div>"
            + "</td>"
            + "</tr>"
            
            // Footer - Apple style minimal
            + "<tr>"
            + "<td style=\"padding: 32px 48px 48px 48px; background-color: #ffffff; text-align: center;\">"
            
            + "<p style=\"color: #86868b; font-size: 15px; margin: 0 0 12px 0; line-height: 1.5; font-weight: 400;\">"
            + "Need help? Contact us at <a href=\"mailto:chatlyticshelp@gmail.com\" style=\"color: #10b981; text-decoration: none; font-weight: 500;\">chatlyticshelp@gmail.com</a>"
            + "</p>"
            
            + "<p style=\"color: #86868b; font-size: 13px; margin: 0 0 24px 0; line-height: 1.5; font-weight: 400;\">"
            + "If you didn't create an account with Chatlytics, you can safely ignore this email."
            + "</p>"
            
            + "<p style=\"color: #86868b; font-size: 12px; margin: 0; padding-top: 24px; border-top: 1px solid #f5f5f7; line-height: 1.5; font-weight: 400;\">"
            + "© 2025 Chatlytics. All rights reserved."
            + "</p>"
            
            + "</td>"
            + "</tr>"
            
            // Spacer bottom
            + "<tr>"
            + "<td style=\"padding: 0 48px 48px 48px; background-color: #ffffff;\">"
            + "</td>"
            + "</tr>"
            
            + "</table>"
            
            + "</td>"
            + "</tr>"
            + "</table>"
            
            + "</body>"
            + "</html>";

        try {
            emailService.sendVerificationEmail(user.getEmail(), subject, htmlMessage);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send verification email.", e);
        }
    }

    private String generateVerificationCode() {
        // Simple 6-digit code generation
        int code = (int)(Math.random() * 900000) + 100000;
        return String.valueOf(code);
    }

    /**
     * Request password reset - sends verification code to user's email
     * @param email User's email address
     */
    public void requestPasswordReset(String email) {
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            // For security, don't reveal if email exists or not
            // Just return silently
            return;
        }

        User user = optionalUser.get();
        String resetCode = generateVerificationCode();
        user.setPasswordResetCode(resetCode);
        user.setPasswordResetCodeExpiresAt(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);

        // Send password reset email
        sendPasswordResetEmail(user, resetCode);
    }

    /**
     * Reset password using verification code
     * @param email User's email
     * @param verificationCode The 6-digit code sent via email
     * @param newPassword The new password to set
     */
    public void resetPassword(String email, String verificationCode, String newPassword) {
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            throw new RuntimeException("User not found.");
        }

        User user = optionalUser.get();
        
        // Check if reset code exists
        if (user.getPasswordResetCode() == null) {
            throw new RuntimeException("No password reset code found. Please request a new one.");
        }

        // Check if code has expired
        if (user.getPasswordResetCodeExpiresAt() != null && 
            user.getPasswordResetCodeExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Password reset code has expired. Please request a new one.");
        }

        // Validate code
        if (!user.getPasswordResetCode().equals(verificationCode)) {
            throw new RuntimeException("Invalid password reset code.");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetCode(null);
        user.setPasswordResetCodeExpiresAt(null);
        userRepository.save(user);
    }

    /**
     * Send password reset email with code
     */
    private void sendPasswordResetEmail(User user, String resetCode) {
        String subject = "Password Reset Code";
        String htmlMessage = "<html>"
        + "<body style=\"font-family: Arial, sans-serif;\">"
        + "<div style=\"background-color: #f5f5f5; padding: 20px;\">"
        + "<h2 style=\"color: #333;\">Password Reset Request</h2>"
        + "<p style=\"font-size: 16px;\">You requested to reset your password. Use the code below to reset it:</p>"
        + "<div style=\"background-color: #fff; padding: 20px; border-radius: 5px; box-shadow: 0 0 10px rgba(0,0,0,0.1);\">"
        + "<h3 style=\"color: #333;\">Reset Code:</h3>"
        + "<p style=\"font-size: 18px; font-weight: bold; color: #007bff;\">" + resetCode + "</p>"
        + "</div>"
        + "<p style=\"font-size: 14px; color: #666; margin-top: 20px;\">This code will expire in 15 minutes.</p>"
        + "<p style=\"font-size: 14px; color: #666;\">If you didn't request this, please ignore this email.</p>"
        + "</div>"
        + "</body>"
        + "</html>";

        try {
            emailService.sendVerificationEmail(user.getEmail(), subject, htmlMessage);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send password reset email.", e);
        }
    }
}
