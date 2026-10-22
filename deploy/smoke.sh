#!/usr/bin/env bash
# Smoke test of a running stack through nginx.
# Usage: deploy/smoke.sh [BASE_URL]           (default http://localhost)
# Optional sign-in check: SMOKE_LOGIN=... SMOKE_PASSWORD=... deploy/smoke.sh
set -euo pipefail

BASE="${1:-http://localhost}"
JAR="$(mktemp)"
trap 'rm -f "$JAR"' EXIT
fail() { echo "FAIL: $*" >&2; exit 1; }

echo "health via nginx"
body=$(curl -fsS "$BASE/actuator/health") || fail "health request failed"
case "$body" in *'"status":"UP"'*) ;; *) fail "actuator health is not UP: $body" ;; esac

echo "SPA served and fallback works"
body=$(curl -fsS "$BASE/") || fail "index request failed"
case "$body" in *'id="root"'*) ;; *) fail "index.html missing root element" ;; esac
body=$(curl -fsS "$BASE/some/client/route") || fail "fallback request failed"
case "$body" in *'id="root"'*) ;; *) fail "SPA fallback broken" ;; esac

echo "security headers"
hdrs=$(curl -fsSI "$BASE/") || fail "header request failed"
case "$hdrs" in *[Nn]osniff*) ;; *) fail "missing security headers" ;; esac

echo "API proxied, unauthenticated -> 401, XSRF cookie issued"
code=$(curl -s -o /dev/null -w '%{http_code}' -c "$JAR" "$BASE/api/v1/auth/me")
[ "$code" = "401" ] || fail "/api/v1/auth/me expected 401, got $code"
grep -q XSRF-TOKEN "$JAR" || fail "XSRF-TOKEN cookie not issued"

if [ -n "${SMOKE_LOGIN:-}" ] && [ -n "${SMOKE_PASSWORD:-}" ]; then
  echo "sign-in through nginx"
  # The session cookie is Secure (prod profile); curl only stores it over http for localhost, so pass the XSRF value explicitly.
  xsrf=$(awk '$6=="XSRF-TOKEN"{print $7}' "$JAR")
  code=$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR" -c "$JAR" -H "X-XSRF-TOKEN: $xsrf" \
    -H 'Content-Type: application/json' \
    -d "{\"login\":\"$SMOKE_LOGIN\",\"password\":\"$SMOKE_PASSWORD\"}" "$BASE/api/v1/auth/login")
  [ "$code" = "200" ] || fail "login expected 200, got $code"
  code=$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR" "$BASE/api/v1/auth/me")
  [ "$code" = "200" ] || fail "session not accepted after login (got $code)"
fi
echo "OK"
