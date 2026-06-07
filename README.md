# Finwally

Spring Boot backend prepared for Docker deployment with PostgreSQL.

## Run with Docker

The Compose stack reads test database settings from `.env`.

```powershell
docker compose up --build
```

Backend:

- Application: http://localhost:8080
- Health: http://localhost:8080/actuator/health

PostgreSQL defaults:

- Database: `finwally`
- User: `finwally_user`
- Password: `finwally_password`
- Host port: `5432`

The frontend is not included in the Compose stack yet because its technology has not been selected.
