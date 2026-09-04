#!/usr/bin/env bash
# Validates a secret-managed compose environment without echoing any values.  The environment file
# remains outside git and must be private to the deployment identity.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"
: "${SERVICEHUB_DEPLOY_ENV_FILE:?Set the absolute path to the deployment environment file}"
env_file="$SERVICEHUB_DEPLOY_ENV_FILE"
fail() { printf 'deployment config gate failed: %s\n' "$1" >&2; exit 1; }
[[ "$env_file" = /* ]] || fail 'deployment environment file must use an absolute path'
[[ -f "$env_file" ]] || fail 'deployment environment file does not exist'
permissions="$((10#$(stat -c '%a' "$env_file")))"
(( permissions % 100 == 0 )) || fail 'deployment environment file must not be group/world readable'
git check-ignore -q "$env_file" || fail 'deployment environment file must be ignored by git'

image_value="$(sed -n 's/^SERVICEHUB_BACKEND_IMAGE=//p' "$env_file" | tail -n 1)"
[[ "$image_value" =~ ^[^[:space:]@]+@sha256:[a-f0-9]{64}$ ]] || fail 'SERVICEHUB_BACKEND_IMAGE must be an immutable digest'
command -v docker >/dev/null 2>&1 || fail 'docker is unavailable for compose validation'

error_file="$(mktemp)"
trap 'rm -f "$error_file"' EXIT
if ! docker compose --env-file "$env_file" -f devops/docker-compose.production.yml config --quiet > /dev/null 2>"$error_file"; then
  # Suppress compose interpolation output because it can include secret values.
  fail 'docker compose validation failed; inspect the protected runner log locally'
fi
printf 'deployment config gate passed\n'
