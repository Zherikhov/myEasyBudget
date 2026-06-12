# myeasybudget

Spring Boot backend prepared for Docker deployment with PostgreSQL.

Production domain: https://my-easy-budget.com

## Run with Docker

The Compose stack reads test database settings from `.env`.
The Compose project name is pinned to `myeasybudget`, so Docker container and
volume names stay stable even if the checkout folder is renamed.

```powershell
docker compose up --build
```

Backend:

- Application: http://localhost:8080
- Health: http://localhost:8080/actuator/health

PostgreSQL defaults:

- Database: `myeasybudget`
- User: `myeasybudget_user`
- Password: `myeasybudget_password`
- Host port: `5433`

## Run backend locally

When running the application from an IDE, Spring Boot starts the local PostgreSQL
container from `compose.local.yaml` automatically.

You can also start PostgreSQL manually:

```powershell
docker compose -f compose.local.yaml up -d db
```

Then run Spring Boot from the host:

```powershell
.\mvnw.cmd spring-boot:run
```

The frontend is not included in the Compose stack yet because its technology has not been selected.
