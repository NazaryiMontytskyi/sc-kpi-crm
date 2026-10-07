# SC KPI CRM

Internal information system of the Student Council of KPI. Specification: `CONTEXT.md`.

## Prerequisites
Java 21, Docker (Postgres via compose and Testcontainers), Node 20+ (frontend, later increments).

## Run the backend locally
```bash
./mvnw spring-boot:run
```
With the `dev` profile (default), Spring Boot starts `deploy/docker-compose.dev.yml` (Postgres 16) automatically. Optional overrides: see `deploy/.env.example`.

## Tests
```bash
./mvnw verify   # unit + integration tests; Docker required
```

## Layout
`src/` Java backend only · `frontend/` React app · `deploy/` compose and ops · `assets/brand/` brand assets · `docs/adr/` decisions.

## First administrator
On an empty database the backend creates the first `Admin` account from environment variables (see `deploy/.env.example`):
`CRM_BOOTSTRAP_ADMIN_LOGIN`, `CRM_BOOTSTRAP_ADMIN_PASSWORD`. The account must change its password on first sign-in. With the variables unset, or when any account exists, nothing happens. Never commit real values.

## Authentication
Session cookie `CRMSESSION` (HttpOnly; Secure in the `prod` profile) plus CSRF: the SPA echoes the `XSRF-TOKEN` cookie in the `X-XSRF-TOKEN` header (`GET /api/v1/auth/me` hands the cookie out even when it answers 401). Tunables (`crm.identity.*`: lockout thresholds, password length, bcrypt cost, `server.servlet.session.timeout`) are in `application.yml`. OpenAPI/Swagger UI are public in `dev`/`test` only and disabled in `prod`.
