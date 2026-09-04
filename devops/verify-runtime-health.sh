#!/usr/bin/env bash
# Verifies the deployment's protected liveness/readiness contract.  Probe credentials are supplied
# through a private curl config file (for example, a short-lived monitor token) and are never read
# into shell output or logs.
set -euo pipefail

: "${SERVICEHUB_RUNTIME_BASE_URL:?Set the approved internal service base URL}"
base_url="${SERVICEHUB_RUNTIME_BASE_URL%/}"
fail() { printf 'runtime health verification failed: %s\n' "$1" >&2; exit 1; }
[[ "$base_url" =~ ^https?://[^/[:space:]]+$ ]] || fail 'runtime base URL must be an http(s) origin without a path'
command -v curl >/dev/null 2>&1 || fail 'curl is unavailable'

base_curl_options=(--silent --show-error --connect-timeout 3 --max-time 10 --output /dev/null --write-out '%{http_code}')
authenticated_curl_options=("${base_curl_options[@]}")
if [[ -n "${SERVICEHUB_HEALTH_CURL_CONFIG:-}" ]]; then
  config_file="$SERVICEHUB_HEALTH_CURL_CONFIG"
  [[ "$config_file" = /* && -f "$config_file" ]] || fail 'health curl config must be an existing absolute path'
  permissions="$((10#$(stat -c '%a' "$config_file")))"
  (( permissions % 100 == 0 )) || fail 'health curl config must not be group/world readable'
  authenticated_curl_options+=(--config "$config_file")
fi

unauthenticated_status="$(curl "${base_curl_options[@]}" "$base_url/actuator/health")" || fail 'unauthenticated health endpoint did not respond'
[[ "$unauthenticated_status" == '401' ]] || fail "unauthenticated actuator health returned HTTP ${unauthenticated_status}, expected 401"

[[ -n "${SERVICEHUB_HEALTH_CURL_CONFIG:-}" ]] || fail 'a protected ACTUATOR_VIEW probe credential is required for readiness verification'
for endpoint in health/liveness health/readiness; do
  status="$(curl "${authenticated_curl_options[@]}" "$base_url/actuator/$endpoint")" || fail "protected ${endpoint} endpoint did not respond"
  [[ "$status" == '200' ]] || fail "protected ${endpoint} endpoint returned HTTP ${status}, expected 200"
done

printf 'runtime liveness/readiness verification passed\n'
