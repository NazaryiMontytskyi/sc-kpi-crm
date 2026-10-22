# ADR-0008: Deployment topology (Docker Compose, nginx, backups)

- Status: Accepted
- Date: 2026-10-22

## Context
INC-049 needs a reproducible production-like stack for a single university server. The server, domain and TLS ownership are not decided (OPEN-Q9), so every host-specific value must be a parameter. (The plan calls this "ADR-0003"; that number is already taken, hence 0008.)

## Decision
- **Four services** in `deploy/docker-compose.yml` (project name `crm`): `postgres` (postgres:16), `backend` (Spring Boot jar, profile `prod`), `frontend` (nginx serving the Vite build and reverse-proxying), `backup` (postgres:16 image running a daily loop). Only `frontend` publishes ports; backend and database are reachable only on the compose network.
- **Same domain for SPA and API.** nginx serves the SPA with a fallback to `index.html` and proxies `/api/` and the single path `/actuator/health` to `backend:8080`. The `CRMSESSION` and `XSRF-TOKEN` cookies therefore never cross origins (ADR-0002); no CORS is needed. `frontend/dist` is built inside the image and is never copied into `src/main/resources/static`.
- **Images.** `deploy/Dockerfile.backend` (maven:3.9 + Temurin 21 build, `eclipse-temurin:21-jre` runtime, non-root user, curl for the healthcheck) and `deploy/Dockerfile.frontend` (node:22 build, nginx:1.27-alpine runtime). The build context is the repository root, trimmed by the root `.dockerignore`; the frontend build reads `assets/brand/` from the context when present (placeholders otherwise).
- **TLS-ready nginx.** Two templates rendered by the official image (envsubst): `http.conf.template` (default) and `https.conf.template` (80 redirects to 443; certificate and key from a mounted directory; HSTS). Selected with `NGINX_TEMPLATE`; `CRM_DOMAIN`, `TLS_CERT_DIR`, `TLS_CERT_FILE`, `TLS_KEY_FILE` are parameters. Shared locations, gzip and security headers (nosniff, X-Frame-Options DENY, Referrer-Policy, Permissions-Policy, CSP) live in `deploy/nginx/crm-common.conf`. Certificate issuance and renewal are out of scope (operator or university IT).
- **Session cookie.** The `prod` profile sets `Secure` (already present) and now `server.forward-headers-strategy: native` so the app honours `X-Forwarded-*` from nginx. Browsers accept Secure cookies on `http://localhost`, so local evaluation works over HTTP; any real deployment must use HTTPS.
- **State in named volumes:** `pgdata`, `files` (mounted at `CRM_FILES_ROOT`, ADR-0005), `backups`. `BACKUP_TARGET` may point to a host directory (e.g. a network share) instead of the `backups` volume.
- **Backups.** `deploy/backup/backup.sh` writes `db-<UTC stamp>.dump` (pg_dump custom format) and `files-<stamp>.tar.gz`, publishing both only if both succeeded, then deletes files older than `BACKUP_RETENTION_DAYS`. `backup-loop.sh` runs it daily at `BACKUP_HOUR_UTC`. `restore.sh <stamp>` restores both (backend stopped). Off-host copies of the backup directory are the operator's responsibility.
- **Secrets** come only from `deploy/.env` (git-ignored); `deploy/.env.example` holds placeholders. `POSTGRES_PASSWORD` is mandatory (compose refuses to start without it).
- **Smoke test:** `deploy/smoke.sh` checks health, SPA and fallback, headers, 401 plus XSRF cookie, and optionally sign-in, all through nginx.

## Consequences
- Single-host deployment, no orchestration, no zero-downtime upgrades (a restart takes about a minute).
- The backend runs as uid 10001; the `files` volume inherits that ownership from the image.
- The Actuator health endpoint is publicly reachable (status only, no details).
- Not covered: CI/CD, monitoring, log shipping, certificate automation.
