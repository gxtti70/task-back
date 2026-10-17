package com.taskmanager.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${taskmanager.billing.frontend-url:http://localhost:4300}")
    private String frontendUrl;

    public void sendPasswordResetEmail(String to, String token) {
        String resetUrl = frontendUrl + "/auth/reset-password?token=" + token;
        String subject = "Recuperación de contraseña - TaskManager";
        String body = """
                Hola,

                Has solicitado restablecer tu contraseña en TaskManager.

                Haz clic en el siguiente enlace para crear una nueva contraseña:

                %s

                Este enlace expirará en 1 hora.

                Si no solicitaste este cambio, puedes ignorar este mensaje.

                Saludos,
                El equipo de TaskManager
                """.formatted(resetUrl);

        try {
            if (fromEmail == null || fromEmail.isBlank()) {
                log.info("=== SMTP no configurado. Link de recuperación para {}: {}", to, resetUrl);
                return;
            }
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email de recuperación enviado a {}", to);
        } catch (Exception e) {
            log.error("Error enviando email de recuperación a {}: {}", to, e.getMessage());
        }
    }
}
