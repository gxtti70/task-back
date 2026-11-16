package com.taskmanager.service.mail;

import com.taskmanager.config.MailProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Registra el canal de correo elegido en el arranque.
 * <p>
 * El objetivo es diagnosticar de un vistazo por qué un código no llega: si el
 * proveedor no es el que espera el entorno, o si faltan sus credenciales, se ve
 * en la primera línea del log sin tener que reproducir la petición.
 */
@Slf4j
@Component
public class MailProviderLogger {

    private final MailProperties mailProperties;

    public MailProviderLogger(MailProperties mailProperties) {
        this.mailProperties = mailProperties;
    }

    @PostConstruct
    void logActiveProvider() {
        String provider = mailProperties.getProvider() == null || mailProperties.getProvider().isBlank()
                ? "smtp"
                : mailProperties.getProvider().trim();
        log.info("Canal de correo transaccional: {}", provider);

        if ("gmail-api".equalsIgnoreCase(provider) && hasMissingGmailCredentials()) {
            log.warn("Gmail API está seleccionado pero faltan credenciales: los códigos de verificación no se enviarán. "
                    + "Define MAIL_FROM, GMAIL_CLIENT_ID, GMAIL_CLIENT_SECRET y GMAIL_REFRESH_TOKEN.");
        }
    }

    private boolean hasMissingGmailCredentials() {
        return isBlank(mailProperties.getFrom())
                || isBlank(mailProperties.getClientId())
                || isBlank(mailProperties.getClientSecret())
                || isBlank(mailProperties.getRefreshToken());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
