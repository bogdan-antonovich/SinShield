#!/usr/bin/env bash
set -euo pipefail

if [[ "${RESET_CONFIRMATION:-}" != "RESET-STALWART" ]]; then
  echo "Refusing to reset Stalwart: reset confirmation does not match RESET-STALWART." >&2
  exit 1
fi

if [[ -z "${STALWART_RECOVERY_ADMIN:-}" || "$STALWART_RECOVERY_ADMIN" != *:* ]]; then
  echo "STALWART_RECOVERY_ADMIN must be set in username:password form." >&2
  exit 1
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mail_dir="$(cd "$script_dir/.." && pwd)"
compose_file="$mail_dir/docker-compose.yml"
bootstrap_file="$mail_dir/docker-compose.bootstrap.yml"
secret_file="/srv/stalwart/secrets/mail.env"

if [[ ! -r "$secret_file" ]]; then
  echo "Cannot read $secret_file; run the host bootstrap first." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$secret_file"
set +a

backup_archive="$($script_dir/backup.sh)"
docker compose -f "$compose_file" down

find /srv/stalwart/etc -mindepth 1 -delete
find /srv/stalwart/data -mindepth 1 -delete

docker compose -f "$compose_file" -f "$bootstrap_file" config --quiet
docker compose -f "$compose_file" -f "$bootstrap_file" up -d --remove-orphans
"$script_dir/health-check.sh"

echo "Stalwart bootstrap was reset after creating $backup_archive"
echo "Sign in as the username from STALWART_RECOVERY_ADMIN, complete the wizard, then run operation=deploy."
