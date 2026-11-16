package com.taskmanager.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuración del canal de envío de correo.
 * <p>
 * {@code provider} selecciona el adaptador: {@code smtp} (Gmail o relay SMTP),
 * {@code resend} (API HTTP de Resend) o {@code gmail-api} (API de Gmail con OAuth2).
 * Render Free bloquea el tráfico saliente SMTP, por lo que en ese entorno solo
 * funcionan los adaptadores por API HTTP.
 */
@Component
@ConfigurationProperties(prefix = "taskmanager.mail")
@Getter
@Setter
public class MailProperties {
    /** Identificador del adaptador de envío: {@code smtp}, {@code resend} o {@code gmail-api}. */
    private String provider = "smtp";
    /** Remitente con formato RFC 5322, por ejemplo {@code TaskManager <no-reply@ejemplo.com>}. */
    private String from = "";
    /** Clave de API del proveedor HTTP. Solo se usa cuando {@code provider=resend}. */
    private String apiKey = "";
    /** Client ID del cliente OAuth de Google. Solo se usa cuando {@code provider=gmail-api}. */
    private String clientId = "";
    /** Client Secret del cliente OAuth de Google. Solo se usa cuando {@code provider=gmail-api}. */
    private String clientSecret = "";
    /** Refresh token de largo alcance con el permiso {@code gmail.send}. Solo se usa cuando {@code provider=gmail-api}. */
    private String refreshToken = "";
}
