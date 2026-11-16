package com.taskmanager.service;

import com.taskmanager.config.PasswordRecoveryProperties;
import com.taskmanager.entity.PasswordResetCode;
import com.taskmanager.entity.User;
import com.taskmanager.repository.PasswordResetCodeRepository;
import com.taskmanager.repository.UserRepository;
import com.taskmanager.security.CodeHashes;
import com.taskmanager.service.mail.MailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El código de 6 dígitos tiene un espacio de búsqueda de un millón de valores, así que
 * estas pruebas cubren las propiedades que lo hacen seguro: no se persiste en claro, caduca,
 * limita los intentos, no revela qué correos existen y revoca las sesiones al usarse.
 */
@ExtendWith(MockitoExtension.class)
class PasswordRecoveryServiceTest {

    private static final String PEPPER = "pepper-de-prueba";

    @Mock UserRepository users;
    @Mock PasswordResetCodeRepository codes;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RefreshTokenService refreshTokens;
    @Mock MailSender mailSender;

    private PasswordRecoveryService service;

    @BeforeEach
    void setUp() {
        PasswordRecoveryProperties properties = new PasswordRecoveryProperties();
        properties.setPepper(PEPPER);
        properties.setCodeTtlSeconds(600);
        properties.setMaxAttempts(5);
        properties.setCooldownSeconds(60);
        properties.setMinResponseMillis(0);
        service = new PasswordRecoveryService(users, codes, passwordEncoder, refreshTokens, mailSender, properties);
    }

    private User user() {
        return User.builder().id(UUID.randomUUID()).email("user@example.test").fullName("Usuario").build();
    }

    private PasswordResetCode activeRow(User user, String code) {
        return PasswordResetCode.builder()
                .user(user)
                .codeHash(CodeHashes.hmacSha256(PEPPER, code))
                .maxAttempts(5)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .used(false)
                .build();
    }

    @Test
    void noEnviaCodigoNiGuardaNadaParaCorreosNoRegistrados() {
        when(users.findByEmailIgnoreCase("nadie@example.test")).thenReturn(Optional.empty());

        PasswordRecoveryService.RecoveryOutcome outcome = service.requestCode("nadie@example.test");

        assertEquals(60, outcome.resendAvailableInSeconds());
        verifyNoInteractions(codes);
        verifyNoInteractions(mailSender);
    }

