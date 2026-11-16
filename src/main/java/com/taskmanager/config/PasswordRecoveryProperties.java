package com.taskmanager.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Política de la recuperación de contraseña por código de 6 dígitos.
 * <p>
 * El código tiene solo un millón de combinaciones, por lo que se almacena como
 * HMAC-SHA256 con un pepper del servidor: una filtración de la base de datos no
 * permite recuperar los códigos sin conocer el pepper.
 */
@Component
@ConfigurationProperties(prefix = "taskmanager.password-recovery")
@Getter
@Setter
public class PasswordRecoveryProperties {
    /** Secreto del servidor usado como pepper del HMAC. Nunca se almacena en la base de datos. */
    private String pepper = "";
    /** Cuando es {@code true} la aplicación no arranca si el pepper está vacío. */
    private boolean requirePepper = false;
    /** Vigencia del código en segundos. */
    private long codeTtlSeconds = 600;
    /** Intentos fallidos permitidos antes de invalidar el código. */
    private int maxAttempts = 5;
    /** Segundos que deben pasar entre dos envíos para el mismo correo. */
    private long cooldownSeconds = 60;
    /**
     * Tiempo mínimo de respuesta del endpoint de solicitud. Iguala el tiempo de respuesta
     * entre correos existentes e inexistentes para no revelar qué correos están registrados.
     */
    private long minResponseMillis = 500;
}
