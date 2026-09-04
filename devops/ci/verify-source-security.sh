#!/usr/bin/env bash
# Static, dependency-free safeguards that run before external SCA/SAST tools.  External scanners
# are intentionally handled by run-security-scans.sh and fail closed when their databases/tools
# are unavailable.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"
fail() { printf 'source security gate failed: %s\n' "$1" >&2; exit 1; }

[[ -f frontend/package-lock.json ]] || fail 'frontend/package-lock.json is required'
[[ -f backend/pom.xml ]] || fail 'backend/pom.xml is required'

if git ls-files | grep -E '(^|/)(\.env|.*\.local|id_rsa|id_ed25519|.*\.(pem|key|p12|pfx))$' | \
    grep -Ev '(^backend/\.env\.example$|^devops/\.env\.production\.example$)'; then
  fail 'a secret-bearing file is tracked by git'
fi

if grep -RInE --exclude-dir=node_modules --exclude-dir=target --exclude-dir=dist --exclude-dir=.git --include='pom.xml' '<version>[[:space:]]*(LATEST|RELEASE|\[[^<]*|\([^<]*)[[:space:]]*</version>' .; then
  fail 'Maven dynamic dependency version found'
fi
if grep -RInE --exclude-dir=node_modules --exclude-dir=dist --include='package.json' '"[^"@]+"[[:space:]]*:[[:space:]]*"(latest|next|\*|[~^]?[0-9]+\.x)' frontend; then
  fail 'npm floating dependency range found; pin the exact version and refresh package-lock.json'
fi
possible_secret_pattern="(password|secret|token|api[_-]?key)[[:space:]]*[:=][[:space:]]*[\\\"']?[A-Za-z0-9_./+=-]{16,}"
# Restrict this quick guard to deployable configuration.  Full repository credential detection is
# performed by Gitleaks below; keeping this path tracked-only prevents a developer's ignored local
# environment file from being read or echoed by CI tooling.
if git grep -nI -E "$possible_secret_pattern" -- ':!*.example' ':!*.md' \
    backend/src/main/resources devops .github; then
  fail 'possible committed plaintext credential found; use a managed runtime secret reference'
fi

printf 'source security gate passed\n'
