package com.taskmanager.service.mail;

/**
 * Canal de envío de correo transaccional.
 * <p>
 * La implementación se selecciona por la propiedad {@code taskmanager.mail.provider}.
 * Los adaptadores no lanzan excepciones hacia el llamador: un fallo de entrega se
 * registra y el flujo de recuperación sigue funcionando, porque el usuario puede
 * pedir un reenvío.
 */
public interface MailSender {

    /**
     * Envía un código de verificación de 6 dígitos.
     *
     * @param to               correo del destinatario
     * @param fullName         nombre del usuario, puede ser vacío
     * @param code             código de 6 dígitos en texto plano
     * @param expiresInMinutes vigencia del código en minutos, para el texto del mensaje
     */
    void sendVerificationCode(String to, String fullName, String code, int expiresInMinutes);
}
