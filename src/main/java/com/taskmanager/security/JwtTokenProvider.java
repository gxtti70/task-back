package com.taskmanager.security;

import com.taskmanager.config.JwtConfig;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private final JwtConfig jwtConfig;
    private Key signingKey;

    @PostConstruct
    protected void init() {
        this.signingKey = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();
        return Jwts.builder()
                .setSubject(userPrincipal.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtConfig.getAccessExpiration()))
                .signWith(signingKey, SignatureAlgorithm.HS512)
                .compact();
    }

    public String generateRefreshToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();
        return Jwts.builder()
                .setSubject(userPrincipal.getUsername())
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtConfig.getRefreshExpiration()))
                .signWith(signingKey, SignatureAlgorithm.HS512)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(authToken);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.error("Firma JWT inválida: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("El token JWT ha expirado: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("Token JWT no soportado: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("La cadena de claims JWT está vacía: {}", e.getMessage());
        }
        return false;
    }
}
