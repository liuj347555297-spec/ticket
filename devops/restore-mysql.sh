#!/usr/bin/env bash
# Restores a previously validated logical backup only into the explicitly named target database.
# Run this against an isolated recovery environment; it never drops a database automatically.
set -euo pipefail

: "${SERVICEHUB_MYSQL_CLIENT_DEFAULTS_FILE:?Set an absolute private MySQL client defaults file}"
: "${SERVICEHUB_RESTORE_DATABASE:?Set the explicitly approved empty recovery database name}"
: "${SERVICEHUB_BACKUP_FILE:?Set the absolute .sql.gz backup file path}"

if [[ ! -f "$SERVICEHUB_MYSQL_CLIENT_DEFAULTS_FILE" || ! -f "$SERVICEHUB_BACKUP_FILE" ]]; then
  echo "Required client defaults file or backup file is missing" >&2
  exit 2
fi
permissions="$((10#$(stat -c '%a' "$SERVICEHUB_MYSQL_CLIENT_DEFAULTS_FILE")))"
if (( permissions % 100 != 0 )); then
  echo "MySQL client defaults file must not be group/world readable" >&2
  exit 2
fi
if [[ ! "$SERVICEHUB_RESTORE_DATABASE" =~ ^[A-Za-z0-9_]{1,64}$ ]]; then
  echo "Recovery database name is invalid" >&2
  exit 2
fi

gzip -t "$SERVICEHUB_BACKUP_FILE"
read -r -p "Restore into '${SERVICEHUB_RESTORE_DATABASE}' on the configured server? Type the database name to continue: " confirmation
if [[ "$confirmation" != "$SERVICEHUB_RESTORE_DATABASE" ]]; then
  echo "Restore cancelled" >&2
  exit 1
fi

# The dump contains CREATE DATABASE/USE for its original database. Replace only those two
# statements while streaming, so the operator-approved recovery database remains the target.
gzip -dc "$SERVICEHUB_BACKUP_FILE" | sed \
  -e "s/^CREATE DATABASE .*\\`.*\\`;/CREATE DATABASE IF NOT EXISTS \\`${SERVICEHUB_RESTORE_DATABASE}\\`;/" \
  -e "s/^USE \\`.*\\`;/USE \\`${SERVICEHUB_RESTORE_DATABASE}\\`;/" | \
  mysql --defaults-extra-file="$SERVICEHUB_MYSQL_CLIENT_DEFAULTS_FILE"

printf 'Restore completed into recovery database: %s\n' "$SERVICEHUB_RESTORE_DATABASE"
