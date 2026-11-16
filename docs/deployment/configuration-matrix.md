# Configuración por ambiente

Esta matriz documenta las variables que consume actualmente el backend y el Compose local. Los valores de producción deben venir del gestor de secretos o de la plataforma; no guardar secretos en Git.

## Matriz

| Variable / ajuste | Local | Pruebas | Producción |
|---|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/taskmanager_db` sin Compose; dentro de Compose usa el host `db` | URL de PostgreSQL aislada y desechable | URL privada de PostgreSQL administrado, con TLS cuando aplique |
| `DB_USER`, `DB_PASSWORD` | Usuario local; contraseña de desarrollo en `.env` | Usuario y clave exclusivos de CI | Usuario de mínimo privilegio; secreto del gestor de secretos |
| `JWT_SECRET` | Secreto aleatorio local | Secreto temporal del job de CI, no compartido con otros ambientes | Secreto aleatorio fuerte, único y rotado conforme al procedimiento operativo |
| `JWT_COOKIE_SECURE` | `false` solo tras proxy local HTTP | `false` en HTTP de pruebas; `true` si pruebas usan HTTPS | `true` obligatorio |
| `JWT_COOKIE_SAME_SITE` | `Lax` con proxy del mismo sitio | Según topología, preferir `Lax` | `Lax` si mismo sitio; `None` solo si frontend/API requieren contexto cross-site y siempre con `Secure=true` |
| `JWT_COOKIE_DOMAIN` | Vacío | Vacío salvo prueba de subdominios | Vacío por defecto; dominio padre solo para subdominios hermanos |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4300` (Compose local también tiene origen E2E) | Origen exacto del frontend de pruebas | Orígenes HTTPS exactos del frontend; sin comodines |
| `BILLING_SIMULATE` | `true` | `true`, salvo pruebas de integración con Stripe de prueba | `false` para pagos reales |
| `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` | Vacíos si se simula | Claves de prueba, almacenadas como secretos del CI | Claves live del gestor de secretos |
| `STRIPE_PREMIUM_PRICE`, `STRIPE_PLUS_PRICE` | IDs de precio de prueba opcionales | IDs creados en cuenta Stripe de prueba | IDs live que correspondan al catálogo |
| `FRONTEND_URL` | `http://localhost:4300` | URL de frontend de pruebas | URL HTTPS pública, usada por enlaces de restablecimiento |
| `GOOGLE_OAUTH_ENABLED` | `false` hasta configurar OAuth | `true` solo con credenciales de prueba | `true` cuando client ID/secret estén en el gestor de secretos |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Credenciales OAuth de desarrollo | Credenciales de cliente OAuth de pruebas | Client ID público y secret privado de Google Cloud, solo en el backend |
| `GOOGLE_REDIRECT_URI` | `http://localhost:4300/login/oauth2/code/google` | Callback HTTPS del frontend de pruebas | `https://<frontend-domain>/login/oauth2/code/google`; debe coincidir exactamente en Google Cloud |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | Valores locales o vacíos si el correo está simulado | Servidor de correo de pruebas o capturador local | Proveedor de correo real; credenciales secretas y remitente verificado. Opcional si `MAIL_PROVIDER` es `resend` o `gmail-api` |
| `MAIL_PROVIDER` | `smtp` | `smtp`, `resend` o `gmail-api` según la prueba | `gmail-api` si no hay dominio propio; `resend` si hay dominio verificado. Render bloquea el tráfico saliente SMTP en los planes gratuitos (puertos 25, 465 y 587), así que `smtp` no es utilizable allí |
| `MAIL_FROM` | Remitente de desarrollo | Remitente de pruebas | Con `gmail-api`, la cuenta Gmail que envía; con `resend`, `TaskManager <no-reply@tudominio.com>` con SPF y DKIM verificados en el dominio |
| `RESEND_API_KEY` | Vacío | Clave de prueba con permiso de envío | Secreto del gestor de secretos; solo si `MAIL_PROVIDER=resend` |
| `GMAIL_CLIENT_ID` | Vacío | Client ID de un cliente OAuth de tipo "Desktop app" | Client ID de Google Cloud, solo en el backend; distinto del cliente OAuth del login |
| `GMAIL_CLIENT_SECRET` | Vacío | Client Secret del cliente OAuth | Secreto del gestor de secretos; solo si `MAIL_PROVIDER=gmail-api` |
| `GMAIL_REFRESH_TOKEN` | Vacío | Refresh token de largo alcance con el permiso `gmail.send` | Secreto del gestor de secretos; solo si `MAIL_PROVIDER=gmail-api`. La pantalla de consentimiento debe estar en "Producción" para que no caduque a los 7 días |
| `RESET_CODE_PEPPER` | Valor local fijo | Valor de pruebas | Secreto aleatorio de 32 bytes o más. Su pérdida invalida los códigos pendientes; no se debe rotar sin avisar |
| `RESET_CODE_REQUIRE_PEPPER` | `false` | `false` | `true`: la aplicación no arranca sin pepper, en lugar de generar uno efímero |
| `RESET_CODE_TTL_SECONDS` | `600` | `600` | `600` (10 minutos) |
| `RESET_CODE_MAX_ATTEMPTS` | `5` | `5` | `5` |
| `RESET_CODE_COOLDOWN_SECONDS` | `60` | `60` | `60` |
| `RESET_CODE_MIN_RESPONSE_MS` | `500` | `0` para pruebas rápidas | `500`: iguala el tiempo de respuesta entre correos existentes e inexistentes |
| `ATTACHMENTS_LOCAL_DIRECTORY` | Directorio local/volumen persistente | Directorio temporal de pruebas | Preferir S3; no usar disco efímero del contenedor |
| `ATTACHMENTS_S3_BUCKET`, `AWS_REGION` | Opcionales | Bucket aislado si se prueba integración | Bucket de producción y región aprobada |
| Credenciales AWS | Evitar claves estáticas; usar perfil local | Rol/credenciales temporales de CI | Rol de identidad de workload con permisos mínimos; no inyectar claves estáticas |
| `LEGAL_TERMS_VERSION`, `LEGAL_PRIVACY_VERSION`, `LEGAL_COOKIES_VERSION`, `LEGAL_EFFECTIVE_DATE` | Valores de desarrollo | Valores de prueba | Versiones y fecha revisadas y aprobadas antes del lanzamiento |
| `LEGAL_CONTROLLER_NAME`, `LEGAL_CONTROLLER_TAX_ID`, `LEGAL_CONTROLLER_ADDRESS`, `LEGAL_PRIVACY_EMAIL` | Datos ficticios claramente identificados | Datos ficticios | Datos legales reales y contacto atendido; eliminar todos los marcadores |
| `JPA_SHOW_SQL` | `false` normalmente | `false` salvo depuración puntual | `false` |
| `spring.jpa.hibernate.ddl-auto` | `validate` | `validate` | `validate`; cambios de esquema por Flyway |
| `spring.flyway.baseline-on-migrate` | `true` solo en Compose local para volúmenes existentes; `false` al ejecutar backend directamente | `false` | `false`; baselinar bases heredadas manualmente según `flyway-baseline.md` |
| `STRIPE_PUBLISHABLE_KEY` | Clave publicable de prueba opcional | Clave publicable de prueba | Clave publicable live. Los scripts `npm run start`, `npm run build` y `npm test` la inyectan en tiempo de ejecución de Angular CLI; es pública y no sustituye claves secretas. |
| `E2E_MANAGER_EMAIL`, `E2E_DEVELOPER_EMAIL`, `E2E_PASSWORD` | Cuentas sembradas localmente para el flujo real de creación | Cuentas de prueba sembradas por job/fixture | No configurar; E2E no debe ejecutarse contra producción |

