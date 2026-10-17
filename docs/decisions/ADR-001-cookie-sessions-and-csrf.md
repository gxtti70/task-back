# ADR-001: HttpOnly cookie sessions and CSRF protection

## Status
Accepted

## Date
2026-10-16

## Context
The browser previously stored both JWTs in localStorage, making access tokens available to injected JavaScript, and refresh tokens had no server-side replay detection or logout revocation. The application is stateless and its development frontend reaches the API through a same-origin `/api` proxy.

## Decision
Use short-lived (10 minute) access JWTs and seven-day rotating refresh JWTs in `HttpOnly` cookies. Store only SHA-256 token hashes in PostgreSQL, associate tokens with a family, and revoke the family when a rotated token is reused. Logout revokes the current family and clears both cookies. Password reset revokes all refresh tokens for that user.

Unsafe requests use Spring Security's double-submit CSRF token: a readable `XSRF-TOKEN` cookie and matching `X-XSRF-TOKEN` header. CORS continues to allow credentials only for configured explicit origins. Development uses `SameSite=Lax`, no cookie Domain, and `Secure=false` behind the local proxy.

Browser and non-browser API clients must first `GET /api/auth/csrf`, retain the returned CSRF cookie, and send its token in `X-XSRF-TOKEN` on unsafe requests, including login, refresh, and logout. Stripe's `/api/subscriptions/webhook` is exempt because Stripe cannot participate in the browser CSRF exchange; its HMAC signature remains mandatory.

## Production cookie topology
Production deployment must set these cookie values intentionally; the development defaults are not production-ready:

- Always set `JWT_COOKIE_SECURE=true` in production.
- When the frontend and API use different hostnames, set `JWT_COOKIE_SAME_SITE=None`; the application rejects `None` unless Secure is enabled.
- Set `JWT_COOKIE_DOMAIN` to the shared parent only when the frontend and API are sibling subdomains. Leave it empty for unrelated domains so the API cookie remains host-only.

For example, with `app.example.com` and `api.example.com`, set `JWT_COOKIE_DOMAIN=.example.com`, `JWT_COOKIE_SECURE=true`, and `JWT_COOKIE_SAME_SITE=None`; use HTTPS for both.

If frontend and API are genuinely cross-site, use the same `None` and `Secure` settings and a host-only API cookie. Browsers may block third-party cookies even with these settings, so use a same-site reverse proxy or move the API under the frontend's site for reliable production sessions. Never use `SameSite=None` without HTTPS.

## Consequences
- JavaScript cannot read access or refresh JWTs; the frontend obtains identity and role only from `/api/auth/me`.
- CSRF tokens are intentionally readable by JavaScript and must be sent on unsafe requests.
- New databases execute V1, V2, and V3. Existing pre-Flyway databases are baselined at version 1 only through the reviewed procedure in `docs/deployment/flyway-baseline.md`, then receive V2 and V3.
- Access JWTs already issued before logout or password reset can remain valid until their short expiry; refresh sessions are revoked immediately.

## Alternatives considered
- localStorage bearer tokens: rejected because script injection can exfiltrate long-lived credentials.
- Stateless refresh JWTs: rejected because replay detection and server-side logout revocation require persisted token state.
- Lax cookies across distinct frontend/API hostnames: rejected for this deployment because cross-origin browser requests need `SameSite=None`; a same-site reverse proxy remains the preferred topology where available.
