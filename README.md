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
