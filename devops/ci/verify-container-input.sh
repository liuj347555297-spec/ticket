#!/usr/bin/env bash
# Validates immutable inputs before an image build or deployment.  It deliberately never prints
# environment values, because those are often loaded from a secret-managed runtime file.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

fail() { printf 'production input gate failed: %s\n' "$1" >&2; exit 1; }
require_digest() {
  local variable_name="$1" value="${!1:-}"
  [[ -n "$value" ]] || fail "${variable_name} must be supplied"
  [[ "$value" =~ ^[^[:space:]@]+@sha256:[a-f0-9]{64}$ ]] || fail "${variable_name} must be an immutable image digest"
}

require_digest SERVICEHUB_BUILD_IMAGE
require_digest SERVICEHUB_RUNTIME_IMAGE
require_digest SERVICEHUB_NODE_BUILD_IMAGE
require_digest SERVICEHUB_NGINX_IMAGE
if [[ -n "${SERVICEHUB_BACKEND_IMAGE:-}" ]]; then
  require_digest SERVICEHUB_BACKEND_IMAGE
fi
if [[ -n "${SERVICEHUB_FRONTEND_IMAGE:-}" ]]; then
  require_digest SERVICEHUB_FRONTEND_IMAGE
fi

grep -Fq 'FROM ${SERVICEHUB_BUILD_IMAGE} AS build' backend/Dockerfile || fail 'backend build base is not deployment supplied'
grep -Fq 'FROM ${SERVICEHUB_RUNTIME_IMAGE}' backend/Dockerfile || fail 'backend runtime base is not deployment supplied'
grep -Fq 'USER servicehub:servicehub' backend/Dockerfile || fail 'backend image must run as the unprivileged servicehub user'
grep -Fq 'FROM ${SERVICEHUB_NODE_BUILD_IMAGE} AS build' frontend/Dockerfile || fail 'frontend build base is not deployment supplied'
grep -Fq 'FROM ${SERVICEHUB_NGINX_IMAGE}' frontend/Dockerfile || fail 'frontend runtime base is not deployment supplied'
grep -Fq 'read_only: true' devops/docker-compose.production.yml || fail 'production container must have a read-only root filesystem'
grep -Fq 'no-new-privileges:true' devops/docker-compose.production.yml || fail 'production container must set no-new-privileges'
grep -Fq 'cap_drop:' devops/docker-compose.production.yml || fail 'production container must drop Linux capabilities'
grep -Fq '127.0.0.1' devops/docker-compose.production.yml || fail 'production backend must default to loopback binding'

printf 'production container input gate passed\n'
