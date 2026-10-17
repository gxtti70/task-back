package com.taskmanager.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

@Component
public class AuthCookieService {
    public static final String ACCESS_COOKIE = "access_token";
    public static final String REFRESH_COOKIE = "refresh_token";
    private final boolean secure;
    private final String sameSite;
    private final String domain;
    private final long accessSeconds;
    private final long refreshSeconds;

    public AuthCookieService(@Value("${jwt.cookie.secure:false}") boolean secure,
            @Value("${jwt.cookie.same-site:Lax}") String sameSite,
            @Value("${jwt.cookie.domain:}") String domain,
            @Value("${jwt.access.expiration:600000}") long accessMillis,
            @Value("${jwt.refresh.expiration:604800000}") long refreshMillis) {
        this.secure = secure;
        this.sameSite = sameSite;
        this.domain = domain;
        if (!"Lax".equalsIgnoreCase(sameSite) && !"Strict".equalsIgnoreCase(sameSite)
                && !"None".equalsIgnoreCase(sameSite)) {
            throw new IllegalArgumentException("JWT_COOKIE_SAME_SITE must be Lax, Strict, or None");
        }
        if ("None".equalsIgnoreCase(sameSite) && !secure) {
            throw new IllegalArgumentException("SameSite=None requires JWT_COOKIE_SECURE=true");
        }
        this.accessSeconds = accessMillis / 1000;
        this.refreshSeconds = refreshMillis / 1000;
    }

    public void setSession(HttpServletResponse response, String access, String refresh) {
        add(response, ACCESS_COOKIE, access, accessSeconds, true);
        add(response, REFRESH_COOKIE, refresh, refreshSeconds, true);
    }

    public void clear(HttpServletResponse response) {
        add(response, ACCESS_COOKIE, "", 0, true);
        add(response, REFRESH_COOKIE, "", 0, true);
    }

    public String read(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies()).filter(c -> name.equals(c.getName())).map(Cookie::getValue).findFirst().orElse(null);
    }

    private void add(HttpServletResponse response, String name, String value, long maxAge, boolean httpOnly) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value).httpOnly(httpOnly)
                .secure(secure).path("/").sameSite(sameSite).maxAge(Duration.ofSeconds(maxAge));
        if (domain != null && !domain.isBlank()) builder.domain(domain.trim());
        response.addHeader("Set-Cookie", builder.build().toString());
    }
}
