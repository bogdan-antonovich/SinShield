#!/usr/bin/env bash
set -euo pipefail

domain="releases.sinshield.app"
script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
release_root="$(CDPATH= cd -- "$script_dir/.." && pwd)"
nginx_available="/etc/nginx/sites-available/$domain"
nginx_enabled="/etc/nginx/sites-enabled/$domain"
publisher_path="/usr/local/sbin/sinshield-release-publish"
sudoers_path="/etc/sudoers.d/sinshield-release-publish"
certbot_hook_path="/etc/letsencrypt/renewal-hooks/deploy/sinshield-releases-nginx"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

if [ "$(id -u)" -ne 0 ]; then
  fail "Run this script with sudo."
fi

deploy_user="${DEPLOY_USER:-${SUDO_USER:-}}"
if [ -z "$deploy_user" ] || [ "$deploy_user" = "root" ]; then
  fail "Set DEPLOY_USER to the non-root account used by GitHub Actions."
fi
getent passwd "$deploy_user" >/dev/null || fail "Deployment user $deploy_user does not exist."
getent group www-data >/dev/null || fail "Expected Nginx group www-data does not exist."

if [ -z "${CERTBOT_EMAIL:-}" ]; then
  fail "Set CERTBOT_EMAIL to the address Certbot should use for expiry notices."
fi

for command in certbot curl nginx openssl visudo; do
  command -v "$command" >/dev/null || fail "Required command is not installed: $command"
done

deploy_home="$(getent passwd "$deploy_user" | cut -d: -f6)"

install -d -o root -g www-data -m 0750 "/srv/www/$domain"
install -d -o root -g root -m 0755 /var/www/certbot
install -d -o "$deploy_user" -g "$deploy_user" -m 0750 "$deploy_home/sinshield-releases"
install -d -o "$deploy_user" -g "$deploy_user" -m 0750 "$deploy_home/sinshield-releases/incoming"

install -o root -g root -m 0755 "$script_dir/sinshield-release-publish" "$publisher_path"
install -o root -g root -m 0755 "$script_dir/certbot-deploy-hook.sh" "$certbot_hook_path"

temporary_sudoers="$(mktemp)"
trap 'rm -f "$temporary_sudoers"' EXIT
printf '%s ALL=(root) NOPASSWD: %s\n' "$deploy_user" "$publisher_path" > "$temporary_sudoers"
chmod 0440 "$temporary_sudoers"
visudo -cf "$temporary_sudoers"
install -o root -g root -m 0440 "$temporary_sudoers" "$sudoers_path"

install -o root -g root -m 0644 \
  "$release_root/nginx/releases.sinshield.app.bootstrap.conf" \
  "$nginx_available"
ln -sfn "$nginx_available" "$nginx_enabled"
nginx -t
systemctl reload nginx

if [ ! -f "/etc/letsencrypt/live/$domain/fullchain.pem" ]; then
  certbot certonly \
    --webroot \
    --webroot-path /var/www/certbot \
    --domain "$domain" \
    --email "$CERTBOT_EMAIL" \
    --agree-tos \
    --non-interactive
fi

bootstrap_token="$(openssl rand -hex 32)"
printf '%s\n' "$bootstrap_token" | "$publisher_path" --configure-token
unset bootstrap_token

install -o root -g root -m 0644 \
  "$release_root/nginx/releases.sinshield.app.conf" \
  "$nginx_available"
nginx -t
systemctl reload nginx

echo "Release host bootstrap complete."
echo "All HTTPS content remains hidden until an authenticated release is published."
