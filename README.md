# Task Manager API

## Local configuration

The API requires PostgreSQL and the following environment variables:

- `DB_URL` (defaults to `jdbc:postgresql://localhost:5432/taskmanager_db`)
- `DB_USER` (defaults to `postgres`)
- `DB_PASSWORD` (required)
- `JWT_SECRET` (required; provide a unique, sufficiently long signing key)
- `CORS_ALLOWED_ORIGINS` (comma-separated origins; defaults to `http://localhost:4200`)
- `JPA_SHOW_SQL` (defaults to `false`; enable only for local debugging)
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `BACKUP_DIR` for the database backup script

Do not commit real credentials or signing keys. Keep deployment origins explicit in `CORS_ALLOWED_ORIGINS`.

Run the backend tests with `mvn test`.

## Health and database backups

`GET /api/health` checks both the application and its PostgreSQL connection. It returns `503` without exposing connection details if the database is unavailable.

Create a compressed logical backup with `scripts/backup-postgres.sh`. The script requires `DB_NAME`, `DB_USER`, and `DB_PASSWORD`; `DB_HOST`, `DB_PORT`, and `BACKUP_DIR` are optional. Store resulting files outside the server as well, encrypt them at rest, and periodically test restoration with `pg_restore`.
