# SinShield Android release hosting

The Android release pipeline publishes immutable signed AAB and APK files to an authenticated
Nginx virtual host at `releases.sinshield.app`. Anonymous requests receive `404`, including for
paths that exist. The only public HTTP route is `/.well-known/acme-challenge/`, which is required
to issue and renew the TLS certificate.

## Authentication

Use one URL-safe random token as the GitHub Actions secret `RELEASES_ACCESS_TOKEN`. Generate one
locally:

```bash
openssl rand -hex 32
```

The server accepts the token in three ways:

- Preferred for scripts: `Authorization: Bearer TOKEN`.
- For a browser download: append `?token=TOKEN` to the URL.
- After a valid query-token request, a Secure, HttpOnly, SameSite cookie permits clean links in
  that browser for 30 days.

Nginx access logging is disabled for this virtual host so query tokens are not written to its
access log. Query tokens can still remain in browser history, so the bearer header is preferred
for command-line downloads.

Example:

```bash
curl --fail --location \
  --header "Authorization: Bearer $RELEASES_ACCESS_TOKEN" \
  --output sinshield.aab \
  https://releases.sinshield.app/latest/sinshield-v1.0.0.aab
```

Each release is available under its tag:

```text
https://releases.sinshield.app/v1.0.0/sinshield-v1.0.0.aab
https://releases.sinshield.app/v1.0.0/sinshield-v1.0.0.apk
https://releases.sinshield.app/v1.0.0/release.json
https://releases.sinshield.app/latest.json
```

`latest` is a server-side alias for the newest published version. Artifact URLs inside
`release.json` intentionally omit the token.

## GitHub configuration

Create a GitHub environment named `production-android`. An approval rule can be added to prevent a
tag from deploying until it is manually approved.

Configure these repository or environment secrets:

| Secret | Purpose |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded permanent Android upload/release keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing-key alias |
| `ANDROID_KEY_PASSWORD` | Signing-key password |
| `RELEASES_ACCESS_TOKEN` | 32-128 character URL-safe download token |
| `VPS_HOST` | Existing SinShield VPS address |
| `VPS_USER` | Non-root SSH deployment user |
| `VPS_SSH_KEY` | Existing deployment SSH private key |

Encode a keystore without line wrapping:

```bash
base64 --wrap=0 /secure/path/sinshield-upload.jks
```

Keep an offline backup of the keystore and its credentials. Losing the signing key can prevent
future updates from being accepted as updates to the same Android application.

## One-time VPS bootstrap

Before bootstrap:

1. Point the `A` and, if used, `AAAA` records for `releases.sinshield.app` to the VPS.
2. Ensure ports 80 and 443 reach host Nginx.
3. Install Nginx, Certbot, curl, and OpenSSL.
4. Start the Android CD workflow once. It automatically syncs `infra/releases` from the tagged
   repository state to `~/sinshield-releases-infra` and then stops with a bootstrap instruction
   when the trusted publisher is not installed yet.

Run bootstrap from that copied directory:

```bash
cd ~/sinshield-releases-infra
sudo CERTBOT_EMAIL=admin@sinshield.app \
  DEPLOY_USER="$USER" \
  ./scripts/bootstrap-host.sh
```

The script installs a root-owned publisher and grants the deployment user passwordless sudo access
only to that publisher. It does not grant general passwordless sudo. The initial random token denies
all access; the first successful tagged deployment replaces it with `RELEASES_ACCESS_TOKEN`. A
Certbot deploy hook validates and reloads Nginx after future certificate renewals.

Every later deployment refreshes the unprivileged infrastructure copy automatically. If the
root-owned publisher differs from the tagged repository copy, deployment stops and asks an
administrator to rerun bootstrap. Repository-controlled files are never executed as root
automatically.

## Publishing

Push a semantic version tag such as `v1.0.0`. Android CD performs lint and unit tests, derives
`versionCode` as `MAJOR * 1,000,000 + MINOR * 1,000 + PATCH`, builds and verifies signed AAB and APK
artifacts, and publishes them atomically. Existing tag directories cannot be overwritten with
different content.

For a manual endpoint check:

```bash
RELEASES_ACCESS_TOKEN='your-token' \
EXPECTED_RELEASE_TAG='v1.0.0' \
./infra/releases/scripts/health-check.sh
```

## Token rotation

Change `RELEASES_ACCESS_TOKEN` in GitHub and rerun the Android CD job for the current tag. Publishing
is idempotent when the artifacts match, and the VPS replaces the authorization token during that
run. The previous token stops working immediately after Nginx reloads.
