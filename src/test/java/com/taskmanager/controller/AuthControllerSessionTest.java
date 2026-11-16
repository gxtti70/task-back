package com.taskmanager.controller;

import com.taskmanager.config.AuthCookieService;
import com.taskmanager.dto.request.ForgotPasswordRequest;
import com.taskmanager.dto.request.ResetPasswordWithCodeRequest;
import com.taskmanager.dto.response.RecoveryRequestResponse;
import com.taskmanager.security.JwtTokenProvider;
import com.taskmanager.security.UserDetailsServiceImpl;
import com.taskmanager.service.ConsentService;
import com.taskmanager.service.NotificationEventService;
import com.taskmanager.service.PasswordRecoveryService;
import com.taskmanager.service.RefreshTokenService;
import com.taskmanager.service.SubscriptionService;
import com.taskmanager.repository.UserRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyString;
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
    @Mock PasswordRecoveryService passwordRecovery;
    @Mock RefreshTokenService refreshTokens;
    @Mock AuthCookieService authCookies;
    @Mock ConsentService consents;
    @InjectMocks AuthController controller;

    @Test
    void passwordResetRevokesRefreshSessionsAndClearsAuthCookies() {
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(passwordRecovery.verifyAndReset("user@example.test", "123456", "new-password")).thenReturn(true);
        ResetPasswordWithCodeRequest request = new ResetPasswordWithCodeRequest();
        request.setEmail("user@example.test");
        request.setCode("123456");
        request.setPassword("new-password");

        ResponseEntity<?> result = controller.resetPassword(request, response);

        assertEquals(200, result.getStatusCode().value());
        verify(passwordRecovery).verifyAndReset("user@example.test", "123456", "new-password");
        verify(authCookies).clear(response);
    }

    @Test
    void passwordResetRejectsAnInvalidCodeWithoutClearingCookies() {
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(passwordRecovery.verifyAndReset(anyString(), anyString(), anyString())).thenReturn(false);
        ResetPasswordWithCodeRequest request = new ResetPasswordWithCodeRequest();
        request.setEmail("user@example.test");
        request.setCode("000000");
        request.setPassword("new-password");

        ResponseEntity<?> result = controller.resetPassword(request, response);

        assertEquals(400, result.getStatusCode().value());
        verify(authCookies, never()).clear(response);
    }

    @Test
    void forgotPasswordAlwaysAnswersTheSameGenericMessage() {
        when(passwordRecovery.requestCode("nadie@example.test"))
                .thenReturn(new PasswordRecoveryService.RecoveryOutcome(60));
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("nadie@example.test");

        ResponseEntity<?> result = controller.forgotPassword(request);

        assertEquals(200, result.getStatusCode().value());
        RecoveryRequestResponse body = assertInstanceOf(RecoveryRequestResponse.class, result.getBody());
        assertEquals(60, body.getResendAvailableInSeconds());
        assertEquals("Si el email está registrado, recibirás un código de 6 dígitos para restablecer tu contraseña.",
                body.getMessage());
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
