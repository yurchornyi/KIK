#!/bin/bash
# Publishes the next KIK release by itself when main changes what goes into the APK, the way a merge to
# TV-Launcher's main does. The APK can only be built here: the original APK, decoded/ and the signing key
# never enter Git. launchd runs this every 5 minutes (scripts/install-auto-release.sh) in its own worktree,
# .worktrees/release, whose private inputs are links to the main checkout's.
#
# A run: compare the latest GitHub release's tag with origin/main over APP; nothing changed, nothing to do.
# Otherwise bump localization/release.json (next versionCode, next minor versionName), build and check the
# APK (scripts/build.sh, scripts/validate.py), push the version commit to main and publish it
# (scripts/publish-release.py). The TVs take it from kik-updates as with a release made by hand.
set -euo pipefail
# The run resets this worktree to origin/main, this file included: it runs from a copy.
if [ -z "${KIK_AUTO_RELEASE_ROOT:-}" ]; then
  copy=$(mktemp)
  cp "$0" "$copy"
  KIK_AUTO_RELEASE_ROOT=$(cd "$(dirname "$0")/.." && pwd) exec bash "$copy"
fi
trap 'rm -f "$0"' EXIT
cd "$KIK_AUTO_RELEASE_ROOT"
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"
REPO=yurchornyi/KIK
# What the APK is built from (scripts/build.sh); the Java checks and the docs do not change it.
APP=(localization scripts/localize.py scripts/simplify.py scripts/branding.py scripts/build.sh scripts/media3.sh
     web/kik-intro.mp4 ':(exclude)localization/*.md')

git fetch -q origin main --tags
LAST=$(gh release view --repo "$REPO" --json tagName --jq .tagName)
# KIK_AUTO_RELEASE_DRY=1: build and check the next version whatever changed, then publish nothing.
DRY=${KIK_AUTO_RELEASE_DRY:-}
[ -z "$DRY" ] && git diff --quiet "$LAST" origin/main -- "${APP[@]}" && exit 0
# A main that failed once is not rebuilt every 5 minutes: the next commit tries again.
FAILED=outputs/auto-release.failed
MAIN=$(git rev-parse origin/main)
[ -z "$DRY" ] && [ "$(cat "$FAILED" 2>/dev/null)" = "$MAIN" ] && exit 0
mkdir -p outputs
trap 'status=$?; rm -f "$0"; if [ $status -ne 0 ]; then echo "$MAIN" > "$FAILED"
  osascript -e "display notification \"Автореліз KIK не вдався: ~/Library/Logs/kik-auto-release.log\" with title \"KIK\"" || true; fi' EXIT

echo "$(date '+%F %T') $LAST..origin/main changes the app: releasing"
git checkout -q --detach origin/main
git reset -q --hard origin/main
git clean -fdq
python3 - <<'PY'
import json, pathlib
path = pathlib.Path('localization/release.json')
release = json.loads(path.read_text())
major, minor = release['version_name'].split('.')[:2]
release['version_code'] += 1
release['version_name'] = f'{major}.{int(minor) + 1}'
path.write_text(json.dumps(release, indent=2, ensure_ascii=False) + '\n')
print(f"KIK {release['version_name']} (versionCode {release['version_code']})")
PY
VERSION=$(python3 -c 'import json; print(json.load(open("localization/release.json"))["version_name"])')
CODE=$(python3 -c 'import json; print(json.load(open("localization/release.json"))["version_code"])')
git tag -l "v$VERSION" | grep -q . && { echo "v$VERSION already exists" >&2; exit 1; }
git commit -qam "KIK $VERSION (versionCode $CODE)"

bash scripts/build.sh
python3 scripts/validate.py
if [ -n "$DRY" ]; then
  git reset -q --hard origin/main
  echo "Dry run: outputs/KIK-$VERSION.apk built and checked, nothing pushed or published"
  exit 0
fi

# The release targets the version commit on GitHub, so it goes to main first. If main moved meanwhile, the
# commit goes on top, unless the new commits change the app: the APK would lack them, so this run gives up
# and the next one builds them all.
for try in 1 2 3; do
  git push -q origin HEAD:main && break
  [ "$try" = 3 ] && { echo "main kept moving: nothing published" >&2; exit 1; }
  git fetch -q origin main
  if ! git diff --quiet HEAD~1 origin/main -- "${APP[@]}"; then
    echo "main got app changes during the build: the next run releases them" >&2
    exit 0
  fi
  git rebase -q origin/main
done

NOTES=outputs/auto-release-notes.txt
echo "Покращення й виправлення" > "$NOTES"
python3 scripts/publish-release.py --publish --notes-file "$NOTES"
echo "$(date '+%F %T') released KIK $VERSION ($CODE)"
