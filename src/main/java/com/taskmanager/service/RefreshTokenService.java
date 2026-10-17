package com.taskmanager.service;

import com.taskmanager.entity.RefreshToken;
import com.taskmanager.entity.User;
import com.taskmanager.repository.RefreshTokenRepository;
import com.taskmanager.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository repository;
    private final JwtTokenProvider tokens;
    private final UserDetailsService userDetailsService;

    @Transactional
    public String create(User user, Authentication authentication) {
        UUID family = UUID.randomUUID();
        String token = tokens.generateRefreshToken(authentication, family);
        repository.save(record(token, user, family));
        return token;
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public String rotate(String rawToken) {
        RefreshToken current = repository.findForUpdateByTokenHash(hash(rawToken)).orElse(null);
        if (current == null) throw new InvalidRefreshTokenException();
        if (current.isRevoked()) {
            revokeFamily(current.getFamilyId());
            throw new InvalidRefreshTokenException();
        }
        if (current.getExpiresAt().isBefore(LocalDateTime.now())) {
            current.setRevoked(true);
            throw new InvalidRefreshTokenException();
        }
        if (!tokens.validateToken(rawToken) || !tokens.isRefreshToken(rawToken)) {
            revokeFamily(current.getFamilyId());
            throw new InvalidRefreshTokenException();
        }
        UserDetails details = userDetailsService.loadUserByUsername(tokens.getUsernameFromToken(rawToken));
        if (!details.isEnabled()) {
            revokeFamily(current.getFamilyId());
            throw new InvalidRefreshTokenException();
        }
        Authentication auth = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        String nextToken = tokens.generateRefreshToken(auth, current.getFamilyId());
        RefreshToken next = record(nextToken, current.getUser(), current.getFamilyId());
        repository.save(next);
        current.setRevoked(true);
        current.setReplacedBy(next.getId());
        return nextToken;
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        repository.findForUpdateByTokenHash(hash(rawToken)).ifPresent(token -> revokeFamily(token.getFamilyId()));
    }

    @Transactional
    public void revokeUser(UUID userId) {
        repository.findByUserIdAndRevokedFalse(userId).forEach(token -> token.setRevoked(true));
    }

    @Transactional
    public void revokeFamily(UUID familyId) {
        repository.findByFamilyId(familyId).forEach(token -> token.setRevoked(true));
    }

    private RefreshToken record(String token, User user, UUID family) {
        return RefreshToken.builder().id(UUID.randomUUID()).familyId(family).user(user).tokenHash(hash(token))
                .expiresAt(LocalDateTime.now().plusSeconds(tokens.refreshExpirationSeconds())).revoked(false).build();
    }

    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static class InvalidRefreshTokenException extends RuntimeException {}
}
