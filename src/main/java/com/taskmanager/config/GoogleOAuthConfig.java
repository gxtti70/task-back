package com.taskmanager.config;

import com.taskmanager.security.GoogleLoginFailureHandler;
import com.taskmanager.security.GoogleLoginSuccessHandler;
import com.taskmanager.service.GoogleAccountService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import com.taskmanager.security.JwtTokenProvider;
import com.taskmanager.security.UserDetailsServiceImpl;
import com.taskmanager.service.RefreshTokenService;

@Configuration
@ConditionalOnProperty(prefix = "taskmanager.auth.google", name = "enabled", havingValue = "true")
public class GoogleOAuthConfig {
    @Bean
    ClientRegistrationRepository googleClientRegistrationRepository(
            @Value("${GOOGLE_CLIENT_ID:}") String clientId,
            @Value("${GOOGLE_CLIENT_SECRET:}") String clientSecret,
            @Value("${GOOGLE_REDIRECT_URI:http://localhost:4300/login/oauth2/code/google}") String redirectUri) {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new IllegalStateException("GOOGLE_CLIENT_ID y GOOGLE_CLIENT_SECRET son obligatorios cuando GOOGLE_OAUTH_ENABLED=true.");
        }
        ClientRegistration google = ClientRegistration.withRegistrationId("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientName("Google")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .scope("openid", "profile", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .userNameAttributeName("sub")
                .issuerUri("https://accounts.google.com")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .clientAuthenticationMethod(org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .build();
        return new InMemoryClientRegistrationRepository(google);
    }

    @Bean
    GoogleLoginSuccessHandler googleLoginSuccessHandler(GoogleAccountService accounts,
            UserDetailsServiceImpl userDetails, JwtTokenProvider tokens, RefreshTokenService refreshTokens,
            AuthCookieService authCookies, @Value("${taskmanager.billing.frontendUrl}") String frontendUrl) {
        return new GoogleLoginSuccessHandler(accounts, userDetails, tokens, refreshTokens, authCookies, frontendUrl);
    }

    @Bean
    GoogleLoginFailureHandler googleLoginFailureHandler(
            @Value("${taskmanager.billing.frontendUrl}") String frontendUrl) {
        return new GoogleLoginFailureHandler(frontendUrl);
    }
}
