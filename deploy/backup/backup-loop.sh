#!/usr/bin/env bash
# Daily scheduler for the `backup` service: runs backup.sh every day at BACKUP_HOUR_UTC (default 02, UTC).
set -uo pipefail

HOUR="${BACKUP_HOUR_UTC:-02}"
while true; do
  now=$(date -u +%s)
  next=$(date -u -d "$(date -u +%Y-%m-%d) $HOUR:00:00" +%s)
  if [ "$next" -le "$now" ]; then next=$((next + 86400)); fi
  echo "next backup at $(date -u -d "@$next" +%FT%TZ)"
  sleep "$((next - now))"
  bash /scripts/backup.sh || echo "backup FAILED" >&2
done
