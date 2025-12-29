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
        User user = new User(input.getUsername(), input.getEmail(),
                passwordEncoder.encode(input.getPassword()));
        user.setVerificationCode(generateVerificationCode());
        user.setVerificationCodeExpiresAt( LocalDateTime.now().plusMinutes(15));
        user.setEnabled(false);
        sendVerificationEmail(user);

        return userRepository.save(user);
    }

    public User authenticate(LoginUserDto input) {
        User user = userRepository.findByEmail(input.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!user.isEnabled()) {
            throw new RuntimeException("User not verified. Please verify your email.");
        }
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(input.getEmail(), input.getPassword()));
        return user;
    }

    public User verifyUser(VerifyUserDto input) {
        Optional<User> optionalUser = userRepository.findByEmail(input.getEmail());
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (user.getVerificationCodeExpiresAt().isBefore(LocalDateTime.now())) {
                throw new RuntimeException("Verification code expired.");
            }
            if (user.getVerificationCode().equals(input.getVerificationCode())) {
                user.setEnabled(true);
                user.setVerificationCode(null);
                user.setVerificationCodeExpiresAt(null);
                User verifiedUser = userRepository.save(user);
                return verifiedUser; // Return verified user for automatic login
            }
            else {
                throw new RuntimeException("Invalid verification code.");
            }
        } else {
            throw new RuntimeException("User not found.");
        }
    }

public void resendVerificationCode(String email) {
        Optional<User> optionalUser = userRepository.findByEmail(email);
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
        String subject = "Your Verification Code";
        String verificationCode = user.getVerificationCode();
        String htmlMessage = "<html>"
        + "<body style=\"font-family: Arial, sans-serif;\">"
        + "<div style=\"background-color: #f5f5f5; padding: 20px;\">"
        + "<h2 style=\"color: #333;\">Welcome to our app!</h2>"
        + "<p style=\"font-size: 16px;\">Please enter the verification code below to continue:</p>"
        + "<div style=\"background-color: #fff; padding: 20px; border-radius: 5px; box-shadow: 0 0 10px rgba(0,0,0,0.1);\">"
        + "<h3 style=\"color: #333;\">Verification Code:</h3>"
        + "<p style=\"font-size: 18px; font-weight: bold; color: #007bff;\">" + verificationCode + "</p>"
        + "</div>"
        + "</div>"
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
