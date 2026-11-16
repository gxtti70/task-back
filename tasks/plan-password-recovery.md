# Plan: recuperación de contraseña por código de 6 dígitos

## Overview
Sustituir el enlace UUID por correo por un flujo de recuperación con código de 6 dígitos, delivered por un canal de correo intercambiable (Gmail SMTP o API HTTP de Resend), sin exponer qué correos están registrados y sin almacenar el código en texto plano.

## Decisions
- El código autoriza el cambio; la nueva contraseña la elige el usuario. Las contraseñas se guardan con BCrypt, que es unidireccional: la contraseña en claro no existe en el sistema y no puede enviarse.
- El código se persiste solo como HMAC-SHA256 con un pepper del servidor. Un hash determinista permite buscar por código; BCrypt no serviría porque no se indexa.
- Caducidad de 10 minutos, 5 intentos por código y 60 segundos de espera entre envíos para el mismo correo. El límite por cuenta vive en la base de datos, no en `RateLimitFilter`, que solo conoce la IP.
- `POST /api/auth/forgot-password` y `POST /api/auth/reset-password` se reapuntan al flujo por código. El mensaje de solicitud es idéntico exista o no el correo; el error de verificación es genérico. Un tiempo de respuesta mínimo iguala ambos caminos.
- El canal de correo se abstrae en `MailSender`, con adaptadores SMTP y Resend, seleccionados por `taskmanager.mail.provider`. Render bloquea el tráfico SMTP saliente en los planes gratuitos, por lo que allí solo funciona la API HTTP.
- El enlace UUID se elimina: la tabla `password_reset_tokens` se borra en la migración V11 y las rutas antiguas redirigen a `/auth/recover-password`.

## Ordered tasks
1. Proveedor de correo: interfaz `MailSender`, adaptadores SMTP y Resend, `MailProperties` y plantilla HTML del código.
2. Esquema: migración Flyway V11 con `password_reset_codes`, entidad y repositorio.
3. Servicio `PasswordRecoveryService`: generación con `SecureRandom`, hash con pepper, enfriamiento, intentos y verificación con revocación de sesiones.
4. Controlador: reapuntar los endpoints de recuperación, nuevo DTO y limpieza de `EmailService`, `PasswordResetToken` y su repositorio.
5. Límite de tasa: documentar el límite por cuenta en `RateLimitFilter`.
6. Frontend: servicio de estado del flujo, componente OTP, asistente de tres pasos, rutas y `AuthService`.
7. Pruebas: servicio de recuperación, controlador y flujo del frontend.
8. Documentación: ADR-002, matriz de configuración y variables de compose.

## Dependencies and risks
La tarea 3 depende del esquema de la tarea 2 y de los adaptadores de la tarea 1. La tarea 4 depende de la 3. La tarea 6 depende del contrato HTTP de la 4.
El pepper es la raíz de la seguridad del código: sin él la aplicación genera uno efímero y lo advierte. La pérdida del pepper invalida los códigos pendientes.
El endpoint de solicitud queda sujeto al límite por IP de `RateLimitFilter`, que es en memoria: en Render Free se reinicia con cada apagado por inactividad.
