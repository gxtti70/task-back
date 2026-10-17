package com.taskmanager.service;

import com.taskmanager.entity.RefreshToken;
import com.taskmanager.entity.User;
import com.taskmanager.repository.RefreshTokenRepository;
import com.taskmanager.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {
    private RefreshTokenRepository repository;
    private JwtTokenProvider jwt;
    private UserDetailsService userDetailsService;
    private RefreshTokenService service;
    private UUID family;
    private User user;

    @BeforeEach
    void setUp() {
        repository = mock(RefreshTokenRepository.class);
        jwt = mock(JwtTokenProvider.class);
        userDetailsService = mock(UserDetailsService.class);
        service = new RefreshTokenService(repository, jwt, userDetailsService);
        family = UUID.randomUUID();
        user = User.builder().id(UUID.randomUUID()).email("person@example.test").role("DEVELOPER").active(true).build();
    }

    @Test
    void rotatesRefreshTokenWithinSameFamilyAndMarksPreviousTokenUsed() throws Exception {
        RefreshToken old = token("old-refresh");
        when(repository.findForUpdateByTokenHash(hash("old-refresh"))).thenReturn(Optional.of(old));
        when(jwt.validateToken("old-refresh")).thenReturn(true);
        when(jwt.isRefreshToken("old-refresh")).thenReturn(true);
        when(jwt.getUsernameFromToken("old-refresh")).thenReturn(user.getEmail());
        when(jwt.generateRefreshToken(any(), eq(family))).thenReturn("new-refresh");
        UserDetails details = org.springframework.security.core.userdetails.User.withUsername(user.getEmail()).password("x").roles("DEVELOPER").build();
        when(userDetailsService.loadUserByUsername(user.getEmail())).thenReturn(details);
        when(jwt.refreshExpirationSeconds()).thenReturn(604800L);
        String expectedHash = hash("new-refresh");

        String result = service.rotate("old-refresh");

        assertEquals("new-refresh", result);
        assertTrue(old.isRevoked());
        assertNotNull(old.getReplacedBy());
        verify(repository).save(argThat(saved -> saved.getFamilyId().equals(family)
                && saved.getTokenHash().equals(expectedHash)));
    }

    @Test
    void reuseOfRotatedTokenRevokesEveryTokenInItsFamily() throws Exception {
        RefreshToken old = token("old-refresh");
        old.setRevoked(true);
        RefreshToken active = token("active-refresh");
        when(repository.findForUpdateByTokenHash(hash("old-refresh"))).thenReturn(Optional.of(old));
        when(repository.findByFamilyId(family)).thenReturn(List.of(old, active));

        assertThrows(RefreshTokenService.InvalidRefreshTokenException.class, () -> service.rotate("old-refresh"));

        assertTrue(old.isRevoked());
        assertTrue(active.isRevoked());
    }

    @Test
    void logoutRevokesTheTokenFamilyServerSide() throws Exception {
        RefreshToken current = token("refresh-cookie");
        RefreshToken sibling = token("sibling");
        when(repository.findForUpdateByTokenHash(hash("refresh-cookie"))).thenReturn(Optional.of(current));
        when(repository.findByFamilyId(family)).thenReturn(List.of(current, sibling));

        service.revoke("refresh-cookie");

        assertTrue(current.isRevoked());
        assertTrue(sibling.isRevoked());
    }

    @Test
    void passwordResetRevokesAllActiveRefreshSessionsForTheUser() throws Exception {
        RefreshToken session = token("session");
        when(repository.findByUserIdAndRevokedFalse(user.getId())).thenReturn(List.of(session));

        service.revokeUser(user.getId());

        assertTrue(session.isRevoked());
    }

    private RefreshToken token(String rawToken) throws Exception {
        return RefreshToken.builder().id(UUID.randomUUID()).familyId(family).user(user)
                .tokenHash(hash(rawToken)).expiresAt(LocalDateTime.now().plusDays(1)).revoked(false).build();
    }

    private String hash(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    }
}
