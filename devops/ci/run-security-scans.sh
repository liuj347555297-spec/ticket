#!/usr/bin/env bash
# Generates auditable SBOM and scanner evidence.  This is intentionally fail-closed: an offline
# runner without its pre-provisioned scanner binaries or vulnerability databases cannot attest a
# release as clean.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"
artifact_dir="${SERVICEHUB_SECURITY_ARTIFACT_DIR:-$repo_root/output/security}"
mkdir -p "$artifact_dir"
fail() { printf 'security scan gate failed: %s\n' "$1" >&2; exit 1; }
for command in syft trivy gitleaks npm mvn; do command -v "$command" >/dev/null 2>&1 || fail "required tool '$command' is unavailable"; done

bash devops/ci/verify-source-security.sh

# Do not let npm invoke package lifecycle scripts during a supply-chain gate.
(cd frontend && npm ci --ignore-scripts)
(cd backend && mvn -B -DskipTests package)

syft "dir:$repo_root/backend/target" -o cyclonedx-json > "$artifact_dir/backend-sbom.cdx.json"
syft "dir:$repo_root/frontend" -o cyclonedx-json > "$artifact_dir/frontend-sbom.cdx.json"
gitleaks detect --no-banner --source "$repo_root" --redact --report-format json --report-path "$artifact_dir/gitleaks.json"
trivy fs --quiet --exit-code 1 --severity HIGH,CRITICAL --scanners vuln,secret,misconfig \
  --format json --output "$artifact_dir/source-trivy.json" "$repo_root"

if [[ -n "${SERVICEHUB_BACKEND_IMAGE:-}" ]]; then
  bash devops/ci/verify-container-input.sh
  syft "${SERVICEHUB_BACKEND_IMAGE}" -o cyclonedx-json > "$artifact_dir/backend-image-sbom.cdx.json"
  trivy image --quiet --exit-code 1 --severity HIGH,CRITICAL --format json \
    --output "$artifact_dir/backend-image-trivy.json" "${SERVICEHUB_BACKEND_IMAGE}"
fi
if [[ -n "${SERVICEHUB_FRONTEND_IMAGE:-}" ]]; then
  bash devops/ci/verify-container-input.sh
  syft "${SERVICEHUB_FRONTEND_IMAGE}" -o cyclonedx-json > "$artifact_dir/frontend-image-sbom.cdx.json"
  trivy image --quiet --exit-code 1 --severity HIGH,CRITICAL --format json \
    --output "$artifact_dir/frontend-image-trivy.json" "${SERVICEHUB_FRONTEND_IMAGE}"
fi

printf 'security scan evidence written to %s\n' "$artifact_dir"
