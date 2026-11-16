package com.taskmanager.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Respuesta de la solicitud de código de verificación.
 * <p>
 * El mensaje es idéntico exista o no el correo, para no revelar qué cuentas están
 * registradas. {@code resendAvailableInSeconds} indica cuándo se puede pedir un reenvío.
 */
@Data
@AllArgsConstructor
public class RecoveryRequestResponse {
    private String message;
    private int resendAvailableInSeconds;
}
