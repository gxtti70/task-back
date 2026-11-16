package com.taskmanager.service.mail;

import com.taskmanager.config.MailProperties;
import com.taskmanager.config.TaskManagerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Envío por la API HTTP de Resend.
 * <p>
 * Es el único canal utilizable desde los planes gratuit de Render, que bloquean
 * el tráfico saliente SMTP. El plan gratuito permite 3.000 correos al mes con un
 * tope de 100 diarios.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "taskmanager.mail.provider", havingValue = "resend")
public class ResendMailSender implements MailSender {

    private static final String BASE_URL = "https://api.resend.com";

    private final RestClient restClient;
    private final MailProperties mailProperties;
    private final String frontendUrl;

    public ResendMailSender(MailProperties mailProperties,
                             TaskManagerProperties taskManagerProperties,
                             RestClient.Builder builder) {
        this.mailProperties = mailProperties;
        this.frontendUrl = taskManagerProperties.getBilling().getFrontendUrl();
        this.restClient = builder.baseUrl(BASE_URL).build();
    }

    @Override
    public void sendVerificationCode(String to, String fullName, String code, int expiresInMinutes) {
        String from = mailProperties.getFrom() == null ? "" : mailProperties.getFrom().trim();
        String apiKey = mailProperties.getApiKey() == null ? "" : mailProperties.getApiKey().trim();
        if (from.isBlank() || apiKey.isBlank()) {
            log.warn("Resend no configurado (MAIL_FROM={}, RESEND_API_KEY={}): el código para {} no fue enviado.",
                    from.isBlank() ? "vacío" : "definido", apiKey.isBlank() ? "vacío" : "definido", to);
            return;
        }

        VerificationCodeEmail message = VerificationCodeEmail.forCode(fullName, code, frontendUrl, expiresInMinutes);
        ResendRequest payload = new ResendRequest(from, to, message.subject(), message.html(), message.text());
        try {
            restClient.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Código de verificación enviado por Resend a {}", to);
        } catch (RestClientException error) {
            log.error("No se pudo enviar el código de verificación por Resend a {}: {}", to, error.getMessage());
        }
    }

    record ResendRequest(String from, String to, String subject, String html, String text) { }
}
