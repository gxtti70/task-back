# Task Manager API

## Local configuration

Copy the example environment file and fill in your own local values before running the app:

```bash
cp .env.example .env
```

The repository intentionally does not store real credentials. Keep secrets in `.env` (ignored by Git) and never commit sensitive values.

The application expects environment variables such as:

- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `JWT_SECRET`
- `CORS_ALLOWED_ORIGINS`
- `JPA_SHOW_SQL`
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `BACKUP_DIR` for the backup script

Use the values in `.env.example` as a template, and replace them with your own local configuration.

Run the backend tests with `mvn test`.

## Health and database backups

`GET /api/health` checks both the application and its PostgreSQL connection. It returns `503` without exposing connection details if the database is unavailable.

Create a compressed logical backup with `scripts/backup-postgres.sh`. The script reads database-related environment variables from the local environment (typically via `.env`) and writes a dump file to the configured backup directory.
