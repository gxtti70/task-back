package com.taskmanager.service;

import com.taskmanager.entity.User;
import com.taskmanager.repository.UserRepository;
import com.taskmanager.security.EmailAddresses;
import com.taskmanager.security.RoleNames;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoogleAccountService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final ConsentService consents;

    @Transactional
    public User findOrCreate(OAuth2User google, HttpServletRequest request) {
        String subject = google.getAttribute("sub");
        String rawEmail = google.getAttribute("email");
        Object emailVerified = google.getAttribute("email_verified");
        boolean verified = Boolean.TRUE.equals(emailVerified)
                || "true".equalsIgnoreCase(String.valueOf(emailVerified));
        if (subject == null || subject.isBlank() || rawEmail == null || rawEmail.isBlank() || !verified) {
            throw new IllegalArgumentException("Google no confirmó una identidad y un correo válidos.");
        }

        User linked = users.findByGoogleSubject(subject).orElse(null);
        if (linked != null) return requireActive(linked);

        String email = EmailAddresses.normalize(rawEmail);
        String hostedDomain = google.getAttribute("hd");
        boolean googleControlsEmail = email.endsWith("@gmail.com")
                || (hostedDomain != null && !hostedDomain.isBlank());
        if (!googleControlsEmail) {
            throw new IllegalArgumentException("Este correo requiere una verificación adicional para vincularlo con Google.");
        }

        User existing = users.findByEmailIgnoreCase(email).orElse(null);
        if (existing != null) {
            requireActive(existing);
            if (existing.getGoogleSubject() != null && !existing.getGoogleSubject().equals(subject)) {
                throw new IllegalArgumentException("El correo ya está vinculado a otra cuenta de Google.");
            }
            existing.setGoogleSubject(subject);
            return users.save(existing);
        }

        HttpSession session = request.getSession(false);
        String termsVersion = session == null ? null : (String) session.getAttribute("googleTermsVersion");
        String privacyVersion = session == null ? null : (String) session.getAttribute("googlePrivacyVersion");
        if (termsVersion == null || privacyVersion == null || !consents.versionsMatch(termsVersion, privacyVersion)) {
            throw new IllegalArgumentException("Para crear una cuenta debes aceptar los documentos legales vigentes.");
        }

        String fullName = google.getAttribute("name");
        if (fullName == null || fullName.isBlank()) fullName = email.substring(0, email.indexOf('@'));
        User created = users.save(User.builder()
                .email(email)
                .fullName(fullName.trim())
                .googleSubject(subject)
                .password(passwords.encode(UUID.randomUUID().toString()))
                .role(RoleNames.normalize("DEVELOPER"))
                .build());
        consents.recordRegistration(created, termsVersion, privacyVersion, request);
        return created;
    }

    private User requireActive(User user) {
        if (!user.isActive()) throw new IllegalArgumentException("La cuenta está desactivada.");
        return user;
    }
}
