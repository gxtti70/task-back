package com.taskmanager.service.mail;

import com.taskmanager.config.MailProperties;
import com.taskmanager.config.TaskManagerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envío por SMTP. Cubre Gmail con contraseña de aplicación y cualquier relay SMTP.
 * <p>
 * Render bloquea el tráfico saliente SMTP en los planes gratuit, por lo que este
 * adaptador no es utilizable desde ese entorno.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "taskmanager.mail.provider", havingValue = "smtp", matchIfMissing = true)
public class SmtpMailSender implements MailSender {

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final String smtpUsername;
    private final String frontendUrl;

    public SmtpMailSender(JavaMailSender mailSender,
                          MailProperties mailProperties,
                          TaskManagerProperties taskManagerProperties,
                          @Value("${spring.mail.username:}") String smtpUsername) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
        this.smtpUsername = smtpUsername;
        this.frontendUrl = taskManagerProperties.getBilling().getFrontendUrl();
    }

    @Override
    public void sendVerificationCode(String to, String fullName, String code, int expiresInMinutes) {
        String from = resolveFrom();
        if (from.isBlank()) {
            log.warn("SMTP no configurado: el código de verificación para {} no fue enviado. "
                    + "Define MAIL_FROM o SMTP_USERNAME.", to);
            return;
        }

        VerificationCodeEmail message = VerificationCodeEmail.forCode(fullName, code, frontendUrl, expiresInMinutes);
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(from);
            mail.setTo(to);
            mail.setSubject(message.subject());
            mail.setText(message.text());
            mailSender.send(mail);
            log.info("Código de verificación enviado por SMTP a {}", to);
        } catch (Exception error) {
            log.error("No se pudo enviar el código de verificación por SMTP a {}: {}", to, error.getMessage());
        }
    }

    private String resolveFrom() {
        if (mailProperties.getFrom() != null && !mailProperties.getFrom().isBlank()) {
            return mailProperties.getFrom().trim();
        }
        return smtpUsername == null ? "" : smtpUsername.trim();
    }
}
