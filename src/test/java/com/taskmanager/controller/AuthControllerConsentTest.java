package com.taskmanager.controller;

import com.taskmanager.config.AuthCookieService;
import com.taskmanager.dto.request.RegistrationRequest;
import com.taskmanager.entity.User;
import com.taskmanager.repository.PasswordResetTokenRepository;
import com.taskmanager.repository.UserRepository;
import com.taskmanager.security.JwtTokenProvider;
import com.taskmanager.security.UserDetailsServiceImpl;
import com.taskmanager.service.ConsentService;
import com.taskmanager.service.EmailService;
import com.taskmanager.service.NotificationEventService;
import com.taskmanager.service.RefreshTokenService;
import com.taskmanager.service.SubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * El registro público exige la autorización previa, expresa e informada y deja
 * constancia de ella (Ley 1581 de 2012, art. 9).
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerConsentTest {
    @Mock AuthenticationManager authenticationManager;
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider tokenProvider;
    @Mock UserDetailsServiceImpl userDetailsService;
    @Mock NotificationEventService notifications;
    @Mock SubscriptionService subscriptions;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock EmailService emailService;
    @Mock RefreshTokenService refreshTokens;
    @Mock AuthCookieService authCookies;
    @Mock ConsentService consents;
    @InjectMocks AuthController controller;

    private RegistrationRequest request(boolean acceptTerms, boolean acceptPrivacy) {
        RegistrationRequest request = new RegistrationRequest();
        request.setFullName("New User");
        request.setEmail("new@example.test");
        request.setPassword("secret123");
        request.setAcceptTerms(acceptTerms);
        request.setAcceptPrivacy(acceptPrivacy);
        request.setTermsVersion("1.0");
        request.setPrivacyVersion("1.0");
        return request;
    }

    @Test
    void rejectsRegistrationWithoutConsent() {
        HttpServletRequest http = mock(HttpServletRequest.class);

        ResponseEntity<?> result = controller.registerUser(request(false, false), http);

        assertEquals(400, result.getStatusCode().value());
        verifyNoInteractions(userRepository);
        verify(consents, never()).recordRegistration(any(), any(), any(), any());
    }

    @Test
    void rejectsRegistrationWithStaleDocumentVersions() {
        HttpServletRequest http = mock(HttpServletRequest.class);
        when(consents.versionsMatch("1.0", "1.0")).thenReturn(false);

        ResponseEntity<?> result = controller.registerUser(request(true, true), http);

        assertEquals(400, result.getStatusCode().value());
        verifyNoInteractions(userRepository);
        verify(consents, never()).recordRegistration(any(), any(), any(), any());
    }

    @Test
    void recordsConsentProofOnValidRegistration() {
        HttpServletRequest http = mock(HttpServletRequest.class);
        when(consents.versionsMatch("1.0", "1.0")).thenReturn(true);
        when(userRepository.existsByEmailIgnoreCase("new@example.test")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> result = controller.registerUser(request(true, true), http);

        assertEquals(201, result.getStatusCode().value());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        verify(consents).recordRegistration(eq(saved.getValue()), eq("1.0"), eq("1.0"), eq(http));
    }
}
