#!/usr/bin/env bash
# Every check that gates a web release, run by Cloudflare Workers Builds before
# a deploy (and alone on branch and pull-request builds). It needs only Node,
# Python 3 and git from the build image. The TV app's Java checks need a JDK and
# the Android SDK, which the image lacks: scripts/check-tv.sh runs them on the
# owner's Mac before every APK build.
set -euo pipefail
cd "$(dirname "$0")/.."

# No private build input (signing keys, APKs, decoded app) may reach Git.
bad=$(git ls-files | grep -E '^(work|backup|decoded|decompiled|localized|outputs)/|\.(apk|jks|keystore|key|pem)$' || true)
if [[ -n "$bad" ]]; then echo "Private build inputs are tracked:" >&2; echo "$bad" >&2; exit 1; fi

python3 -m py_compile scripts/*.py
node --test cloudflare/worker.test.mjs
npm test --prefix web
npm run build --prefix web

# Admin, settings and security against a local Worker with a local D1.
npm exec --prefix web -- wrangler d1 migrations apply kik-data --local --config web/wrangler.jsonc
port=$(node -e 'const s=require("net").createServer().listen(0,"127.0.0.1",()=>{console.log(s.address().port);s.close()})')
export KIK_TEST_URL="http://localhost:$port"
log=$(mktemp)
npm exec --prefix web -- wrangler dev --config web/wrangler.test.jsonc --port "$port" >"$log" 2>&1 &
preview_pid=$!
trap 'kill "$preview_pid" 2>/dev/null || true; rm -f "$log"' EXIT
ready=0
for _ in $(seq 1 60); do
  if curl -fsS "$KIK_TEST_URL/api/health" >/dev/null 2>&1; then ready=1; break; fi
  sleep 1
done
if [[ "$ready" != 1 ]]; then cat "$log" >&2; exit 1; fi
node web/management-integration.mjs
node web/security-integration.mjs
