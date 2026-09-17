#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mail_dir="$(cd "$script_dir/.." && pwd)"
compose_file="$mail_dir/docker-compose.yml"
backup_dir="/srv/stalwart/backups"
secret_file="/srv/stalwart/secrets/mail.env"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
archive="$backup_dir/stalwart-$timestamp.tar.gz"

if [[ ! -r "$secret_file" ]]; then
  echo "Cannot read $secret_file; run the host bootstrap first." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$secret_file"
set +a

mkdir -p "$backup_dir"
chmod 0700 "$backup_dir"

was_running=false
if docker inspect -f '{{.State.Running}}' stalwart 2>/dev/null | grep -qx true; then
  was_running=true
  docker compose -f "$compose_file" stop stalwart roundcube
fi

restore_services() {
  if [[ "$was_running" == true ]]; then
    docker compose -f "$compose_file" start stalwart roundcube >/dev/null
  fi
}
trap restore_services EXIT

tar -C /srv/stalwart -czf "$archive" etc data certs roundcube secrets
chmod 0600 "$archive"

restore_services
was_running=false
trap - EXIT

echo "$archive"
