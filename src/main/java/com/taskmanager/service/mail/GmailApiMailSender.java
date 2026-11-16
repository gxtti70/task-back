package com.taskmanager.service.mail;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.taskmanager.config.MailProperties;
import com.taskmanager.config.TaskManagerProperties;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

/**
 * Envío por la API de Gmail con OAuth2.
 * <p>
 * Es el canal gratuito que funciona desde Render Free: la API viaja por HTTPS
 * (puerto 443), que no está bloqueado, a diferencia del SMTP saliente (25, 465 y
 * 587). El remitente es una cuenta Gmail personal y el límite del plan gratuito
 * es de 500 correos al día.
 * <p>
 * La credencial es el refresh token de un cliente OAuth de tipo "Desktop app"
 * con el alcance {@code gmail.send}, distinto del cliente OAuth del login. La
 * pantalla de consentimiento debe quedar en "Producción": en "Testing" Google
 * caduca el refresh token a los 7 días.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "taskmanager.mail.provider", havingValue = "gmail-api")
public class GmailApiMailSender implements MailSender {

    /** Canje del refresh token por un access token. */
    static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    /** Envío del mensaje ya compuesto en base64url. */
    static final String SEND_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";

    /** Margen con el que se renueva el access token antes de que caduque. */
    private static final long TOKEN_EXPIRY_MARGIN_SECONDS = 60;
    /** Vigencia por defecto cuando Google no informa de ella. */
    private static final long DEFAULT_TOKEN_LIFETIME_SECONDS = 3600;

    private final RestClient restClient;
    private final MailProperties mailProperties;
    private final String frontendUrl;

    /** Candado de acceso al token: renovarlo es caro y no debe hacerse en paralelo. */
    private final Object tokenLock = new Object();
    private String accessToken;
    private Instant accessTokenExpiresAt;

    public GmailApiMailSender(MailProperties mailProperties,
                              TaskManagerProperties taskManagerProperties,
                              RestClient.Builder builder) {
        this.mailProperties = mailProperties;
        this.frontendUrl = taskManagerProperties.getBilling().getFrontendUrl();
        this.restClient = builder.build();
    }

    @Override
    public void sendVerificationCode(String to, String fullName, String code, int expiresInMinutes) {
        String from = value(mailProperties.getFrom());
        String clientId = value(mailProperties.getClientId());
        String clientSecret = value(mailProperties.getClientSecret());
        String refreshToken = value(mailProperties.getRefreshToken());
        if (from.isBlank() || clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank()) {
            log.warn("Gmail API no configurado (MAIL_FROM={}, GMAIL_CLIENT_ID={}, GMAIL_CLIENT_SECRET={}, GMAIL_REFRESH_TOKEN={}): "
                    + "el código para {} no fue enviado.", state(from), state(clientId), state(clientSecret), state(refreshToken), to);
            return;
        }

        VerificationCodeEmail message = VerificationCodeEmail.forCode(fullName, code, frontendUrl, expiresInMinutes);
        String raw = encodeRawMessage(from, to, message);
        if (raw == null) {
            log.error("No se pudo componer el mensaje de verificación para {}", to);
            return;
        }

        String token = resolveAccessToken(clientId, clientSecret, refreshToken);
        if (token == null) {
            log.error("Sin access token de Gmail: el código para {} no fue enviado.", to);
            return;
        }

        SendResult result = sendRaw(token, raw, to);
        if (result == SendResult.SENT) {
            log.info("Código de verificación enviado por Gmail API a {}", to);
            return;
        }
        if (result == SendResult.FAILED) {
            return;
        }

        // 401: el token pudo caducar entre la comprobación y el envío. Se renueva una sola vez.
        invalidateAccessToken();
        String renewed = resolveAccessToken(clientId, clientSecret, refreshToken);
        if (renewed == null) {
            log.error("Sin access token de Gmail tras renovarlo: el código para {} no fue enviado.", to);
            return;
        }
        if (sendRaw(renewed, raw, to) == SendResult.SENT) {
            log.info("Código de verificación enviado por Gmail API a {} tras renovar el token", to);
        }
    }

    /**
     * Devuelve el access token vigente, renovándolo solo cuando está por caducar.
     * Los errores se registran y devuelven {@code null}: la interfaz pide que ningún
     * adaptador lance excepciones hacia el flujo de recuperación.
     */
    private String resolveAccessToken(String clientId, String clientSecret, String refreshToken) {
        synchronized (tokenLock) {
            if (accessToken != null && accessTokenExpiresAt != null
                    && Instant.now().isBefore(accessTokenExpiresAt.minusSeconds(TOKEN_EXPIRY_MARGIN_SECONDS))) {
                return accessToken;
            }
            return requestAccessToken(clientId, clientSecret, refreshToken);
        }
    }

    private String requestAccessToken(String clientId, String clientSecret, String refreshToken) {
        String form = formBody(clientId, clientSecret, refreshToken);
        try {
            TokenResponse token = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
                log.error("Google respondió sin access_token al renovar el token de Gmail.");
                return null;
            }
            long lifetime = token.expiresIn() > 0 ? token.expiresIn() : DEFAULT_TOKEN_LIFETIME_SECONDS;
            accessToken = token.accessToken();
            accessTokenExpiresAt = Instant.now().plusSeconds(lifetime);
            return accessToken;
        } catch (RestClientException error) {
            accessToken = null;
            accessTokenExpiresAt = null;
            log.error("No se pudo renovar el access token de Gmail: {}", error.getMessage());
            return null;
        }
    }

    private void invalidateAccessToken() {
        synchronized (tokenLock) {
            accessToken = null;
            accessTokenExpiresAt = null;
        }
    }

    private SendResult sendRaw(String token, String raw, String to) {
        try {
            restClient.post()
                    .uri(SEND_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(Map.of("raw", raw))
                    .retrieve()
                    .toBodilessEntity();
            return SendResult.SENT;
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                return SendResult.UNAUTHORIZED;
            }
            log.error("Gmail API respondió {} al enviar el código a {}: {}",
                    error.getStatusCode().value(), to, error.getMessage());
            return SendResult.FAILED;
        } catch (RestClientException error) {
            log.error("No se pudo enviar el código de verificación por Gmail API a {}: {}", to, error.getMessage());
            return SendResult.FAILED;
        }
    }

    /**
     * Compone el MIME del correo y lo codifica en base64url, el formato que exige
     * el campo {@code raw} de la API de Gmail.
     *
     * @return la cadena lista para enviar, o {@code null} si el mensaje no se pudo componer
     */
    static String encodeRawMessage(String from, String to, VerificationCodeEmail message) {
        try {
            MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
            mime.setFrom(new InternetAddress(from));
            mime.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            // El asunto lleva acentos: hay que codificarlo explícitamente en cabecera.
            mime.setSubject(MimeUtility.encodeText(message.subject(), StandardCharsets.UTF_8.name(), null));
            mime.setSentDate(new Date());

            MimeBodyPart text = new MimeBodyPart();
            text.setContent(message.text(), "text/plain; charset=" + StandardCharsets.UTF_8.name());
            MimeBodyPart html = new MimeBodyPart();
            html.setContent(message.html(), "text/html; charset=" + StandardCharsets.UTF_8.name());
            MimeMultipart multipart = new MimeMultipart("alternative");
            multipart.addBodyPart(text);
            multipart.addBodyPart(html);
            mime.setContent(multipart);

            mime.saveChanges();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            mime.writeTo(buffer);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.toByteArray());
        } catch (MessagingException | IOException error) {
            log.error("No se pudo componer el mensaje de verificación: {}", error.getMessage());
            return null;
        }
    }

    /** Cuerpo {@code application/x-www-form-urlencoded} del canje de refresh token. */
    private static String formBody(String clientId, String clientSecret, String refreshToken) {
        return "client_id=" + formEncode(clientId)
                + "&client_secret=" + formEncode(clientSecret)
                + "&refresh_token=" + formEncode(refreshToken)
                + "&grant_type=refresh_token";
    }

    /** Codificación de formulario: {@code +} para el espacio y porcentaje para el resto. */
    private static String formEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String value(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static String state(String raw) {
        return raw.isEmpty() ? "vacío" : "definido";
    }

    private enum SendResult { SENT, UNAUTHORIZED, FAILED }

    /** Respuesta de {@code https://oauth2.googleapis.com/token}. */
    record TokenResponse(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("expires_in") long expiresIn) { }
}
