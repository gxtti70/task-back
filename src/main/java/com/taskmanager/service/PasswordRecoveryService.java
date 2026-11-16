package com.taskmanager.service;

import com.taskmanager.config.PasswordRecoveryProperties;
import com.taskmanager.entity.PasswordResetCode;
import com.taskmanager.entity.User;
import com.taskmanager.repository.PasswordResetCodeRepository;
import com.taskmanager.repository.UserRepository;
import com.taskmanager.security.CodeHashes;
import com.taskmanager.security.EmailAddresses;
import com.taskmanager.security.VerificationCodes;
import com.taskmanager.service.mail.MailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Recuperación de contraseña mediante un código de 6 dígitos.
 * <p>
 * Reglas de seguridad aplicadas:
 * <ul>
 *   <li>El código se genera con {@link SecureRandom} y solo se persiste su HMAC-SHA256
 *       con el pepper del servidor.</li>
 *   <li>El código expira a los 10 minutos y admite 5 intentos, tras los cuales queda invalidado.</li>
 *   <li>Entre dos envíos para el mismo correo hay un tiempo de espera de 60 segundos.</li>
 *   <li>El endpoint de solicitud siempre responde lo mismo, exista o no el correo, y el
 *       tiempo de respuesta se iguala con un mínimo para no revelar qué correos están
 *       registrados.</li>
 *   <li>Al restablecer la contraseña se revocan todas las sesiones activas del usuario.</li>
 * </ul>
 */
@Slf4j
@Service
public class PasswordRecoveryService {

    private final UserRepository users;
    private final PasswordResetCodeRepository codes;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final MailSender mailSender;
    private final PasswordRecoveryProperties properties;
    private final String pepper;

    public PasswordRecoveryService(UserRepository users,
                                   PasswordResetCodeRepository codes,
                                   PasswordEncoder passwordEncoder,
                                   RefreshTokenService refreshTokens,
                                   MailSender mailSender,
                                   PasswordRecoveryProperties properties) {
        this.users = users;
        this.codes = codes;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.mailSender = mailSender;
        this.properties = properties;
        this.pepper = resolvePepper(properties);
    }

    /**
     * Emite un código de verificación si el correo corresponde a una cuenta activa.
     * <p>
     * La respuesta no revela si el correo está registrado: el llamador siempre recibe
     * el mismo resultado y el mismo número de segundos antes de poder reenviar.
     */
    public RecoveryOutcome requestCode(String rawEmail) {
        long startedAt = System.nanoTime();
        try {
            String email = EmailAddresses.normalize(rawEmail);
            User user = users.findByEmailIgnoreCase(email).orElse(null);
            if (user == null || !user.isActive()) {
                return RecoveryOutcome.of(properties.getCooldownSeconds());
            }
            if (isCoolingDown(user.getId())) {
                return RecoveryOutcome.of(properties.getCooldownSeconds());
            }

            invalidateActiveCodes(user.getId());

            String code = VerificationCodes.generate();
            PasswordResetCode row = PasswordResetCode.builder()
                    .user(user)
                    .codeHash(CodeHashes.hmacSha256(pepper, code))
                    .maxAttempts(properties.getMaxAttempts())
                    .expiresAt(LocalDateTime.now().plusSeconds(properties.getCodeTtlSeconds()))
                    .build();
            codes.save(row);

            mailSender.sendVerificationCode(user.getEmail(), user.getFullName(), code, expiresInMinutes());
            log.info("Código de verificación emitido para el usuario {}", user.getId());
        } catch (RuntimeException error) {
            log.error("No se pudo emitir el código de verificación: {}", error.getMessage());
        } finally {
            enforceMinimumResponseTime(startedAt);
        }
        return RecoveryOutcome.of(properties.getCooldownSeconds());
    }

    /**
     * Valida el código y restablece la contraseña.
     *
     * @return {@code true} solo si el código era válido y la contraseña se actualizó
     */
    @Transactional
    public boolean verifyAndReset(String rawEmail, String rawCode, String newPassword) {
        String email = EmailAddresses.normalize(rawEmail);
        User user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !user.isActive()) return false;

