#!/usr/bin/env bash
set -euo pipefail

attempts="${HEALTH_CHECK_ATTEMPTS:-30}"
delay="${HEALTH_CHECK_DELAY_SECONDS:-5}"

for ((attempt = 1; attempt <= attempts; attempt++)); do
  stalwart_running="$(docker inspect -f '{{.State.Running}}' stalwart 2>/dev/null || true)"
  roundcube_running="$(docker inspect -f '{{.State.Running}}' sinshield-roundcube 2>/dev/null || true)"

  if [[ "$stalwart_running" == true && "$roundcube_running" == true ]] \
    && curl --fail --silent --show-error --max-time 5 http://127.0.0.1:8080/admin >/dev/null \
    && curl --fail --silent --show-error --max-time 5 http://127.0.0.1:9002/ >/dev/null; then
    echo "Mail services are healthy."
    exit 0
  fi

  echo "Waiting for mail services ($attempt/$attempts)..."
  sleep "$delay"
done

echo "Mail services did not become healthy." >&2
docker logs --tail 100 stalwart >&2 || true
docker logs --tail 100 sinshield-roundcube >&2 || true
exit 1

