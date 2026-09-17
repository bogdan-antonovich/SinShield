#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mail_dir="$(cd "$script_dir/.." && pwd)"
compose_file="$mail_dir/docker-compose.yml"
secret_file="/srv/stalwart/secrets/mail.env"

required_paths=(
  /srv/stalwart/etc
  /srv/stalwart/data
  /srv/stalwart/certs/fullchain.pem
  /srv/stalwart/certs/privkey.pem
  /srv/stalwart/roundcube
  "$secret_file"
)

for path in "${required_paths[@]}"; do
  if [[ ! -e "$path" ]]; then
    echo "Host bootstrap is incomplete; missing $path" >&2
    echo "Run: sudo $mail_dir/scripts/bootstrap-host.sh" >&2
    exit 1
  fi
done

set -a
# shellcheck disable=SC1090
source "$secret_file"
set +a

export MAIL_IPV4="${MAIL_IPV4:-72.60.118.148}"
export STALWART_IMAGE="${STALWART_IMAGE:-stalwartlabs/stalwart:v0.16}"
export ROUNDCUBE_IMAGE="${ROUNDCUBE_IMAGE:-roundcube/roundcubemail:1.7.x-apache}"

docker compose -f "$compose_file" config --quiet

if docker inspect stalwart >/dev/null 2>&1; then
  "$script_dir/backup.sh"
fi

docker compose -f "$compose_file" pull
docker compose -f "$compose_file" up -d --remove-orphans
"$script_dir/health-check.sh"

echo "Mail deployment completed with Stalwart image: $STALWART_IMAGE"

