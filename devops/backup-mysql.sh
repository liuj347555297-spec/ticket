#!/usr/bin/env bash
# Produces a consistent logical MySQL backup without putting a password on the command line.
# The defaults file is an operator-provided, mode 0600 client option file outside this repository.
set -euo pipefail

: "${SERVICEHUB_MYSQL_CLIENT_DEFAULTS_FILE:?Set an absolute private MySQL client defaults file}"
: "${SERVICEHUB_DB_NAME:?Set the target database name}"
: "${SERVICEHUB_BACKUP_DIR:?Set an absolute approved backup directory}"

if [[ ! -f "$SERVICEHUB_MYSQL_CLIENT_DEFAULTS_FILE" ]]; then
  echo "MySQL client defaults file does not exist" >&2
  exit 2
fi
permissions="$((10#$(stat -c '%a' "$SERVICEHUB_MYSQL_CLIENT_DEFAULTS_FILE")))"
if (( permissions % 100 != 0 )); then
  echo "MySQL client defaults file must not be group/world readable" >&2
  exit 2
fi

mkdir -p "$SERVICEHUB_BACKUP_DIR"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
target="$SERVICEHUB_BACKUP_DIR/servicehub-${SERVICEHUB_DB_NAME}-${timestamp}.sql.gz"
umask 077

mysqldump --defaults-extra-file="$SERVICEHUB_MYSQL_CLIENT_DEFAULTS_FILE" \
  --single-transaction --routines --events --triggers --set-gtid-purged=OFF \
  --default-character-set=utf8mb4 --databases "$SERVICEHUB_DB_NAME" | gzip -c > "$target"

gzip -t "$target"
printf 'Backup created: %s\n' "$target"
