package com.taskmanager.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;

public class GoogleLoginFailureHandler implements AuthenticationFailureHandler {
    private final String frontendUrl;

    public GoogleLoginFailureHandler(String frontendUrl) { this.frontendUrl = frontendUrl; }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        response.sendRedirect(frontendUrl + "/auth/login?googleError=1");
    }
}
