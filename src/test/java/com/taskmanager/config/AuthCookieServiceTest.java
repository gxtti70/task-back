package com.taskmanager.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AuthCookieServiceTest {
    @Test
    void setsHttpOnlyLaxCookiesWithConfiguredExpiryInDevelopment() {
        AuthCookieService cookies = new AuthCookieService(false, "Lax", "", 600_000, 604_800_000);
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookies.setSession(response, "access-jwt", "refresh-jwt");

        List<String> headers = response.getHeaders("Set-Cookie").stream().map(Object::toString).toList();
        assertTrue(headers.stream().anyMatch(value -> value.startsWith("access_token=access-jwt")
                && value.contains("HttpOnly") && value.contains("SameSite=Lax") && value.contains("Max-Age=600")
                && !value.contains("Secure")));
        assertTrue(headers.stream().anyMatch(value -> value.startsWith("refresh_token=refresh-jwt")
                && value.contains("HttpOnly") && value.contains("SameSite=Lax") && value.contains("Max-Age=604800")));
    }

    @Test
    void requiresSecureCookiesForCrossSiteNoneAndAppliesProductionDomain() {
        assertThrows(IllegalArgumentException.class, () -> new AuthCookieService(false, "None", "", 600_000, 604_800_000));
        AuthCookieService cookies = new AuthCookieService(true, "None", ".example.com", 600_000, 604_800_000);
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookies.setSession(response, "access-jwt", "refresh-jwt");

        String accessCookie = response.getHeaders("Set-Cookie").iterator().next();
        assertTrue(accessCookie.contains("Domain=.example.com"));
        assertTrue(accessCookie.contains("Secure"));
        assertTrue(accessCookie.contains("SameSite=None"));
        assertTrue(accessCookie.contains("HttpOnly"));
    }

    @Test
    void clearingCookiesUsesZeroMaxAge() {
        AuthCookieService cookies = new AuthCookieService(false, "Lax", "", 600_000, 604_800_000);
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookies.clear(response);

        assertEquals(2, response.getHeaders("Set-Cookie").size());
        assertTrue(response.getHeaders("Set-Cookie").stream().allMatch(value -> value.toString().contains("Max-Age=0")));
    }
}