## Requisitos de salida a producción

- Usar HTTPS en frontend y API, con `JWT_COOKIE_SECURE=true`.
- Preferir frontend y API bajo el mismo sitio mediante proxy. Si deben ser cross-site, validar cookies `SameSite=None` en navegadores objetivo y asumir posibles restricciones de cookies de terceros.
- Configurar CORS con los orígenes HTTPS exactos y revisar que el cliente use credenciales.
- Desactivar billing simulado y configurar precios, claves y endpoint webhook de Stripe live antes de aceptar pagos.
- Configurar correo, almacenamiento persistente, monitoreo y respaldos antes de habilitar usuarios reales.
- Configurar `MAIL_PROVIDER` según el entorno: `gmail-api` cuando no hay dominio propio (API de Gmail por HTTPS, 500 correos al día gratis) o `resend` cuando el dominio del remitente tenga SPF y DKIM verificados. En cualquier caso, Render bloquea el tráfico saliente SMTP en los puertos 25, 465 y 587, así que `smtp` solo sirve en local.
- Definir `RESET_CODE_PEPPER` como secreto fuerte y `RESET_CODE_REQUIRE_PEPPER=true` para que la aplicación no arranque sin él.
- Migrar la base de datos de Render antes de que expire el plan gratuito: las bases gratuitas caducan a los 30 días de su creación y Render las borra tras un periodo de gracia de 14 días.
- Sustituir datos legales de ejemplo y revisar su contenido con el responsable legal.
- Para una base ya existente, tomar y verificar un respaldo y comprobar la migración Flyway en una copia antes del despliegue. Usar el procedimiento controlado de `flyway-baseline.md`.

## Pendientes de implementación detectados

- No hay un manifiesto de despliegue de producción en el repositorio; `task-front/compose.yaml` es para desarrollo local.
- La clave publicable de Stripe se inyecta por `STRIPE_PUBLISHABLE_KEY` mediante los scripts npm del frontend; los comandos directos `npx ng ...` omiten esa inicialización.
- Los textos legales contienen marcadores y deben completarse antes de publicación.
- El procedimiento de baseline está documentado, pero aún debe probarse con un respaldo/copia de la base real antes del primer despliegue.
