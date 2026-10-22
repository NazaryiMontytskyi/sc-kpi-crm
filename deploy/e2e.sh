#!/usr/bin/env bash
# End-to-end suite (Playwright, INC-051): builds and starts an isolated compose stack, runs the suite headless
# against it and stops the stack again. Data volumes are kept (`down` without -v) so a re-run is fast; the scenarios
# create their own uniquely named FAKE data, so re-runs never collide.
#
# Usage:   bash deploy/e2e.sh [playwright args...]        e.g.  bash deploy/e2e.sh --project=mobile-375
# Options (environment):
#   E2E_PROJECT      compose project name (default crme2e; keeps the stack apart from a normal `crm` stack)
#   E2E_HTTP_PORT    host port of nginx (default 8081)
#   E2E_KEEP_STACK=1 leave the stack running afterwards
#   E2E_RESET=1      also remove the e2e volumes (`down -v`) before starting, for a pristine database
#   E2E_BASE_URL     run against an already running stack instead (nothing is started or stopped)
#
# The values below are deliberately fake and only valid for this throw-away stack; nothing here is a real secret.
# No env file is read or written: everything is passed through the process environment.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PROJECT="${E2E_PROJECT:-crme2e}"
PORT="${E2E_HTTP_PORT:-8081}"

export E2E_ADMIN_LOGIN="${E2E_ADMIN_LOGIN:-e2e.admin}"
export E2E_ADMIN_PASSWORD="${E2E_ADMIN_PASSWORD:-Fake-Bootstrap-7q2m9x}"
export E2E_ADMIN_NEW_PASSWORD="${E2E_ADMIN_NEW_PASSWORD:-Fake-Changed-4k8t1z}"

compose() {
  docker compose -p "$PROJECT" -f "$ROOT/deploy/docker-compose.yml" "$@"
}

if [ -z "${E2E_BASE_URL:-}" ]; then
  export E2E_BASE_URL="http://localhost:${PORT}"
  export HTTP_PORT="$PORT" HTTPS_PORT="${E2E_HTTPS_PORT:-8444}"
  export POSTGRES_DB=crm POSTGRES_USER=crm POSTGRES_PASSWORD="${E2E_POSTGRES_PASSWORD:-fake-e2e-db-3n6v0c}"
  export CRM_BOOTSTRAP_ADMIN_LOGIN="$E2E_ADMIN_LOGIN" CRM_BOOTSTRAP_ADMIN_PASSWORD="$E2E_ADMIN_PASSWORD"
  export CRM_DOMAIN=localhost NGINX_TEMPLATE=http.conf.template BACKUP_TARGET=backups

  stop_stack() {
    if [ "${E2E_KEEP_STACK:-0}" != "1" ]; then
      echo "stopping e2e stack (volumes kept)"
      compose down || true
    fi
  }
  trap stop_stack EXIT

  if [ "${E2E_RESET:-0}" = "1" ]; then
    compose down -v || true
  fi
  echo "starting e2e stack '$PROJECT' on $E2E_BASE_URL"
  # The backup service is not needed by the scenarios.
  compose up -d --build --wait postgres backend frontend
  bash "$ROOT/deploy/smoke.sh" "$E2E_BASE_URL"
fi

cd frontend
if [ ! -d node_modules ]; then
  npm ci
fi
npx playwright install chromium
npx playwright test --config e2e/playwright.config.ts "$@"