        String code = rawCode == null ? "" : rawCode.trim();
        List<PasswordResetCode> active = codes.findByUserIdAndUsedFalse(user.getId());

        PasswordResetCode matched = null;
        if (isSixDigits(code)) {
            String hash = CodeHashes.hmacSha256(pepper, code);
            for (PasswordResetCode row : active) {
                if (CodeHashes.matches(row.getCodeHash(), hash)) {
                    matched = row;
                    break;
                }
            }
        }

        if (matched == null) {
            consumeAttempt(active);
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        if (matched.getExpiresAt().isBefore(now)) {
            matched.setUsed(true);
            codes.save(matched);
            log.info("Código de verificación expirado para el usuario {}", user.getId());
            return false;
        }
        if (matched.getAttempts() >= matched.getMaxAttempts()) {
            matched.setUsed(true);
            codes.save(matched);
            return false;
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        users.save(user);
        refreshTokens.revokeUser(user.getId());
        matched.setUsed(true);
        codes.save(matched);
        log.info("Contraseña restablecida con código de verificación para el usuario {}", user.getId());
        return true;
    }

    private int expiresInMinutes() {
        return (int) Math.max(1, properties.getCodeTtlSeconds() / 60);
    }

    private boolean isCoolingDown(UUID userId) {
        return codes.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(row -> row.getCreatedAt() != null
                        && row.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(properties.getCooldownSeconds())))
                .orElse(false);
    }

    private void invalidateActiveCodes(UUID userId) {
        List<PasswordResetCode> active = codes.findByUserIdAndUsedFalse(userId);
        for (PasswordResetCode row : active) {
            row.setUsed(true);
            codes.save(row);
        }
    }

    private void consumeAttempt(List<PasswordResetCode> active) {
        for (PasswordResetCode row : active) {
            // El contador nunca supera el techo: un código ya agotado se invalida,
            // pero no se le suma un intento más allá de max_attempts.
            if (row.getAttempts() < row.getMaxAttempts()) {
                row.setAttempts(row.getAttempts() + 1);
            }
            if (row.getAttempts() >= row.getMaxAttempts()) {
                row.setUsed(true);
                log.info("Código de verificación invalidado tras agotar los intentos del usuario {}", row.getUser().getId());
            }
            codes.save(row);
        }
    }

    private static boolean isSixDigits(String value) {
        if (value == null || value.length() != 6) return false;
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) return false;
        }
        return true;
    }

    private void enforceMinimumResponseTime(long startedAtNanos) {
        long minimum = properties.getMinResponseMillis();
        if (minimum <= 0) return;
        long elapsed = (System.nanoTime() - startedAtNanos) / 1_000_000L;
        long remaining = minimum - elapsed;
        if (remaining <= 0) return;
        try {
            Thread.sleep(remaining);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static String resolvePepper(PasswordRecoveryProperties properties) {
        String configured = properties.getPepper();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        if (properties.isRequirePepper()) {
            throw new IllegalStateException(
                    "El pepper de los códigos de verificación es obligatorio: "
                            + "define RESET_CODE_PEPPER o taskmanager.password-recovery.pepper.");
        }
        byte[] seed = new byte[32];
        new SecureRandom().nextBytes(seed);
        log.warn("No hay pepper configurado para los códigos de verificación, se generó uno efímero. "
                + "Los códigos no sobrevivirán a un reinicio de la aplicación. Define RESET_CODE_PEPPER.");
        return Base64.getEncoder().encodeToString(seed);
    }

    /**
     * Resultado de solicitar un código. No distingue entre correos existentes e inexistentes.
     *
     * @param resendAvailableInSeconds segundos que deben pasar antes de poder reenviar
     */
    public record RecoveryOutcome(int resendAvailableInSeconds) {
        static RecoveryOutcome of(long seconds) {
            return new RecoveryOutcome((int) Math.max(0, Math.min(seconds, Integer.MAX_VALUE)));
        }
    }
}
