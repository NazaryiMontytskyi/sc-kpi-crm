#!/usr/bin/env bash
# Restore a backup pair into the running database and the file volume.
# Usage (backend stopped!):  restore.sh <STAMP>      e.g. restore.sh 20261021T020000Z
# DESTRUCTIVE: replaces the database content and the file storage with the backup.
set -euo pipefail

STAMP="${1:?usage: restore.sh <STAMP> (see ls of the backup directory)}"
FILES_DIR="${FILES_DIR:-/data/files}"
BACKUP_DIR="${BACKUP_DIR:-/backups}"
DUMP="$BACKUP_DIR/db-$STAMP.dump"
ARCHIVE="$BACKUP_DIR/files-$STAMP.tar.gz"

[ -f "$DUMP" ] || { echo "missing $DUMP" >&2; exit 1; }
[ -f "$ARCHIVE" ] || { echo "missing $ARCHIVE" >&2; exit 1; }

pg_restore --clean --if-exists --no-owner --exit-on-error --dbname="$PGDATABASE" "$DUMP"

find "$FILES_DIR" -mindepth 1 -delete
tar -xzf "$ARCHIVE" -C "$FILES_DIR"
echo "restored $STAMP"
