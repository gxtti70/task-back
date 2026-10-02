# Task Manager API

Backend for a task and project management platform built with Spring Boot, PostgreSQL, and JWT-based authentication. The application supports user management, project organization, task tracking, and real-time-style notifications for collaborative workflows.

## Overview

This project provides a robust backend for a task management system inspired by Jira-style workflows. It enables:

- User registration and authentication
- Role-based access control
- Project creation and member management
- Task creation, updates, assignment, and status tracking
- Notification delivery for system and user events
- Health checks and database backup automation

## Tech Stack

- Java 17
- Spring Boot 3.3.0
- Spring Web
- Spring Data JPA
- Spring Security
- PostgreSQL
- JWT (jjwt)
- Maven

## Architecture

The project follows a clean layered architecture:

- `controller`: REST APIs for authentication, projects, tasks, notifications, and health endpoints
- `service`: business logic and orchestration
- `repository`: persistence access using Spring Data repositories
- `entity`: JPA entities for users, projects, tasks, and notifications
- `dto`: request/response models
- `security`: JWT and authentication configuration
- `config`: application configuration

## Project Structure

```text
.
├── src/
│   ├── main/
│   │   ├── java/com/taskmanager/
│   │   │   ├── config/
│   │   │   ├── controller/
│   │   │   ├── dto/
│   │   │   ├── entity/
│   │   │   ├── repository/
│   │   │   ├── security/
│   │   │   ├── service/
│   │   │   └── TaskManagerApplication.java
│   │   └── resources/
│   │       ├── application.properties
│   │       └── schema.sql
│   └── test/
├── scripts/
│   └── backup-postgres.sh
├── .env.example
├── .gitignore
├── pom.xml
├── README.md
└── .mvn/
```

## Prerequisites

Before running the project, make sure you have:

- Java 17+
- Maven 3.9+
- PostgreSQL 12+
- A local or remote PostgreSQL instance

## Local Configuration

1. Copy the sample environment file:

```bash
cp .env.example .env
```

2. Update the values in `.env` with your local credentials and configuration.

Example variables:

```env
DB_URL=jdbc:postgresql://localhost:5432/taskmanager_db
DB_USER=postgres
DB_PASSWORD=change_me
JWT_SECRET=replace_with_a_long_random_secret
CORS_ALLOWED_ORIGINS=http://localhost:4200
JPA_SHOW_SQL=false
DB_HOST=localhost
DB_PORT=5432
DB_NAME=taskmanager_db
BACKUP_DIR=./backups
```

Important:

- The repository intentionally keeps secrets out of version control.
- Never commit real credentials or production values.
- Store sensitive data only in `.env` or your secure deployment environment.

## Running the Application

Start the backend with Maven:

```bash
mvn spring-boot:run
```

The application runs by default on:

```text
http://localhost:8080
```

## Running Tests

Execute the test suite with:

```bash
mvn test
```

## Authentication and Authorization

The application uses Spring Security and JWT tokens for authentication.

Supported roles include:

- `ADMIN`
- `MANAGER`
- `SCRUM`
- `DEVELOPER`

Authentication endpoints are exposed under:

```text
/api/auth
```

Examples include:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `GET /api/auth/admin/users`

## API Highlights

### Projects

```text
GET    /api/projects
POST   /api/projects
PUT    /api/projects/{id}
GET    /api/projects/{id}/members
POST   /api/projects/{id}/members
DELETE /api/projects/{id}/members/{userId}
```

### Tasks

```text
GET    /api/tasks
POST   /api/tasks
GET    /api/tasks/{id}
PUT    /api/tasks/{id}
DELETE /api/tasks/{id}
```

### Notifications

```text
GET /api/notifications
POST /api/notifications/{id}/read
```

### Health

```text
GET /api/health
```

The health endpoint validates both the application and the PostgreSQL connection status. If the database is unavailable, the service responds with `503` without exposing internal connection details.

## Database Backup

A PostgreSQL logical backup utility is included at:

```bash
scripts/backup-postgres.sh
```

This script loads environment variables from `.env` when available and creates a compressed custom dump using `pg_dump`.

Example:

```bash
bash scripts/backup-postgres.sh
```

## Environment Configuration Reference

The application reads the following key values from environment variables:

- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `JWT_SECRET`
- `CORS_ALLOWED_ORIGINS`
- `JPA_SHOW_SQL`
- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `BACKUP_DIR`

These values are mapped in `src/main/resources/application.properties`.

## Security Notes

- JWT is used for stateless API authentication.
- Passwords are encoded using Spring Security's password encoding utilities.
- Access controls are enforced with method-level authorization annotations.
- Only trusted origins should be configured in `CORS_ALLOWED_ORIGINS`.

## License

This project is currently distributed without an explicit license file. If you plan to publish or share it publicly, consider adding a license such as MIT or Apache 2.0.

## Contributing

Contributions are welcome. To contribute:

1. Fork the repository
2. Create a feature branch
3. Commit your changes with clear messages
4. Open a pull request with a summary of the improvements

## Support

For technical issues, configuration questions, or deployment support, open an issue in the repository and include:

- environment used
- Java version
- PostgreSQL version
- relevant logs
- steps to reproduce

---

Built with Spring Boot for a modern, scalable task management backend.
