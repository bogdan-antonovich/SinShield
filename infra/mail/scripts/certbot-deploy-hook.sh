#!/usr/bin/env bash
set -euo pipefail

lineage="${RENEWED_LINEAGE:-/etc/letsencrypt/live/mail.sinshield.app}"
certificate_dir="/srv/stalwart/certs"

if [[ ! -f "$lineage/fullchain.pem" || ! -f "$lineage/privkey.pem" ]]; then
  echo "Certificate lineage is incomplete: $lineage" >&2
  exit 1
fi

install -d -o 2000 -g 2000 -m 2770 "$certificate_dir"
install -o 2000 -g 2000 -m 0644 "$lineage/fullchain.pem" "$certificate_dir/fullchain.pem"
install -o 2000 -g 2000 -m 0640 "$lineage/privkey.pem" "$certificate_dir/privkey.pem"

if docker inspect stalwart >/dev/null 2>&1; then
  docker restart stalwart >/dev/null
  echo "Stalwart restarted with the renewed certificate."
fi
