package com.taskmanager.security;

import com.taskmanager.config.AuthCookieService;
import com.taskmanager.entity.User;
import com.taskmanager.service.GoogleAccountService;
import com.taskmanager.service.RefreshTokenService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;

@Slf4j
public class GoogleLoginSuccessHandler implements AuthenticationSuccessHandler {
    private final GoogleAccountService accounts;
    private final UserDetailsServiceImpl userDetails;
    private final JwtTokenProvider tokens;
    private final RefreshTokenService refreshTokens;
    private final AuthCookieService authCookies;
    private final String frontendUrl;

    public GoogleLoginSuccessHandler(GoogleAccountService accounts, UserDetailsServiceImpl userDetails,
            JwtTokenProvider tokens, RefreshTokenService refreshTokens, AuthCookieService authCookies, String frontendUrl) {
        this.accounts = accounts; this.userDetails = userDetails; this.tokens = tokens;
        this.refreshTokens = refreshTokens; this.authCookies = authCookies; this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        try {
            User user = accounts.findOrCreate((OAuth2User) authentication.getPrincipal(), request);
            UserDetails details = userDetails.loadUserByUsername(user.getEmail());
            Authentication appAuthentication = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
            String refresh = refreshTokens.create(user, appAuthentication);
            authCookies.setSession(response, tokens.generateAccessToken(appAuthentication), refresh);
            invalidateOAuthSession(request);
            response.sendRedirect(frontendUrl + "/auth/google/callback");
        } catch (RuntimeException error) {
            log.warn("Google sign-in was rejected: {}", error.getMessage());
            authCookies.clear(response);
            invalidateOAuthSession(request);
            response.sendRedirect(frontendUrl + "/auth/login?googleError=1");
        }
    }

    private void invalidateOAuthSession(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
    }
}
