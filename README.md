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

## Frontend
React + TypeScript (Vite, Mantine), stack and versions in `docs/adr/0004-frontend-stack.md`. Node 22.12+.
```bash
cd frontend
npm ci
npm run dev             # http://localhost:5173, proxies /api to http://localhost:8080
npm run build | npm run lint | npm test
npm run api:generate    # regenerate the typed client from the pinned frontend/openapi/openapi.json
npm run api:generate:live   # refresh openapi.json from a running backend (dev profile), then regenerate
```
Environment: copy `frontend/.env.example` to `frontend/.env.local`. Brand colors only via theme tokens (`src/theme/colors.ts`); logos are read from `assets/brand/` and fall back to the product name as text when missing. UI strings live in `src/i18n/locales/{uk,en}.json` (keep keys identical).

## Deployment (Docker Compose)
Topology and rationale: `docs/adr/0008-deployment-topology.md`. Services: `postgres`, `backend` (profile `prod` by default), `frontend` (nginx: SPA + reverse proxy of `/api`, same domain), `backup`.
```bash
cp deploy/.env.example deploy/.env      # then set POSTGRES_PASSWORD, CRM_BOOTSTRAP_ADMIN_LOGIN/PASSWORD, CRM_DOMAIN, ...
docker compose -f deploy/docker-compose.yml up --build -d
SMOKE_LOGIN=<admin login> SMOKE_PASSWORD=<admin password> bash deploy/smoke.sh http://localhost
docker compose -f deploy/docker-compose.yml down       # stops the stack; volumes (data) are kept
```
The deployment settings file is git-ignored; never commit it. The SPA is at `http://localhost` (change `HTTP_PORT` if 80 is busy). The first Admin is created from `CRM_BOOTSTRAP_ADMIN_*` on an empty database and must change the password on first sign-in. Because the session cookie is `Secure` in `prod`, use HTTPS on any real host (browsers allow it on `localhost`).

The backend profile is `SPRING_PROFILES_ACTIVE` (default `prod`). For local evaluation only, `SPRING_PROFILES_ACTIVE=dev` runs the same stack with the DEV_ONLY seeders (currently no sample rows), a non-Secure session cookie (plain HTTP works on any host) and public OpenAPI/Swagger UI in the backend (nginx proxies only `/api/` and the health probe, so they are not published through the stack). Never use `dev` on a real server.

### HTTPS
Set `NGINX_TEMPLATE=https.conf.template`, `CRM_DOMAIN`, `TLS_CERT_DIR` (host directory), `TLS_CERT_FILE`, `TLS_KEY_FILE` in the deployment settings. Issuing and renewing the certificate is up to the operator or university IT (OPEN-Q9: server, domain and TLS ownership are not decided yet); reload nginx after renewal: `docker compose -f deploy/docker-compose.yml exec frontend nginx -s reload`.

### Backups
The `backup` service runs daily at `BACKUP_HOUR_UTC` (default 02:00 UTC) and writes `db-<stamp>.dump` and `files-<stamp>.tar.gz` to `BACKUP_TARGET` (default: named volume `backups`; set an absolute host path for a mounted share), keeping `BACKUP_RETENTION_DAYS` days (default 14). Run one on demand:
```bash
docker compose -f deploy/docker-compose.yml exec backup bash /scripts/backup.sh
docker compose -f deploy/docker-compose.yml exec backup ls /backups
```
Copy the backup directory off the server regularly; a backup on the same host does not protect against losing the host.

### Restore (destructive: replaces database and files)
```bash
docker compose -f deploy/docker-compose.yml stop backend frontend
docker compose -f deploy/docker-compose.yml exec backup bash /scripts/restore.sh <stamp>   # e.g. 20261021T021500Z
docker compose -f deploy/docker-compose.yml start backend frontend
bash deploy/smoke.sh http://localhost
```
To restore onto a new server, bring the stack up, place the two backup files into the backup directory, then run the same steps. On Windows Git Bash prefix `MSYS_NO_PATHCONV=1` to the `exec` commands.

## End-to-end tests (Playwright)
The suite in `frontend/e2e` drives the real UI of the production-like compose stack headless in Chromium, once at desktop size and once at a 375 px phone viewport (projects `desktop` and `mobile-375`). Scenarios: sign-in with forced password change, HR creates a member, a department and a role, resolution issue -> redirect -> submit -> rework -> accept, reading an article and saving a new version, search, notification bell. Requirements: Docker and Node 22 (`cd frontend && npm ci` once).
```bash
bash deploy/e2e.sh                                  # build + start stack "crme2e", run everything headless, stop the stack
bash deploy/e2e.sh --project=mobile-375             # extra arguments go to Playwright (project, file, --headed, ...)
E2E_RESET=1 bash deploy/e2e.sh                      # start from an empty database (also exercises the bootstrap Admin password change)
E2E_BASE_URL=http://localhost:8081 npm run e2e      # from frontend/: run against a stack that is already up (nothing is started)
```
`deploy/e2e.sh` uses its own compose project (`E2E_PROJECT`, default `crme2e`, nginx on `E2E_HTTP_PORT`, default 8081), so it never touches a normal `crm` stack. The stack is stopped afterwards with its volumes kept (`E2E_KEEP_STACK=1` leaves it running). No env file is read or created: the script passes fake, throw-away values (database password, bootstrap Admin `e2e.admin`) through the process environment, and the suite creates its own uniquely named fake members, roles, spaces and articles through the API and UI, so it can be re-run any number of times. Report and traces of failures are written to `frontend/e2e/.output/` (git-ignored). Vitest (`npm test`) does not pick up the e2e specs.
