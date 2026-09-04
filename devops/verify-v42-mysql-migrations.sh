#!/usr/bin/env bash
# Verifies the complete ServiceHub + Flowable schema through V44, including synthetic V42/V43 history.
# Credentials are read only from an operator-owned MySQL defaults file and are never printed.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"
: "${SERVICEHUB_MYSQL_VERIFY_DATABASE:?Set a disposable schema named servicehub_migration_verify_*}"
: "${SERVICEHUB_ALLOW_SCHEMA_CREATE_DROP:?Set exactly YES to create and drop the disposable schema}"

fail() { printf 'V44 MySQL migration verification failed: %s\n' "$1" >&2; exit 1; }
database="$SERVICEHUB_MYSQL_VERIFY_DATABASE"
[[ "$SERVICEHUB_ALLOW_SCHEMA_CREATE_DROP" == 'YES' ]] || fail 'explicit schema create/drop approval is required'
[[ "$database" =~ ^servicehub_migration_verify_[A-Za-z0-9_]{1,32}$ ]] || fail 'verification schema name is outside the disposable prefix'

if [[ -n "${SERVICEHUB_MYSQL_VERIFY_DOCKER_CONTAINER:-}" ]]; then
  container="$SERVICEHUB_MYSQL_VERIFY_DOCKER_CONTAINER"
  [[ "$container" =~ ^servicehub-mysql-v[0-9]+-verify$ ]] || fail 'Docker verification container name is outside the approved prefix'
  command -v docker >/dev/null 2>&1 || fail 'docker client is unavailable'
  [[ "$(docker inspect --format '{{.State.Running}}' "$container" 2>/dev/null)" == true ]] || fail 'Docker verification container is not running'
  mysql_client=(docker exec -i "$container" mysql -uroot --default-character-set=utf8mb4 --batch --skip-column-names)
