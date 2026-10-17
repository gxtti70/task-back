# Task list: cookie authentication and Flyway

- [x] 1. Add Flyway V1/V2 and remove SQL startup initialization. Verify migration SQL matches current entities and `ddl-auto=validate`.
- [x] 2. Implement HttpOnly access/refresh cookies, CSRF, rotating refresh families, logout, and password reset revocation.
- [x] 3. Add backend tests for refresh rotation/reuse, logout, reset-password revocation, and CSRF.
- [x] 4. Migrate Angular state/interceptor/guards to `/me` and cookie-based refresh/logout.
- [x] 5. Update Angular auth/interceptor tests and add session ADR with production SameSite/Domain guidance.
- [ ] 6. Finish backend Maven tests and end-to-end/manual session verification when Maven dependencies and the API are available.
  - [x] Frontend lint and production build pass; the original 21-test suite passed before the added 403 regression case.

## Checkpoint
- [ ] Backend suite passes; frontend lint and production build pass; 21 unit tests passed before the final 403 case was added.
- [ ] Login, reload, refresh, replay revocation, logout, and CSRF flows meet the acceptance criteria.
