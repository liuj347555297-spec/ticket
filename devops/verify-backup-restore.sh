#!/usr/bin/env bash
# Performs a logical backup and isolated recovery rehearsal.  It will never target the source
# database and refuses a recovery schema that already contains tables.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"
: "${SERVICEHUB_MYSQL_CLIENT_DEFAULTS_FILE:?Set the private MySQL client defaults file}"
: "${SERVICEHUB_DB_NAME:?Set the source database name}"
: "${SERVICEHUB_RECOVERY_DATABASE:?Set an isolated recovery database name}"
: "${SERVICEHUB_BACKUP_DIR:?Set an approved backup directory}"
source_database="$SERVICEHUB_DB_NAME"
recovery_database="$SERVICEHUB_RECOVERY_DATABASE"
fail() { printf 'backup recovery rehearsal failed: %s\n' "$1" >&2; exit 1; }
[[ "$source_database" =~ ^[A-Za-z0-9_]{1,64}$ ]] || fail 'source database name is invalid'
[[ "$recovery_database" =~ ^[A-Za-z0-9_]{1,64}$ ]] || fail 'recovery database name is invalid'
[[ "$recovery_database" != "$source_database" ]] || fail 'recovery database must differ from source database'
command -v mysql >/dev/null 2>&1 || fail 'mysql client is unavailable'

existing_table_count="$(mysql --defaults-extra-file="$SERVICEHUB_MYSQL_CLIENT_DEFAULTS_FILE" -Nse \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '${recovery_database}'" 2>/dev/null)" || fail 'cannot inspect recovery database'
[[ "$existing_table_count" == '0' ]] || fail 'recovery database is not empty'

SERVICEHUB_DB_NAME="$source_database" SERVICEHUB_BACKUP_DIR="$SERVICEHUB_BACKUP_DIR" bash devops/backup-mysql.sh
backup_file="$(find "$SERVICEHUB_BACKUP_DIR" -maxdepth 1 -type f -name "servicehub-${source_database}-*.sql.gz" -printf '%T@ %p\n' | sort -nr | head -n 1 | cut -d' ' -f2-)"
[[ -n "$backup_file" && -f "$backup_file" ]] || fail 'backup script did not create a recovery artifact'

printf '%s\n' "$recovery_database" | SERVICEHUB_RESTORE_DATABASE="$recovery_database" \
  SERVICEHUB_BACKUP_FILE="$backup_file" bash devops/restore-mysql.sh

expected_version="${SERVICEHUB_EXPECTED_FLYWAY_VERSION:-32}"
installed_version="$(mysql --defaults-extra-file="$SERVICEHUB_MYSQL_CLIENT_DEFAULTS_FILE" -Nse \
  "SELECT version FROM \`${recovery_database}\`.flyway_schema_history WHERE success = 1 AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1")" || fail 'recovery schema does not contain Flyway history'
[[ "$installed_version" == "$expected_version" ]] || fail "recovered Flyway version is '${installed_version:-missing}', expected '${expected_version}'"
table_count="$(mysql --defaults-extra-file="$SERVICEHUB_MYSQL_CLIENT_DEFAULTS_FILE" -Nse \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '${recovery_database}'")"
[[ "$table_count" =~ ^[1-9][0-9]*$ ]] || fail 'recovery database has no tables'

printf 'backup recovery rehearsal passed: Flyway V%s, %s tables restored\n' "$installed_version" "$table_count"
