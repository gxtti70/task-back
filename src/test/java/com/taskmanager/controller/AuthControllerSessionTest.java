package com.taskmanager.controller;

import com.taskmanager.config.AuthCookieService;
import com.taskmanager.entity.PasswordResetToken;
import com.taskmanager.entity.User;
import com.taskmanager.repository.PasswordResetTokenRepository;
import com.taskmanager.repository.UserRepository;
import com.taskmanager.security.JwtTokenProvider;
import com.taskmanager.security.UserDetailsServiceImpl;
import com.taskmanager.service.EmailService;
import com.taskmanager.service.NotificationEventService;
import com.taskmanager.service.RefreshTokenService;
import com.taskmanager.service.SubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerSessionTest {
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
    @InjectMocks AuthController controller;

    @Test
    void passwordResetRevokesRefreshSessionsAndClearsAuthCookies() {
        User user = User.builder().id(java.util.UUID.randomUUID()).email("user@example.test").build();
        PasswordResetToken token = PasswordResetToken.builder().user(user).token("reset-token")
                .expiresAt(LocalDateTime.now().plusMinutes(20)).used(false).build();
        when(passwordResetTokenRepository.findByToken("reset-token")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-password");
        HttpServletResponse response = mock(HttpServletResponse.class);
        com.taskmanager.dto.request.ResetPasswordRequest request = new com.taskmanager.dto.request.ResetPasswordRequest();
        request.setToken("reset-token");
        request.setPassword("new-password");

        ResponseEntity<?> result = controller.resetPassword(request, response);

        assertEquals(200, result.getStatusCode().value());
        assertEquals("encoded-password", user.getPassword());
        verify(refreshTokens).revokeUser(user.getId());
        verify(authCookies).clear(response);
        verify(passwordResetTokenRepository).save(token);
    }

    @Test
    void logoutRevokesTheRefreshFamilyAndClearsCookies() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(authCookies.read(request, AuthCookieService.REFRESH_COOKIE)).thenReturn("refresh-cookie");

        ResponseEntity<?> result = controller.logout(request, response);

        assertEquals(200, result.getStatusCode().value());
        verify(refreshTokens).revoke("refresh-cookie");
        verify(authCookies).clear(response);
    }
}
