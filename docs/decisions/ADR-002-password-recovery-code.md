# ADR-002: Recuperación de contraseña por código de 6 dígitos

## Status
Accepted

## Date
2026-11-13

## Context

El restablecimiento de contraseña usaba un token UUID en un enlace enviado por correo. Ese flujo nunca funcionó en producción: `EmailService` solo registraba el enlace en el log cuando `SMTP_USERNAME` no estaba configurado, de modo que ningún usuario recibía el mensaje y la tabla `password_reset_tokens` acumulaba enlaces caducos.

La recuperación debía pasar a un código de 6 dígitos. Un código numérico tiene un espacio de búsqueda de un millón de valores, muy por debajo de la entropía de un UUID, así que el diseño tiene que devolver esa seguridad por otros medios: hash con pepper, caducidad corta, límite de intentos, enfriamiento entre envíos y respuestas que no revelen qué correos existen.

Las contraseñas se almacenan con BCrypt, que es unidireccional: la contraseña en claro no existe en ninguna parte del sistema y no puede enviarse por correo. El código sustituye a la credencial: autoriza el cambio, y la nueva contraseña la elige el usuario.

## Decision

El usuario se recupera en tres pasos: correo, código de 6 dígitos y nueva contraseña. El código se valida junto con la contraseña en una sola operación transaccional, de forma que un solo envío resuelve la recuperación.

**Almacenamiento del código.** Nunca se persiste en texto plano. Se guarda su HMAC-SHA256 con un pepper del servidor (`RESET_CODE_PEPPER`), lo que permite localizar el registro por código y, a la vez, impide recuperar los códigos a partir de una filtración de la base de datos. Un hash determinista es imprescindible: BCrypt no serviría porque no se puede indexar. El pepper nunca se guarda en la base de datos y su pérdida invalida los códigos pendientes.

**Caducidad e intentos.** El código vive 10 minutos y admite 5 intentos. Agotarlos invalida el código y obliga a pedir uno nuevo. Entre dos envíos para el mismo correo hay 60 segundos de espera; cada código nuevo invalida el anterior.

**No enumeración de cuentas.** `POST /api/auth/forgot-password` devuelve siempre el mismo mensaje y el mismo número de segundos antes de poder reenviar, exista o no el correo. El error de verificación es genérico y no distingue entre código inexistente, expirado o agotado. El tiempo de respuesta tiene un mínimo configurable para que la diferencia entre un correo existente y uno inexistente no sea medible.

**Revocación de sesiones.** Al restablecer la contraseña se revocan todos los refresh tokens del usuario, igual que en el flujo anterior.

**Canal de correo.** El envío se abstrae detrás de `MailSender` con tres adaptadores: SMTP (Gmail o relay), la API HTTP de Resend y la API de Gmail con OAuth2, seleccionados con `taskmanager.mail.provider`. La abstracción es necesaria por una restricción de la plataforma: Render bloquea el tráfico saliente SMTP en los planes gratuitos, por lo que allí solo funcionan los adaptadores por API HTTP.

El canal elegido para producción es `gmail-api`. Un adaptador por API de Gmail viaja por HTTPS (puerto 443), que no está bloqueado, y se autentica con el refresh token de un cliente OAuth de tipo "Desktop app" con el alcance `gmail.send`, sin depender de ningún proveedor externo ni de un dominio propio. Su límite es de 500 correos al día, más que suficiente para códigos de recuperación. Resend sigue siendo la alternativa cuando exista un dominio con SPF y DKIM verificados, porque exige verificar el dominio por DNS antes de enviar.

**Restricciones de despliegue.** El pepper es obligatorio cuando `RESET_CODE_REQUIRE_PEPPER=true`. Sin pepper la aplicación genera uno efímero en el arranque y lo registra como advertencia: los códigos no sobreviven a un reinicio, pero nunca se degrada a un hash sin pepper.

## Consequences

- El enlace por correo se elimina: `password_reset_tokens` se borra en la migración V11 y las rutas `/auth/forgot-password` y `/auth/reset-password` redirigen a `/auth/recover-password`.
- La tabla `password_reset_codes` almacena `code_hash`, `attempts`, `max_attempts`, `expires_at` y `used`. El código en claro solo existe en memoria, entre la generación y el envío.
- El límite por cuenta vive en la base de datos, no en `RateLimitFilter`: el filtro solo conoce la IP de origen y no protege un código corto frente a un atacante distribuido. En Render Free, además, el filtro en memoria se reinicia en cada apagado por inactividad.
- Render requiere un adaptador por API HTTP: `MAIL_PROVIDER=gmail-api` (sin dominio propio) o `MAIL_PROVIDER=resend` (con dominio verificado). Con `smtp` el correo no sale, porque los puertos 25, 465 y 587 están bloqueados.
- El canal `gmail-api` entrega el correo a través del buzón de la cuenta Gmail configurada en `MAIL_FROM`, que es quien aparece como remitente. El código de recuperación se envía en claro por esa vía, igual que por cualquier otro canal.
- El refresh token de Gmail no caduca si la pantalla de consentimiento de Google Cloud queda en "Producción"; en modo "Testing" Google lo caduca a los 7 días y el envío deja de funcionar sin más aviso que un error en el log.
- El correo se entrega en claro al proveedor de correo y al buzón del usuario: el código es un factor de autenticación temporal y debe tratarse como tal.
- Un usuario con sesión de Google puede fijar una contraseña y quedar con dos vías de acceso. El sistema ya admitía cuentas con contraseña y Google enlazado, así que no es una capacidad nueva.

## Alternatives considered

- **Cloudflare Email Routing.** Solo enruta correo entrante y exige control exclusivo de los registros MX del dominio; además, no existe sin dominio propio. El envío saliente de Cloudflare requiere Workers Paid (5 USD/mes). Descartado por coste y porque no resuelve el problema sin dominio.
- **Google Cloud Identity Platform / Firebase Auth.** Es el único servicio gratuito de Google que emite códigos, pero obliga a reescribir la autenticación completa (usuarios, sesiones, cookies) y su flujo por correo es un enlace, no un código de 6 dígitos. Descartado por coste de migración.
- **Enviar la contraseña actual.** imposible con BCrypt. Requeriría almacenamiento reversible, que es un retroceso de seguridad.
- **Enviar una contraseña temporal generada por el backend.** Viable guardando solo su hash, pero la credencial viaja en texto plano por el correo, es reutilizable hasta que el usuario la cambie y queda expuesta a reenvíos y a los logs del proveedor. Se descartó por el mismo motivo que el enlace UUID no era suficiente.
- **Consultar el código en texto plano en la base de datos.** Una filtración permitiría enumerar los códigos válidos en segundos. Descartado.
- **Validar el código en dos pasos (verificar y luego fijar contraseña).** Añade un estado intermedio y un token de autorización adicional sin ganancia de seguridad relevante. La operación única es transaccional y atómica.
- **Reenviar la contraseña en la respuesta de la API.** Filtraría el código a cualquiera que controle la red o el navegador.
