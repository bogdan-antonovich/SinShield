#!/usr/bin/env bash
set -euo pipefail

domain="${RELEASES_DOMAIN:-releases.sinshield.app}"
base_url="https://$domain"

if [[ ! "${RELEASES_ACCESS_TOKEN:-}" =~ ^[A-Za-z0-9_-]{32,128}$ ]]; then
  echo "Set RELEASES_ACCESS_TOKEN to the configured 32-128 character token." >&2
  exit 1
fi

unauthenticated_status="$(curl \
  --silent \
  --output /dev/null \
  --write-out '%{http_code}' \
  "$base_url/latest.json")"

if [ "$unauthenticated_status" != "404" ]; then
  echo "Expected unauthenticated status 404, got $unauthenticated_status." >&2
  exit 1
fi

if [ -n "${EXPECTED_RELEASE_TAG:-}" ]; then
  published_tag="$(curl \
    --fail \
    --silent \
    --show-error \
    --header "Authorization: Bearer $RELEASES_ACCESS_TOKEN" \
    "$base_url/latest.json" \
    | sed -n 's/.*"tag"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"

  if [ "$published_tag" != "$EXPECTED_RELEASE_TAG" ]; then
    echo "Expected $EXPECTED_RELEASE_TAG, got ${published_tag:-no release tag}." >&2
    exit 1
  fi
else
  curl \
    --fail \
    --silent \
    --show-error \
    --output /dev/null \
    --header "Authorization: Bearer $RELEASES_ACCESS_TOKEN" \
    "$base_url/"
fi

echo "Authenticated release endpoint is healthy; anonymous access is hidden."
