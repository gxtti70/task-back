# Task list: recuperación de contraseña por código de 6 dígitos

- [x] 1. Crear `MailSender` con adaptadores SMTP y Resend, `MailProperties` y la plantilla HTML del código.
- [x] 2. Añadir la migración Flyway V11 con `password_reset_codes`, la entidad `PasswordResetCode` y su repositorio.
- [x] 3. Implementar `PasswordRecoveryService`: generación, hash con pepper, enfriamiento, límite de intentos, caducidad y revocación de sesiones.
- [x] 4. Reapuntar `forgot-password` y `reset-password`, añadir `ResetPasswordWithCodeRequest` y `RecoveryRequestResponse`, y borrar `EmailService`, `PasswordResetToken` y su repositorio.
- [x] 5. Documentar en `RateLimitFilter` que el límite por cuenta vive en la base de datos.
- [x] 6. Frontend: `PasswordRecoveryService`, `OtpInputComponent`, `RecoverPasswordComponent`, rutas y métodos de `AuthService`.
- [x] 7. Pruebas: `PasswordRecoveryServiceTest`, `AuthControllerSessionTest`, `AuthControllerConsentTest` y `recover-password.spec.ts`.
- [x] 8. Documentación: ADR-002, matriz de configuración y variables de compose.
- [ ] 9. Ejecutar la suite del backend con Maven y verificar el flujo real contra una base de datos con la migración V11 aplicada.

## Canal de correo por Gmail API (sin dominio propio)

- [x] 1. Ampliar `MailProperties` con `clientId`, `clientSecret` y `refreshToken`.
- [x] 2. Crear `GmailApiMailSender`: caché de access token, `MimeMessage` → base64url → `POST /gmail/v1/users/me/messages/send`, reintento único ante un 401.
- [x] 3. Añadir los mappings `GMAIL_CLIENT_ID`, `GMAIL_CLIENT_SECRET` y `GMAIL_REFRESH_TOKEN` en `application.yml`.
- [x] 4. Registrar el canal activo y la falta de credenciales en el arranque con `MailProviderLogger`.
- [x] 5. Pruebas: `GmailApiMailSenderTest` (MIME, caché de token, reintento ante 401 y ausencia de credenciales).
- [x] 6. Documentación: `.env.example`, matriz de configuración, ADR-002 y compose.
- [ ] 7. Google Cloud Console: habilitar la API de Gmail, crear un cliente OAuth "Desktop app" y obtener el refresh token con `prompt=consent`.
- [ ] 8. Configurar `MAIL_PROVIDER=gmail-api` y las credenciales en Render, redeploy y verificar la llegada real del correo (revisar Spam).

## Checkpoint
- [x] Frontend: `npm run lint`, `npm test` (60 pruebas) y `npm run build` pasan.
- [ ] Backend: la suite de Maven no se pudo ejecutar porque el entorno no tiene Java ni Maven instalados; los tests nuevos y los modificados quedan sin verificación en ejecución.
- [ ] Manual: solicitar un código, comprobar que llega por correo, que un código incorrecto consume un intento y que el restablecimiento revoca las sesiones activas.

## Notas de entorno
- El plan gratuito de Render bloquea el tráfico SMTP saliente (puertos 25, 465 y 587). En Render hay que usar un canal por API: `MAIL_PROVIDER=gmail-api` cuando no hay dominio propio, o `MAIL_PROVIDER=resend` cuando el dominio esté verificado.
- La base de datos gratuita de Render caduca a los 30 días de su creación.
- `vitest-base.config.ts` fija `environment: 'jsdom'` y `vitest-setup.ts` restaura `localStorage` y `sessionStorage`: Node 22+ define un `localStorage` experimental que sin `--localstorage-file` devuelve `undefined` y pisa la implementación de jsdom. Con ese arreglo la suite pasó de 45 a 60 pruebas en verde.
