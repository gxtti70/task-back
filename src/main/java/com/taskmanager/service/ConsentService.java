package com.taskmanager.service;

import com.taskmanager.config.TaskManagerProperties;
import com.taskmanager.entity.ConsentDocument;
import com.taskmanager.entity.User;
import com.taskmanager.entity.UserConsent;
import com.taskmanager.repository.UserConsentRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Registra y valida la autorización del titular. La prueba de la autorización
 * debe conservarse y exhibirse cuando la autoridad o el titular la soliciten
 * (Decreto 1074 de 2015, art. 2.2.2.25.2.1).
 */
@Service
@RequiredArgsConstructor
public class ConsentService {
    private final UserConsentRepository repository;
    private final TaskManagerProperties properties;

    /** Valida que el titular aceptó las versiones vigentes, no unas caducadas. */
    public boolean versionsMatch(String termsVersion, String privacyVersion) {
        TaskManagerProperties.Legal legal = properties.getLegal();
        return legal.getTermsVersion().equals(termsVersion)
                && legal.getPrivacyVersion().equals(privacyVersion);
    }

    @Transactional
    public void recordRegistration(User user, String termsVersion, String privacyVersion,
                                   HttpServletRequest request) {
        LocalDateTime now = LocalDateTime.now();
        String ip = clientIp(request);
        String userAgent = truncate(request.getHeader("User-Agent"), 512);
        repository.save(build(user, ConsentDocument.TERMS, termsVersion, now, ip, userAgent));
        repository.save(build(user, ConsentDocument.PRIVACY, privacyVersion, now, ip, userAgent));
    }

    private UserConsent build(User user, ConsentDocument document, String version,
                              LocalDateTime acceptedAt, String ip, String userAgent) {
        return UserConsent.builder()
                .id(UUID.randomUUID())
                .user(user)
                .document(document)
                .version(version)
                .acceptedAt(acceptedAt)
                .ip(ip)
                .userAgent(userAgent)
                .build();
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
