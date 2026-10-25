#!/usr/bin/env bash
# Branch and pull-request check (Workers Builds non-production builds, or by
# hand): every release check plus dry-run bundles of both Workers. Deploys nothing.
set -euo pipefail
cd "$(dirname "$0")/.."

bash scripts/cloudflare-checks.sh
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
npm exec --prefix web -- wrangler deploy --dry-run --config web/wrangler.jsonc --outdir "$out/kik"
npm exec --prefix web -- wrangler deploy --dry-run --config cloudflare/wrangler.jsonc --outdir "$out/kik-updates"
