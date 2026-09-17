#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mail_dir="$(cd "$script_dir/.." && pwd)"
compose_file="$mail_dir/docker-compose.yml"
backup_dir="/srv/stalwart/backups"
secret_file="/srv/stalwart/secrets/mail.env"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
archive="$backup_dir/stalwart-$timestamp.tar.gz"
archive_name="$(basename "$archive")"
backup_image="${ROUNDCUBE_IMAGE:-roundcube/roundcubemail:1.7.x-apache}"

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

# Files inside bind mounts keep their container ownership and modes, so the
# deployment user cannot reliably read them directly. Use an already-pulled
# service image as a narrowly mounted root helper, then return ownership of the
# completed archive to the deployment user.
docker run --rm \
  --entrypoint /bin/sh \
  --volume /srv/stalwart:/source:ro \
  --volume "$backup_dir:/backup" \
  "$backup_image" \
  -c 'tar -C /source -czf "/backup/$1" etc data certs roundcube secrets && chown "$2:$3" "/backup/$1" && chmod 0600 "/backup/$1"' \
  backup-helper "$archive_name" "$(id -u)" "$(id -g)"

restore_services
was_running=false
trap - EXIT

echo "$archive"
