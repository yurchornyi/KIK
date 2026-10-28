#!/bin/bash
# Publishes the next KIK release when main changes what goes into the APK, the way a merge to TV-Launcher's
# main does. Run by .github/workflows/release.yml on GitHub, which first lays out the private inputs that
# never enter Git: the original APK, decoded/ (apktool d of it) and work/signing.
#
# Compares the latest GitHub release's tag with origin/main over APP; nothing changed, nothing to do.
# Otherwise bumps localization/release.json (next versionCode, next minor versionName), builds and checks
# the APK (scripts/build.sh, scripts/validate.py), pushes the version commit to main and publishes it
# (scripts/publish-release.py). The TVs take it from kik-updates as with a release made by hand.
# KIK_AUTO_RELEASE_DRY=1: build and check the next version whatever changed, then publish nothing.
set -euo pipefail
cd "$(dirname "$0")/.."
REPO=yurchornyi/KIK
DRY=${KIK_AUTO_RELEASE_DRY:-}
# What the APK is built from (scripts/build.sh); the Java checks and the docs do not change it.
APP=(localization scripts/localize.py scripts/simplify.py scripts/branding.py scripts/build.sh scripts/media3.sh
     web/kik-intro.mp4 ':(exclude)localization/*.md')

git fetch -q origin main --tags
LAST=$(gh release view --repo "$REPO" --json tagName --jq .tagName)
if [ -z "$DRY" ] && git diff --quiet "$LAST" origin/main -- "${APP[@]}"; then
  echo "Nothing in the app changed since $LAST."
  exit 0
fi

echo "Releasing what changed since $LAST"
git checkout -q --detach origin/main
python3 - <<'PY'
import json, pathlib
path = pathlib.Path('localization/release.json')
release = json.loads(path.read_text())
major, minor = release['version_name'].split('.')[:2]
release['version_code'] += 1
release['version_name'] = f'{major}.{int(minor) + 1}'
path.write_text(json.dumps(release, indent=2, ensure_ascii=False) + '\n')
PY
VERSION=$(python3 -c 'import json; print(json.load(open("localization/release.json"))["version_name"])')
CODE=$(python3 -c 'import json; print(json.load(open("localization/release.json"))["version_code"])')
if git rev-parse -q --verify "refs/tags/v$VERSION" >/dev/null; then echo "v$VERSION already exists" >&2; exit 1; fi
git commit -qam "KIK $VERSION (versionCode $CODE)"
echo "KIK $VERSION ($CODE)"

bash scripts/build.sh
python3 scripts/validate.py
if [ -n "$DRY" ]; then
  echo "Dry run: outputs/KIK-$VERSION.apk built and checked, nothing pushed or published"
  exit 0
fi

# The release targets the version commit on GitHub, so it goes to main first. If main moved meanwhile, the
# commit goes on top, unless the new commits change the app: the APK would lack them, so this run stops and
# the release their push started builds them all.
for try in 1 2 3; do
  git push -q origin HEAD:main && break
  [ "$try" = 3 ] && { echo "main kept moving: nothing published" >&2; exit 1; }
  git fetch -q origin main
  if ! git diff --quiet HEAD~1 origin/main -- "${APP[@]}"; then
    echo "main got app changes during the build: their own release publishes them"
    exit 0
  fi
  git rebase -q origin/main
done

NOTES=outputs/release-notes.txt
echo "Покращення й виправлення" > "$NOTES"
python3 scripts/publish-release.py --publish --notes-file "$NOTES"
echo "Released KIK $VERSION ($CODE)"
