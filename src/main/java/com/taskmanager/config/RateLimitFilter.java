package com.taskmanager.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador de tasa en memoria (ventana deslizante) para los endpoints de autenticación.
 * Sin dependencias externas. No sustituye a un limitador distribuido (Redis) en prod,
 * pero elimina fuerza bruta trivial de un solo origen.
 *
 * <p>La recuperación por código de 6 dígitos añade además un límite por cuenta en la
 * base de datos ({@code password_reset_codes.attempts}): este filtro solo conoce la IP
 * de origen y no protege un código corto frente a un atacante distribuido.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(int max, long windowMs) {}

    private static final Map<String, Rule> RULES = Map.of(
            "POST /api/auth/login", new Rule(10, 60_000),
            "POST /api/auth/register", new Rule(5, 600_000),
            "POST /api/auth/forgot-password", new Rule(5, 600_000),
            "POST /api/auth/reset-password", new Rule(10, 600_000)
    );
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = RULES.get(req.getMethod() + " " + req.getRequestURI());
        if (rule == null) {
            chain.doFilter(req, res);
            return;
        }

        String key = clientIp(req) + " " + req.getRequestURI();
        long now = System.currentTimeMillis();
        Deque<Long> stamps = hits.computeIfAbsent(key, k -> new ArrayDeque<>());

        boolean blocked;
        synchronized (stamps) {
            while (!stamps.isEmpty() && now - stamps.peekFirst() > rule.windowMs()) {
                stamps.pollFirst();
            }
            if (stamps.size() >= rule.max()) {
                blocked = true;
            } else {
                stamps.addLast(now);
                blocked = false;
            }
        }

        if (blocked) {
            res.setStatus(429);
            res.setHeader("Retry-After", Long.toString(rule.windowMs() / 1000));
            res.setContentType("application/json");
            res.getWriter().write("{\"message\":\"Demasiadas solicitudes. Intenta de nuevo más tarde.\"}");
            return;
        }

        chain.doFilter(req, res);
    }

    private String clientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
