#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run this script with sudo." >&2
  exit 1
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mail_dir="$(cd "$script_dir/.." && pwd)"
deploy_user="${SUDO_USER:-root}"
deploy_group="$(id -gn "$deploy_user")"

for command in certbot docker nginx openssl; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Required command is missing: $command" >&2
    exit 1
  fi
done

if ! getent group 2000 >/dev/null 2>&1; then
  groupadd --gid 2000 stalwart-mail
fi
usermod --append --groups 2000 "$deploy_user"

install -d -o 2000 -g 2000 -m 2770 /srv/stalwart/etc
install -d -o 2000 -g 2000 -m 2770 /srv/stalwart/data
install -d -o 2000 -g 2000 -m 2770 /srv/stalwart/certs
install -d -o 33 -g 2000 -m 2770 /srv/stalwart/roundcube
install -d -o "$deploy_user" -g "$deploy_group" -m 0700 /srv/stalwart/secrets
install -d -o "$deploy_user" -g "$deploy_group" -m 0700 /srv/stalwart/backups

secret_file="/srv/stalwart/secrets/mail.env"
if [[ ! -f "$secret_file" ]]; then
  roundcube_key="$(openssl rand -hex 24)"
  printf 'ROUNDCUBE_DES_KEY=%s\n' "$roundcube_key" >"$secret_file"
  chown "$deploy_user:$deploy_group" "$secret_file"
  chmod 0600 "$secret_file"
fi

nginx_available="/etc/nginx/sites-available/mail.sinshield.app"
nginx_enabled="/etc/nginx/sites-enabled/mail.sinshield.app"
certificate_lineage="/etc/letsencrypt/live/mail.sinshield.app"
if [[ ! -f "$certificate_lineage/fullchain.pem" ]]; then
  install -d -o root -g root -m 0755 /var/www/certbot
  install -o root -g root -m 0644 "$mail_dir/nginx/mail.sinshield.app.bootstrap.conf" "$nginx_available"
  ln -sfn "$nginx_available" "$nginx_enabled"
  nginx -t
  systemctl reload nginx
  certbot certonly --webroot --webroot-path /var/www/certbot -d mail.sinshield.app
fi

install -o root -g root -m 0644 "$mail_dir/nginx/mail.sinshield.app.conf" "$nginx_available"
ln -sfn "$nginx_available" "$nginx_enabled"
nginx -t
systemctl reload nginx

hook_target="/etc/letsencrypt/renewal-hooks/deploy/sinshield-mail"
install -o root -g root -m 0755 "$mail_dir/scripts/certbot-deploy-hook.sh" "$hook_target"
RENEWED_LINEAGE="$certificate_lineage" "$hook_target"

echo
echo "Host bootstrap complete. Re-run the Mail Infrastructure workflow with operation=deploy."
