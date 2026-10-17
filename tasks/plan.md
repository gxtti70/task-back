# Plan: cookie authentication and Flyway

## Overview
Move browser authentication from JavaScript-managed bearer tokens to HttpOnly access and rotating refresh cookies, enforce CSRF on unsafe requests, persist refresh-token families for revocation and replay detection, and replace startup SQL with versioned Flyway migrations.

## Decisions
- Keep the existing auth URLs and profile response. Token material is delivered only through cookies; login and refresh retain non-secret identity/profile fields.
- Access JWT lifetime is 10 minutes; refresh JWTs are tracked by SHA-256 hash and family in PostgreSQL. Rotation is transactional; reuse revokes the family.
- Use Spring's cookie CSRF repository (`XSRF-TOKEN` + `X-XSRF-TOKEN`). Development defaults to Lax behind the same-origin proxy. Production with separate frontend/API hostnames requires HTTPS, `Secure`, and `SameSite=None`; use a parent Domain only for sibling subdomains and prefer a same-site API proxy because third-party cookie policies may block cross-site sessions.
- Flyway V1 baselines the existing schema, V2 adds refresh-token persistence. Hibernate remains validate-only.

## Ordered tasks
1. Backend schema and dependencies: Flyway, V1 baseline, V2 refresh table, remove startup SQL and DML.
2. Backend session lifecycle: cookie configuration, refresh family service, login/refresh/logout cookies, CSRF and password-reset revocation.
3. Backend tests: refresh rotation/reuse, logout, reset-password session revocation, cookie and CSRF behavior.
4. Frontend migration: cookie credentials, CSRF bootstrap/header, `/me` as session source, logout endpoint, interceptor retry without bearer tokens.
5. Frontend tests and ADR: revise auth/interceptor specs and document deployment cookie topology.
6. Verification: Maven and frontend checks where available; report environmental blockers.

## Dependencies and risks
Tasks 2-3 depend on schema task 1. Frontend task 4 depends on the cookie and CSRF contract in task 2. Existing data is preserved by V1; production Flyway baseline behavior must not silently baseline an empty or incompatible schema. Cross-site cookies require HTTPS and compatible domains.
