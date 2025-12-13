package com.whatsappanalytics.whatsapp_analytics_backend.service;

import com.whatsappanalytics.whatsapp_analytics_backend.dto.LoginUserDto;
import com.whatsappanalytics.whatsapp_analytics_backend.dto.RegisterUserDto;
import com.whatsappanalytics.whatsapp_analytics_backend.dto.VerifyUserDto;
import com.whatsappanalytics.whatsapp_analytics_backend.model.User;
import com.whatsappanalytics.whatsapp_analytics_backend.repository.UserRepository;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthenticationService authService;

    private RegisterUserDto registerDto;
    private LoginUserDto loginDto;
    private User testUser;

    @BeforeEach
    void setUp() {
        registerDto = new RegisterUserDto();
        registerDto.setUsername("john");
        registerDto.setEmail("john@example.com");
        registerDto.setPassword("pass123");

        loginDto = new LoginUserDto();
        loginDto.setEmail("john@example.com");
        loginDto.setPassword("pass123");

        testUser = new User("john", "john@example.com", "encodedPass");
        testUser.setEnabled(true);
    }

    @Test
    void signup_shouldCreateUserAndSendEmail() throws MessagingException {
        when(passwordEncoder.encode(registerDto.getPassword())).thenReturn("encodedPass");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = authService.signup(registerDto);

        assertEquals(registerDto.getUsername(), user.getDisplayName());  // Check display name
        assertEquals(registerDto.getEmail(), user.getUsername());        // getUsername() now returns email
        assertFalse(user.isEnabled());
        assertNotNull(user.getVerificationCode());
        assertNotNull(user.getVerificationCodeExpiresAt());

        verify(emailService).sendVerificationEmail(
                eq(user.getEmail()),
                anyString(),
                anyString()
        );
        verify(userRepository).save(user);
    }

    @Test
    void authenticate_shouldThrowIfUserNotFound() {
        when(userRepository.findByEmail(loginDto.getEmail())).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                authService.authenticate(loginDto));

        assertEquals("User not found", exception.getMessage());
    }

    @Test
    void authenticate_shouldThrowIfUserNotVerified() {
        testUser.setEnabled(false);
        when(userRepository.findByEmail(loginDto.getEmail())).thenReturn(Optional.of(testUser));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                authService.authenticate(loginDto));

        assertEquals("User not verified. Please verify your email.", exception.getMessage());
    }

    @Test
    void authenticate_shouldReturnUserIfVerified() {
        when(userRepository.findByEmail(loginDto.getEmail())).thenReturn(Optional.of(testUser));

        User result = authService.authenticate(loginDto);

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        assertEquals(testUser, result);
    }

    @Test
    void verifyUser_shouldEnableUserWhenCodeValid() {
        testUser.setVerificationCode("123456");
        testUser.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(5));
        testUser.setEnabled(false);

        VerifyUserDto verifyDto = new VerifyUserDto();
        verifyDto.setEmail(testUser.getEmail());
        verifyDto.setVerificationCode("123456");

        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.verifyUser(verifyDto);

        assertTrue(testUser.isEnabled());
        assertNull(testUser.getVerificationCode());
        assertNull(testUser.getVerificationCodeExpiresAt());
    }
}
