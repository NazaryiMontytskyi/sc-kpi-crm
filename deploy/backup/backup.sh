#!/usr/bin/env bash
# One backup run: timestamped PostgreSQL dump (custom format) + tar.gz of the file storage, then retention cleanup.
# Runs inside the `backup` service (image postgres:16). Env: PGHOST PGUSER PGPASSWORD PGDATABASE,
# FILES_DIR (default /data/files), BACKUP_DIR (default /backups), BACKUP_RETENTION_DAYS (default 14).
set -euo pipefail

FILES_DIR="${FILES_DIR:-/data/files}"
BACKUP_DIR="${BACKUP_DIR:-/backups}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"

mkdir -p "$BACKUP_DIR"
DB_TMP="$BACKUP_DIR/.db-$STAMP.dump.part"
FILES_TMP="$BACKUP_DIR/.files-$STAMP.tar.gz.part"
trap 'rm -f "$DB_TMP" "$FILES_TMP"' EXIT

pg_dump --format=custom --no-owner --file="$DB_TMP"
tar -czf "$FILES_TMP" -C "$FILES_DIR" .

# Publish both parts only when both succeeded.
mv "$DB_TMP" "$BACKUP_DIR/db-$STAMP.dump"
mv "$FILES_TMP" "$BACKUP_DIR/files-$STAMP.tar.gz"
echo "backup $STAMP: db-$STAMP.dump files-$STAMP.tar.gz"

find "$BACKUP_DIR" -maxdepth 1 -type f \( -name 'db-*.dump' -o -name 'files-*.tar.gz' \) -mtime "+$RETENTION_DAYS" -print -delete