    @Test
    void persisteSoloElHashYEnviaElCodigoEnClaroPorCorreo() {
        User user = user();
        when(users.findByEmailIgnoreCase("user@example.test")).thenReturn(Optional.of(user));
        when(codes.findFirstByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(Optional.empty());
        when(codes.findByUserIdAndUsedFalse(user.getId())).thenReturn(List.of());
        when(codes.save(any(PasswordResetCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.requestCode("user@example.test");

        ArgumentCaptor<PasswordResetCode> saved = ArgumentCaptor.forClass(PasswordResetCode.class);
        verify(codes).save(saved.capture());
        PasswordResetCode row = saved.getValue();
        assertEquals(64, row.getCodeHash().length());
        assertTrue(row.getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(9)));
        assertFalse(row.isUsed());

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(mailSender).sendVerificationCode(eq("user@example.test"), eq("Usuario"), sent.capture(), eq(10));
        String code = sent.getValue();
        assertTrue(code.matches("\\d{6}"));
        assertEquals(CodeHashes.hmacSha256(PEPPER, code), row.getCodeHash());
        assertNotEquals(code, row.getCodeHash());
    }

    @Test
    void invalidaLosCodigosActivosAnteriores() {
        User user = user();
        PasswordResetCode previous = activeRow(user, "111111");
        when(users.findByEmailIgnoreCase("user@example.test")).thenReturn(Optional.of(user));
        when(codes.findFirstByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(Optional.empty());
        when(codes.findByUserIdAndUsedFalse(user.getId())).thenReturn(List.of(previous));
        when(codes.save(any(PasswordResetCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.requestCode("user@example.test");

        assertTrue(previous.isUsed());
        verify(codes, times(2)).save(any(PasswordResetCode.class));
    }

    @Test
    void respetaElTiempoDeEsperaEntreEnvios() {
        User user = user();
        PasswordResetCode recent = activeRow(user, "111111");
        recent.setCreatedAt(LocalDateTime.now().minusSeconds(10));
        when(users.findByEmailIgnoreCase("user@example.test")).thenReturn(Optional.of(user));
        when(codes.findFirstByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(Optional.of(recent));

        PasswordRecoveryService.RecoveryOutcome outcome = service.requestCode("user@example.test");

        assertEquals(60, outcome.resendAvailableInSeconds());
        verify(codes, never()).save(any());
        verifyNoInteractions(mailSender);
    }

    @Test
    void unCodigoIncorrectoConsumeUnIntentoYNoCambiaLaContraseña() {
        User user = user();
        PasswordResetCode row = activeRow(user, "123456");
        when(users.findByEmailIgnoreCase("user@example.test")).thenReturn(Optional.of(user));
        when(codes.findByUserIdAndUsedFalse(user.getId())).thenReturn(List.of(row));
        when(codes.save(any(PasswordResetCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean updated = service.verifyAndReset("user@example.test", "654321", "new-password");

        assertFalse(updated);
        assertEquals(1, row.getAttempts());
        assertFalse(row.isUsed());
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void invalidaElCodigoAlAgotarLosIntentos() {
        User user = user();
        PasswordResetCode row = activeRow(user, "123456");
        row.setAttempts(5);
        when(users.findByEmailIgnoreCase("user@example.test")).thenReturn(Optional.of(user));
        when(codes.findByUserIdAndUsedFalse(user.getId())).thenReturn(List.of(row));
        when(codes.save(any(PasswordResetCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean updated = service.verifyAndReset("user@example.test", "654321", "new-password");

        assertFalse(updated);
        assertEquals(5, row.getAttempts());
        assertTrue(row.isUsed());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void rechazaUnCodigoExpirado() {
        User user = user();
        PasswordResetCode row = activeRow(user, "123456");
        row.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(users.findByEmailIgnoreCase("user@example.test")).thenReturn(Optional.of(user));
        when(codes.findByUserIdAndUsedFalse(user.getId())).thenReturn(List.of(row));
        when(codes.save(any(PasswordResetCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean updated = service.verifyAndReset("user@example.test", "123456", "new-password");

        assertFalse(updated);
        assertTrue(row.isUsed());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void actualizaLaContraseñaRevocaLasSesionesYConsumeElCodigo() {
        User user = user();
        PasswordResetCode row = activeRow(user, "123456");
        when(users.findByEmailIgnoreCase("user@example.test")).thenReturn(Optional.of(user));
        when(codes.findByUserIdAndUsedFalse(user.getId())).thenReturn(List.of(row));
        when(codes.save(any(PasswordResetCode.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode("new-password")).thenReturn("hashed");

        boolean updated = service.verifyAndReset("user@example.test", "123456", "new-password");

        assertTrue(updated);
        assertEquals("hashed", user.getPassword());
        assertTrue(row.isUsed());
        verify(users).save(user);
        verify(refreshTokens).revokeUser(user.getId());
    }

    @Test
    void rechazaElRestablecimientoParaUnCorreoNoRegistrado() {
        when(users.findByEmailIgnoreCase("nadie@example.test")).thenReturn(Optional.empty());

        boolean updated = service.verifyAndReset("nadie@example.test", "123456", "new-password");

        assertFalse(updated);
        verifyNoInteractions(codes);
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void unCodigoConFormatoInvalidoNoSeComparaYConsumeUnIntento() {
        User user = user();
        PasswordResetCode row = activeRow(user, "123456");
        when(users.findByEmailIgnoreCase("user@example.test")).thenReturn(Optional.of(user));
        when(codes.findByUserIdAndUsedFalse(user.getId())).thenReturn(List.of(row));
        when(codes.save(any(PasswordResetCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean updated = service.verifyAndReset("user@example.test", "12a456", "new-password");

        assertFalse(updated);
        assertEquals(1, row.getAttempts());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void unCorreoEnMinusculasYSinEspaciosEncuentraLaCuenta() {
        User user = user();
        when(users.findByEmailIgnoreCase("user@example.test")).thenReturn(Optional.of(user));
        when(codes.findFirstByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(Optional.empty());
        when(codes.findByUserIdAndUsedFalse(user.getId())).thenReturn(List.of());
        when(codes.save(any(PasswordResetCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.requestCode("  USER@Example.Test  ");

        verify(users).findByEmailIgnoreCase("user@example.test");
        verify(mailSender).sendVerificationCode(eq("user@example.test"), anyString(), anyString(), anyInt());
    }
}