else
  : "${SERVICEHUB_MYSQL_CLIENT_DEFAULTS_FILE:?Set an absolute private MySQL client defaults file}"
  defaults_file="$SERVICEHUB_MYSQL_CLIENT_DEFAULTS_FILE"
  [[ "$defaults_file" = /* && -f "$defaults_file" ]] || fail 'client defaults file must be an existing absolute path'
  permissions="$((10#$(stat -c '%a' "$defaults_file")))"
  (( permissions % 100 == 0 )) || fail 'client defaults file must not be group/world readable'
  command -v mysql >/dev/null 2>&1 || fail 'mysql client is unavailable'
  mysql_client=(mysql --defaults-extra-file="$defaults_file" --default-character-set=utf8mb4 --batch --skip-column-names)
fi

mapfile -t migrations < <(find backend/src/main/resources/db/migration -maxdepth 1 -type f -name 'V*.sql' -printf '%f\n' | sort -V)
(( ${#migrations[@]} >= 44 )) || fail 'migration chain does not contain V1 through V44'
[[ "${migrations[-1]}" == V44__*.sql ]] || fail 'V44 migration is missing or is not the highest migration'
for version in $(seq 1 44); do
  count=0
  for file in "${migrations[@]}"; do [[ "$file" == "V${version}__"*.sql ]] && count=$((count + 1)); done
  (( count == 1 )) || fail "expected exactly one V${version} migration, found ${count}"
done

existing="$("${mysql_client[@]}" -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='${database}'")"
[[ "$existing" == '0' ]] || fail 'verification schema already exists; refusing to reuse or overwrite it'
created=false
cleanup() {
  if [[ "$created" == true ]]; then
    "${mysql_client[@]}" -e "DROP DATABASE IF EXISTS \`${database}\`" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

"${mysql_client[@]}" -e "CREATE DATABASE \`${database}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
created=true
"${mysql_client[@]}" "$database" < devops/database-baseline/flowable-7.2.0-mysql-schema.sql

for file in "${migrations[@]}"; do
  if [[ "$file" == V43__*.sql ]]; then
    "${mysql_client[@]}" "$database" -e "
      INSERT INTO support_queue_command_idempotency
        (actor_iam_user_id,idempotency_key,operation,resource_type,resource_code,request_fingerprint,status,response_status,response_summary,error_code,created_at,completed_at,expires_at,version)
      VALUES
        ('verify-v42-progress','42000000-0000-4000-8000-000000000001','UPDATE','support-queue','VERIFY_QUEUE','AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA','IN_PROGRESS',NULL,NULL,NULL,UTC_TIMESTAMP(6),NULL,UTC_TIMESTAMP(6)+INTERVAL 1 DAY,0),
        ('verify-v42-success','42000000-0000-4000-8000-000000000002','ACTIVATE','support-queue','VERIFY_QUEUE','BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB','SUCCEEDED',200,JSON_OBJECT('legacy',TRUE),NULL,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)+INTERVAL 1 DAY,1),
        ('verify-v42-failed','42000000-0000-4000-8000-000000000003','DEACTIVATE','support-queue','VERIFY_QUEUE','CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC','FAILED_RETRYABLE',NULL,NULL,'LEGACY_FAILURE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)+INTERVAL 1 DAY,1);"
  fi
  if [[ "$file" == V44__*.sql ]]; then
    "${mysql_client[@]}" "$database" -e "
      INSERT INTO support_queue_command_idempotency
        (actor_iam_user_id,idempotency_key,operation,resource_type,resource_code,request_fingerprint,status,response_status,response_summary,error_code,created_at,completed_at,expires_at,version,lease_owner,lease_expires_at,attempt_count,key_version,result_resource_type,result_resource_id,heartbeat_at)
      VALUES
        ('verify-v43-strong','43000000-0000-4000-8000-000000000001','CREATE','support-queue','VERIFY_QUEUE','DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD','SUCCEEDED',201,JSON_OBJECT('legacy',TRUE),NULL,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)+INTERVAL 1 DAY,1,NULL,NULL,1,NULL,'support-queue','VERIFY_QUEUE',NULL);"
  fi
  started="$(date +%s%N)"
  "${mysql_client[@]}" "$database" < "backend/src/main/resources/db/migration/$file"
  finished="$(date +%s%N)"
  printf 'migration applied: %s (%s ms)\n' "$file" "$(((finished - started) / 1000000))"
done

required_tables=(support_team support_queue support_team_member support_queue_scope ticket_workflow_queue_routing_snapshot \
  knowledge_document knowledge_document_version support_queue_command_idempotency support_queue_migration_plan support_queue_migration_plan_item \
  support_queue_idempotency_reconciliation_request)
for table in "${required_tables[@]}"; do
  count="$("${mysql_client[@]}" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${database}' AND table_name='${table}'")"
  [[ "$count" == '1' ]] || fail "required table ${table} is missing"
done

for spec in 'knowledge_document:owning_organization_id' 'knowledge_document:service_catalog_item_ids' \
            'knowledge_document_version:owning_organization_id' 'knowledge_document_version:service_catalog_item_ids' \
            'ticket_workflow_task:queue_code' 'support_queue_command_idempotency:lease_owner' \
            'support_queue_command_idempotency:lease_expires_at' 'support_queue_command_idempotency:attempt_count' \
            'support_queue_command_idempotency:key_version' 'support_queue_command_idempotency:result_resource_type' \
            'support_queue_command_idempotency:result_resource_id' 'support_queue_command_idempotency:heartbeat_at'; do
  table="${spec%%:*}"; column="${spec#*:}"
  count="$("${mysql_client[@]}" -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${database}' AND table_name='${table}' AND column_name='${column}'")"
  [[ "$count" == '1' ]] || fail "required column ${table}.${column} is missing"
done

constraint_count="$("${mysql_client[@]}" -e "SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema='${database}' AND constraint_type IN ('FOREIGN KEY','CHECK')")"
(( constraint_count > 0 )) || fail 'foreign-key/check constraints were not installed'

"${mysql_client[@]}" "$database" -e "EXPLAIN SELECT q.queue_code FROM support_queue q JOIN support_team_member m ON m.team_code=q.team_code AND m.active=TRUE JOIN support_queue_scope s ON s.queue_code=q.queue_code AND s.active=TRUE WHERE q.status='ACTIVE' AND m.iam_user_id='verify-user' AND s.scope_type='ORGANIZATION' AND s.scope_id='verify-org' ORDER BY q.queue_code LIMIT 20" >/dev/null
"${mysql_client[@]}" "$database" -e "EXPLAIN SELECT id,title FROM knowledge_document WHERE owning_organization_id='verify-org' AND status='PUBLISHED' ORDER BY updated_at DESC LIMIT 20" >/dev/null
"${mysql_client[@]}" "$database" -e "EXPLAIN SELECT id,ticket_id FROM ticket_workflow_task WHERE queue_code='VERIFY' AND status='OPEN' ORDER BY created_at LIMIT 100" >/dev/null
"${mysql_client[@]}" "$database" -e "EXPLAIN SELECT plan_id,item_id,ticket_id FROM support_queue_migration_plan_item WHERE plan_id='VERIFY' AND status='PENDING' ORDER BY item_type,item_id LIMIT 100" >/dev/null
"${mysql_client[@]}" "$database" -e "EXPLAIN SELECT actor_iam_user_id,idempotency_key,version FROM support_queue_command_idempotency WHERE status='IN_PROGRESS' AND lease_expires_at<UTC_TIMESTAMP(6) ORDER BY lease_expires_at LIMIT 100" >/dev/null

for index in idx_support_queue_command_lease idx_support_queue_command_result idx_support_queue_command_key_retention; do
  count="$("${mysql_client[@]}" -e "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='${database}' AND table_name='support_queue_command_idempotency' AND index_name='${index}'")"
  (( count > 0 )) || fail "required V43 index ${index} is missing"
done

null_versions="$("${mysql_client[@]}" "$database" -e "SELECT COUNT(*) FROM support_queue_command_idempotency WHERE key_version IS NULL")"
[[ "$null_versions" == '0' ]] || fail 'V44 left NULL key versions'
active_legacy="$("${mysql_client[@]}" "$database" -e "SELECT COUNT(*) FROM support_queue_command_idempotency WHERE status='IN_PROGRESS' AND (key_version='LEGACY_UNVERIFIABLE' OR lease_owner IS NULL OR lease_expires_at IS NULL)")"
[[ "$active_legacy" == '0' ]] || fail 'V44 left active legacy IN_PROGRESS rows'
classification="$("${mysql_client[@]}" "$database" -e "SELECT GROUP_CONCAT(CONCAT(actor_iam_user_id,':',status) ORDER BY actor_iam_user_id SEPARATOR ',') FROM support_queue_command_idempotency WHERE actor_iam_user_id LIKE 'verify-v4%'")"
expected='verify-v42-failed:FAILED_FINAL,verify-v42-progress:RECONCILIATION_REQUIRED,verify-v42-success:RECONCILIATION_REQUIRED,verify-v43-strong:LEGACY_RESULT_ONLY'
[[ "$classification" == "$expected" ]] || fail "unexpected V44 legacy classification: ${classification}"

printf 'V44 MySQL migration verification passed: %s migrations, %s FK/CHECK constraints; V42/V43 history classified; disposable schema cleaned on exit\n' "${#migrations[@]}" "$constraint_count"
