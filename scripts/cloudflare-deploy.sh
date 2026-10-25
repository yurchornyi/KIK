#!/usr/bin/env bash
# Production deploy run by Cloudflare Workers Builds after a push to main: no
# owner computer and no Cloudflare token in GitHub. A failing check stops the
# build before anything is deployed. Only the `kik` Worker deploys here, as
# before; kik-updates, the kiktv Pages project and remote D1 migrations stay
# manual (README).
set -euo pipefail
cd "$(dirname "$0")/.."

sha="${WORKERS_CI_COMMIT_SHA:?Workers Builds sets WORKERS_CI_COMMIT_SHA}"
if [[ "${WORKERS_CI_BRANCH:-}" != "main" ]]; then
  echo "Production deploys only from main, not ${WORKERS_CI_BRANCH:-an unknown branch}" >&2
  exit 1
fi

bash scripts/cloudflare-checks.sh
cd web
npx --no-install wrangler deploy --tag "$sha"
