#!/usr/bin/env bash
# A read-only workload for capacity evidence.  It is not a claim that the 1,000-user target has
# passed: the caller must set SERVICEHUB_LOAD_VUS=1000 and archive the generated summary.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"
: "${SERVICEHUB_RUNTIME_BASE_URL:?Set the approved test-environment base URL}"
: "${SERVICEHUB_LOAD_HEADERS_JSON:?Set authenticated request headers through protected CI variables}"
: "${SERVICEHUB_K6_SUMMARY_FILE:?Set an absolute evidence output path}"
summary_file="$SERVICEHUB_K6_SUMMARY_FILE"
[[ "$summary_file" = /* ]] || { printf 'load test failed: evidence output path must be absolute\n' >&2; exit 2; }
command -v k6 >/dev/null 2>&1 || { printf 'load test failed: k6 is unavailable\n' >&2; exit 1; }
mkdir -p "$(dirname "$summary_file")"

k6 run --summary-export "$summary_file" devops/load-test/k6-read-only.js
[[ -s "$summary_file" ]] || { printf 'load test failed: k6 produced no summary evidence\n' >&2; exit 1; }
printf 'read-only load-test evidence written to %s\n' "$summary_file"
