# Task Manager API

<div align="center">

![Java 17](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.0-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-12%2B-336791?style=for-the-badge&logo=postgresql&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-Auth-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-Build-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)

</div>

Backend for a task and project management platform built with Spring Boot, PostgreSQL, and JWT-based authentication. The application supports user management, project organization, task tracking, and notification-driven collaboration for modern team workflows.

## Overview

This project provides a robust backend for a task management system inspired by Jira-style workflows. It enables:

- User registration and login with JWT authentication
- Role-based access control
- Project creation and member management
- Task creation, updates, assignment, and status tracking
- Notification delivery for project and user events
- Health checks and database backup automation

## Features

- Secure authentication and authorization with Spring Security
- Project lifecycle management with ownership and permissions
- Task tracking with statuses and priorities
- Collaborative project membership management
- Custom user directory and admin user operations
- PostgreSQL persistence with JPA
- Backup script for logical database dumps
- Health endpoint for monitoring and deployment checks

## Tech Stack

- Java 17
- Spring Boot 3.3.0
- Spring Web
- Spring Data JPA
- Spring Security
- PostgreSQL
- JWT (jjwt)
- Maven
- Lombok

## Architecture

The project follows a clean layered architecture:

- `controller`: REST APIs for authentication, projects, tasks, notifications, and health
- `service`: business logic and orchestration
- `repository`: Spring Data repositories for persistence access
- `entity`: JPA entities for users, projects, tasks, notifications, and relationships
- `dto`: request/response models
- `security`: JWT utilities, user details, and security configuration
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

## Getting Started

### 1) Clone the repository

```bash
git clone https://github.com/gxtti70/task-back.git
cd task-back
```

### 2) Configure environment variables

Copy the sample environment file:

```bash
cp .env.example .env
```

Then update the values in `.env` with your local configuration.

Example:

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
GET  /api/notifications
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

This script loads environment variables from `.env` when available and creates a PostgreSQL custom dump using `pg_dump`.

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

Built with Spring Boot for a scalable, modern task management backend.
