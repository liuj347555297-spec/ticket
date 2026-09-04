#!/usr/bin/env bash
# Executes only an explicitly authorized rolling-deployment rehearsal.  It sends SIGTERM to one
# already-verified container and proves that it exits inside the configured shutdown budget.  It
# intentionally does not restart the container; the orchestrator/operator owns recovery.
set -euo pipefail

: "${SERVICEHUB_ALLOW_GRACEFUL_STOP:?Set exactly YES for an isolated rehearsal}"
: "${SERVICEHUB_GRACEFUL_CONTAINER:?Set the exact container ID or name}"
: "${SERVICEHUB_SHUTDOWN_TIMEOUT_SECONDS:=45}"
[[ "$SERVICEHUB_ALLOW_GRACEFUL_STOP" == 'YES' ]] || { printf 'graceful shutdown rehearsal requires explicit YES\n' >&2; exit 2; }
[[ "$SERVICEHUB_SHUTDOWN_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]] || { printf 'shutdown timeout must be a positive integer\n' >&2; exit 2; }
command -v docker >/dev/null 2>&1 || { printf 'graceful shutdown rehearsal failed: docker is unavailable\n' >&2; exit 1; }

container_id="$(docker inspect --format '{{.Id}}' "$SERVICEHUB_GRACEFUL_CONTAINER" 2>/dev/null)" || { printf 'graceful shutdown rehearsal failed: target container was not found\n' >&2; exit 1; }
running="$(docker inspect --format '{{.State.Running}}' "$container_id")"
[[ "$running" == 'true' ]] || { printf 'graceful shutdown rehearsal failed: target container is not running\n' >&2; exit 1; }

docker kill --signal=TERM "$container_id" >/dev/null
deadline=$((SECONDS + SERVICEHUB_SHUTDOWN_TIMEOUT_SECONDS))
while (( SECONDS < deadline )); do
  running="$(docker inspect --format '{{.State.Running}}' "$container_id" 2>/dev/null || printf 'false')"
  [[ "$running" == 'false' ]] && { printf 'graceful shutdown rehearsal passed\n'; exit 0; }
  sleep 1
done
printf 'graceful shutdown rehearsal failed: process did not exit within %s seconds\n' "$SERVICEHUB_SHUTDOWN_TIMEOUT_SECONDS" >&2
exit 1
