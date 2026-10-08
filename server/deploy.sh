#!/usr/bin/env bash
# One-shot deploy of the PersonalMentor proxy to Cloudflare Workers.
# Usage: UPSTREAM_API_KEY=sk-... [REPORT_WEBHOOK_URL=https://...] ./deploy.sh
set -euo pipefail
cd "$(dirname "$0")"

: "${UPSTREAM_API_KEY:?Set UPSTREAM_API_KEY to your AI provider key}"

echo "Running proxy tests..."
npm test --silent

# A fresh client token; keep CLIENT_TOKENS from the environment to rotate (comma-separated, new first).
NEW_TOKEN="$(openssl rand -hex 24)"
CLIENT_TOKENS="${CLIENT_TOKENS:-$NEW_TOKEN}"
APP_TOKEN="${CLIENT_TOKENS%%,*}"

printf '%s' "$UPSTREAM_API_KEY" | npx --yes wrangler secret put UPSTREAM_API_KEY
printf '%s' "$CLIENT_TOKENS"    | npx --yes wrangler secret put CLIENT_TOKENS
if [[ -n "${REPORT_WEBHOOK_URL:-}" ]]; then
  printf '%s' "$REPORT_WEBHOOK_URL" | npx --yes wrangler secret put REPORT_WEBHOOK_URL
fi
npx --yes wrangler deploy

cat <<MSG

Done. Put these in the app's local.properties (use the URL wrangler printed above):

PROXY_BASE_URL=https://personalmentor-proxy.<your-subdomain>.workers.dev/
PROXY_CLIENT_TOKEN=${APP_TOKEN}

Check it:  curl -s -X POST "\$PROXY_BASE_URL/v1/report" -H "Authorization: Bearer ${APP_TOKEN}" \\
             -H 'content-type: application/json' -d '{"reason":"other","response":"test"}' -o /dev/null -w '%{http_code}\n'   # expect 204
MSG
